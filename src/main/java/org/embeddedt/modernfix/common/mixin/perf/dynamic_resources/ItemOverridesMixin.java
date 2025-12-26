package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemOverrides.class)
@ClientOnlyMixin
public class ItemOverridesMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBaker;getModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;"))
    private UnbakedModel preventThrowForMissing(ModelBaker instance, ResourceLocation resourceLocation, Operation<UnbakedModel> original) {
        boolean prevState = ((IExtendedModelBaker)instance).throwOnMissingModel(false);
        try {
            return original.call(instance, resourceLocation);
        } finally {
            ((IExtendedModelBaker)instance).throwOnMissingModel(prevState);
        }
    }
}
