package org.embeddedt.modernfix.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class SafeBlockGetter implements BlockGetter {
    private final ServerLevel wrapped;
    private final Thread mainThread;

    public SafeBlockGetter(ServerLevel wrapped) {
        this.wrapped = wrapped;
        this.mainThread = Thread.currentThread();
    }

    public boolean shouldUse() {
        return Thread.currentThread() != this.mainThread;
    }

    @Nullable
    private BlockGetter getChunkSafe(BlockPos pos) {
        // can safely call getChunkForLighting off-thread
        BlockGetter access = this.wrapped.getChunkSource().getChunkForLighting(pos.getX() >> 4, pos.getZ() >> 4);
        if(!(access instanceof ChunkAccess))
            return null;
        ChunkAccess chunk = (ChunkAccess)access;
        if(!chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL))
            return null;
        return chunk;
    }

    @Override
    public int getMaxY() {
        return this.wrapped.getMaxY();
    }

    @Override
    public int getMinY() {
        return this.wrapped.getMinY();
    }

    @Override
    public int getHeight() {
        return this.wrapped.getHeight();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        BlockGetter g = getChunkSafe(pos);
        return g == null ? null : g.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockGetter g = getChunkSafe(pos);
        return g == null ? Blocks.AIR.defaultBlockState() : g.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        BlockGetter g = getChunkSafe(pos);
        return g == null ? Fluids.EMPTY.defaultFluidState() : g.getFluidState(pos);
    }
}
