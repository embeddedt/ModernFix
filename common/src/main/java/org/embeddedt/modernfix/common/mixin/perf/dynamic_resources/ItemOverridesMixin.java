package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import org.embeddedt.modernfix.dynamicresources.ItemOverrideBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemOverrides.class)
public class ItemOverridesMixin {
    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void getRealModel(CallbackInfoReturnable<BakedModel> cir) {
        BakedModel original = cir.getReturnValue();
        if(original instanceof ItemOverrideBakedModel) {
            ItemOverrideBakedModel override = (ItemOverrideBakedModel)original;
            cir.setReturnValue(override.getRealModel());
        }
    }
}
