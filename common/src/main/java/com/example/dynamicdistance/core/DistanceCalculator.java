package com.example.dynamicdistance.core;

/**
 * Pure per-player distance math. No Minecraft imports on purpose so it can be
 * unit-tested without the game and shared byte-for-byte between the 1.21.x and
 * 26.1 builds.
 *
 * All values are "chunk distances" (render-distance units), the same unit the
 * client sends in its options packet and the same unit server.properties uses.
 */
public final class DistanceCalculator {

    /** Sentinel meaning "no per-player override set". */
    public static final int NO_OVERRIDE = -1;

    /** Hard engine bounds. Vanilla clamps client view distance into [2, 32]. */
    public static final int ENGINE_MIN = 2;
    public static final int ENGINE_MAX = 32;

    private DistanceCalculator() {}

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * Effective server-side view / chunk-send distance for one player.
     *
     * @param requested  the render distance the client asked for (from its options packet)
     * @param serverMax  the server's own view-distance ceiling (server.properties); never exceeded
     * @param override   per-player manual override, or {@link #NO_OVERRIDE}
     * @param cfg        validated config (min/max window)
     */
    public static int effectiveView(int requested, int serverMax, int override, DistanceConfig cfg) {
        int base = (override != NO_OVERRIDE) ? override : requested;

        // Upper bound: the tightest of config-max, the real server cap, and the engine cap.
        int hi = Math.min(Math.min(cfg.maximumViewDistance, serverMax), ENGINE_MAX);
        // Lower bound: config-min, but never below the engine floor.
        int lo = Math.max(cfg.minimumViewDistance, ENGINE_MIN);
        // A tighter server cap than our configured minimum wins (can't send more than the server allows).
        if (lo > hi) lo = hi;

        return clamp(base, lo, hi);
    }

    /**
     * Effective per-player simulation distance.
     *
     * Clients never send a simulation distance, so it is derived from the player's
     * effective view distance:
     *   default:  min(effectiveView + simulationDistanceOffset, maximumSimulationDistance)
     * then floored/capped by the configured simulation window and engine bounds.
     * A per-player simulation override (if set) replaces the derived value but is
     * still clamped to the configured window.
     *
     * @param effectiveView the value returned by {@link #effectiveView}
     * @param override      per-player simulation override, or {@link #NO_OVERRIDE}
     * @param cfg           validated config
     */
    public static int effectiveSimulation(int effectiveView, int override, DistanceConfig cfg) {
        int derived = Math.min(effectiveView + cfg.simulationDistanceOffset, cfg.maximumSimulationDistance);
        int base = (override != NO_OVERRIDE) ? override : derived;

        int hi = Math.min(cfg.maximumSimulationDistance, ENGINE_MAX);
        int lo = Math.max(cfg.minimumSimulationDistance, ENGINE_MIN);
        if (lo > hi) lo = hi;

        return clamp(base, lo, hi);
    }
}
