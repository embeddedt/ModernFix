package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.embeddedt.modernfix.duck.release_protochunks.IClearableChunkHolder;
import org.embeddedt.modernfix.duck.release_protochunks.ISuspendedHolderTrackingChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin implements IClearableChunkHolder {
    @Shadow
    @Final
    private AtomicReferenceArray<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> futures;

    @Shadow
    private CompletableFuture<ChunkAccess> chunkToSave;

    @Shadow
    private int ticketLevel;

    @Shadow
    @Final
    private ChunkPos pos;

    @Shadow
    @Final
    private ChunkHolder.PlayerProvider playerProvider;

    /**
     * Used to track the number of neighboring holders actively using this chunk for generation.
     */
    @Unique
    private final AtomicInteger mfix$generationRefCount = new AtomicInteger(0);

    @Override
    public void mfix$resetProtoChunkFutures() {
        int len = this.futures.length();
        for (int i = 0; i < len; i++) {
            this.futures.set(i, null);
        }
        this.chunkToSave = CompletableFuture.completedFuture(null);
    }

    @Override
    public AtomicInteger mfix$getGenerationRefCount() {
        return this.mfix$generationRefCount;
    }

    /*
     * The methods below trigger the ChunkMap to check whether this holder can be "suspended" (have its ProtoChunk-only
     * futures cleared) each time a new version of the chunkToSave future has completed. The ChunkMap itself
     * also verifies that all conditions are still met for suspension in case the holder has become necessary
     * again in the meantime.
     */

    @Inject(method = "addSaveDependency", at = @At("RETURN"))
    private void recheckSuspensionAfterNeighbor(String source, CompletableFuture<?> future, CallbackInfo ci) {
        this.mfix$markAsNeedingProtoChunkDrop();
    }

    @Inject(method = "updateChunkToSave", at = @At("RETURN"))
    private void checkSuspension(CallbackInfo ci) {
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
            this.chunkToSave.whenCompleteAsync((r, e) -> {
                map.mfix$markForSuspensionCheck(this.pos);
            }, map.mfix$getMainThreadExecutor());
        }
    }
}
