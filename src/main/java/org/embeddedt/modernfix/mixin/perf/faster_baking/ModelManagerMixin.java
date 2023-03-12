package org.embeddedt.modernfix.mixin.perf.faster_baking;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.ResourceManager;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At(value = "RETURN"))
    private void allowBake(ModelBakery pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfo ci) {
        LazyBakedModel.allowBakeForFlags = true;
    }
}
