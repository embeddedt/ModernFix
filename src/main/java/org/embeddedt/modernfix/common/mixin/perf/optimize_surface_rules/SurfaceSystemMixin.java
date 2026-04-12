package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.embeddedt.modernfix.world.gen.ChunkBiomeLookup;
import org.embeddedt.modernfix.world.gen.PrefetchingBlockColumn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = SurfaceSystem.class, priority = 2000)
public class SurfaceSystemMixin {
    private static final ThreadLocal<ChunkBiomeLookup> MFIX_LOOKUP_CACHE = ThreadLocal.withInitial(ChunkBiomeLookup::new);
    private static final ThreadLocal<PrefetchingBlockColumn> MFIX_BLOCK_COLUMN = new ThreadLocal<>();

    @ModifyArg(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;<init>(Lnet/minecraft/world/level/levelgen/SurfaceSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Ljava/util/function/Function;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)V"), index = 4)
    private Function<BlockPos, Holder<Biome>> useFasterLookup(Function<BlockPos, Holder<Biome>> biomeGetter,
                                                              @Local(ordinal = 0, argsOnly = true) BiomeManager manager,
                                                              @Local(ordinal = 0, argsOnly = true) ChunkAccess chunk,
                                                              @Share("chunkBiomeLookup") LocalRef<ChunkBiomeLookup> lookupRef) {
        var lookup = MFIX_LOOKUP_CACHE.get();
        BiomeManagerAccessor accessor = (BiomeManagerAccessor)manager;
        lookup.prepare(accessor.mfix$getBiomeSource(), accessor.mfix$getZoomSeed(), chunk, manager);
        lookupRef.set(lookup);
        return lookup;
    }

    @Inject(method = "buildSurface", at = @At("TAIL"))
    private void finishAndDisposeLookups(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean p_224652_, WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, CallbackInfo ci) {
        MFIX_LOOKUP_CACHE.get().dispose();
        var column = MFIX_BLOCK_COLUMN.get();
        if (column != null) {
            column.dispose();
        }
    }

    @Redirect(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> useFasterLookup(BiomeManager instance, BlockPos pos, @Share("chunkBiomeLookup") LocalRef<ChunkBiomeLookup> lookupRef) {
        return lookupRef.get().apply(pos);
    }

    @Inject(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;<init>(Lnet/minecraft/world/level/levelgen/SurfaceSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Ljava/util/function/Function;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)V"))
    private void captureRealBlockColumn(CallbackInfo ci, @Local(ordinal = 0) LocalRef<BlockColumn> column,
                                        @Local(ordinal = 0, argsOnly = true) ChunkAccess chunk,
                                        @Share("prefetchColumn") LocalRef<PrefetchingBlockColumn> prefetchRef) {
        var prefetchingBlockColumn = MFIX_BLOCK_COLUMN.get();
        if (prefetchingBlockColumn == null || prefetchingBlockColumn.getExpectedHeight() != chunk.getHeight()) {
            prefetchingBlockColumn = new PrefetchingBlockColumn(chunk.getHeight());
            MFIX_BLOCK_COLUMN.set(prefetchingBlockColumn);
        }
        column.set(prefetchingBlockColumn);
        prefetchRef.set(prefetchingBlockColumn);
    }

    @Inject(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;setZ(I)Lnet/minecraft/core/BlockPos$MutableBlockPos;", ordinal = 0, shift = At.Shift.AFTER))
    private void prefetchBlockArray(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean p_224652_,
                                    WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, CallbackInfo ci,
                                    @Local(ordinal = 0) BlockColumn column,
                                    @Local(ordinal = 0) BlockPos.MutableBlockPos cursor) {
        ((PrefetchingBlockColumn)column).prefetch(chunk, cursor.getX() & 15, cursor.getZ() & 15);
    }
}
