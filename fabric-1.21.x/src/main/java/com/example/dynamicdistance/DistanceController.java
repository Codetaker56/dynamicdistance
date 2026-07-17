package com.example.dynamicdistance;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Duck interface implemented (by mixin) on the server's chunk-loading manager
 * (vanilla ChunkMap). Keeps the version-specific reach into chunk internals in
 * one place. Simulation ticket work goes through the ChunkTicketManager directly
 * (its handle-chunk methods are public), so it is not part of this surface.
 */
public interface DistanceController {

    /** The server's own view-distance ceiling (server.properties). Never exceeded. */
    int dynamicdistance$serverViewDistance();

    /** Re-run per-player chunk tracking so a new effective view distance takes effect now. */
    void dynamicdistance$refreshViewDistance(ServerPlayerEntity player);
}
