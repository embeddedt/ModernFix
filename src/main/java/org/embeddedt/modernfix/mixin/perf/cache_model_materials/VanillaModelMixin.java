package org.embeddedt.modernfix.mixin.perf.cache_model_materials;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

@Mixin(value = {MultiVariant.class, MultiPart.class, BlockModel.class})
public class VanillaModelMixin {
    private Collection<Material> materialsCache = null;

    @Inject(method = "getMaterials", at = @At("HEAD"), cancellable = true)
    private void useCachedMaterials(Function<ResourceLocation, UnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors, CallbackInfoReturnable<Collection<Material>> cir) {
        if(materialsCache != null) {
            cir.setReturnValue(materialsCache);
        }
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void storeCachedMaterials(Function<ResourceLocation, UnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors, CallbackInfoReturnable<Collection<Material>> cir) {
        if(materialsCache == null)
            materialsCache = Collections.unmodifiableCollection(cir.getReturnValue());
    }
}
