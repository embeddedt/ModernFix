package org.embeddedt.modernfix.duck;

import net.minecraft.world.level.biome.Climate;

public interface IFlatClimateTree<T> {
    boolean mfix$hasFlatTree();

    T mfix$findValueFlat(Climate.TargetPoint target);
}
