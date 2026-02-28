package org.embeddedt.modernfix.common.mixin.bugfix.paper_chunk_patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.*;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;

    /* https://github.com/PaperMC/Paper/blob/ver/1.17.1/patches/server/0752-Fix-chunks-refusing-to-unload-at-low-TPS.patch */
    @ModifyArg(method = "prepareAccessibleChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainThreadExecutor(Executor executor) {
        return this.mainThreadExecutor;
    }

    /**
     * @author embeddedt
     * @reason 1.17+ uses getNow to check if the parent future is ready, and calls scheduleChunkGeneration as soon as
     * it is found to not be ready. In the latter scenario, a massive number of extra CompletableFutures are allocated
     * even if they are not actually necessary if the future is waited for. To prevent this, if the parent future
     * is not done, we wait for it to complete and then retry schedule(). This will either detect an adequate
     * status and return a loading future, or re-enter this injector with the parent future completed, in which case
     * we proceed to schedule generation as originally requested.
     */
    @WrapOperation(method = "schedule", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;scheduleChunkGeneration(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/ChunkStatus;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> mfix$avoidSchedulingGenerationPrematurely(ChunkMap map, ChunkHolder holder, ChunkStatus status, Operation<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        if (!status.hasLoadDependencies()) {
            var parentFuture = holder.getOrScheduleFuture(status.getParent(), map);
            if (!parentFuture.isDone()) {
                return parentFuture.thenComposeAsync(
                        either -> map.schedule(holder, status),
                        this.mainThreadExecutor
                );
            }
        }
        return original.call(map, holder, status);
    }
}
