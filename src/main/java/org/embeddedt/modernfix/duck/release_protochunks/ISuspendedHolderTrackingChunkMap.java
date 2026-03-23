package org.embeddedt.modernfix.duck.release_protochunks;

import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.Executor;

public interface ISuspendedHolderTrackingChunkMap {
    void mfix$markForSuspensionCheck(ChunkPos pos);

    Executor mfix$getMainThreadExecutor();
}
