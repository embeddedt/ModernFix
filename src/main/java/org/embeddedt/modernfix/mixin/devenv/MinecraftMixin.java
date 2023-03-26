package org.embeddedt.modernfix.mixin.devenv;

import com.mojang.authlib.minecraft.OfflineSocialInteractions;
import com.mojang.authlib.minecraft.SocialInteractionsService;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "createSocialInteractions", at = @At("HEAD"), cancellable = true)
    private void noSocialInteraction(CallbackInfoReturnable<SocialInteractionsService> cir) {
        cir.setReturnValue(new OfflineSocialInteractions());
    }
}
