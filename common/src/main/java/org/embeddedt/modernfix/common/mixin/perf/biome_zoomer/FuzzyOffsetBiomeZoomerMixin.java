package org.embeddedt.modernfix.common.mixin.perf.biome_zoomer;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FuzzyOffsetBiomeZoomer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FuzzyOffsetBiomeZoomer.class)
public abstract class FuzzyOffsetBiomeZoomerMixin {
    @Shadow protected static double getFiddledDistance(long seed, int x, int y, int z, double scaleX, double scaleY, double scaleZ) {
        throw new AssertionError();
    }

    /**
     * @author embeddedt
     * @reason use the modern logic that doesn't allocate an array of 8 doubles every time
     */
    @Overwrite
    public Biome getBiome(long seed, int xIn, int yIn, int zIn, BiomeManager.NoiseBiomeSource biomeReader) {
        int i = xIn - 2;
        int j = yIn - 2;
        int k = zIn - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        double d0 = (double)(i & 3) / 4.0D;
        double d1 = (double)(j & 3) / 4.0D;
        double d2 = (double)(k & 3) / 4.0D;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;

        for(int l1 = 0; l1 < 8; ++l1) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            int i2 = flag ? l : l + 1;
            int j2 = flag1 ? i1 : i1 + 1;
            int k2 = flag2 ? j1 : j1 + 1;
            double d4 = flag ? d0 : d0 - 1.0D;
            double d5 = flag1 ? d1 : d1 - 1.0D;
            double d6 = flag2 ? d2 : d2 - 1.0D;
            double d7 = getFiddledDistance(seed, i2, j2, k2, d4, d5, d6);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }

        int l2 = (k1 & 4) == 0 ? l : l + 1;
        int i3 = (k1 & 2) == 0 ? i1 : i1 + 1;
        int j3 = (k1 & 1) == 0 ? j1 : j1 + 1;
        return biomeReader.getNoiseBiome(l2, i3, j3);
    }

}
