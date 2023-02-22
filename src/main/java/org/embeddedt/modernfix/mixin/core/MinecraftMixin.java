package org.embeddedt.modernfix.mixin.core;

import com.mojang.datafixers.util.Function4;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void setLatch(String string, RegistryAccess.RegistryHolder arg, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> function, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> function4, boolean bl, Minecraft.ExperimentalDialogType arg2, boolean creating, CallbackInfo ci) {
        ModernFix.worldLoadSemaphore = new CountDownLatch(1);
    }
}
