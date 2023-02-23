package org.embeddedt.modernfix.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "doLoadLevel", at = @At("HEAD"), remap = false)
    private void setLatch(String string, Function<LevelStorageSource.LevelStorageAccess, WorldStem.DataPackConfigSupplier> function, Function<LevelStorageSource.LevelStorageAccess, WorldStem.WorldDataSupplier> function2, boolean bl, Minecraft.ExperimentalDialogType arg, boolean creating, CallbackInfo ci) {
        ModernFix.worldLoadSemaphore = new CountDownLatch(1);
    }
}
