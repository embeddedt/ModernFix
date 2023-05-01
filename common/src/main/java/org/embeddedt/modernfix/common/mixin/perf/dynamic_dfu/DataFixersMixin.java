package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import com.mojang.datafixers.DataFixer;
import net.minecraft.util.datafix.DataFixers;
import org.embeddedt.modernfix.dfu.LazyDataFixer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataFixers.class)
public abstract class DataFixersMixin {
    @Shadow protected static DataFixer createFixerUpper() {
        throw new AssertionError();
    }

    private static LazyDataFixer lazyDataFixer;

    /**
     * Avoid classloading the DFU logic until we actually need it.
     */
    @Inject(method = "createFixerUpper", at = @At("HEAD"), cancellable = true)
    private static void createLazyFixerUpper(CallbackInfoReturnable<DataFixer> cir) {
        if(lazyDataFixer == null) {
            lazyDataFixer = new LazyDataFixer(DataFixersMixin::createFixerUpper);
            cir.setReturnValue(lazyDataFixer);
        }
    }
}
