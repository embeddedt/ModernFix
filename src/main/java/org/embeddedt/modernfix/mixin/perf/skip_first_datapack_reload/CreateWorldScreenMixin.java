package org.embeddedt.modernfix.mixin.perf.skip_first_datapack_reload;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
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
}
