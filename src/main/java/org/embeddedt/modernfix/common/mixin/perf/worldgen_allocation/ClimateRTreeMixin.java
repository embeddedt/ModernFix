package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.world.gen.ClimateRTreeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(Climate.RTree.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ClimateRTreeMixin {
    /**
     * @author embeddedt
     * @reason Build the same tree as vanilla with far less sorting overhead and allocation
     */
    @Overwrite
    public static <T> Climate.RTree<T> create(List<Pair<Climate.ParameterPoint, T>> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Need at least one value to build the search tree.");
        }
        return new Climate.RTree<>(ClimateRTreeBuilder.build(points));
    }
}
