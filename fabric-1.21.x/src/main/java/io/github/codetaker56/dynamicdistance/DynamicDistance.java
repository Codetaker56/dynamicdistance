package io.github.codetaker56.dynamicdistance;

import io.github.codetaker56.dynamicdistance.core.DistanceConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DynamicDistance - per-player view / chunk-send / simulation distance for dedicated
 * Fabric servers. No client mod required: the server simply honours each client's own
 * render-distance setting (clamped to a configurable window) instead of forcing one
 * global value on everyone.
 */
public final class DynamicDistance implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("DynamicDistance");

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("dynamicdistance.properties");

    private static volatile DistanceConfig config = new DistanceConfig();
    private static final PlayerDistanceService SERVICE = new PlayerDistanceService();

    // error-log throttle: same message at most once per window
    private static final ConcurrentHashMap<String, Long> LAST_LOGGED = new ConcurrentHashMap<>();
    private static final long LOG_THROTTLE_MS = 30_000L;

    public static DistanceConfig config() {
        return config;
    }

    public static PlayerDistanceService service() {
        return SERVICE;
    }

    @Override
    public void onInitialize() {
        reload(); // load config from disk (writes defaults on first run)

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                DynamicDistanceCommand.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SERVICE.onJoin(handler.player));

        // Debounced apply + offline-state pruning. Pruning here (not on a disconnect event)
        // keeps a leaving player's cached state alive while vanilla untracks them, so their
        // simulation ticket is removed at the matching level rather than leaking.
        ServerTickEvents.END_SERVER_TICK.register(SERVICE::tick);

        LOGGER.info("[dynamicdistance] ready (enabled={}, view {}..{}, sim {}..{})",
                config.enabled, config.minimumViewDistance, config.maximumViewDistance,
                config.minimumSimulationDistance, config.maximumSimulationDistance);
    }

    /** (Re)load config from disk, validate, log corrections, and normalise the file. Returns the corrections. */
    public static List<String> reload() {
        DistanceConfig fresh = new DistanceConfig();
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                fresh.load(in);
            } catch (IOException e) {
                logError("could not read " + CONFIG_FILE + " (using defaults)", e);
            }
        }
        List<String> notes = fresh.validate();
        for (String n : notes) LOGGER.warn("[dynamicdistance] config: {}", n);

        config = fresh; // volatile publish

        try {
            if (CONFIG_FILE.getParent() != null) Files.createDirectories(CONFIG_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                fresh.save(out);
            }
        } catch (IOException e) {
            logError("could not write " + CONFIG_FILE, e);
        }
        return notes;
    }

    /** Log an error at most once per throttle window per message, so a repeating fault can't spam the console. */
    public static void logError(String message, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOGGED.get(message);
        if (last == null || now - last >= LOG_THROTTLE_MS) {
            LAST_LOGGED.put(message, now);
            LOGGER.error("[dynamicdistance] {}", message, t);
        }
    }
}
