package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.extensions.IForgeBlockGetter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class ServerLevel_ExistingBlockEntityMixin implements IForgeBlockGetter {
    /**
     * @author embeddedt
     * @reason Prevent deadlocks in getExistingBlockEntity by not waiting on chunk loads
     */
    @Override
    @Nullable
    public BlockEntity getExistingBlockEntity(BlockPos pos) {
        ServerLevel self = (ServerLevel) (Object) this;
        ServerChunkCache chunkSource = self.getChunkSource();
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        LevelChunk chunk;
        if (self.getServer().isSameThread()) {
            chunk = chunkSource.getChunkNow(chunkX, chunkZ);
        } else {
            chunk = chunkSource.getChunkForLighting(chunkX, chunkZ) instanceof LevelChunk levelChunk ? levelChunk : null;
        }
        return chunk == null ? null : chunk.getExistingBlockEntity(pos);
    }
}
