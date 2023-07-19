package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dynamicresources.ItemOverrideBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ItemOverrides.class)
public class ItemOverridesFabricMixin {
    @Inject(method = "bakeModel", at = @At("HEAD"), cancellable = true)
    private void useDynamicallyBakedModel(ModelBakery baker, BlockModel model, Function<ResourceLocation, UnbakedModel> modelGetter, ItemOverride override, CallbackInfoReturnable<BakedModel> cir) {
        cir.setReturnValue(ItemOverrideBakedModel.of(override.getModel()));
    }
}
