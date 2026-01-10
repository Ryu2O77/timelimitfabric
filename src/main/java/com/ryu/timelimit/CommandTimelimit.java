package com.ryu.timelimit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CommandTimelimit {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = buildRoot("timelimit");
            LiteralArgumentBuilder<CommandSourceStack> alias = buildRoot("tl");
            dispatcher.register(root);
            dispatcher.register(alias);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String literal) {
        return Commands.literal(literal)

                // Ayuda
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            sendInfoLines(src,
                                    "Comandos:",
                                    "/timelimit remaining                      (tu tiempo)",
                                    "/timelimit hud bossbar|actionbar|none     (preferencia propia)",
                                    "/timelimit nextreset",
                                    "/timelimit admin hud <targets> bossbar|actionbar|none    [admin]",
                                    "/timelimit admin setremaining <targets> <duración>        [admin]",
                                    "/timelimit admin add <targets> <duración>                 [admin]",
                                    "/timelimit admin decrease <targets> <duración>            [admin]",
                                    "/timelimit admin pause <targets>                          [admin]",
                                    "/timelimit admin resume <targets>                         [admin]",
                                    "/timelimit admin reset <targets>                          [admin]",
                                    "/timelimit admin reset offline <name|uuid>                [admin]",
                                    "/timelimit admin resetall                                 [admin]",
                                    "/timelimit admin bypass add|remove|list <name|uuid>       [admin]",
                                    "/timelimit admin purge <days>                             [admin]",
                                    "/timelimit admin reload                                   [admin]"
                            );
                            return 1;
                        })
                )

                // Público: ver su tiempo restante
                .then(Commands.literal("remaining")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer self = src.getPlayer();
                            if (self == null) { sendErr(src, "Solo jugadores."); return 0; }
                            int def = TimeLimitMod.getConfig().defaultTime;
                            int remaining = DataStorage.getRemaining(self.getUUID(), def);
                            sendInfo(src, "Tu tiempo restante: " + TextUtil.formatTimeHMS(remaining));
                            return 1;
                        })
                )

                // Público: HUD personal (solo self)
                .then(Commands.literal("hud")
                        .then(Commands.literal("bossbar").executes(ctx -> setSelfHud(ctx, DataStorage.DisplayMode.BOSSBAR)))
                        .then(Commands.literal("actionbar").executes(ctx -> setSelfHud(ctx, DataStorage.DisplayMode.ACTIONBAR)))
                        .then(Commands.literal("none").executes(ctx -> setSelfHud(ctx, DataStorage.DisplayMode.NONE)))
                )

                // Próximo reset
                .then(Commands.literal("nextreset")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            DailyResetScheduler sched = TimeLimitMod.getResetScheduler();
                            Config cfg = TimeLimitMod.getConfig();
                            if (!cfg.dailyResetEnabled) { sendInfo(src, "El reset diario está deshabilitado."); return 1; }
                            sendInfo(src, "Próximo reset: " + sched.getNextResetDisplay());
                            sendInfo(src, "Tiempo restante: " + sched.getTimeUntilDisplay());
                            return 1;
                        })
                )

                // Admin: grupo unificado "admin" (subcomandos visibles antes de elegir targets)
                .then(Commands.literal("admin")
                        .requires(s -> PermissionsUtil.hasNode(s, TimeLimitMod.getConfig().permissionAdminNode, 2))

                        // HUD
                        .then(Commands.literal("hud")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    CommandSourceStack src = ctx.getSource();
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    String modeStr = StringArgumentType.getString(ctx, "mode");
                                                    DataStorage.DisplayMode mode = DataStorage.DisplayMode.parse(modeStr);
                                                    if (mode == DataStorage.DisplayMode.SCOREBOARD) mode = DataStorage.DisplayMode.ACTIONBAR;
                                                    for (ServerPlayer p : targets) {
                                                        DataStorage.setDisplayMode(p.getUUID(), mode);
                                                        PlayerTimeManagerWrapper.get().refreshHud(p);
                                                    }
                                                    sendOk(src, "HUD " + mode + " aplicado a " + targets.size() + " jugador(es).");
                                                    DataStorage.saveAsync();
                                                    return targets.size();
                                                })
                                        )
                                )
                        )

                        // setremaining
                        .then(Commands.literal("setremaining")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("duration", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSourceStack src = ctx.getSource();
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    String spec = StringArgumentType.getString(ctx, "duration");
                                                    try {
                                                        int seconds = DurationParser.parseSeconds(spec);
                                                        PlayerTimeManagerWrapper.get().setRemainingSecondsBatch(targets, seconds);
                                                        sendOk(src, "Restante establecido: " + TextUtil.formatTimeHMS(seconds) +
                                                                " para " + targets.size() + " jugador(es).");
                                                        return targets.size();
                                                    } catch (IllegalArgumentException e) {
                                                        sendErr(src, "Duración inválida: " + spec + " (ej: 30s, 10m, 1h30m)");
                                                        return 0;
                                                    }
                                                })
                                        )
                                )
                        )

                        // add
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("duration", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSourceStack src = ctx.getSource();
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    String spec = StringArgumentType.getString(ctx, "duration");
                                                    try {
                                                        int seconds = DurationParser.parseSeconds(spec);
                                                        PlayerTimeManagerWrapper.get().addSecondsBatch(targets, seconds);
                                                        sendOk(src, "Se añadieron " + TextUtil.formatTimeHMS(seconds) +
                                                                " a " + targets.size() + " jugador(es).");
                                                        return targets.size();
                                                    } catch (IllegalArgumentException e) {
                                                        sendErr(src, "Duración inválida: " + spec + " (ej: 30s, 10m, 1h30m)");
                                                        return 0;
                                                    }
                                                })
                                        )
                                )
                        )

                        // decrease
                        .then(Commands.literal("decrease")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("duration", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSourceStack src = ctx.getSource();
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    String spec = StringArgumentType.getString(ctx, "duration");
                                                    try {
                                                        int seconds = DurationParser.parseSeconds(spec);
                                                        int def = TimeLimitMod.getConfig().defaultTime;
                                                        for (ServerPlayer p : targets) {
                                                            int current = DataStorage.getRemaining(p.getUUID(), def);
                                                            int newRem = Math.max(0, current - seconds);
                                                            DataStorage.setRemaining(p.getUUID(), newRem);
                                                            PlayerTimeManagerWrapper.get().refreshHud(p);
                                                        }
                                                        DataStorage.saveAsync();
                                                        sendOk(src, "Se descontaron " + TextUtil.formatTimeHMS(seconds) +
                                                                " a " + targets.size() + " jugador(es).");
                                                        return targets.size();
                                                    } catch (IllegalArgumentException e) {
                                                        sendErr(src, "Duración inválida: " + spec + " (ej: 30s, 10m, 1h30m)");
                                                        return 0;
                                                    }
                                                })
                                        )
                                )
                        )

                        // pause
                        .then(Commands.literal("pause")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            PlayerTimeManagerWrapper.get().setPausedBatch(targets, true);
                                            sendOk(src, "Pausa activada para " + targets.size() + " jugador(es).");
                                            return targets.size();
                                        })
                                )
                        )

                        // resume
                        .then(Commands.literal("resume")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            PlayerTimeManagerWrapper.get().setPausedBatch(targets, false);
                                            sendOk(src, "Pausa desactivada para " + targets.size() + " jugador(es).");
                                            return targets.size();
                                        })
                                )
                        )

                        // reset (online)
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            PlayerTimeManagerWrapper.get().resetBatch(targets);
                                            sendOk(src, "Reseteados " + targets.size() + " jugador(es) al default.");
                                            return targets.size();
                                        })
                                )
                                // reset offline
                                .then(Commands.literal("offline")
                                        .then(Commands.argument("spec", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggestOfflinePlayers(ctx.getSource(), builder))
                                                .executes(ctx -> {
                                                    CommandSourceStack src = ctx.getSource();
                                                    String spec = StringArgumentType.getString(ctx, "spec");
                                                    boolean ok = PlayerTimeManagerWrapper.get().resetOfflineBySpec(spec);
                                                    if (ok) { sendOk(src, "Jugador offline reseteado: " + spec); return 1; }
                                                    else { sendErr(src, "No se encontró UUID/nombre: " + spec); return 0; }
                                                })
                                        )
                                )
                        )

                        // resetall
                        .then(Commands.literal("resetall")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    Config cfg = TimeLimitMod.getConfig();
                                    PlayerTimeManagerWrapper.get().resetAllToDefaultIncludingOffline(cfg.defaultTime, src.getServer());
                                    sendOk(src, "Reset global aplicado al default.");
                                    return 1;
                                })
                        )

                        // bypass
                        .then(buildBypassGroupUnderAdmin())

                        // purge (stub informativo)
                        .then(Commands.literal("purge")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 36500))
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            int days = IntegerArgumentType.getInteger(ctx, "days");
                                            long cutoffMs = System.currentTimeMillis() - (days * 24L * 3600L * 1000L);
                                            int candidates = 0;
                                            for (var e : new ArrayList<>(DataStorage.allEntries())) {
                                                if (e == null) continue;
                                                boolean online = src.getServer().getPlayerList().getPlayer(e.playerId) != null;
                                                if (!online && e.lastReset < cutoffMs) candidates++;
                                            }
                                            sendOk(src, "Encontrados " + candidates + " candidatos a purga (stub).");
                                            return candidates;
                                        })
                                )
                        )

                        // reload
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    Config newCfg = Config.loadFromFabricConfigDir();
                                    TimeLimitMod.reloadConfig(newCfg);
                                    TimeLimitMod.broadcastToAdmins(TextUtil.color(TimeLimitMod.getConfig().messages.prefix + TextUtil.colorizeRaw(TimeLimitMod.getConfig().messages.reloadApplied)));
                                    sendOk(src, "Configuración recargada.");
                                    return 1;
                                })
                        )
                );
    }

    // Helpers públicos
    private static int setSelfHud(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, DataStorage.DisplayMode mode) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer self = src.getPlayer();
        if (self == null) { sendErr(src, "Solo jugadores."); return 0; }
        DataStorage.setDisplayMode(self.getUUID(), mode);
        PlayerTimeManagerWrapper.get().refreshHud(self);
        sendOk(src, "HUD cambiado a " + mode + ".");
        DataStorage.saveAsync();
        return 1;
    }

    // Sugerencias: jugadores OFFLINE (nombres + UUIDs) conocidos en players.json
    private static CompletableFuture<Suggestions> suggestOfflinePlayers(CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Set<String> suggestions = new HashSet<>();
        var server = source.getServer();
        for (var e : DataStorage.allEntries()) {
            if (e == null) continue;
            boolean online = server.getPlayerList().getPlayer(e.playerId) != null;
            if (!online) {
                if (e.name != null && !e.name.isBlank()) suggestions.add(e.name);
                if (e.playerId != null) suggestions.add(e.playerId.toString());
            }
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    // Sugerencias: jugadores conocidos (online + offline) para bypass add
    private static CompletableFuture<Suggestions> suggestKnownPlayers(CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Set<String> suggestions = new HashSet<>();
        var server = source.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            suggestions.add(p.getName().getString());
            suggestions.add(p.getUUID().toString());
        }
        for (var e : DataStorage.allEntries()) {
            if (e == null) continue;
            if (e.name != null && !e.name.isBlank()) suggestions.add(e.name);
            if (e.playerId != null) suggestions.add(e.playerId.toString());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    // Sugerencias: entradas actuales del bypass para remove
    private static CompletableFuture<Suggestions> suggestBypassList(CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Set<String> suggestions = new HashSet<>(TimeLimitMod.getConfig().immunePlayers);
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    // BYPASS subárbol dentro de "admin"
    private static LiteralArgumentBuilder<CommandSourceStack> buildBypassGroupUnderAdmin() {
        return Commands.literal("bypass")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            String list = String.join(", ", TimeLimitMod.getConfig().immunePlayers);
                            sendInfo(ctx.getSource(), "Bypass: " + (list.isEmpty() ? "(vacío)" : list));
                            return 1;
                        })
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("spec", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKnownPlayers(ctx.getSource(), builder))
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String spec = StringArgumentType.getString(ctx, "spec");
                                    Config cfg = TimeLimitMod.getConfig();
                                    if (!cfg.immunePlayers.contains(spec)) cfg.immunePlayers.add(spec);
                                    Config.save(cfg);
                                    sendOk(src, "Añadido a bypass: " + spec);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("spec", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestBypassList(ctx.getSource(), builder))
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String spec = StringArgumentType.getString(ctx, "spec");
                                    Config cfg = TimeLimitMod.getConfig();
                                    boolean removed = cfg.immunePlayers.removeIf(s -> s.equalsIgnoreCase(spec));
                                    Config.save(cfg);
                                    if (removed) sendOk(src, "Quitado de bypass: " + spec);
                                    else sendErr(src, "No estaba en la lista: " + spec);
                                    return removed ? 1 : 0;
                                })
                        )
                );
    }

    // Helpers mensajes
    private static void sendOk(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> TextUtil.color(TimeLimitMod.getConfig().messages.prefix + TextUtil.colorizeRaw(msg)), true);
    }
    private static void sendErr(CommandSourceStack src, String msg) {
        src.sendFailure(TextUtil.color(TimeLimitMod.getConfig().messages.prefix + TextUtil.colorizeRaw(msg)));
    }
    private static void sendInfo(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> TextUtil.color(TimeLimitMod.getConfig().messages.prefix + TextUtil.colorizeRaw(msg)), false);
    }
    private static void sendInfoLines(CommandSourceStack src, String... lines) {
        if (lines == null) return;
        for (String line : lines) sendInfo(src, line);
    }
}