package org.embeddedt.modernfix.forge.mixin.core;

import net.minecraft.network.Connection;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkContext;
import org.embeddedt.modernfix.forge.packet.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeHooks.class)
public abstract class NetworkHooksMixin {
    @Inject(method = "handleClientConfigurationComplete", at = @At("RETURN"), remap = false)
    private static void setVanillaGlobalFlag(Connection manager, CallbackInfo ci) {
        NetworkUtils.isCurrentlyVanilla = NetworkContext.get(manager).getType() == ConnectionType.VANILLA;
    }
}
