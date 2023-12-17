package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.ItemOverrideBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemOverrides.class)
@ClientOnlyMixin
public class ItemOverridesFabricMixin {
    @Inject(method = "bakeModel", at = @At("HEAD"), cancellable = true)
    private void useDynamicallyBakedModel(ModelBaker baker, BlockModel model, ItemOverride override, CallbackInfoReturnable<BakedModel> cir) {
        cir.setReturnValue(ItemOverrideBakedModel.of(override.getModel()));
    }
}
