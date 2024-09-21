package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public abstract class MinecraftMixin_ModelTicking {
    @Shadow public abstract ModelManager getModelManager();

    @Inject(method = "tick", at = @At(value = "RETURN"))
    private void tickModels(CallbackInfo ci) {
        ((IExtendedModelManager)this.getModelManager()).mfix$tick();
    }
}
