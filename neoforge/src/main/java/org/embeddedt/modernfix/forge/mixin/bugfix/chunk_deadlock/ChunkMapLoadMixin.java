package org.embeddedt.modernfix.forge.mixin.bugfix.chunk_deadlock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Field;

@Mixin(ChunkMap.class)
public abstract class ChunkMapLoadMixin {
    @Shadow @Nullable protected abstract ChunkHolder getVisibleChunkIfPresent(long l);

    private static final Field currentlyLoadingField = ObfuscationReflectionHelper.findField(ChunkHolder.class, "currentlyLoading");

    private static void setCurrentlyLoading(ChunkHolder holder, LevelChunk value) {
        try {
            currentlyLoadingField.set(holder, value);
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set currentlyLoading before calling runPostLoad and restore its old value afterwards. We track the old value
     * to avoid conflicting with Forge if/when this feature is added.
     */
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V"))
    private void setCurrentLoadingThenPostLoad(LevelChunk chunk, Operation<Void> operation) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunk.getPos().toLong());
        if(holder != null) {
            LevelChunk prevLoading = null;
            try {
                prevLoading = (LevelChunk)currentlyLoadingField.get(holder);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
            try {
                setCurrentlyLoading(holder, chunk);
                operation.call(chunk);
            } finally {
                setCurrentlyLoading(holder, prevLoading);
            }
        } else {
            ModernFix.LOGGER.warn("Unable to find chunk holder for loading chunk");
            operation.call(chunk);
        }
    }
}
