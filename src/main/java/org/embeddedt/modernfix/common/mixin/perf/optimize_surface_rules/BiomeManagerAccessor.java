package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
    @Accessor("biomeZoomSeed")
    long mfix$getZoomSeed();

    @Accessor("noiseBiomeSource")
    BiomeManager.NoiseBiomeSource mfix$getBiomeSource();
}
