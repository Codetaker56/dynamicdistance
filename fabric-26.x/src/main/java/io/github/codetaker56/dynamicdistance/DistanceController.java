package io.github.codetaker56.dynamicdistance;

import net.minecraft.server.level.ServerPlayer;

/**
 * Duck interface implemented (by mixin) on vanilla's ChunkMap. Keeps the
 * version-specific reach into chunk internals in one place. Simulation ticket
 * work goes through the DistanceManager directly (reached via ChunkMap's public
 * getDistanceManager()), so it is not part of this surface.
 */
public interface DistanceController {

    /** The server's own view-distance ceiling (server.properties). Never exceeded. */
    int dynamicdistance$serverViewDistance();

    /** Re-run per-player chunk tracking so a new effective view distance takes effect now. */
    void dynamicdistance$refreshViewDistance(ServerPlayer player);
}
