package org.embeddedt.modernfix.neoforge.mixin.core;

import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.NetworkHooks;
import org.embeddedt.modernfix.neoforge.packet.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkHooks.class)
public abstract class NetworkHooksMixin {
    @Inject(method = "handleClientLoginSuccess", at = @At("RETURN"), remap = false)
    private static void setVanillaGlobalFlag(Connection manager, CallbackInfo ci) {
        NetworkUtils.isCurrentlyVanilla = NetworkHooks.isVanillaConnection(manager);
    }
}
