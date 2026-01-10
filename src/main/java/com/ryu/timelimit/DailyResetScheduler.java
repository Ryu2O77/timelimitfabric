package com.ryu.timelimit;

import net.minecraft.server.MinecraftServer;

import java.time.*;

public class DailyResetScheduler {
    private volatile Config config;
    private volatile ZoneId zone;
    private volatile LocalTime resetTime;
    private volatile Instant nextResetInstant;

    public DailyResetScheduler(Config config) {
        applyConfig(config);
    }

    public synchronized void applyConfig(Config cfg) {
        this.config = cfg != null ? cfg : new Config();

        ZoneId z;
        try { z = ZoneId.of(this.config.reset.timezone); } catch (Exception e) { z = ZoneId.systemDefault(); }
        this.zone = z;

        LocalTime t;
        try { t = LocalTime.parse(this.config.reset.time); } catch (Exception e) { t = LocalTime.of(4, 0); }
        this.resetTime = t;

        recomputeNextReset(Instant.now());
    }

    private synchronized void recomputeNextReset(Instant now) {
        ZonedDateTime znow = ZonedDateTime.ofInstant(now, zone);
        ZonedDateTime candidate = ZonedDateTime.of(znow.toLocalDate(), resetTime, zone);
        if (!candidate.isAfter(znow)) {
            candidate = candidate.plusDays(1);
        }
        this.nextResetInstant = candidate.toInstant();
    }

    public void onTick(MinecraftServer server) {
        if (!config.dailyResetEnabled) return;
        if (server.getTickCount() % 20 != 0) return; // 1s

        Instant now = Instant.now();
        Instant next = this.nextResetInstant;
        if (next != null && !now.isBefore(next)) {
            if (config.incrementalResetEnabled) {
                TimeLimitMod.getResetOrchestrator().startIncremental(server, config.defaultTime);
            } else {
                PlayerTimeManagerWrapper.get().resetAllToDefaultIncludingOffline(config.defaultTime, server);
                DataStorage.saveAsync();
            }
            synchronized (this) { recomputeNextReset(now.plusSeconds(1)); }
            TimeLimitMod.broadcastToAdmins(TextUtil.color(
                    Config.loadFromFabricConfigDir().messages.prefix +
                            TextUtil.colorizeRaw(Config.loadFromFabricConfigDir().messages.broadcastReset.replace("%next%", getNextResetDisplay()))
            ));
        }
    }

    public synchronized String getNextResetDisplay() {
        if (nextResetInstant == null) return "n/a";
        ZonedDateTime zdt = ZonedDateTime.ofInstant(nextResetInstant, zone);
        return zdt.toString();
    }

    public synchronized long secondsUntilNextReset() {
        if (!config.dailyResetEnabled || nextResetInstant == null) return -1L;
        Duration d = Duration.between(Instant.now(), nextResetInstant);
        long s = d.getSeconds();
        return Math.max(0L, s);
    }

    public synchronized String getTimeUntilDisplay() {
        long s = secondsUntilNextReset();
        if (s < 0) return "deshabilitado";
        int sec = (int) Math.min(Integer.MAX_VALUE, s);
        return TextUtil.formatTimeHMS(sec);
    }
}