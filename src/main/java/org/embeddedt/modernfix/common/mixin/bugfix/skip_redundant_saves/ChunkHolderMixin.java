package org.embeddedt.modernfix.common.mixin.bugfix.skip_redundant_saves;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin {
    @Shadow
    @Nullable
    public abstract LevelChunk getTickingChunk();

    /**
     * @author embeddedt
     * @reason prevent chunks from being flagged for saving when light engine is loading data from disk
     */
    @WrapWithCondition(method = "sectionLightChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setUnsaved(Z)V"))
    private boolean onlyMarkUnsavedIfAlreadyTicking(ChunkAccess instance, boolean unsaved) {
        return this.getTickingChunk() != null;
    }
}
