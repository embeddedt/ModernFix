package org.embeddedt.modernfix.common.mixin.perf.flat_climate_search;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Climate.RTree.Leaf.class)
public interface ClimateRTreeLeafAccessor {
    @Accessor("value")
    Object mfix$getValue();
}
