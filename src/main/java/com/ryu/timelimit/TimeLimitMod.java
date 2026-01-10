package com.ryu.timelimit;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TimeLimitMod implements DedicatedServerModInitializer {
    private static Config config;
    private static PlayerTimeManager playerTimeManager;
    private static DailyResetScheduler resetScheduler;
    private static BossBarManager bossBarManager;
    private static ResetOrchestrator resetOrchestrator;

    private static volatile MinecraftServer CURRENT_SERVER;

    @Override
    public void onInitializeServer() {
        config = Config.loadFromFabricConfigDir();

        DataStorage.init(config.dataFileName);
        DataStorage.loadData();

        bossBarManager = new BossBarManager(config);
        playerTimeManager = new PlayerTimeManager(config, bossBarManager);
        PlayerTimeManagerWrapper.init(playerTimeManager);

        resetOrchestrator = new ResetOrchestrator(config);
        resetScheduler = new DailyResetScheduler(config);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CURRENT_SERVER = server;
            playerTimeManager.loadAllFromStorage(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            playerTimeManager.onTick(server);
            if (resetScheduler != null) resetScheduler.onTick(server);
            if (resetOrchestrator != null) resetOrchestrator.onTick(server, playerTimeManager);
            long intervalTicks = 20L * config.autosaveIntervalSeconds;
            if (intervalTicks > 0 && server.getTickCount() % intervalTicks == 0) {
                DataStorage.saveAsync();
            }
        });

        // Bloqueo temprano en INIT para evitar "entra/sale" visibles
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player == null) return;
            DataStorage.ensureEntry(player.getUUID(), config.defaultTime, player.getName().getString());
            boolean immune = PermissionsUtil.isImmune(player, config);
            int remaining = DataStorage.getRemaining(player.getUUID(), config.defaultTime);
            if (!immune && remaining <= 0) {
                String msg = config.messages.timeExpired;
                handler.disconnect(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(msg)));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> playerTimeManager.onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> playerTimeManager.onPlayerQuit(handler.getPlayer()));

        // Registro de comandos
        CommandTimelimit.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataStorage.saveNow();
            bossBarManager.removeAll();
            CURRENT_SERVER = null;
        });

        System.out.println("[TimeLimitMod] Inicializado (server-side)");
    }

    public static void reloadConfig(Config newCfg) {
        if (newCfg == null) return;
        config = newCfg;
        DataStorage.init(config.dataFileName);
        bossBarManager.removeAll();
        bossBarManager = new BossBarManager(config);
        playerTimeManager = new PlayerTimeManager(config, bossBarManager);
        PlayerTimeManagerWrapper.init(playerTimeManager);
        if (resetScheduler == null) resetScheduler = new DailyResetScheduler(config);
        else resetScheduler.applyConfig(config);
        resetOrchestrator = new ResetOrchestrator(config);
    }

    public static DailyResetScheduler getResetScheduler() { return resetScheduler; }
    public static ResetOrchestrator getResetOrchestrator() { return resetOrchestrator; }
    public static Config getConfig() { return config; }

    // Broadcast a admins
    public static void broadcastToAdmins(Object msgObj) {
        if (msgObj == null || CURRENT_SERVER == null) return;
        for (ServerPlayer p : CURRENT_SERVER.getPlayerList().getPlayers()) {
            try {
                if (PermissionsUtil.hasNode(p.createCommandSourceStack(), config.permissionAdminNode, 2)) {
                    if (msgObj instanceof Component c) {
                        p.sendSystemMessage(c);
                    } else {
                        p.sendSystemMessage(TextUtil.color(config.messages.prefix + TextUtil.colorizeRaw(String.valueOf(msgObj))));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}