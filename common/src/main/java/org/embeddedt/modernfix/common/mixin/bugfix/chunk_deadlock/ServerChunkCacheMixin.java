package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.*;

@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class ServerChunkCacheMixin {
    @Shadow @Final private Thread mainThread;
    @Shadow @Final public ServerLevel level;

    @Shadow protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFutureMainThread(int k, int l, ChunkStatus arg, boolean bl);

    @Shadow @Final private ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    private final boolean debugDeadServerAccess = Boolean.getBoolean("modernfix.debugBadChunkloading");

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void bailIfServerDead(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if(!this.level.getServer().isRunning() && !this.mainThread.isAlive()) {
            ModernFix.LOGGER.fatal("A mod is accessing chunks from a stopped server (this will also cause memory leaks)");
            if(debugDeadServerAccess) {
                new Exception().printStackTrace();
            }
            cir.setReturnValue(new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ)));
        } else if(Thread.currentThread() != this.mainThread) {
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = CompletableFuture.supplyAsync(() -> this.getChunkFutureMainThread(chunkX, chunkZ, requiredStatus, false), this.mainThreadProcessor).join();
            if(!future.isDone()) {
                // Wait at least 500 milliseconds before printing anything
                Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> resultingChunk = null;
                try {
                    resultingChunk = future.get(500, TimeUnit.MILLISECONDS);
                } catch(InterruptedException | ExecutionException | TimeoutException ignored) {
                }
                if(resultingChunk != null && resultingChunk.left().isPresent()) {
                    cir.setReturnValue(resultingChunk.left().get());
                    return;
                }
                if(debugDeadServerAccess)
                    ModernFix.LOGGER.warn("Async loading of a chunk was requested, this might not be desirable", new Exception());
                else
                    ModernFix.LOGGER.warn("Suspicious async chunkload, pass -Dmodernfix.debugBadChunkloading=true for more details");
                try {
                    resultingChunk = future.get(10, TimeUnit.SECONDS);
                    if(resultingChunk.left().isPresent()) {
                        cir.setReturnValue(resultingChunk.left().get());
                        return;
                    }
                } catch(InterruptedException | ExecutionException | TimeoutException e) {
                    ModernFix.LOGGER.error("Async chunk load took way too long, this needs to be reported to the appropriate mod.", e);
                }
                //cir.setReturnValue(new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ)));
            }
        }
    }
}
