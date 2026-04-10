package org.embeddedt.modernfix.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Drop-in replacement for {@code biomeManager::getBiome} in SurfaceSystem.buildSurface.
 *
 * <p>Pre-computes the Voronoi bias (fiddle) values and quart-resolution biome data for an
 * entire chunk, then uses two optimizations:
 * <ul>
 *   <li><b>Uniform check:</b> If all 8 Voronoi candidate cells for a given quart position
 *       hold the same biome, the Voronoi computation is skipped entirely.</li>
 *   <li><b>Pre-computed bias:</b> When the Voronoi is needed, the 48 LCG operations per block
 *       are replaced by array lookups of pre-computed fiddle values.</li>
 * </ul>
 */
public class ChunkBiomeLookup implements Function<BlockPos, Holder<Biome>> {
    @SuppressWarnings("unchecked")
    private Holder<Biome>[] biomes = new Holder[0];
    private double[] biasX = new double[0], biasY = new double[0], biasZ = new double[0];
    private boolean[] uniform = new boolean[0];

    private int qMinX, qMinY, qMinZ;
    private int sizeX, sizeY, sizeZ;

    private BiomeManager fallbackManager;

    /**
     * Pre-compute biome and bias data for the given chunk. Must be called before any
     * {@link #getBiome} calls for positions within this chunk.
     *
     * @param source        the underlying quart-resolution biome source (e.g. the chunk)
     * @param biomeZoomSeed the obfuscated biome zoom seed from BiomeManager
     */
    @SuppressWarnings("unchecked")
    public void prepare(BiomeManager.NoiseBiomeSource source, long biomeZoomSeed, ChunkAccess chunk, BiomeManager fallback) {
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int minBuildHeight = chunk.getMinY();
        int maxBuildHeight = minBuildHeight + chunk.getHeight(); // exclusive

        // BiomeManager.getBiome subtracts a 2-block offset before converting to quart coords,
        // then considers quart and quart+1 as the 8 Voronoi candidates.
        int biomeOffset = 2;
        int minBlockX = chunkMinX - biomeOffset;
        int maxBlockX = chunkMinX + 15 - biomeOffset;
        int minBlockZ = chunkMinZ - biomeOffset;
        int maxBlockZ = chunkMinZ + 15 - biomeOffset;
        int minBlockY = minBuildHeight - biomeOffset;
        int maxBlockY = maxBuildHeight - 1 - biomeOffset;

        // Quart range: fromBlock(min) to fromBlock(max) + 1 (for the +1 Voronoi candidate)
        this.qMinX = QuartPos.fromBlock(minBlockX);
        int qMaxX = QuartPos.fromBlock(maxBlockX) + 1;
        this.qMinZ = QuartPos.fromBlock(minBlockZ);
        int qMaxZ = QuartPos.fromBlock(maxBlockZ) + 1;
        this.qMinY = QuartPos.fromBlock(minBlockY);
        int qMaxY = QuartPos.fromBlock(maxBlockY) + 1;

        this.sizeX = qMaxX - qMinX + 1; // always 6 for 16-wide chunks
        this.sizeY = qMaxY - qMinY + 1;
        this.sizeZ = qMaxZ - qMinZ + 1;

        int totalCells = sizeX * sizeY * sizeZ;

        // Reuse arrays across chunks if large enough
        if (biomes.length < totalCells) {
            biomes = new Holder[totalCells];
            biasX = new double[totalCells];
            biasY = new double[totalCells];
            biasZ = new double[totalCells];
            uniform = new boolean[totalCells];
        }

        // Fetch quart-resolution biomes
        boolean isSingleBiome = !fetchBiomes(source);

        if (isSingleBiome) {
            // All cells hold the same biome, so no need to do expensive computations.
            Arrays.fill(uniform, 0, totalCells, true);
        } else {
            this.computeUniformity();
            this.computeBiases(biomeZoomSeed);
        }

        this.fallbackManager = fallback;
    }

    public void dispose() {
        // Make sure we do not retain strong references to the biome holders
        Arrays.fill(biomes, null);
        this.fallbackManager = null;
    }

    private boolean fetchBiomes(BiomeManager.NoiseBiomeSource source) {
        var biomes = this.biomes;
        Holder<Biome> firstSeen = null;
        boolean seenMultiple = false;
        for (int rx = 0; rx < sizeX; rx++) {
            int wx = qMinX + rx;
            for (int rz = 0; rz < sizeZ; rz++) {
                int wz = qMinZ + rz;
                for (int ry = 0; ry < sizeY; ry++) {
                    int wy = qMinY + ry;
                    var biome = source.getNoiseBiome(wx, wy, wz);
                    biomes[index(rx, ry, rz)] = biome;
                    if (biome != firstSeen) {
                        if (firstSeen == null) {
                            firstSeen = biome;
                        } else {
                            seenMultiple = true;
                        }
                    }
                }
            }
        }
        return seenMultiple;
    }

