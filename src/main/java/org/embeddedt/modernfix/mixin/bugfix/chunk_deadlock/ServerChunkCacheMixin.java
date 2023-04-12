package org.embeddedt.modernfix.mixin.bugfix.chunk_deadlock;

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

@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class ServerChunkCacheMixin {
    @Shadow @Final private Thread mainThread;
    @Shadow @Final public ServerLevel level;
    private final boolean debugDeadServerAccess = Boolean.getBoolean("modernfix.debugBadChunkloading");
    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void bailIfServerDead(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if(!this.mainThread.isAlive()) {
            ModernFix.LOGGER.fatal("A mod is accessing chunks from a stopped server (this will also cause memory leaks)");
            if(debugDeadServerAccess) {
                new Exception().printStackTrace();
            }
            cir.setReturnValue(new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ)));
        }
    }
}
