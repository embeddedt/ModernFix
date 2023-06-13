package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SharedConstants.class)
public class SharedConstantsMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void skipSchemaCheck(CallbackInfo ci) {
        SharedConstants.CHECK_DATA_FIXER_SCHEMA = false;
    }
}
