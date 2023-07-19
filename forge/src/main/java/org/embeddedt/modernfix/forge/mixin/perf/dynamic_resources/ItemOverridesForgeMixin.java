package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dynamicresources.ItemOverrideBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ItemOverrides.class)
public class ItemOverridesForgeMixin {
    @Shadow @Final private ItemOverrides.BakedOverride[] overrides;
    private volatile boolean forceLoadedModels = false;

    @Inject(method = "bakeModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void useDynamicallyBakedModel(ModelBakery baker, UnbakedModel model, Function<ResourceLocation, UnbakedModel> function, Function<Material, TextureAtlasSprite> textureGetter, ItemOverride override, CallbackInfoReturnable<BakedModel> cir) {
        cir.setReturnValue(ItemOverrideBakedModel.of(override.getModel()));
    }

    @Inject(method = "getOverrides", at = @At("HEAD"), remap = false)
    private void doForceloadModels(CallbackInfoReturnable<ItemOverrides.BakedOverride> cir) {
        if(!forceLoadedModels) {
            synchronized (this) {
                if(!forceLoadedModels) {
                    for(ItemOverrides.BakedOverride override : overrides) {
                        if(override != null && override.model instanceof ItemOverrideBakedModel)
                            override.model = ((ItemOverrideBakedModel)override.model).getRealModel();
                    }
                    forceLoadedModels = true;
                }
            }
        }
    }
}
