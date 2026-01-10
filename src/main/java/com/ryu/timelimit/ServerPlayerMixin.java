package com.ryu.timelimit.mixin;

import com.ryu.timelimit.DataStorage;
import com.ryu.timelimit.DataStorage.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Shadow public ServerGamePacketListenerImpl connection;

    // Bloquea cualquier intento de escribir en ActionBar si el jugador tiene HUD = NONE
    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void timelimit$blockActionBarWhenHudNone(Component component, boolean actionBar, CallbackInfo ci) {
        if (!actionBar) return; // solo nos importa el action bar

        ServerPlayer self = (ServerPlayer) (Object) this;
        DisplayMode mode = DataStorage.getDisplayMode(self.getUUID());
        if (mode != DisplayMode.NONE) return;

        // El jugador tiene HUD en NONE → limpiar y bloquear cualquier texto en action bar
        try {
            if (this.connection != null) {
                this.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
            }
        } catch (Throwable ignored) {
            // Si el paquete cambia en tu mapeo, cancelar igual para que no se vuelva a pintar
        }
        ci.cancel();
    }
}