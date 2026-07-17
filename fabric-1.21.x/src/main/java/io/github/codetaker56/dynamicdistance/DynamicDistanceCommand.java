package io.github.codetaker56.dynamicdistance;

import io.github.codetaker56.dynamicdistance.core.DistanceCalculator;
import io.github.codetaker56.dynamicdistance.core.DistanceConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /dynamicdistance ...  (all subcommands require permission level 2)
 *   reload
 *   status [player]
 *   set <player> <distance>          per-player view-distance override
 *   simulation <player> <distance>   per-player simulation-distance override
 *   reset <player>                   clear overrides
 */
public final class DynamicDistanceCommand {

    private DynamicDistanceCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("dynamicdistance")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("reload").executes(DynamicDistanceCommand::reload))
                .then(literal("status")
                        .executes(ctx -> status(ctx, ctx.getSource().getPlayer()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> status(ctx, EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("set")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("distance", IntegerArgumentType.integer(
                                        DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX))
                                        .executes(DynamicDistanceCommand::setView))))
                .then(literal("simulation")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("distance", IntegerArgumentType.integer(
                                        DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX))
                                        .executes(DynamicDistanceCommand::setSim))))
                .then(literal("reset")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(DynamicDistanceCommand::reset))));
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        List<String> notes = DynamicDistance.reload();
        DynamicDistance.service().applyAll(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal(
                "DynamicDistance config reloaded" + (notes.isEmpty() ? "" : " (" + notes.size() + " value(s) corrected)")
                        + " and reapplied to all players."), true);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Specify a player: /dynamicdistance status <player>"));
            return 0;
        }
        PlayerDistanceService svc = DynamicDistance.service();
        DistanceConfig cfg = DynamicDistance.config();

        int serverMax = svc.serverViewDistance(player);
        int requested = player.getViewDistance();
        int view = svc.effectiveView(player, serverMax);
        int sim = svc.computeSimulation(player);
        int vOver = svc.viewOverride(player.getUuid());
        int sOver = svc.simOverride(player.getUuid());
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
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int setView(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        if (!requireOverrides(ctx)) return 0;
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        DynamicDistance.service().setViewOverride(player, distance);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTicks());
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Set view override for " + player.getName().getString() + " to " + distance
                        + " (resets when they disconnect)."), true);
        return 1;
    }

    private static int setSim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        if (!requireOverrides(ctx)) return 0;
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        DynamicDistance.service().setSimOverride(player, distance);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTicks());
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Set simulation override for " + player.getName().getString() + " to " + distance
                        + " (resets when they disconnect)."), true);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        DynamicDistance.service().clearOverrides(player);
        DynamicDistance.service().applyNow(player, ctx.getSource().getServer().getTicks());
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Cleared overrides for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static boolean requireOverrides(CommandContext<ServerCommandSource> ctx) {
        if (!DynamicDistance.config().allowPerPlayerOverrides) {
            ctx.getSource().sendError(Text.literal("Per-player overrides are disabled (allowPerPlayerOverrides=false)."));
            return false;
        }
        return true;
    }
}
