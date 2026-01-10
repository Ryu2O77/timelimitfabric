package com.ryu.timelimit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Build anterior con un único fix:
 * - Si el jugador tiene displayMode="NONE"/"OFF" en players.json, se oculta el HUD por completo:
 *   limpia el ActionBar y no dibuja nada.
 * Nada más cambia.
 */
public final class PlayerTimeManagerWrapper {
    private static final PlayerTimeManagerWrapper INSTANCE = new PlayerTimeManagerWrapper();
    private static Object delegate;

    public static PlayerTimeManagerWrapper get() { return INSTANCE; }
    public static void init(Object playerTimeManager) { delegate = playerTimeManager; }

    // Descuento básico por segundo (sin cambios)
    public void tickOneSecond(ServerPlayer player) {
        if (player == null) return;
        Config cfg = TimeLimitMod.getConfig();

        int def = cfg.defaultTime;
        int remaining = DataStorage.getRemaining(player.getUUID(), def);

        if (remaining > 0) {
            DataStorage.setRemaining(player.getUUID(), remaining - 1);
            refreshHud(player);
            DataStorage.saveAsync();
            return;
        }

        // Manejo de expiración (sin cambios)
        if (cfg.kickOnExpire) {
            String msg = (cfg.messages != null && cfg.messages.prefix != null ? cfg.messages.prefix : "")
                    + (cfg.kickMessage == null ? "Tu tiempo de juego de hoy ha terminado. ¡Nos vemos mañana!" : cfg.kickMessage);
            player.connection.disconnect(TextUtil.color(TextUtil.colorizeRaw(msg)));
        } else {
            String chat = (cfg.messages != null && cfg.messages.prefix != null ? cfg.messages.prefix : "")
                    + (cfg.messages != null && cfg.messages.timeExpired != null ? cfg.messages.timeExpired : "Tu tiempo de juego de hoy ha terminado. ¡Nos vemos mañana!");
            player.sendSystemMessage(TextUtil.color(TextUtil.colorizeRaw(chat)));
        }
        refreshHud(player);
    }

    // --------- Batch ops (sin cambios) ----------
    public void setRemainingSecondsBatch(Iterable<ServerPlayer> targets, int seconds) {
        for (ServerPlayer p : targets) {
            DataStorage.setRemaining(p.getUUID(), seconds);
            refreshHud(p);
        }
        DataStorage.saveAsync();
    }

    public void addSecondsBatch(Iterable<ServerPlayer> targets, int seconds) {
        int def = TimeLimitMod.getConfig().defaultTime;
        for (ServerPlayer p : targets) {
            int cur = DataStorage.getRemaining(p.getUUID(), def);
            DataStorage.setRemaining(p.getUUID(), Math.max(0, cur + seconds));
            refreshHud(p);
        }
        DataStorage.saveAsync();
    }

    public void resetBatch(Iterable<ServerPlayer> targets) {
        int def = TimeLimitMod.getConfig().defaultTime;
        for (ServerPlayer p : targets) {
            DataStorage.setRemaining(p.getUUID(), def);
            refreshHud(p);
        }
        DataStorage.saveAsync();
    }

    public void setPausedBatch(Iterable<ServerPlayer> targets, boolean paused) {
        for (ServerPlayer p : targets) {
            DataStorage.setPaused(p.getUUID(), paused);
            refreshHud(p);
        }
        DataStorage.saveAsync();
    }

    // ---------- ÚNICO FIX: respetar displayMode por jugador y ocultar completamente si es NONE/OFF ----------
    public void refreshHud(ServerPlayer player) {
        Config cfg = TimeLimitMod.getConfig();
        if (cfg == null || cfg.hud == null) return;

        String mode = resolveDisplayMode(player.getUUID()); // NONE | ACTION_BAR | BOSSBAR | null
        if (mode == null) mode = "BOSSBAR"; // mismo default que antes

        // Si el jugador eligió oculto → limpiar y salir (no dibujar nada más)
        if ("NONE".equalsIgnoreCase(mode) || "OFF".equalsIgnoreCase(mode)) {
            try {
                player.displayClientMessage(Component.empty(), true); // limpia ActionBar
            } catch (Throwable ignored) {}
            return;
        }

        // Si NO es action bar, asegura que el action bar quede vacío para evitar texto residual
        if (!"ACTION_BAR".equalsIgnoreCase(mode) && !"ACTIONBAR".equalsIgnoreCase(mode)) {
            try {
                player.displayClientMessage(Component.empty(), true);
            } catch (Throwable ignored) {}
            // No dibujar aquí; otros componentes (bossbar/scoreboard) los maneja tu flujo original
            return;
        }

        // Modo action bar → dibujar como siempre
        String msg = cfg.hud.actionBarMessage;
        if (msg == null || msg.isBlank()) {
            try { player.displayClientMessage(Component.empty(), true); } catch (Throwable ignored) {}
            return;
        }

        int def = cfg.defaultTime;
        int remaining = DataStorage.getRemaining(player.getUUID(), def);
        String h = String.valueOf(remaining / 3600);
        String m = String.valueOf((remaining % 3600) / 60);
        String s = String.valueOf(remaining % 60);

        msg = msg.replace("%hours%", h)
                .replace("%minutes%", m)
                .replace("%seconds%", s);

        try {
            player.displayClientMessage(TextUtil.color(TextUtil.colorizeRaw(msg)), true);
        } catch (Throwable ignored) {}
    }

