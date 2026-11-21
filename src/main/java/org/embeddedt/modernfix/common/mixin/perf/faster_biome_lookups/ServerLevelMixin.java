package org.embeddedt.modernfix.common.mixin.perf.faster_biome_lookups;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements LevelReader {
    @Shadow
    public abstract ServerChunkCache getChunkSource();

    // The chunk cache only matches the exact status, so the default BIOMES lookup misses loaded chunks cached as FULL.
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        int chunkX = QuartPos.toSection(x);
        int chunkZ = QuartPos.toSection(z);
        LevelChunk loaded = this.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            return loaded.getNoiseBiome(x, y, z);
        }
        ChunkAccess chunk = this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false);
        return chunk != null ? chunk.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }
}
