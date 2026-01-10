package com.ryu.timelimit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTimeManager {
    private final Config config;
    private final BossBarManager bossBarManager;
    private final DisplayManager displayManager;

    private final Map<UUID, Long> lastUpdateMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> firedWarnings = new ConcurrentHashMap<>();
    private final Set<UUID> welcomedThisSession = ConcurrentHashMap.newKeySet();

    // AFK tracking
    private final Map<UUID, AfkState> afkStates = new ConcurrentHashMap<>();

    private static final class AfkState {
        double x, y, z;
        float yRot;
        long lastActiveMs;
        boolean afk;
    }

    public PlayerTimeManager(Config config, BossBarManager bossBarManager) {
        this.config = config;
        this.bossBarManager = bossBarManager;
        this.displayManager = new DisplayManager(config);
    }

    public void loadAllFromStorage(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            DataStorage.ensureEntry(id, effectiveDefaultTime(p), p.getName().getString());
            firedWarnings.put(id, new HashSet<>());
            lastUpdateMillis.put(id, now);
            initAfkState(p);
        }
    }

    private void initAfkState(ServerPlayer p) {
        AfkState s = new AfkState();
        s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
        s.yRot = p.getYRot();
        s.lastActiveMs = System.currentTimeMillis();
        s.afk = false;
        afkStates.put(p.getUUID(), s);
    }

    private void updateAfk(ServerPlayer p, long now) {
        if (!config.afk.enabled) return;
        AfkState s = afkStates.computeIfAbsent(p.getUUID(), k -> {
            AfkState ns = new AfkState();
            ns.x = p.getX(); ns.y = p.getY(); ns.z = p.getZ();
            ns.yRot = p.getYRot();
            ns.lastActiveMs = now;
            return ns;
        });
        double dx = Math.abs(p.getX() - s.x);
        double dy = Math.abs(p.getY() - s.y);
        double dz = Math.abs(p.getZ() - s.z);
        float dRot = Math.abs(p.getYRot() - s.yRot);

        boolean moved = dx > config.afk.moveThreshold || dy > config.afk.moveThreshold || dz > config.afk.moveThreshold || dRot > config.afk.rotationThreshold;
        if (moved) {
            s.lastActiveMs = now;
            s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
            s.yRot = p.getYRot();
            s.afk = false;
        } else {
            long inactive = now - s.lastActiveMs;
            s.afk = inactive >= (config.afk.inactivitySeconds * 1000L);
        }
    }

    private int effectiveDefaultTime(ServerPlayer p) {
        if (config.profiles != null && !config.profiles.isEmpty()) {
            for (Config.Profile pr : config.profiles) {
                if (pr == null || pr.node == null || pr.node.isBlank()) continue;
                if (PermissionsUtil.hasNode(p.createCommandSourceStack(), pr.node, 0)) {
                    return Math.max(0, pr.defaultTime);
                }
            }
        }
        return Math.max(0, config.defaultTime);
    }

    public void onTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        List<ServerPlayer> toKick = new ArrayList<>();

        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            int limit = effectiveDefaultTime(p);

            DataStorage.ensureEntry(id, limit, p.getName().getString());
            firedWarnings.putIfAbsent(id, new HashSet<>());
            lastUpdateMillis.putIfAbsent(id, now);
            updateAfk(p, now);

            boolean immune = PermissionsUtil.isImmune(p, config);
            boolean paused = DataStorage.isPaused(id);
            boolean afk = afkStates.getOrDefault(id, new AfkState()).afk;

            if (!immune && !paused && !(config.afk.enabled && config.afk.pauseCounting && afk)) {
                long last = lastUpdateMillis.get(id);
                long deltaMs = Math.max(0L, now - last);
                if (deltaMs >= 1000L) {
                    int dec = (int) (deltaMs / 1000L);
                    int current = DataStorage.getRemaining(id, limit);
                    int newRem = Math.max(0, current - dec);
                    DataStorage.setRemaining(id, newRem);
                    long remainder = deltaMs % 1000L;
                    lastUpdateMillis.put(id, now - remainder);
                }
            } else {
                lastUpdateMillis.put(id, now);
            }

            int remaining = DataStorage.getRemaining(id, limit);

            // HUD según preferencia
            DataStorage.DisplayMode mode = DataStorage.getDisplayMode(id);
            if (mode == DataStorage.DisplayMode.BOSSBAR) {
                if (immune) {
                    bossBarManager.remove(p);
                } else {
                    bossBarManager.addOrUpdate(p, remaining, limit);
                }
            } else {
                bossBarManager.remove(p);
                displayManager.update(p, remaining, limit);
            }

            if (!immune) {
                for (Config.Warning w : config.warnings) {
                    if (w == null) continue;
                    int at = Math.max(0, w.time);
                    Set<Integer> fired = firedWarnings.get(id);
                    if (remaining <= at && !fired.contains(at)) {
                        if (w.title != null && !w.title.isBlank()) {
                            p.displayClientMessage(TextUtil.color(w.title), true);
                        }
                        if (w.message != null && !w.message.isBlank()) {
                            String msg = TextUtil.applyPlaceholders(w.message, remaining);
                            p.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(msg)));
                        }
                        fired.add(at);
                    }
                }

                if (remaining <= 0 && config.kickOnExpire) {
                    toKick.add(p);
                    bossBarManager.remove(p);
                    displayManager.remove(p);
                }
            }
        }

        if (!toKick.isEmpty()) {
            server.execute(() -> {
                for (ServerPlayer p : toKick) {
                    String msg = config.kickMessage.replace("%LIMIT%", TextUtil.formatTimeHMS(effectiveDefaultTime(p)));
                    p.connection.disconnect(TextUtil.color(msg));
                    bossBarManager.remove(p);
                    displayManager.remove(p);
                }
            });
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID id = player.getUUID();
        int limit = effectiveDefaultTime(player);
        DataStorage.ensureEntry(id, limit, player.getName().getString());
        firedWarnings.put(id, new HashSet<>());
        lastUpdateMillis.put(id, System.currentTimeMillis());
        initAfkState(player);

        int remaining = DataStorage.getRemaining(id, limit);

        if (config.sendWelcomeOnJoin && welcomedThisSession.add(id)) {
            String welcome = TextUtil.applyPlaceholders(config.messages.welcome, remaining);
            player.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(welcome)));
        }

        // Mostrar HUD según preferencia
        DataStorage.DisplayMode mode = DataStorage.getDisplayMode(id);
        if (mode == DataStorage.DisplayMode.BOSSBAR) {
            if (PermissionsUtil.isImmune(player, config)) {
                bossBarManager.remove(player);
            } else {
                bossBarManager.addOrUpdate(player, remaining, limit);
            }
        } else {
            bossBarManager.remove(player);
            displayManager.update(player, remaining, limit);
        }
    }

    public void onPlayerQuit(ServerPlayer player) {
        bossBarManager.remove(player);
        displayManager.remove(player);
        UUID id = player.getUUID();
        lastUpdateMillis.remove(id);
        firedWarnings.remove(id);
        welcomedThisSession.remove(id);
        afkStates.remove(id);
    }

    public void refreshHud(ServerPlayer player) {
        UUID id = player.getUUID();
        int limit = effectiveDefaultTime(player);
        int remaining = DataStorage.getRemaining(id, limit);
        firedWarnings.put(id, new HashSet<>());
        lastUpdateMillis.put(id, System.currentTimeMillis());
        DataStorage.DisplayMode mode = DataStorage.getDisplayMode(id);
        if (mode == DataStorage.DisplayMode.BOSSBAR) {
            if (PermissionsUtil.isImmune(player, config)) {
                bossBarManager.remove(player);
            } else {
                bossBarManager.addOrUpdate(player, remaining, limit);
            }
        } else {
            bossBarManager.remove(player);
            displayManager.update(player, remaining, limit);
        }
    }

    public void refreshHudForAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            refreshHud(p);
        }
    }

    public void setRemainingSeconds(ServerPlayer player, int remainingSeconds) {
        UUID id = player.getUUID();
        DataStorage.setRemaining(id, Math.max(0, remainingSeconds));
        firedWarnings.put(id, new HashSet<>());
        lastUpdateMillis.put(id, System.currentTimeMillis());
        refreshHud(player);
        DataStorage.saveAsync();
    }

    public void addSeconds(ServerPlayer player, int seconds) {
        UUID id = player.getUUID();
        int addSec = Math.max(0, seconds);
        DataStorage.addRemaining(id, addSec);
        firedWarnings.put(id, new HashSet<>());
        lastUpdateMillis.put(id, System.currentTimeMillis());
        refreshHud(player);
        String msg = config.messages.timeAdded.replace("%time%", TextUtil.formatTimeHMS(addSec));
        player.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(msg)));
        DataStorage.saveAsync();
    }

    public void setPaused(ServerPlayer player, boolean paused) {
        UUID id = player.getUUID();
        DataStorage.setPaused(id, paused);
        lastUpdateMillis.put(id, System.currentTimeMillis());
        String msg = config.messages.paused;
        player.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(msg)));
        DataStorage.saveAsync();
    }

    public void resetUsed(ServerPlayer player) {
        UUID id = player.getUUID();
        int limit = effectiveDefaultTime(player);
        DataStorage.resetToDefault(id, limit);
        firedWarnings.put(id, new HashSet<>());
        lastUpdateMillis.put(id, System.currentTimeMillis());
        refreshHud(player);
        String msg = config.messages.timeReset.replace("%time%", TextUtil.formatTimeHMS(limit));
        player.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(msg)));
        DataStorage.saveAsync();
    }

    public void setRemainingSecondsBatch(Collection<ServerPlayer> targets, int seconds) {
        for (ServerPlayer p : targets) setRemainingSeconds(p, seconds);
    }
    public void addSecondsBatch(Collection<ServerPlayer> targets, int seconds) {
        for (ServerPlayer p : targets) addSeconds(p, seconds);
    }
    public void setPausedBatch(Collection<ServerPlayer> targets, boolean paused) {
        for (ServerPlayer p : targets) setPaused(p, paused);
    }
    public void resetBatch(Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) resetUsed(p);
    }

    public void resetAllToDefaultIncludingOffline(int defaultSeconds, MinecraftServer server) {
        DataStorage.resetAllToDefault(defaultSeconds);
        firedWarnings.clear();

        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            lastUpdateMillis.put(id, now);
            refreshHud(p);
        }

        DataStorage.saveAsync();
        System.out.println("[TimeLimitMod] Reset aplicado a todos (incl. offline).");
    }

    public boolean resetOfflineBySpec(String spec) {
        UUID id = DataStorage.lookupByNameOrUuid(spec);
        if (id == null) return false;
        DataStorage.resetToDefault(id, config.defaultTime);
        DataStorage.saveAsync();
        return true;
    }
}