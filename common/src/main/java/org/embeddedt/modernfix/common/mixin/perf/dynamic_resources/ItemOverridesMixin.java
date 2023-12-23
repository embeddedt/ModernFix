package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.dynamicresources.ItemOverrideBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemOverrides.class)
public class ItemOverridesMixin {
    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void getRealModel(BakedModel bakedModel, ItemStack itemStack, ClientLevel clientLevel, LivingEntity livingEntity, CallbackInfoReturnable<BakedModel> cir) {
        BakedModel original = cir.getReturnValue();
        if(original instanceof ItemOverrideBakedModel) {
            ItemOverrideBakedModel override = (ItemOverrideBakedModel)original;
            BakedModel overrideModel = override.getRealModel();
            cir.setReturnValue(overrideModel != null ? overrideModel : bakedModel);
        }
    }
}
