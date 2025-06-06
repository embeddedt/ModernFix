package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.DataFixerBuilder;
import net.minecraft.util.datafix.DataFixers;
import org.embeddedt.modernfix.dfu.DFUBlaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DataFixers.class)
public class DataFixersMixin {
    @ModifyReturnValue(method = "createFixerUpper", at = @At("RETURN"))
    private static DataFixerBuilder.Result setupMapBlasting(DataFixerBuilder.Result original) {
        DFUBlaster.blastMaps();
        return original;
    }
}
