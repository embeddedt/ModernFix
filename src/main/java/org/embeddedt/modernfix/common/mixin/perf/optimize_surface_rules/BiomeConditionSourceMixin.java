package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.embeddedt.modernfix.world.gen.ExtendedSurfaceContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

@Mixin(SurfaceRules.BiomeConditionSource.class)
public class BiomeConditionSourceMixin {
    @Shadow
    @Final
    public List<ResourceKey<Biome>> biomes;

    /**
     * @author Mojang, embeddedt
     * @reason Hoist evaluation of the biome conditions where possible, in cases where we know the chunk contains
     * no matching biomes, or only matching biomes
     */
    @Inject(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$Condition;", at = @At("HEAD"), cancellable = true)
    private void mfix$optimizeCondition(SurfaceRules.Context p_context, CallbackInfoReturnable<SurfaceRules.Condition> cir) {
        var possibleBiomes = ((ExtendedSurfaceContext)(Object)p_context).mfix$getPossibleBiomes();
        if (possibleBiomes != null) {
            if (mfix$guaranteedNoMatch(possibleBiomes)) {
                cir.setReturnValue(() -> false);
            } else if (mfix$alwaysMatches(possibleBiomes)) {
                cir.setReturnValue(() -> true);
            }
        }
    }

    private boolean mfix$guaranteedNoMatch(Set<ResourceKey<Biome>> possibleBiomesInChunk) {
        var testBiomes = this.biomes;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < testBiomes.size(); i++) {
            if (possibleBiomesInChunk.contains(testBiomes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean mfix$alwaysMatches(Set<ResourceKey<Biome>> possibleBiomesInChunk) {
        var testBiomes = this.biomes;
        // Check each of the biomes in the chunk and see if we'd always match it
        for (var biome : possibleBiomesInChunk) {
            if (!testBiomes.contains(biome)) {
                // at least one biome would not match
                return false;
            }
        }
        return true;
    }
}
