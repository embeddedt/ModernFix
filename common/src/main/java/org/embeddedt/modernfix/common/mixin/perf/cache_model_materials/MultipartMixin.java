package org.embeddedt.modernfix.common.mixin.perf.cache_model_materials;

import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(MultiPart.class)
@ClientOnlyMixin
public class MultipartMixin {
    private Collection<ResourceLocation> dependencyCache = null;
    @Inject(method = "getDependencies", at = @At("HEAD"), cancellable = true)
    private void useDependencyCache(CallbackInfoReturnable<Collection<ResourceLocation>> cir) {
        if(dependencyCache != null)
            cir.setReturnValue(dependencyCache);
    }

    @Inject(method = "getDependencies", at = @At("RETURN"))
    private void storeDependencyCache(CallbackInfoReturnable<Collection<ResourceLocation>> cir) {
        if(dependencyCache == null)
            dependencyCache = cir.getReturnValue();
    }
}
