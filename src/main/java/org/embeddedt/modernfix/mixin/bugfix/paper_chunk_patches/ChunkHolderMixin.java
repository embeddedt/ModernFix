package org.embeddedt.modernfix.mixin.bugfix.paper_chunk_patches;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.embeddedt.modernfix.duck.IPaperChunkHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements IPaperChunkHolder {

    @Shadow public abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getFutureIfPresentUnchecked(ChunkStatus arg);

    @Shadow @Final private static List<ChunkStatus> CHUNK_STATUSES;

    public ChunkStatus mfix$getChunkHolderStatus() {
        for (ChunkStatus curr = ChunkStatus.FULL, next = curr.getParent(); curr != next; curr = next, next = next.getParent()) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = this.getFutureIfPresentUnchecked(curr);
            Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either = future.getNow(null);
            if (either == null || !either.left().isPresent()) {
                continue;
            }
            return curr;
        }

        return null;
    }

    public ChunkAccess mfix$getAvailableChunkNow() {
        // TODO can we just getStatusFuture(EMPTY)?
        for (ChunkStatus curr = ChunkStatus.FULL, next = curr.getParent(); curr != next; curr = next, next = next.getParent()) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = this.getFutureIfPresentUnchecked(curr);
            Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either = future.getNow(null);
            if (either == null || !either.left().isPresent()) {
                continue;
            }
            return either.left().get();
        }
        return null;
    }

    private static ChunkStatus mfix$getNextStatus(ChunkStatus status) {
        if (status == ChunkStatus.FULL) {
            return status;
        }
        return CHUNK_STATUSES.get(status.getIndex() + 1);
    }

    @Override
    public boolean mfix$canAdvanceStatus() {
        ChunkStatus status = mfix$getChunkHolderStatus();
        ChunkAccess chunk = mfix$getAvailableChunkNow();
        return chunk != null && (status == null || chunk.getStatus().isOrAfter(mfix$getNextStatus(status)));
    }
}
