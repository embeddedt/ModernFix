package org.embeddedt.modernfix.common.mixin.safety;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
@ClientOnlyMixin
public class LivingEntityRendererMixin {
    @Shadow @Final @Mutable
    protected List<RenderLayer<?, ?>> layers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void synchronizeLayerList(CallbackInfo ci) {
        /* allows buggy mods to call addLayer concurrently, order is not deterministic but can't fix that */
        this.layers = Collections.synchronizedList(layers);
    }
}
