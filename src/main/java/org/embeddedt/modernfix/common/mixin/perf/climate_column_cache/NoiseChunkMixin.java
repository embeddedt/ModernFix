package org.embeddedt.modernfix.common.mixin.perf.climate_column_cache;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.world.gen.HeightInvariance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseChunk.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public abstract class NoiseChunkMixin {
    @Shadow protected abstract DensityFunction wrap(DensityFunction densityFunction);

    /**
     * Vanilla evaluates temperature and vegetation at every quart position, although they only vary per column.
     * Climate functions that ignore Y get a flat_cache, which computes each column once at y = 0.
     */
    @ModifyReturnValue(method = "cachedClimateSampler", at = @At("RETURN"))
    private Climate.Sampler cacheHeightInvariantClimate(Climate.Sampler sampler) {
        return new Climate.Sampler(
                mfix$columnCached(sampler.temperature()),
                mfix$columnCached(sampler.humidity()),
                mfix$columnCached(sampler.continentalness()),
                mfix$columnCached(sampler.erosion()),
                mfix$columnCached(sampler.depth()),
                mfix$columnCached(sampler.weirdness()),
                sampler.spawnTarget());
    }

    @Unique
    private DensityFunction mfix$columnCached(DensityFunction function) {
        if (!HeightInvariance.ignoresY(function)) {
            return function;
        }
        return this.wrap(DensityFunctions.flatCache(function));
    }
}
