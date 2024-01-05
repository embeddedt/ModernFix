package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemOverrides.class)
@ClientOnlyMixin
public class ItemOverridesFabricMixin {
    /**
     * @author embeddedt
     * @reason servers insist on generating invalid item overrides that have missing models
     */
    @WrapOperation(method = "bakeModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBaker;bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel bake(ModelBaker instance, ResourceLocation resourceLocation, ModelState modelState, Operation<BakedModel> original) {
        boolean prevState = ((IExtendedModelBaker)instance).throwOnMissingModel(false);
        try {
            return original.call(instance, resourceLocation, modelState);
        } finally {
            ((IExtendedModelBaker)instance).throwOnMissingModel(prevState);
        }
    }
}
