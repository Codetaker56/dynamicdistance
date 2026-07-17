package com.example.dynamicdistance.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Mod configuration. Backed by a plain .properties file (stdlib, no dependency,
 * trivially unit-testable). Every field is a scalar so Properties is a perfect fit.
 *
 * {@link #validate()} coerces the loaded values into a self-consistent, engine-safe
 * state and returns a list of human-readable corrections for logging. It never throws:
 * a broken config degrades to sane defaults rather than taking the server down.
 */
public final class DistanceConfig {

    // --- options (see README) ---
    public boolean enabled = true;
    public int minimumViewDistance = 3;
    public int maximumViewDistance = 32;
    public int minimumSimulationDistance = 3;
    public int maximumSimulationDistance = 12;
    public int simulationDistanceOffset = 0;
    public int updateCooldownTicks = 20;          // debounce window (1s at 20tps)
    public boolean updateOnClientSettingsChange = true;
    public boolean updateOnJoin = true;
    public boolean allowPerPlayerOverrides = true;
    public boolean debugLogging = false;

    public DistanceConfig() {}

    // ---- (de)serialization ----

    public void load(InputStream in) throws IOException {
        Properties p = new Properties();
        p.load(in);
        enabled = bool(p, "enabled", enabled);
        minimumViewDistance = integer(p, "minimumViewDistance", minimumViewDistance);
        maximumViewDistance = integer(p, "maximumViewDistance", maximumViewDistance);
        minimumSimulationDistance = integer(p, "minimumSimulationDistance", minimumSimulationDistance);
        maximumSimulationDistance = integer(p, "maximumSimulationDistance", maximumSimulationDistance);
        simulationDistanceOffset = integer(p, "simulationDistanceOffset", simulationDistanceOffset);
        updateCooldownTicks = integer(p, "updateCooldownTicks", updateCooldownTicks);
        updateOnClientSettingsChange = bool(p, "updateOnClientSettingsChange", updateOnClientSettingsChange);
        updateOnJoin = bool(p, "updateOnJoin", updateOnJoin);
        allowPerPlayerOverrides = bool(p, "allowPerPlayerOverrides", allowPerPlayerOverrides);
        debugLogging = bool(p, "debugLogging", debugLogging);
    }

    public void save(OutputStream out) throws IOException {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("minimumViewDistance", Integer.toString(minimumViewDistance));
        p.setProperty("maximumViewDistance", Integer.toString(maximumViewDistance));
        p.setProperty("minimumSimulationDistance", Integer.toString(minimumSimulationDistance));
        p.setProperty("maximumSimulationDistance", Integer.toString(maximumSimulationDistance));
        p.setProperty("simulationDistanceOffset", Integer.toString(simulationDistanceOffset));
        p.setProperty("updateCooldownTicks", Integer.toString(updateCooldownTicks));
        p.setProperty("updateOnClientSettingsChange", Boolean.toString(updateOnClientSettingsChange));
        p.setProperty("updateOnJoin", Boolean.toString(updateOnJoin));
        p.setProperty("allowPerPlayerOverrides", Boolean.toString(allowPerPlayerOverrides));
        p.setProperty("debugLogging", Boolean.toString(debugLogging));
        p.store(out, "DynamicDistance config - per-player view & simulation distance");
    }

    /** Load from a path, writing defaults first if the file does not exist. */
    public static DistanceConfig loadOrCreate(Path file) throws IOException {
        DistanceConfig cfg = new DistanceConfig();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                cfg.load(in);
            }
        }
        cfg.validate(); // always land in a consistent state
        if (Files.notExists(file)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                cfg.save(out);
            }
        }
        return cfg;
    }

    // ---- validation ----

    /**
     * Force the config into an engine-safe, self-consistent state.
     * Returns one message per correction made (empty = nothing was wrong).
     * Minimums are never allowed to exceed their maximums.
     */
    public List<String> validate() {
        List<String> notes = new ArrayList<>();

        minimumViewDistance = coerce("minimumViewDistance", minimumViewDistance,
                DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX, notes);
        maximumViewDistance = coerce("maximumViewDistance", maximumViewDistance,
                DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX, notes);
        minimumSimulationDistance = coerce("minimumSimulationDistance", minimumSimulationDistance,
                DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX, notes);
        maximumSimulationDistance = coerce("maximumSimulationDistance", maximumSimulationDistance,
                DistanceCalculator.ENGINE_MIN, DistanceCalculator.ENGINE_MAX, notes);
        simulationDistanceOffset = coerce("simulationDistanceOffset", simulationDistanceOffset,
                -DistanceCalculator.ENGINE_MAX, DistanceCalculator.ENGINE_MAX, notes);

        if (minimumViewDistance > maximumViewDistance) {
            notes.add("minimumViewDistance (" + minimumViewDistance + ") > maximumViewDistance ("
                    + maximumViewDistance + "); lowering minimum to maximum");
            minimumViewDistance = maximumViewDistance;
        }
        if (minimumSimulationDistance > maximumSimulationDistance) {
            notes.add("minimumSimulationDistance (" + minimumSimulationDistance + ") > maximumSimulationDistance ("
                    + maximumSimulationDistance + "); lowering minimum to maximum");
            minimumSimulationDistance = maximumSimulationDistance;
        }
        if (updateCooldownTicks < 0) {
            notes.add("updateCooldownTicks was negative; clamped to 0");
            updateCooldownTicks = 0;
        }
        return notes;
    }

    private static int coerce(String name, int v, int lo, int hi, List<String> notes) {
        int c = DistanceCalculator.clamp(v, lo, hi);
        if (c != v) notes.add(name + " (" + v + ") out of range [" + lo + ".." + hi + "]; clamped to " + c);
        return c;
    }

    // ---- lenient parsers: bad values fall back to the default, never throw ----

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return def;
    }

    private static int integer(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
