package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.embeddedt.modernfix.world.gen.SurfaceConditionRecords;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;


public class SurfaceRulesMixin {
    @Mixin(value = SurfaceRules.BiomeConditionSource.class, priority = 100)
    public static final class BiomeConditionSource {
        @Final @Shadow Predicate<ResourceKey<Biome>> biomeNameTest;
        /**
         * @author VoidsongDragonfly
         * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance detriments due to unused caching behavior.
         * This is an exact reimplementation of the surface rule, without the caching; check code and effect are identical.
         */
        @Overwrite
        public SurfaceRules.Condition apply(final SurfaceRules.Context pContext) {
            // Return the condition; this record is kept outside the class at embeddedt's wishes due to mixin compat concerns
            return new SurfaceConditionRecords.PerformantBiomeCondition(pContext, biomeNameTest);
        }
    }

    @Mixin(value = SurfaceRules.StoneDepthCheck.class, priority = 100)
    public static final class StoneDepthCheck {
        @Final @Shadow int offset;
        @Final @Shadow boolean addSurfaceDepth;
        @Final @Shadow int secondaryDepthRange;
        @Final @Shadow private CaveSurface surfaceType;

        /**
         * @author VoidsongDragonfly
         * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance detriments due to unused caching behavior.
         * This is an exact reimplementation of the surface rule, without the caching; check code and effect are identical.
         */
        @Overwrite
        public SurfaceRules.Condition apply(final SurfaceRules.Context pContext) {
            // Copied Vanilla variables
            final boolean flag = this.surfaceType == CaveSurface.CEILING;
            // Return the condition; this record is kept outside the class at embeddedt's wishes due to mixin compat concerns
            return new SurfaceConditionRecords.PerformantStoneDepthCondition(pContext, offset, addSurfaceDepth, secondaryDepthRange, flag);
        }
    }

    @Mixin(value = SurfaceRules.VerticalGradientConditionSource.class, priority = 100)
    public static final class VerticalGradientConditionSource {
        @Final @Shadow private ResourceLocation randomName;
        @Final @Shadow private VerticalAnchor trueAtAndBelow;
        @Final @Shadow private VerticalAnchor falseAtAndAbove;

        /**
         * @author VoidsongDragonfly
         * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance detriments due to unused caching behavior.
         * This is an exact reimplementation of the surface rule, without the caching; check code and effect are identical.
         */
        @Overwrite
        public SurfaceRules.Condition apply(final SurfaceRules.Context pContext) {
            // Copied Vanilla variables
            final int i = trueAtAndBelow.resolveY(pContext.context);
            final int j = falseAtAndAbove.resolveY(pContext.context);
            final PositionalRandomFactory positionalRandomFactory = pContext.randomState.getOrCreateRandomFactory(randomName);
            // Return the condition; this record is kept outside the class at embeddedt's wishes due to mixin compat concerns
            return new SurfaceConditionRecords.PerformantVerticalGradientCondition(pContext, i, j, positionalRandomFactory);
        }
    }

    @Mixin(value = SurfaceRules.WaterConditionSource.class, priority = 100)
    public static final class WaterConditionSource {
        @Final @Shadow int offset;
        @Final @Shadow int surfaceDepthMultiplier;
        @Final @Shadow boolean addStoneDepth;

        /**
         * @author VoidsongDragonfly
         * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance detriments due to unused caching behavior.
         * This is an exact reimplementation of the surface rule, without the caching; check code and effect are identical.
         */
        @Overwrite
        public SurfaceRules.Condition apply(final SurfaceRules.Context pContext) {
            // Return the condition; this record is kept outside the class at embeddedt's wishes due to mixin compat concerns
            return new SurfaceConditionRecords.PerformantWaterCondition(pContext, offset, surfaceDepthMultiplier, addStoneDepth);
        }

    }

    @Mixin(value = SurfaceRules.YConditionSource.class, priority = 100)
    public static final class YConditionSource {
        @Final @Shadow VerticalAnchor anchor;
        @Final @Shadow int surfaceDepthMultiplier;
        @Final @Shadow boolean addStoneDepth;
        /**
         * @author VoidsongDragonfly
         * @reason Replacing Vanilla's use of {@link SurfaceRules.LazyYCondition LazyYCondition} that causes performance detriments due to unused caching behavior.
         * This is an exact reimplementation of the surface rule, without the caching; check code and effect are identical.
         */
        @Overwrite
        public SurfaceRules.Condition apply(final SurfaceRules.Context pContext) {
            // Return the condition; this record is kept outside the class at embeddedt's wishes due to mixin compat concerns
            return new SurfaceConditionRecords.PerformantYCondition(pContext, anchor, surfaceDepthMultiplier, addStoneDepth);
        }
    }
}
