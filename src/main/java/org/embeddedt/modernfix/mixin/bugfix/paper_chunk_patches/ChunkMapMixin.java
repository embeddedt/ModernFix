package org.embeddedt.modernfix.mixin.bugfix.paper_chunk_patches;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.duck.IPaperChunkHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;


@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;

    private Executor mainInvokingExecutor;

    @Inject(method = "<init>", at = @At("RETURN"), cancellable = true)
    private void setup(CallbackInfo ci) {
        this.mainInvokingExecutor = (runnable) -> {
            if(ServerLifecycleHooks.getCurrentServer().isSameThread())
                runnable.run();
            else
                this.mainThreadExecutor.execute(runnable);
        };
    }


    /* https://github.com/PaperMC/Paper/blob/ver/1.17.1/patches/server/0752-Fix-chunks-refusing-to-unload-at-low-TPS.patch */
    @ModifyArg(method = "prepareAccessibleChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainThreadExecutor(Executor executor) {
        return this.mainThreadExecutor;
    }

    /* https://github.com/PaperMC/Paper/blob/master/patches/removed/1.19.2-legacy-chunksystem/0482-Improve-Chunk-Status-Transition-Speed.patch */
    @ModifyArg(method = "prepareEntityTickingChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainInvokingExecutor(Executor executor) {
        return this.mainInvokingExecutor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "scheduleChunkGeneration", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture skipWorkerIfPossible(CompletableFuture inputFuture, Function function, Executor executor, ChunkHolder holder) {
        Executor targetExecutor = (runnable) -> {
            if(((IPaperChunkHolder)holder).mfix$canAdvanceStatus()) {
                this.mainInvokingExecutor.execute(runnable);
                return;
            }
            executor.execute(runnable);
        };
        return inputFuture.thenComposeAsync(function, targetExecutor);
    }
}
