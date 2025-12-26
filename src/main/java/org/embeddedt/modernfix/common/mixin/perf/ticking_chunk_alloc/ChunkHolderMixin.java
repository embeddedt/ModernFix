package org.embeddedt.modernfix.common.mixin.perf.ticking_chunk_alloc;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.modernfix.util.EitherUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

@Mixin(value = ChunkHolder.class, priority = 500)
public abstract class ChunkHolderMixin {
    @Shadow public abstract CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getTickingChunkFuture();

    @Shadow public abstract CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getFullChunkFuture();

    /**
     * @author embeddedt
     * @reason avoid Optional allocation
     */
    @Overwrite
    public LevelChunk getTickingChunk() {
        CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completableFuture = this.getTickingChunkFuture();
        Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> either = completableFuture.getNow(null);
        return either == null ? null : EitherUtil.leftOrNull(either);
    }

    /**
     * @author embeddedt
     * @reason avoid Optional allocation
     */
    @Overwrite
    public LevelChunk getFullChunk() {
        CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completableFuture = this.getFullChunkFuture();
        Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> either = completableFuture.getNow(null);
        return either == null ? null : EitherUtil.leftOrNull(either);
    }
}
