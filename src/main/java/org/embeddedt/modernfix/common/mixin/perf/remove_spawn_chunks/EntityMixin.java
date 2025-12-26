package org.embeddedt.modernfix.common.mixin.perf.remove_spawn_chunks;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
    /**
     * @author embeddedt
     * @reason If the spawn chunks are not loaded, end portals linking to the overworld will teleport entities into
     * the void at the spawn position, which is not ideal. To solve this, we create a PORTAL ticket if the expected
     * overworld chunk is missing.
     */
    @ModifyExpressionValue(method = "findDimensionEntryPoint", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSharedSpawnPos()Lnet/minecraft/core/BlockPos;"), require = 0)
    private BlockPos mfix$triggerChunkloadAtSpawnPos(BlockPos spawnPos, ServerLevel destination) {
        // Only apply this change if the overworld is the destination
        if (destination.dimension() == ServerLevel.OVERWORLD) {
            // No ticket is required if the chunk happens to already be loaded
            if(!destination.hasChunk(spawnPos.getX() >> 4, spawnPos.getZ() >> 4)) {
                // Create a portal ticket. While we could just load the chunk once, it would immediately unload on the
                // next tick, causing churn. The ticket will keep it loaded for a few seconds which should give high
                // performance for farms pumping things through portals frequently.
                BlockPos key = spawnPos.immutable();
                destination.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(key), 3, key);
                // Wait for the chunk to be loaded, as adding the ticket is asynchronous
                destination.getChunk(key);
            }
        }
        return spawnPos;
    }
}
