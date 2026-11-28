package org.embeddedt.modernfix.common.mixin.perf.flat_climate_search;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.duck.IFlatClimateTree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Climate.ParameterList.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ClimateParameterListMixin<T> {
    @Shadow @Final private Climate.RTree<T> index;

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Ljava/lang/Object;")
    private T searchFlatTree(Climate.TargetPoint target, Operation<T> original) {
        // A subclass may override the two-argument overload with its own distance metric.
        IFlatClimateTree<T> tree = (IFlatClimateTree<T>) (Object) this.index;
        if ((Object) this.getClass() != Climate.ParameterList.class || !tree.mfix$hasFlatTree()) {
            return original.call(target);
        }
        return tree.mfix$findValueFlat(target);
    }
}
