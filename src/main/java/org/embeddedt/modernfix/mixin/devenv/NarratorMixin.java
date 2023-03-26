package org.embeddedt.modernfix.mixin.devenv;

import com.mojang.text2speech.Narrator;
import com.mojang.text2speech.NarratorDummy;
import net.minecraft.client.gui.chat.NarratorChatListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NarratorChatListener.class)
public class NarratorMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"))
    private Narrator useDummyNarrator() {
        return new NarratorDummy();
    }
}
