package org.embeddedt.modernfix.common.mixin.perf.flat_climate_search;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Climate.RTree.Node.class)
public interface ClimateRTreeNodeAccessor {
    @Accessor("parameterSpace")
    Climate.Parameter[] mfix$getParameterSpace();
}
