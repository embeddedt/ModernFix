package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import org.embeddedt.modernfix.duck.release_protochunks.IClearableChunkHolder;
import org.embeddedt.modernfix.duck.release_protochunks.ISuspendedHolderTrackingChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ISuspendedHolderTrackingChunkMap {

    private static final int MFIX$TICKS_TO_WAIT_BEFORE_SUSPENDING = 100;

    @Shadow
    @Final
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow
    protected abstract void lambda$scheduleUnload$12(ChunkHolder holder, long chunkPos);

    @Shadow
    @Final
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    private final Long2IntOpenHashMap mfix$protoChunksToDrop = new Long2IntOpenHashMap();

    private int mfix$dropTickCounter = 0;

    /**
     * @author embeddedt
     * @reason We keep track of ChunkHolders that only contain protochunks, and are not loaded to a full status.
     * This hook unloads their contents once there are no generation tasks actively relying on the chunk.
     */
    @Inject(method = "processUnloads(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void dropProtoChunks(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        int suspended = 0;
        int iterations = 0;
        mfix$dropTickCounter++;
        var dropIterator = mfix$protoChunksToDrop.long2IntEntrySet().fastIterator();
        while (dropIterator.hasNext() && suspended < 50 && iterations < 500 && (hasMoreTime.getAsBoolean() || mfix$protoChunksToDrop.size() > 1000)) {
            iterations++;
            var entry = dropIterator.next();
            long pos = entry.getLongKey();
            ChunkHolder holder = this.updatingChunkMap.get(pos);
            if (holder == null // already removed
                || holder.getTicketLevel() < IClearableChunkHolder.LOWEST_DROPPABLE_TICKET_LEVEL // promoted to FULL or adjacent to FULL chunk
                || !ChunkLevel.isLoaded(holder.getTicketLevel()) // is going to be dropped through normal code path
            ) {
                dropIterator.remove();
                continue;
            }

            if (!holder.isReadyForSaving() // saveSync dependencies have not completed or chunk is still being referenced by another chunk for generation
            ) {
                // Not safe to suspend yet, reset timer
                entry.setValue(mfix$dropTickCounter);
                continue;
            }

            if ((mfix$dropTickCounter - entry.getIntValue()) < MFIX$TICKS_TO_WAIT_BEFORE_SUSPENDING) {
                // Chunk has not been idle for long enough, wait
                continue;
            }

            // All generation work done, so we can suspend and remove from set
            dropIterator.remove();

            // Execute the logic inside scheduleUnload() inline, without delegating to a queue
            // When this returns it is safe to release any data the ChunkHolder holds
            this.pendingUnloads.put(pos, holder);
            this.lambda$scheduleUnload$12(holder, pos);

            ((IClearableChunkHolder)holder).mfix$resetProtoChunkFutures();

            suspended++;
        }
    }

    @Override
    public void mfix$markForSuspensionCheck(ChunkPos pos) {
        this.mfix$protoChunksToDrop.put(pos.toLong(), this.mfix$dropTickCounter);
    }

    @Override
    public Executor mfix$getMainThreadExecutor() {
        return this.mainThreadExecutor;
    }
}
