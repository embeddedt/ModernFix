package org.embeddedt.modernfix.common.mixin.perf.flat_climate_search;

import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.duck.IFlatClimateLeaf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.RTree.Leaf.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ClimateRTreeLeafMixin implements IFlatClimateLeaf {
    @Unique
    private int mfix$flatIndex = -1;

    @Override
    public int mfix$getFlatIndex() {
        return this.mfix$flatIndex;
    }

    @Override
    public void mfix$setFlatIndex(int index) {
        this.mfix$flatIndex = index;
    }
}
