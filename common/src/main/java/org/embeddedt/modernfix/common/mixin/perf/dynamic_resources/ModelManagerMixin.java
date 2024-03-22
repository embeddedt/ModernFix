package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Shadow private Map<ResourceLocation, BakedModel> bakedRegistry;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectDummyBakedRegistry(CallbackInfo ci) {
        if(this.bakedRegistry == null) {
            // Create a dummy baked registry. This prevents NPEs when mods query block models too early
            this.bakedRegistry = new HashMap<>();
        }
    }
}