    private void computeUniformity() {
        // For each quart position, check if all 8 Voronoi candidates hold the same biome.
        // If so, the Voronoi result is guaranteed to be that biome regardless of fractional
        // position, so we can skip the distance computation entirely.
        var uniform = this.uniform;
        int sizeX = this.sizeX, sizeY = this.sizeY, sizeZ = this.sizeZ;

        for (int rx = 0; rx < sizeX - 1; rx++) {
            for (int rz = 0; rz < sizeZ - 1; rz++) {
                for (int ry = 0; ry < sizeY - 1; ry++) {
                    uniform[index(rx, ry, rz)] = isUniform(rx, ry, rz);
                }
            }
        }
    }

    private void computeBiases(long biomeZoomSeed) {
        int sizeX = this.sizeX, sizeY = this.sizeY, sizeZ = this.sizeZ;
        int qMinX = this.qMinX, qMinY = this.qMinY, qMinZ = this.qMinZ;

        // Pre-compute bias (fiddle) values for the Voronoi distance computation.
        for (int rx = 0; rx < sizeX; rx++) {
            int wx = qMinX + rx;
            for (int rz = 0; rz < sizeZ; rz++) {
                int wz = qMinZ + rz;
                for (int ry = 0; ry < sizeY; ry++) {
                    computeBias(index(rx, ry, rz), biomeZoomSeed, wx, qMinY + ry, wz);
                }
            }
        }
    }

    private void computeBias(int idx, long seed, int x, int y, int z) {
        // Reproduces the LCG chain from BiomeManager.getFiddledDistance exactly
        long s = LinearCongruentialGenerator.next(seed, x);
        s = LinearCongruentialGenerator.next(s, y);
        s = LinearCongruentialGenerator.next(s, z);
        s = LinearCongruentialGenerator.next(s, x);
        s = LinearCongruentialGenerator.next(s, y);
        s = LinearCongruentialGenerator.next(s, z);
        biasX[idx] = getFiddle(s);
        s = LinearCongruentialGenerator.next(s, seed);
        biasY[idx] = getFiddle(s);
        s = LinearCongruentialGenerator.next(s, seed);
        biasZ[idx] = getFiddle(s);
    }

    private static double getFiddle(long seed) {
        double d = (double) Math.floorMod(seed >> 24, 1024) / 1024.0D;
        return (d - 0.5D) * 0.9D;
    }

    private boolean isUniform(int rx, int ry, int rz) {
        var biomes = this.biomes;
        Holder<Biome> ref = biomes[index(rx, ry, rz)];
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (biomes[index(rx + dx, ry + dy, rz + dz)] != ref) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int index(int rx, int ry, int rz) {
        return (rx * sizeY + ry) * sizeZ + rz;
    }

    @Override
    public Holder<Biome> apply(BlockPos pos) {
        return getBiome(pos);
    }

    public Holder<Biome> getBiome(BlockPos pos) {
        int i = pos.getX() - 2;
        int j = pos.getY() - 2;
        int k = pos.getZ() - 2;

        int rx = QuartPos.fromBlock(i) - qMinX;
        int ry = QuartPos.fromBlock(j) - qMinY;
        int rz = QuartPos.fromBlock(k) - qMinZ;

        if (rx < 0 || rx >= sizeX - 1 || ry < 0 || ry >= sizeY - 1 || rz < 0 || rz >= sizeZ - 1) {
            return fallbackManager.getBiome(pos);
        }

        int baseIdx = index(rx, ry, rz);
        if (uniform[baseIdx]) {
            return biomes[baseIdx];
        }

        return getBiomeWithVoronoi(i, j, k, rx, ry, rz);
    }

    private Holder<Biome> getBiomeWithVoronoi(int i, int j, int k, int rx, int ry, int rz) {
        var biasX = this.biasX;
        var biasY = this.biasY;
        var biasZ = this.biasZ;

        double d0 = (double) QuartPos.quartLocal(i) / 4.0D;
        double d1 = (double) QuartPos.quartLocal(j) / 4.0D;
        double d2 = (double) QuartPos.quartLocal(k) / 4.0D;

        int closestIdx = 0;
        double closestDist = Double.POSITIVE_INFINITY;

        for (int c = 0; c < 8; c++) {
            boolean fx = (c & 4) == 0;
            boolean fy = (c & 2) == 0;
            boolean fz = (c & 1) == 0;

            int idx = index(
                    rx + (fx ? 0 : 1),
                    ry + (fy ? 0 : 1),
                    rz + (fz ? 0 : 1)
            );

            double dx = (fx ? d0 : d0 - 1.0D) + biasX[idx];
            double dy = (fy ? d1 : d1 - 1.0D) + biasY[idx];
            double dz = (fz ? d2 : d2 - 1.0D) + biasZ[idx];
            double dist = Mth.square(dx) + Mth.square(dy) + Mth.square(dz);

            if (dist < closestDist) {
                closestDist = dist;
                closestIdx = idx;
            }
        }

        return biomes[closestIdx];
    }
}
