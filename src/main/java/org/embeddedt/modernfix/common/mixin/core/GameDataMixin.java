package org.embeddedt.modernfix.common.mixin.core;

import net.minecraftforge.registries.GameData;
import org.embeddedt.modernfix.forge.init.ModernFixForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameData.class, remap = false)
public class GameDataMixin {
    @Inject(method = "postRegisterEvents", at = @At("RETURN"))
    private static void markPosted(CallbackInfo ci) {
        ModernFixForge.registryEventsFired = true;
    }
}
