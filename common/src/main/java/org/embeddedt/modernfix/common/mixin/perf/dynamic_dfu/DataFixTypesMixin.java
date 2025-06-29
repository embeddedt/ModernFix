package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.DataFixTypes;
import org.embeddedt.modernfix.dfu.DFUBlaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataFixTypes.class)
public class DataFixTypesMixin {
    @Inject(method = "update(Lcom/mojang/datafixers/DataFixer;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;", at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/DataFixer;update(Lcom/mojang/datafixers/DSL$TypeReference;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;"), remap = false)
    private void kickOnUpdate(DataFixer fixer, Dynamic<?> input, int version, int newVersion, CallbackInfoReturnable<Dynamic<?>> cir) {
        if (version < newVersion) {
            DFUBlaster.kick();
        }
    }
}
