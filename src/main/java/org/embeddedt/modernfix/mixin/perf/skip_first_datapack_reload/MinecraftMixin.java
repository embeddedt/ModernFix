package org.embeddedt.modernfix.mixin.perf.skip_first_datapack_reload;

import com.mojang.datafixers.util.Function4;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.ILevelSave;
import org.spongepowered.asm.mixin.Final;
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
@ClientOnlyMixin
public abstract class MinecraftMixin {
    @Shadow public abstract Minecraft.ServerStem makeServerStem(RegistryAccess.RegistryHolder dynamicRegistries, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> worldStorageToDatapackFunction, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> quadFunction, boolean vanillaOnly, LevelStorageSource.LevelStorageAccess worldStorage) throws InterruptedException, ExecutionException;

    @Shadow @Final private LevelStorageSource levelSource;

    @Redirect(method = "loadLevel(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/RegistryAccess;builtin()Lnet/minecraft/core/RegistryAccess$RegistryHolder;"))
    private RegistryAccess.RegistryHolder useNullRegistry() {
        return null;
    }

    @Redirect(method = "loadWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;makeServerStem(Lnet/minecraft/core/RegistryAccess$RegistryHolder;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;)Lnet/minecraft/client/Minecraft$ServerStem;", ordinal = 0))
    private Minecraft.ServerStem skipFirstReload(Minecraft client, RegistryAccess.RegistryHolder dynamicRegistries, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> worldStorageToDatapackFunction, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> quadFunction, boolean vanillaOnly, LevelStorageSource.LevelStorageAccess levelSave, String worldName, RegistryAccess.RegistryHolder originalRegistries, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> levelSaveToDatapackFunction, Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> quadFunction2, boolean vanillaOnly2, Minecraft.ExperimentalDialogType selectionType, boolean creating) throws InterruptedException, ExecutionException {
        if(!creating) {
            ModernFix.LOGGER.debug("Skipping first datapack reload");
            ModernFix.runningFirstInjection = true;
            ((ILevelSave)levelSave).runWorldPersistenceHooks(levelSource);
            ModernFix.runningFirstInjection = false;
            return null;
        } else {
            /* allow reload */
            return makeServerStem(dynamicRegistries, worldStorageToDatapackFunction, quadFunction, vanillaOnly, levelSave);
        }
    }
}
