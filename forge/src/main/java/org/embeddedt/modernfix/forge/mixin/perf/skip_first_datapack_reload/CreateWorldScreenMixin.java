package org.embeddedt.modernfix.forge.mixin.perf.skip_first_datapack_reload;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Component arg) {
        super(arg);
    }
    // TODO: incorporate https://github.com/MinecraftForge/MinecraftForge/pull/9454
    @ModifyArg(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/WorldDataConfiguration;)Lnet/minecraft/server/WorldLoader$InitConfig;"), index = 1)
    private static WorldDataConfiguration useDefaultConfiguration(WorldDataConfiguration config) {
        return WorldDataConfiguration.DEFAULT;
    }

    @Redirect(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;withDataConfiguration(Lnet/minecraft/world/level/WorldDataConfiguration;)Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;"))
    private static WorldCreationContext sameDataConfiguration(WorldCreationContext context, WorldDataConfiguration config) {
        return context;
    }

    @Redirect(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;tryApplyNewDataPacks(Lnet/minecraft/server/packs/repository/PackRepository;ZLjava/util/function/Consumer;)V"))
    private static void skipReapply(CreateWorldScreen screen, PackRepository repository, boolean bl, Consumer<WorldDataConfiguration> consumer) {
        /* no-op */
    }
}
