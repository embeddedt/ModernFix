package org.embeddedt.modernfix.forge.mixin.core;

import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkHooks;
import org.embeddedt.modernfix.forge.packet.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkHooks.class)
public abstract class NetworkHooksMixin {
    @Shadow public static boolean isVanillaConnection(Connection manager) {
        throw new AssertionError();
    }

    @Inject(method = "handleClientLoginSuccess", at = @At("RETURN"), remap = false)
    private static void setVanillaGlobalFlag(Connection manager, CallbackInfo ci) {
        NetworkUtils.isCurrentlyVanilla = isVanillaConnection(manager);
    }
}
