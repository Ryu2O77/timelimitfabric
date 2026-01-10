package com.ryu.timelimit;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PermissionsUtil {

    // Intento de usar LuckPerms Fabric Permissions API si existe; fallback a OP nivel defaultOpLevel.
    private static Boolean checkPermission(ServerPlayer player, String node, int defaultOpLevel) {
        try {
            // me.lucko.fabric.api.permissions.v0.Permissions.check(CommandSourceStack, String, int)
            Class<?> cls = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method m = cls.getMethod("check", CommandSourceStack.class, String.class, int.class);
            Object res = m.invoke(null, player.createCommandSourceStack(), node, defaultOpLevel);
            return (Boolean) res;
        } catch (Throwable ignored) {
            return player.hasPermissions(defaultOpLevel);
        }
    }

    // Permiso “admin” desde CommandSource (soporta consola)
    public static boolean hasNode(CommandSourceStack src, String node, int defaultOpLevel) {
        if (src == null) return false;
        // Consola siempre permitida
        if (src.getEntity() == null) return true;
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        if (node == null || node.isBlank()) {
            return player.hasPermissions(defaultOpLevel);
        }
        Boolean ok = checkPermission(player, node, defaultOpLevel);
        return ok != null && ok;
    }

    // Inmunidad por config o permiso de bypass
    public static boolean isImmune(ServerPlayer player, Config cfg) {
        if (player == null || cfg == null) return false;

        String name = player.getName().getString();
        UUID uuid = player.getUUID();
        for (String entry : cfg.immunePlayers) {
            if (entry == null || entry.isBlank()) continue;
            String s = entry.trim();
            if (s.equalsIgnoreCase(name)) return true;
            try {
                if (UUID.fromString(s).equals(uuid)) return true;
            } catch (IllegalArgumentException ignored) {}
        }

        if (cfg.permissionBypassNode != null && !cfg.permissionBypassNode.isBlank()) {
            Boolean ok = checkPermission(player, cfg.permissionBypassNode, 2);
            if (ok != null && ok) return true;
        }

        return player.hasPermissions(2);
    }
}