package com.ryu.timelimit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HudController {
    private static final HudController INSTANCE = new HudController();
    public static HudController get() { return INSTANCE; }
    private HudController() {}

    public void refresh(ServerPlayer player) {
        Config cfg = TimeLimitMod.getConfig();
        if (cfg == null || cfg.hud == null) return;

        // Si el HUD está desactivado (vacío/none/off), limpia y no pinta nada
        String msg = cfg.hud.actionBarMessage;
        if (msg == null || msg.isBlank()
                || "none".equalsIgnoreCase(msg)
                || "off".equalsIgnoreCase(msg)) {
            clearActionBar(player);
            return;
        }

        // Mostrar action bar con placeholders
        int remaining = remainingSeconds(player);
        String h = String.valueOf(remaining / 3600);
        String m = String.valueOf((remaining % 3600) / 60);
        String s = String.valueOf(remaining % 60);

        msg = msg.replace("%hours%", h)
                .replace("%minutes%", m)
                .replace("%seconds%", s);

        player.displayClientMessage(TextUtil.color(TextUtil.colorizeRaw(msg)), true);
    }

    public void clearActionBar(ServerPlayer player) {
        // Enviar vacío borra el último texto del action bar en el cliente
        player.displayClientMessage(Component.empty(), true);
    }

    private int remainingSeconds(ServerPlayer p) {
        Config cfg = TimeLimitMod.getConfig();
        int def = (cfg == null) ? 0 : cfg.defaultTime;
        return DataStorage.getRemaining(p.getUUID(), def);
    }
}