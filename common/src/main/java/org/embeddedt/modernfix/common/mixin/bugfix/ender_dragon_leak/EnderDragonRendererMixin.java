package org.embeddedt.modernfix.common.mixin.bugfix.ender_dragon_leak;

import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonRenderer.class)
@ClientOnlyMixin
public abstract class EnderDragonRendererMixin {
    @Shadow @Final private EnderDragonRenderer.DragonModel model;

    /**
     * Prevent leaking the client world through the entity reference.
     */
    @Inject(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void clearDragonEntityReference(CallbackInfo ci) {
        this.model.entity = null;
    }
}
