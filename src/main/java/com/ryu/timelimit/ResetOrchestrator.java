package com.ryu.timelimit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ResetOrchestrator {
    private final Config config;

    private boolean inProgress = false;
    private int defaultSeconds = 0;
    private int batchesProcessed = 0;
    private long lastTick = 0L;
    private final Deque<UUID> queue = new ArrayDeque<>();

    public ResetOrchestrator(Config cfg) {
        this.config = cfg;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void startIncremental(MinecraftServer server, int defaultSeconds) {
        if (inProgress) return;
        this.defaultSeconds = Math.max(0, defaultSeconds);
        this.queue.clear();

        // Encolar todos los jugadores conocidos (offline incluidos)
        for (DataStorage.PlayerEntry e : DataStorage.allEntries()) {
            if (e != null && e.playerId != null) {
                queue.add(e.playerId);
            }
        }
        // Si no había datos previos, al menos encolar conectados
        if (queue.isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                queue.add(p.getUUID());
            }
        }

        this.inProgress = true;
        this.batchesProcessed = 0;
        this.lastTick = server.getTickCount();

        System.out.println("[TimeLimitMod] Reset incremental iniciado. Total en cola: " + queue.size());
    }

    public void onTick(MinecraftServer server, PlayerTimeManager ptm) {
        if (!inProgress) return;

        int tick = server.getTickCount();
        if ((tick - lastTick) < Math.max(1, config.resetBatchIntervalTicks)) {
            return;
        }
        lastTick = tick;

        int batchSize = Math.max(1, config.resetBatchSize);
        int processed = 0;

        while (processed < batchSize && !queue.isEmpty()) {
            UUID id = queue.poll();
            // Reset de data (actualiza remainingTime y lastReset)
            DataStorage.resetToDefault(id, defaultSeconds);

            // Si está conectado, refrescar HUD sin bienvenida
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                ptm.refreshHud(online);
            }
            processed++;
        }

        if (processed > 0) {
            batchesProcessed++;
            if (config.resetSaveEveryBatches > 0 && (batchesProcessed % config.resetSaveEveryBatches) == 0) {
                DataStorage.saveAsync();
            }
        }

        if (queue.isEmpty()) {
            inProgress = false;
            DataStorage.saveAsync();
            System.out.println("[TimeLimitMod] Reset incremental terminado. Lotes: " + batchesProcessed);
        }
    }

    public String status() {
        return "inProgress=" + inProgress +
                ", queued=" + queue.size() +
                ", batchSize=" + config.resetBatchSize +
                ", intervalTicks=" + config.resetBatchIntervalTicks;
    }
}