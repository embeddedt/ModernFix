package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ItemModelShaper.class)
@ClientOnlyMixin
public abstract class ItemModelShaperMixin {
    @Shadow @Final @Mutable private Map<ResourceLocation, BakedModel> modelToBakedModel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeLazyCache(CallbackInfo ci) {
        this.modelToBakedModel = new Object2ObjectLinkedOpenHashMap<>(this.modelToBakedModel);
    }

    /**
     * @author embeddedt
     * @reason Prevent all baked item models from being cached forever. We can safely mutate the map here as vanilla
     * also uses computeIfAbsent, which means multithreaded access is not safe in vanilla either.
     */
    @Inject(method = "getItemModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At(value = "RETURN"))
    private void limitCacheSize(ResourceLocation resourceLocation, CallbackInfoReturnable<BakedModel> cir) {
        var map = modelToBakedModel;
        if (map instanceof Object2ObjectLinkedOpenHashMap<ResourceLocation, BakedModel> linkedMap && linkedMap.size() > 1000) {
            linkedMap.removeFirst();
        }
    }
}
