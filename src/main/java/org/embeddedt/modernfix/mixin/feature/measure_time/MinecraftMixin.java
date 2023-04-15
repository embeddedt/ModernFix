package org.embeddedt.modernfix.mixin.feature.measure_time;

import com.mojang.datafixers.util.Function4;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    /* not supported in 1.19
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
    */
    @Inject(method = "m_261031_", at = @At("HEAD"), remap = false)
    private void recordWorldLoadStart(CallbackInfo ci) {
        ModernFixClient.worldLoadStartTime = System.nanoTime();
    }
}
