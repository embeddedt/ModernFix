package org.embeddedt.modernfix.mixin.bugfix.starlight_emptiness;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StarLightEngine.class)
@RequiresMod("starlight")
public abstract class StarLightEngineMixin {
    @Shadow protected abstract LevelChunkSection getChunkSection(int chunkX, int chunkY, int chunkZ);

    @Shadow @Final protected int minSection;

    @Inject(method = "handleEmptySectionChanges(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/chunk/ChunkAccess;[Ljava/lang/Boolean;Z)[Z",
            at = @At(value = "INVOKE", target = "Lca/spottedleaf/starlight/common/light/StarLightEngine;setEmptinessMapCache(II[Z)V",
            shift = At.Shift.AFTER))
    private void lazyInitMapIfNeeded(LightChunkGetter lightAccess, ChunkAccess chunk, Boolean[] emptinessChanges, boolean unlit, CallbackInfoReturnable<int[]> cir) {
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;
        for (int sectionIndex = (emptinessChanges.length - 1); sectionIndex >= 0; --sectionIndex) {
            if(emptinessChanges[sectionIndex] == null) {
                final LevelChunkSection section = this.getChunkSection(chunkX, sectionIndex + this.minSection, chunkZ);
                emptinessChanges[sectionIndex] = section == null || section.isEmpty() ? Boolean.TRUE : Boolean.FALSE;
            }
        }
    }
}
