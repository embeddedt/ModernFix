package org.embeddedt.modernfix.common.mixin.feature.remove_chat_signing;

import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatTrustLevel.class)
@ClientOnlyMixin
public class ChatTrustLevelMixin {
    @Inject(method = "evaluate", at = @At("HEAD"), cancellable = true)
    private static void alwaysShowSecure(CallbackInfoReturnable<ChatTrustLevel> cir) {
        cir.setReturnValue(ChatTrustLevel.SECURE);
    }
}
