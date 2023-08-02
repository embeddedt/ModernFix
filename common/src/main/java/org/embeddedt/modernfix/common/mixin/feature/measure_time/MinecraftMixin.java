package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    // TODO re-add datapack reload time measurement
    @Shadow @Nullable public Overlay overlay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        if(this.overlay == null) {
            ModernFixClient.INSTANCE.onGameLaunchFinish();
        }
    }
}
