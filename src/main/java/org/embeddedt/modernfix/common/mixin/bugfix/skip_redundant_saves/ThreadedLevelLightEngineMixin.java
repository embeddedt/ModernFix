package org.embeddedt.modernfix.common.mixin.bugfix.skip_redundant_saves;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedLevelLightEngine.class)
public class ThreadedLevelLightEngineMixin {
    @Shadow
    @Final
    private ChunkMap chunkMap;

    /**
     * @author embeddedt
     * @reason avoid toggling the lightCorrect flag when chunk is already lit, because it triggers saving
     */
    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private void skipLightCorrectFlagChange(ChunkAccess chunk, boolean isAlreadyLit, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (isAlreadyLit) {
            ((ChunkMapAccessor)this.chunkMap).mfix$invokeReleaseLightTicket(chunk.getPos());
            // Defensively ensure the lightCorrect flag is set properly on exit from this method
            if (!chunk.isLightCorrect()) {
                chunk.setLightCorrect(true);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }
}
