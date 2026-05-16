package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.embeddedt.modernfix.chunk.ExtendedPalettedContainer;
import org.embeddedt.modernfix.world.gen.ExtendedSurfaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.NoSuchElementException;
import java.util.Set;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
    @SuppressWarnings("unchecked")
    private static void mfix$accumulate(Set<ResourceKey<Biome>> chunkBiomes, LevelChunkSection section) {
        var palette = ((ExtendedPalettedContainer<Holder<Biome>>)section.getBiomes()).mfix$getPalette();
        for (int i = 0; i < palette.getSize(); i++) {
            chunkBiomes.add(palette.valueFor(i).unwrapKey().orElseThrow());
        }
    }

    private static Set<ResourceKey<Biome>> mfix$obtainBiomes(WorldGenRegion region, int chunkRadius) {
        Set<ResourceKey<Biome>> chunkBiomes = new ReferenceOpenHashSet<>();
        ChunkPos center = region.getCenter();
        for (int z = center.z() - chunkRadius; z <= center.z() + chunkRadius; z++) {
            for (int x = center.x() - chunkRadius; x <= center.x() + chunkRadius; x++) {
                var chunk = region.getChunk(x, z);
                for (var section : chunk.getSections()) {
                    mfix$accumulate(chunkBiomes, section);
                }
            }
        }
        return chunkBiomes;
    }

    /**
     * @author embeddedt
     * @reason scan for all biomes in the chunk and its neighbors before building surface
     */
    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V"))
    private void mfix$findNearbyBiomes(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        try {
            ExtendedSurfaceContext.COMPUTED_POSSIBLE_BIOMES.set(mfix$obtainBiomes(level, 1));
        } catch (NoSuchElementException ignored) {
            // Catch in case a biome somehow does not have a key. In that case we just don't use the computed set
        }
    }
}
