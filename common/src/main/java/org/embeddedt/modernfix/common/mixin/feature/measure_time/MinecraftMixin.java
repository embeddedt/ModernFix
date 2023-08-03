package org.embeddedt.modernfix.common.mixin.feature.measure_time;

import com.mojang.datafixers.util.Function4;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
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

import java.util.function.Function;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    @Shadow @Nullable public Overlay overlay;
    private long datapackReloadStartTime;

    @Inject(method = "makeServerStem", at = @At(value = "HEAD"))
    private void recordReloadStart(RegistryAccess.RegistryHolder p_238189_1_, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> p_238189_2_, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> p_238189_3_, boolean p_238189_4_, LevelStorageSource.LevelStorageAccess p_238189_5_, CallbackInfoReturnable<Minecraft.ServerStem> cir) {
        datapackReloadStartTime = System.nanoTime();
    }

    @Inject(method = "makeServerStem", at = @At(value = "RETURN"))
    private void recordReloadEnd(RegistryAccess.RegistryHolder p_238189_1_, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> p_238189_2_, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> p_238189_3_, boolean p_238189_4_, LevelStorageSource.LevelStorageAccess p_238189_5_, CallbackInfoReturnable<Minecraft.ServerStem> cir) {
        float timeSpentReloading = ((float)(System.nanoTime() - datapackReloadStartTime) / 1000000000f);
        ModernFix.LOGGER.warn("Datapack reload took " + timeSpentReloading + " seconds.");
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        if(this.overlay == null && ModernFixClient.INSTANCE != null) {
            ModernFixClient.INSTANCE.onGameLaunchFinish();
        }
    }
}
