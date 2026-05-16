package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.embeddedt.modernfix.world.gen.ExtendedSurfaceContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(SurfaceRules.Context.class)
public class SurfaceRulesContextMixin implements ExtendedSurfaceContext {
    @Nullable Set<ResourceKey<Biome>> mfix$possibleBiomes;

    @Override
    public void mfix$applyPossibleBiomes() {
        // Copy into a field so we don't hit a thread local for each BiomeConditionSource instance
        this.mfix$possibleBiomes = ExtendedSurfaceContext.COMPUTED_POSSIBLE_BIOMES.get();
        ExtendedSurfaceContext.COMPUTED_POSSIBLE_BIOMES.remove();
    }

    @Override
    public @Nullable Set<ResourceKey<Biome>> mfix$getPossibleBiomes() {
        return this.mfix$possibleBiomes;
    }
}
