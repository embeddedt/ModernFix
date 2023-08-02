package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.WorldStem;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    @Shadow @Nullable public Overlay overlay;
    private long datapackReloadStartTime;

    @Inject(method = "makeWorldStem(Lnet/minecraft/server/packs/repository/PackRepository;ZLnet/minecraft/server/WorldStem$DataPackConfigSupplier;Lnet/minecraft/server/WorldStem$WorldDataSupplier;)Lnet/minecraft/server/WorldStem;", at = @At(value = "HEAD"))
    private void recordReloadStart(CallbackInfoReturnable<WorldStem> cir) {
        datapackReloadStartTime = System.nanoTime();
    }

    @Inject(method = "makeWorldStem(Lnet/minecraft/server/packs/repository/PackRepository;ZLnet/minecraft/server/WorldStem$DataPackConfigSupplier;Lnet/minecraft/server/WorldStem$WorldDataSupplier;)Lnet/minecraft/server/WorldStem;", at = @At(value = "RETURN"))
    private void recordReloadEnd(CallbackInfoReturnable<WorldStem> cir) {
        float timeSpentReloading = ((float)(System.nanoTime() - datapackReloadStartTime) / 1000000000f);
        ModernFix.LOGGER.warn("Datapack reload took " + timeSpentReloading + " seconds.");
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        if(this.overlay == null) {
            ModernFixClient.INSTANCE.onGameLaunchFinish();
        }
    }
}
