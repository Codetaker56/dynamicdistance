package io.github.codetaker56.dynamicdistance;

import io.github.codetaker56.dynamicdistance.core.DistanceCalculator;
import io.github.codetaker56.dynamicdistance.core.DistanceConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * /dynamicdistance ...  (all subcommands require the gamemaster / level-2 permission)
 *   reload
 *   status [player]
 *   set <player> <distance>          per-player view-distance override
 *   simulation <player> <distance>   per-player simulation-distance override
 *   reset <player>                   clear overrides
 */
public final class DynamicDistanceCommand {

    private DynamicDistanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dynamicdistance")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload").executes(DynamicDistanceCommand::reload))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx, ctx.getSource().getPlayer()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("distance", IntegerArgumentType.integer(
                                        DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX))
                                        .executes(DynamicDistanceCommand::setView))))
                .then(Commands.literal("simulation")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("distance", IntegerArgumentType.integer(
                                        DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX))
                                        .executes(DynamicDistanceCommand::setSim))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DynamicDistanceCommand::reset))));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        List<String> notes = DynamicDistance.reload();
        DynamicDistance.service().applyAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "DynamicDistance config reloaded" + (notes.isEmpty() ? "" : " (" + notes.size() + " value(s) corrected)")
                        + " and reapplied to all players."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Specify a player: /dynamicdistance status <player>"));
            return 0;
        }
        PlayerDistanceService svc = DynamicDistance.service();
        DistanceConfig cfg = DynamicDistance.config();

        int serverMax = svc.serverViewDistance(player);
        int requested = player.requestedViewDistance();
        int view = svc.effectiveView(player, serverMax);
        int sim = svc.computeSimulation(player);
        int vOver = svc.viewOverride(player.getUUID());
        int sOver = svc.simOverride(player.getUUID());
        String override = (vOver == DistanceCalculator.NO_OVERRIDE && sOver == DistanceCalculator.NO_OVERRIDE)
                ? "none"
                : (vOver != DistanceCalculator.NO_OVERRIDE ? "view=" + vOver + " " : "")
                        + (sOver != DistanceCalculator.NO_OVERRIDE ? "sim=" + sOver : "");

        String msg = "DynamicDistance — " + player.getName().getString() + "\n"
                + "  enabled            : " + cfg.enabled + (cfg.enabled ? "" : "  (disabled — values below are vanilla)") + "\n"
                + "  client requested   : " + requested + "\n"
                + "  effective view     : " + view + "\n"
                + "  effective send     : " + view + "  (send == view in vanilla)\n"
                + "  effective sim      : " + sim + "\n"
                + "  manual override    : " + override + "\n"
                + "  server view max    : " + serverMax + "\n"
                + "  config view range  : " + cfg.minimumViewDistance + ".." + cfg.maximumViewDistance + "\n"
                + "  config sim range   : " + cfg.minimumSimulationDistance + ".." + cfg.maximumSimulationDistance;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int setView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!requireOverrides(ctx)) return 0;
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        DynamicDistance.service().setViewOverride(player, distance);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTickCount());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set view override for " + player.getName().getString() + " to " + distance
                        + " (resets when they disconnect)."), true);
        return 1;
    }

    private static int setSim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!requireOverrides(ctx)) return 0;
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        DynamicDistance.service().setSimOverride(player, distance);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTickCount());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set simulation override for " + player.getName().getString() + " to " + distance
                        + " (resets when they disconnect)."), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DynamicDistance.service().clearOverrides(player);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTickCount());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cleared overrides for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static boolean requireOverrides(CommandContext<CommandSourceStack> ctx) {
        if (!DynamicDistance.config().allowPerPlayerOverrides) {
            ctx.getSource().sendFailure(Component.literal("Per-player overrides are disabled (allowPerPlayerOverrides=false)."));
            return false;
        }
        return true;
    }
}
