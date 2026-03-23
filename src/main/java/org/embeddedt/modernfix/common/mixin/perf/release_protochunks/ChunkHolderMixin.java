package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.embeddedt.modernfix.duck.release_protochunks.IClearableChunkHolder;
import org.embeddedt.modernfix.duck.release_protochunks.ISuspendedHolderTrackingChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin extends GenerationChunkHolder implements IClearableChunkHolder {
    @Shadow
    private CompletableFuture<ChunkAccess> saveSync;

    @Shadow
    private int ticketLevel;

    @Shadow
    @Final
    private ChunkHolder.PlayerProvider playerProvider;

    public ChunkHolderMixin(ChunkPos pos) {
        super(pos);
    }

    @Override
    public void mfix$resetProtoChunkFutures() {
        int len = this.futures.length();
        for (int i = 0; i < len; i++) {
            this.futures.set(i, null);
        }
        this.saveSync = CompletableFuture.completedFuture(null);
        this.startedWork.set(null);
    }

    /*
     * The methods below trigger the ChunkMap to check whether this holder can be "suspended" (have its ProtoChunk-only
     * futures cleared) each time a new version of the chunkToSave future has completed. The ChunkMap itself
     * also verifies that all conditions are still met for suspension in case the holder has become necessary
     * again in the meantime.
     */

    @Inject(method = "addSaveDependency", at = @At("RETURN"))
    private void recheckSuspensionAfterNeighbor(CompletableFuture<?> future, CallbackInfo ci) {
        this.mfix$markAsNeedingProtoChunkDrop();
    }

    @Inject(method = "updateFutures", at = @At("RETURN"))
    private void markForSuspensionOnDemotion(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
        this.mfix$markAsNeedingProtoChunkDrop();
    }

    private void mfix$markAsNeedingProtoChunkDrop() {
        if (!ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL)
                && ChunkLevel.isLoaded(this.ticketLevel)) {
            // register for suspension check when chain completes
            var map = ((ISuspendedHolderTrackingChunkMap)this.playerProvider);
            this.saveSync.whenCompleteAsync((r, e) -> {
                map.mfix$markForSuspensionCheck(this.pos);
            }, map.mfix$getMainThreadExecutor());
        }
    }
}
