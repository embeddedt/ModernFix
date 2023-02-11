package org.embeddedt.modernfix.mixin.perf.faster_baking;

import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.model.ModelManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/renderer/model/ModelBakery;Lnet/minecraft/resources/IResourceManager;Lnet/minecraft/profiler/IProfiler;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onModelBake(Lnet/minecraft/client/renderer/model/ModelManager;Ljava/util/Map;Lnet/minecraftforge/client/model/ModelLoader;)V", shift = At.Shift.AFTER)
    )
    private void allowBaking(ModelBakery pObject, IResourceManager pResourceManager, IProfiler pProfiler, CallbackInfo ci) {
        LazyBakedModel.allowBakeForFlags = true;
    }
}
