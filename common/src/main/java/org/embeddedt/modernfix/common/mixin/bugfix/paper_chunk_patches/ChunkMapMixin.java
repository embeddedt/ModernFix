package org.embeddedt.modernfix.common.mixin.bugfix.paper_chunk_patches;

import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow @Final private ChunkMap.DistanceManager distanceManager;

    @Shadow protected abstract CompletableFuture<ChunkAccess> protoChunkToFullChunk(ChunkHolder arg, ChunkAccess chunkAccess);

    @Shadow @Final private ChunkProgressListener progressListener;

    @Shadow protected abstract CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGeneration(ChunkHolder chunkHolder, ChunkStatus chunkStatus);

    @Shadow private WorldGenContext worldGenContext;

    /* https://github.com/PaperMC/Paper/blob/ver/1.17.1/patches/server/0752-Fix-chunks-refusing-to-unload-at-low-TPS.patch */
    @ModifyArg(method = "prepareAccessibleChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainThreadExecutor(Executor executor) {
        return this.mainThreadExecutor;
    }

    /**
     * @author embeddedt
     * @reason revert 1.17 chunk system changes, significantly reduces time and RAM needed to load chunks
     */
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void useLegacySchedulingLogic(ChunkHolder holder, ChunkStatus requiredStatus, CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if(requiredStatus != ChunkStatus.EMPTY && !requiredStatus.hasLoadDependencies()) {
            ChunkPos chunkpos = holder.getPos();
            CompletableFuture<ChunkResult<ChunkAccess>> future = holder.getOrScheduleFuture(requiredStatus.getParent(), (ChunkMap)(Object)this);
            cir.setReturnValue(future.thenComposeAsync((either) -> {
                ChunkAccess partialChunk = either.orElse(null);

                if (requiredStatus == ChunkStatus.LIGHT) {
                    this.distanceManager.addTicket(TicketType.LIGHT, chunkpos, 33 + ChunkStatus.getDistance(ChunkStatus.LIGHT), chunkpos);
                }

                // from original method
                if (partialChunk != null && partialChunk.getStatus().isOrAfter(requiredStatus)) {
                    CompletableFuture<ChunkAccess> completablefuture = requiredStatus.load(this.worldGenContext, (partialChunkAccess) -> {
                        return this.protoChunkToFullChunk(holder, partialChunkAccess);
                    }, partialChunk);
                    this.progressListener.onStatusChange(chunkpos, requiredStatus);
                    return completablefuture.thenApply(ChunkResult::of);
                } else {
                    return this.scheduleChunkGeneration(holder, requiredStatus);
                }
            }, this.mainThreadExecutor).thenComposeAsync(CompletableFuture::completedFuture, this.mainThreadExecutor));
        }
    }
}
