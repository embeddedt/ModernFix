package org.embeddedt.modernfix.neoforge.mixin.bugfix.chunk_deadlock;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class ServerChunkCache_CurrentLoadingMixin {
    @Shadow @Nullable protected abstract ChunkHolder getVisibleChunkIfPresent(long l);

    private static final MethodHandle CURRENTLY_LOADING;

    static {
        try {
            Field currentlyLoadingField = ObfuscationReflectionHelper.findField(ChunkHolder.class, "currentlyLoading");
            currentlyLoadingField.setAccessible(true);
            CURRENTLY_LOADING = MethodHandles.lookup().unreflectGetter(currentlyLoadingField);
        } catch(Exception e) {
            throw new RuntimeException("Failed to get currentlyLoading field", e);
        }
    }

    /**
     * Check the currentlyLoading field before going to the future chain, as was done in 1.16. In 1.18 upstream seems
     * to have only applied this to getChunkNow().
     */
    @Inject(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"), cancellable = true, require = 0)
    private void checkCurrentlyLoading(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        long i = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder holder = this.getVisibleChunkIfPresent(i);
        if(holder != null) {
            LevelChunk c;
            try {
                c = (LevelChunk)CURRENTLY_LOADING.invokeExact(holder);
            } catch(Throwable e) {
                e.printStackTrace();
                c = null;
            }
            if(c != null)
                cir.setReturnValue(c);
        }
    }
}