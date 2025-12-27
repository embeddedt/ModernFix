package org.embeddedt.modernfix.common.mixin.feature.suppress_narrator_stacktrace;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.text2speech.Narrator;
import com.mojang.text2speech.NarratorLinux;
import com.mojang.text2speech.OperatingSystem;
import net.minecraft.client.GameNarrator;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@ClientOnlyMixin
@Mixin(GameNarrator.class)
public class GameNarratorMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"))
    private Narrator suppressStacktracePrinting(Operation<Narrator> original) {
        try {
            return switch (OperatingSystem.get()) {
                case LINUX -> new NarratorLinux();
                default -> original.call();
            };
        } catch (Narrator.InitializeException e) {
            ModernFix.LOGGER.warn("Failed to initialize Linux Narrator. Make sure you have libflite installed!");
            return Narrator.EMPTY;
        }
    }
}
