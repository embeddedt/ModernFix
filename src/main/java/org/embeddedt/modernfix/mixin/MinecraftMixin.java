package org.embeddedt.modernfix.mixin;

import com.mojang.datafixers.util.Function4;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.duck.ILevelSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public abstract Minecraft.PackManager makeServerStem(DynamicRegistries.Impl dynamicRegistries, Function<SaveFormat.LevelSave, DatapackCodec> worldStorageToDatapackFunction, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> quadFunction, boolean vanillaOnly, SaveFormat.LevelSave worldStorage) throws InterruptedException, ExecutionException;

    private long datapackReloadStartTime;
    private int registryHash;
    @Inject(method = "makeServerStem", at = @At(value = "HEAD"))
    private void recordReloadStart(DynamicRegistries.Impl p_238189_1_, Function<SaveFormat.LevelSave, DatapackCodec> p_238189_2_, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> p_238189_3_, boolean p_238189_4_, SaveFormat.LevelSave p_238189_5_, CallbackInfoReturnable<Minecraft.PackManager> cir) {
        datapackReloadStartTime = System.nanoTime();
    }

    @Inject(method = "makeServerStem", at = @At(value = "RETURN"))
    private void recordReloadEnd(DynamicRegistries.Impl p_238189_1_, Function<SaveFormat.LevelSave, DatapackCodec> p_238189_2_, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> p_238189_3_, boolean p_238189_4_, SaveFormat.LevelSave p_238189_5_, CallbackInfoReturnable<Minecraft.PackManager> cir) {
        float timeSpentReloading = ((float)(System.nanoTime() - datapackReloadStartTime) / 1000000000f);
        ModernFix.LOGGER.warn("Datapack reload took " + timeSpentReloading + " seconds.");
    }

    @Inject(method = "loadWorld(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistries$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/Minecraft$WorldSelectionType;Z)V", at = @At("HEAD"), remap = false)
    private void recordWorldLoadStart(String worldName, DynamicRegistries.Impl dynamicRegistries, Function<SaveFormat.LevelSave, DatapackCodec> levelSaveToDatapackFunction, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> quadFunction, boolean vanillaOnly, Minecraft.WorldSelectionType selectionType, boolean creating, CallbackInfo ci) {
        ModernFixClient.worldLoadStartTime = System.nanoTime();
    }

    @Redirect(method = "loadLevel(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/registry/DynamicRegistries;builtin()Lnet/minecraft/util/registry/DynamicRegistries$Impl;"))
    private DynamicRegistries.Impl useNullRegistry() {
        return null;
    }

    @Redirect(method = "loadWorld(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistries$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/Minecraft$WorldSelectionType;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;makeServerStem(Lnet/minecraft/util/registry/DynamicRegistries$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/world/storage/SaveFormat$LevelSave;)Lnet/minecraft/client/Minecraft$PackManager;", ordinal = 0))
    private Minecraft.PackManager skipFirstReload(Minecraft client, DynamicRegistries.Impl dynamicRegistries, Function<SaveFormat.LevelSave, DatapackCodec> worldStorageToDatapackFunction, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> quadFunction, boolean vanillaOnly, SaveFormat.LevelSave levelSave, String worldName, DynamicRegistries.Impl originalRegistries, Function<SaveFormat.LevelSave, DatapackCodec> levelSaveToDatapackFunction, Function4<SaveFormat.LevelSave, DynamicRegistries.Impl, IResourceManager, DatapackCodec, IServerConfiguration> quadFunction2, boolean vanillaOnly2, Minecraft.WorldSelectionType selectionType, boolean creating) throws InterruptedException, ExecutionException {
        if(!creating) {
            ModernFix.LOGGER.warn("Skipping first reload, this is still experimental");
            ModernFix.runningFirstInjection = true;
            ((ILevelSave)levelSave).runWorldPersistenceHooks();
            ModernFix.runningFirstInjection = false;
            return null;
        } else {
            /* allow reload */
            return makeServerStem(dynamicRegistries, worldStorageToDatapackFunction, quadFunction, vanillaOnly, levelSave);
        }
    }
}
