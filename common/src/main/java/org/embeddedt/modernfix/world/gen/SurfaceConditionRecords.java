package org.embeddedt.modernfix.world.gen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

import java.util.function.Predicate;

public class SurfaceConditionRecords {
    public record PerformantBiomeCondition(SurfaceRules.Context pContext, Predicate<ResourceKey<Biome>> biomeNameTest) implements SurfaceRules.Condition {
        @Override
        public boolean test() {
            return pContext.biome.get().is(biomeNameTest);
        }
    }

    public record PerformantStoneDepthCondition(SurfaceRules.Context pContext, int offset, boolean addSurfaceDepth, int secondaryDepthRange, boolean flag) implements SurfaceRules.Condition {
        @Override
        public boolean test() {
            int i = flag ? pContext.stoneDepthBelow : pContext.stoneDepthAbove;
            int j = addSurfaceDepth ? pContext.surfaceDepth : 0;
            int k = secondaryDepthRange == 0
                ? 0
                : (int) Mth.map(pContext.getSurfaceSecondary(), -1.0, 1.0, 0.0, secondaryDepthRange);
            return i <= 1 + offset + j + k;
        }
    }

    public record PerformantVerticalGradientCondition(SurfaceRules.Context pContext, int i, int j, PositionalRandomFactory positionalRandomFactory) implements SurfaceRules.Condition {
        @Override
        public boolean test() {
            int k = pContext.blockY;
            if (k <= i) {
                return true;
            } else if (k >= j) {
                return false;
            } else {
                double d0 = Mth.map(k, i, j, 1.0, 0.0);
                RandomSource randomsource = positionalRandomFactory.at(pContext.blockX, k, pContext.blockZ);
                return (double)randomsource.nextFloat() < d0;
            }
        }
    }

    public record PerformantWaterCondition(SurfaceRules.Context pContext, int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.Condition {
        @Override
        public boolean test() {
            return pContext.waterHeight == Integer.MIN_VALUE
                || pContext.blockY + (addStoneDepth ? pContext.stoneDepthAbove : 0)
                >= pContext.waterHeight
                + offset
                + pContext.surfaceDepth * surfaceDepthMultiplier;
        }
    }

    public record PerformantYCondition(SurfaceRules.Context pContext, VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.Condition {
        @Override
        public boolean test() {
            return pContext.blockY + (addStoneDepth ? pContext.stoneDepthAbove : 0)
                >= anchor.resolveY(pContext.context)
                + pContext.surfaceDepth * surfaceDepthMultiplier;
        }
    }
}
