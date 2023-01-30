package org.embeddedt.modernfix.mixin.perf.cache_model_materials;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.model.BlockModel;
import net.minecraft.client.renderer.model.IUnbakedModel;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.model.VariantList;
import net.minecraft.client.renderer.model.multipart.Multipart;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

@Mixin(value = {VariantList.class, Multipart.class, BlockModel.class})
public class VanillaModelMixin {
    private Collection<RenderMaterial> materialsCache = null;

    @Inject(method = "getMaterials", at = @At("HEAD"), cancellable = true)
    private void useCachedMaterials(Function<ResourceLocation, IUnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors, CallbackInfoReturnable<Collection<RenderMaterial>> cir) {
        if(materialsCache != null) {
            cir.setReturnValue(materialsCache);
        }
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void storeCachedMaterials(Function<ResourceLocation, IUnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors, CallbackInfoReturnable<Collection<RenderMaterial>> cir) {
        if(materialsCache == null)
            materialsCache = Collections.unmodifiableCollection(cir.getReturnValue());
    }
}
