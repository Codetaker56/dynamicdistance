package com.example.dynamicdistance.mixin;

import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The ChunkTicketManager lives on ServerChunkManager (not the loading manager).
 * This accessor lets the service reach it to swap a player's simulation ticket
 * via the ticket manager's public handleChunkEnter/handleChunkLeave methods.
 */
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessor {

    @Accessor("ticketManager")
    ChunkTicketManager dynamicdistance$ticketManager();
}
