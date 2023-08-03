package org.embeddedt.modernfix.fabric.mixin.core;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.embeddedt.modernfix.ModernFixClientFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 1500)
public class ClientPlayNetHandlerMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void signalRecipes(CallbackInfo ci) {
        ModernFixClientFabric.commonMod.onRecipesUpdated();
    }
}
