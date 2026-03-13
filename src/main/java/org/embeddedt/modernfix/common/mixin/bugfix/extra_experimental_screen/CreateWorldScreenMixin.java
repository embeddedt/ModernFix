package org.embeddedt.modernfix.common.mixin.bugfix.extra_experimental_screen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CreateWorldScreen.class)
@ClientOnlyMixin
public class CreateWorldScreenMixin {
    /**
     * Fix experimental world dialog still being shown the first time you reopen a world that was created
     * as experimental.
     */
    @ModifyArg(method = "createNewWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)V"), index = 3)
    private LevelDataAndDimensions.WorldDataAndGenSettings setExperimentalFlag(LevelDataAndDimensions.WorldDataAndGenSettings settings) {
        if(settings.data() instanceof PrimaryLevelData pld && settings.data().worldGenSettingsLifecycle() != Lifecycle.stable()) {
            pld.withConfirmedWarning(true);
        }
        return settings;
    }
}
