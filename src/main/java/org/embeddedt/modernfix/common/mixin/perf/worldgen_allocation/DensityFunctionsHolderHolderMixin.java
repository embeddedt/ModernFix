package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.embeddedt.modernfix.world.gen.MemoizingDensityVisitor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DensityFunctions.HolderHolder.class)
public class DensityFunctionsHolderHolderMixin {
    @WrapMethod(method = "mapAll")
    private DensityFunction memoizeMapAll(DensityFunction.Visitor visitor, Operation<DensityFunction> original) {
        if (!(visitor instanceof MemoizingDensityVisitor memo)) {
            return original.call(visitor);
        }
        DensityFunction key = ((DensityFunctions.HolderHolder)(Object)this).function().value();
        DensityFunction result = memo.holders.get(key);
        if (result == null) {
            result = original.call(visitor);
            memo.holders.put(key, result);
        }
        return result;
    }
}
