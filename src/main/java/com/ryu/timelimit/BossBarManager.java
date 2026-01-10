package com.ryu.timelimit;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {
    private final Config config;
    private final Map<UUID, ServerBossEvent> bars = new ConcurrentHashMap<>();

    public BossBarManager(Config config) { this.config = config; }

    private BossEvent.BossBarColor colorFrom(String s) {
        try { return BossEvent.BossBarColor.valueOf(s.toUpperCase()); } catch (Exception ignored) {}
        return BossEvent.BossBarColor.WHITE;
    }

    public void addOrUpdate(ServerPlayer player, int remainingSec, int maxSec) {
        if (maxSec <= 0) return;
        UUID id = player.getUUID();
        ServerBossEvent bar = bars.computeIfAbsent(id, k -> {
            ServerBossEvent ev = new ServerBossEvent(TextUtil.color(""), colorFrom(config.bossbar.color), BossEvent.BossBarOverlay.NOTCHED_20);
            ev.setVisible(true);
            ev.addPlayer(player);
            return ev;
        });

        String msg = TextUtil.applyPlaceholders(config.bossbar.message, remainingSec);
        bar.setName(TextUtil.color(msg));
        float progress = Math.max(0f, Math.min(1f, (float) remainingSec / (float) maxSec));
        bar.setProgress(progress);
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public void remove(ServerPlayer player) {
        ServerBossEvent bar = bars.remove(player.getUUID());
        if (bar != null) { bar.removePlayer(player); bar.setVisible(false); }
    }

    public void removeAll() {
        for (ServerBossEvent bar : bars.values()) { bar.setVisible(false); }
        bars.clear();
    }
}