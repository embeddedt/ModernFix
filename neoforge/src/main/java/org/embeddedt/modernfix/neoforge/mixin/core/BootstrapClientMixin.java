package org.embeddedt.modernfix.neoforge.mixin.core;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
@ClientOnlyMixin
public class BootstrapClientMixin {
    /**
     * Hack to workaround RenderStateShard deadlock (by loading it early on a single thread). We use validate
     * here to ensure Forge registries are initialized.
     */
    @Inject(method = "validate", at = @At("HEAD"))
    private static void loadClientClasses(CallbackInfo ci) {
        RenderType.solid();
    }
}
