package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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

    @Shadow protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int k, int l, ChunkStatus arg, boolean bl);

    @Shadow @Final private ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    private final boolean debugDeadServerAccess = Boolean.getBoolean("modernfix.debugBadChunkloading");

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void bailIfServerDead(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if(!this.level.getServer().isRunning() && !this.mainThread.isAlive()) {
            ModernFix.LOGGER.fatal("A mod is accessing chunks from a stopped server (this will also cause memory leaks)");
            if(debugDeadServerAccess) {
                new Exception().printStackTrace();
            }
            Holder<Biome> plains = this.level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            cir.setReturnValue(new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), plains));
        } else if(Thread.currentThread() != this.mainThread) {
            CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(() -> this.getChunkFutureMainThread(chunkX, chunkZ, requiredStatus, false), this.mainThreadProcessor).join();
            if(!future.isDone()) {
                // Wait at least 500 milliseconds before printing anything
                ChunkResult<ChunkAccess> resultingChunk = null;
                try {
                    resultingChunk = future.get(500, TimeUnit.MILLISECONDS);
                } catch(InterruptedException | ExecutionException | TimeoutException ignored) {
                }
                if(resultingChunk != null && resultingChunk.isSuccess()) {
                    cir.setReturnValue(resultingChunk.orElse(null));
                    return;
                }
                if(debugDeadServerAccess)
                    ModernFix.LOGGER.warn("Async loading of a chunk was requested, this might not be desirable", new Exception());
                try {
                    resultingChunk = future.get(10, TimeUnit.SECONDS);
                    if(resultingChunk.isSuccess()) {
                        cir.setReturnValue(resultingChunk.orElse(null));
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
