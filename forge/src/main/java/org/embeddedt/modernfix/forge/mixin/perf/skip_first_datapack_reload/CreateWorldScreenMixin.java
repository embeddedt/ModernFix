package org.embeddedt.modernfix.forge.mixin.perf.skip_first_datapack_reload;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldGenSettingsComponent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow protected static WorldLoader.InitConfig createDefaultLoadConfig(PackRepository arg, DataPackConfig arg2) {
        throw new AssertionError();
    }

    @Shadow protected DataPackConfig dataPacks;

    @Shadow @Final public WorldGenSettingsComponent worldGenSettingsComponent;

    @Shadow protected abstract void openDataPackSelectionScreen();

    protected CreateWorldScreenMixin(Component arg) {
        super(arg);
    }

    @Redirect(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/WorldLoader;load(Lnet/minecraft/server/WorldLoader$InitConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;Lnet/minecraft/server/WorldLoader$ResultFactory;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<WorldCreationContext> doLoad(WorldLoader.InitConfig config, WorldLoader.WorldDataSupplier<WorldGenSettings> supplier, WorldLoader.ResultFactory<WorldGenSettings, WorldCreationContext> factory, Executor backgroundExecutor, Executor mainExecutor) {
        ModernFix.LOGGER.warn("Skipping first datapack reload");
        // Make sure to configure the pack repository as though the load happened
        MinecraftServer.configurePackRepository(config.packConfig().packRepository(), config.packConfig().initialDataPacks(), config.packConfig().safeMode());
        Pair<WorldGenSettings, RegistryAccess.Frozen> creationPair = supplier.get(null, null);
        WorldGenSettings settings = creationPair.getFirst();
        // Don't load any datapack resources, since Forge is about to do it themselves
        WorldCreationContext context = new WorldCreationContext(settings, Lifecycle.stable(), creationPair.getSecond(), null);
        return CompletableFuture.completedFuture(context);
    }

    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ConfirmScreen;<init>(Lit/unimi/dsi/fastutil/booleans/BooleanConsumer;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V"), index = 0)
    private BooleanConsumer replaceConsumer(BooleanConsumer consumer) {
        return bl -> {
            if(bl) {
                // just open the selection screen again
                this.openDataPackSelectionScreen();
            } else {
                this.dataPacks = new DataPackConfig(ImmutableList.of("vanilla"), ImmutableList.of());
                this.mfix$loadVanillaResources();
                this.minecraft.setScreen(this);
            }
        };
    }

    private void mfix$loadVanillaResources() {
        ModernFix.LOGGER.warn("Loading vanilla resources");
        // We now need to perform the reload that was skipped above
        PackRepository packrepository = new PackRepository(PackType.SERVER_DATA, new ServerPacksSource());
        WorldLoader.InitConfig vanillaConfig = createDefaultLoadConfig(packrepository, new DataPackConfig(ImmutableList.of("vanilla"), ImmutableList.of()));
        CompletableFuture<WorldCreationContext> completablefuture = WorldLoader.load(vanillaConfig, (argx, arg2x) -> {
            RegistryAccess.Frozen registryaccess$frozen = RegistryAccess.builtinCopy().freeze();
            WorldGenSettings worldgensettings = WorldPresets.createNormalWorldFromPreset(registryaccess$frozen);
            return Pair.of(worldgensettings, registryaccess$frozen);
        }, (argx, arg2x, arg3, arg4) -> {
            argx.close();
            return new WorldCreationContext(arg4, Lifecycle.stable(), arg3, arg2x);
        }, ModernFix.resourceReloadExecutor(), this.minecraft);
        this.minecraft.managedBlock(completablefuture::isDone);
        this.worldGenSettingsComponent.updateSettings(completablefuture.join());
        this.rebuildWidgets();
    }
}