    /**
     * Busca el displayMode del jugador sin depender de métodos específicos (compatibilidad).
     * Prioriza DataStorage.getDisplayMode(UUID), luego DataStorage.getHudMode(UUID).
     * Si no existen, intenta leer DataStorage.allEntries() y acceder al campo "displayMode".
     */
    private String resolveDisplayMode(UUID id) {
        // Intento: DataStorage.getDisplayMode(UUID)
        try {
            Method m = DataStorage.class.getMethod("getDisplayMode", UUID.class);
            Object r = m.invoke(null, id);
            if (r != null) return r.toString();
        } catch (Throwable ignored) {}

        // Intento: DataStorage.getHudMode(UUID)
        try {
            Method m = DataStorage.class.getMethod("getHudMode", UUID.class);
            Object r = m.invoke(null, id);
            if (r != null) return r.toString();
        } catch (Throwable ignored) {}

        // Fallback: recorrer entries y leer campo "displayMode"
        try {
            for (Object e : DataStorage.allEntries()) {
                if (e == null) continue;
                // playerId
                try {
                    Field fId = e.getClass().getField("playerId");
                    Object vId = fId.get(e);
                    UUID entryId = null;
                    if (vId instanceof UUID) entryId = (UUID) vId;
                    else if (vId != null) entryId = UUID.fromString(String.valueOf(vId));
                    if (entryId == null || !entryId.equals(id)) continue;
                } catch (Throwable ignored2) {
                    continue;
                }
                // displayMode
                try {
                    Field fMode = e.getClass().getField("displayMode");
                    Object vMode = fMode.get(e);
                    if (vMode != null) return vMode.toString();
                } catch (Throwable ignored3) {
                    // si no existe el campo, seguimos
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // ---------- Reset offline/global (sin cambios) ----------
    public boolean resetOfflineBySpec(String spec) {
        if (spec == null || spec.isBlank()) return false;

        Config cfg = TimeLimitMod.getConfig();
        UUID targetId = null;

        try { targetId = UUID.fromString(spec); } catch (IllegalArgumentException ignored) {}

        if (targetId == null) {
            for (var e : DataStorage.allEntries()) {
                if (e == null) continue;
                try {
                    Field fName = e.getClass().getField("name");
                    Object vName = fName.get(e);
                    if (vName != null && vName.toString().equalsIgnoreCase(spec)) {
                        Field fId = e.getClass().getField("playerId");
                        Object vId = fId.get(e);
                        if (vId instanceof UUID) targetId = (UUID) vId;
                        else if (vId != null) targetId = UUID.fromString(String.valueOf(vId));
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (targetId == null) return false;

        DataStorage.setRemaining(targetId, cfg.defaultTime);
        DataStorage.saveAsync();
        return true;
    }

    public void resetAllToDefaultIncludingOffline(int defaultTime, MinecraftServer server) {
        for (var e : DataStorage.allEntries()) {
            if (e == null) continue;
            try {
                Field fId = e.getClass().getField("playerId");
                Object vId = fId.get(e);
                UUID entryId = null;
                if (vId instanceof UUID) entryId = (UUID) vId;
                else if (vId != null) entryId = UUID.fromString(String.valueOf(vId));
                if (entryId == null) continue;
                DataStorage.setRemaining(entryId, defaultTime);
            } catch (Throwable ignored) {}
        }

        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                DataStorage.setRemaining(p.getUUID(), defaultTime);
                refreshHud(p);
            }
        }

        DataStorage.saveAsync();
    }
}