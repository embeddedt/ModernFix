package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.embeddedt.modernfix.duck.release_protochunks.IClearableChunkHolder;
import org.embeddedt.modernfix.duck.release_protochunks.ISuspendedHolderTrackingChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ISuspendedHolderTrackingChunkMap {
    @Shadow
    @Final
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow
    protected abstract void lambda$scheduleUnload$14(ChunkHolder holder, CompletableFuture<ChunkAccess> chunkToSaveFuture, long chunkPos, ChunkAccess chunk);

    @Shadow
    @Final
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    private final LongOpenHashSet mfix$protoChunksToDrop = new LongOpenHashSet();

    /**
     * @author embeddedt
     * @reason We keep track of ChunkHolders that only contain protochunks, and are not loaded to a full status.
     * This hook unloads their contents once there are no generation tasks actively relying on the chunk.
     */
    @Inject(method = "processUnloads(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void dropProtoChunks(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        int suspended = 0;
        int iterations = 0;
        LongIterator dropIterator = mfix$protoChunksToDrop.longIterator();
        while (dropIterator.hasNext() && suspended < 50 && iterations < 500 && (hasMoreTime.getAsBoolean() || mfix$protoChunksToDrop.size() > 1000)) {
            iterations++;
            long pos = dropIterator.nextLong();
            ChunkHolder holder = this.updatingChunkMap.get(pos);
            if (holder == null // already removed
                || ChunkLevel.fullStatus(holder.getTicketLevel()).isOrAfter(FullChunkStatus.FULL) // promoted to FULL
                || !ChunkLevel.isLoaded(holder.getTicketLevel()) // is going to be dropped through normal code path
            ) {
                dropIterator.remove();
                continue;
            }

            if (!holder.getChunkToSave().isDone()
                || ((IClearableChunkHolder)holder).mfix$getGenerationRefCount().get() != 0) {
                // Not safe to suspend yet; either the chunkToSave chain is still pending, or a neighbor's
                // generation task is still actively using this chunk's sections
                continue;
            }

            // All generation work done, so we can suspend and remove from set
            dropIterator.remove();

            var chunkToSaveFuture = holder.getChunkToSave();

            // Execute the logic inside scheduleUnload() inline, without delegating to a queue
            // When this returns it is safe to release any data the ChunkHolder holds
            this.pendingUnloads.put(pos, holder);
            this.lambda$scheduleUnload$14(holder, chunkToSaveFuture, pos, chunkToSaveFuture.getNow(null));

            ((IClearableChunkHolder)holder).mfix$resetProtoChunkFutures();

            suspended++;
        }
    }

    /**
     * @author embeddedt
     * @reason increment the generation ref count on all neighboring chunk holders within the range when a generation
     * task starts
     */
    @Inject(method = "scheduleChunkGeneration", at = @At("HEAD"))
    private void incrementGenRefCounts(ChunkHolder chunkHolder, ChunkStatus chunkStatus, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        int range = chunkStatus.getRange();
        ChunkPos center = chunkHolder.getPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkHolder neighbor = this.updatingChunkMap.get(ChunkPos.asLong(center.x + dx, center.z + dz));
                if (neighbor != null) {
                    ((IClearableChunkHolder)neighbor).mfix$getGenerationRefCount().incrementAndGet();
                }
            }
        }
    }

    /**
     * @author embeddedt
     * @reason decrement the generation ref count on all neighboring chunk holders within the range when the generation
     * task is completely finished
     */
    @ModifyReturnValue(method = "scheduleChunkGeneration", at = @At("RETURN"))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> decrementGenRefCountsOnComplete(CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future,
                                                                                                                    @Local(ordinal = 0, argsOnly = true) ChunkHolder chunkHolder,
                                                                                                                    @Local(ordinal = 0, argsOnly = true) ChunkStatus chunkStatus) {
        int range = chunkStatus.getRange();
        ChunkPos center = chunkHolder.getPos();
        return future.whenCompleteAsync((result, error) -> {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    ChunkHolder neighbor = this.updatingChunkMap.get(ChunkPos.asLong(center.x + dx, center.z + dz));
                    if (neighbor != null) {
                        ((IClearableChunkHolder)neighbor).mfix$getGenerationRefCount().decrementAndGet();
                    }
                }
            }
        }, this.mainThreadExecutor);
    }

    @Override
    public void mfix$markForSuspensionCheck(ChunkPos pos) {
        this.mfix$protoChunksToDrop.add(pos.toLong());
    }

    @Override
    public Executor mfix$getMainThreadExecutor() {
        return this.mainThreadExecutor;
    }
}
