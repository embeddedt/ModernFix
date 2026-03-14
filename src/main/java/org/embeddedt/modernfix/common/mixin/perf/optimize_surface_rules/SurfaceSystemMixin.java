package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.embeddedt.modernfix.world.gen.ChunkBiomeLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(SurfaceSystem.class)
public class SurfaceSystemMixin {
    private static final ThreadLocal<ChunkBiomeLookup> MFIX_LOOKUP_CACHE = ThreadLocal.withInitial(ChunkBiomeLookup::new);

    @ModifyArg(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;<init>(Lnet/minecraft/world/level/levelgen/SurfaceSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Ljava/util/function/Function;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)V"), index = 4)
    private Function<BlockPos, Holder<Biome>> useFasterLookup(Function<BlockPos, Holder<Biome>> biomeGetter, @Local(ordinal = 0, argsOnly = true) BiomeManager manager, @Local(ordinal = 0, argsOnly = true) ChunkAccess chunk) {
        var lookup = MFIX_LOOKUP_CACHE.get();
        BiomeManagerAccessor accessor = (BiomeManagerAccessor)manager;
        lookup.prepare(accessor.mfix$getBiomeSource(), accessor.mfix$getZoomSeed(), chunk, manager);
        return lookup;
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void disposeLookup(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean p_224652_, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, CallbackInfo ci) {
        MFIX_LOOKUP_CACHE.get().dispose();
    }
}
