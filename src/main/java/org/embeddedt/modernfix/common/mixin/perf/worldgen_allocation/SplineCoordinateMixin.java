package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.embeddedt.modernfix.world.gen.MemoizingDensityVisitor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DensityFunctions.Spline.Coordinate.class)
public class SplineCoordinateMixin {
    @WrapMethod(method = "mapAll")
    private DensityFunctions.Spline.Coordinate memoizeMapAll(DensityFunction.Visitor visitor, Operation<DensityFunctions.Spline.Coordinate> original) {
        if (!(visitor instanceof MemoizingDensityVisitor memo)) {
            return original.call(visitor);
        }
        DensityFunction key = ((DensityFunctions.Spline.Coordinate)(Object)this).function().value();
        DensityFunctions.Spline.Coordinate result = memo.coordinates.get(key);
        if (result == null) {
            result = original.call(visitor);
            memo.coordinates.put(key, result);
        }
        return result;
    }
}
