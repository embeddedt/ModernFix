package org.embeddedt.modernfix.fabric.mixin.perf.dynamic_resources;

import net.fabricmc.fabric.impl.client.model.ModelLoadingRegistryImpl;
import net.minecraft.client.resources.model.ModelBakery;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelLoadingRegistryImpl.LoaderInstance.class)
@RequiresMod("fabric-models-v0")
@ClientOnlyMixin
public class LoaderInstanceMixin {
    @Redirect(method = "finish", at = @At(value = "FIELD", target = "Lnet/fabricmc/fabric/impl/client/model/ModelLoadingRegistryImpl$LoaderInstance;loader:Lnet/minecraft/client/resources/model/ModelBakery;"))
    private void keepLoader(ModelLoadingRegistryImpl.LoaderInstance instance, ModelBakery value) {
        /* allow loading models to happen later */
    }
}