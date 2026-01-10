package com.ryu.timelimit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DisplayManager {
    private final Config config;

    public DisplayManager(Config config) {
        this.config = config;
    }

    // Muestra HUD no-bossbar (ActionBar). Si el jugador está en NONE, se limpia y no se dibuja nada.
    public void update(ServerPlayer player, int remainingSec, int maxSec) {
        DataStorage.DisplayMode mode = DataStorage.getDisplayMode(player.getUUID());

        // NONE → ocultar todo y limpiar ActionBar
        if (mode == DataStorage.DisplayMode.NONE) {
            player.displayClientMessage(Component.empty(), true);
            return;
        }

        // BOSSBAR lo maneja BossBarManager en PlayerTimeManager
        if (mode == DataStorage.DisplayMode.BOSSBAR) {
            return;
        }

        // Cualquier otro modo (incluye el valor legacy SCOREBOARD) → usar ActionBar
        String msg = TextUtil.applyPlaceholders(config.hud.actionBarMessage, Math.max(0, remainingSec));
        player.displayClientMessage(TextUtil.color(msg), true);
    }

    // Limpia ActionBar cuando se pide remover
    public void remove(ServerPlayer player) {
        player.displayClientMessage(Component.empty(), true);
    }
}