package org.embeddedt.modernfix.mixin.perf.reuse_datapacks;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.reuse_datapacks.ICachingResourceClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements ICachingResourceClient {
    @Shadow public abstract boolean isLocalServer();

    private ServerResources cachedResources;
    private List<String> cachedDataPackConfig;

    private List<String> loadingDataPackConfig;

    @Redirect(method = "makeServerStem", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;configurePackRepository(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/DataPackConfig;Z)Lnet/minecraft/world/level/DataPackConfig;"))
    private DataPackConfig saveLoadingConfig(PackRepository repo, DataPackConfig inCodec, boolean vanillaOnly) {
        DataPackConfig config = MinecraftServer.configurePackRepository(repo, inCodec, vanillaOnly);
        loadingDataPackConfig = repo.getSelectedPacks().stream().map(Pack::getId).collect(ImmutableList.toImmutableList());
        return config;
    }

    @Redirect(method = "makeServerStem", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerResources;loadResources(Ljava/util/List;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ServerResources> useCachedResources(List<PackResources> list, Commands.CommandSelection arg, int i, Executor executor, Executor executor2) {
        if(cachedResources != null) {
            if(cachedResources.getResourceManager().getNamespaces().size() > 0) {
                if (cachedDataPackConfig.equals(loadingDataPackConfig)) {
                    ModernFix.LOGGER.warn("Reusing loaded server resources from previous world");
                    return CompletableFuture.completedFuture(cachedResources);
                } else {
                    ModernFix.LOGGER.warn("Discarding cached server resources, datapack configs have changed");
                    ModernFix.LOGGER.warn("Old: {}", "[" + String.join(", ", cachedDataPackConfig) + "]");
                    ModernFix.LOGGER.warn("New: {}", "[" + String.join(", ", loadingDataPackConfig) + "]");
                    cachedResources.close();
                    cachedResources = null;
                    cachedDataPackConfig = null;
                }
            } else {
                ModernFix.LOGGER.error("Cached server resources were closed somehow, that shouldn't happen");
                cachedResources = null;
            }
        }
        return ServerResources.loadResources(list, arg, i, executor, executor2);
    }


    @Override
    public void setCachedResources(ServerResources r) {
        cachedResources = r;
    }

    @Override
    public void setCachedDataPackConfig(Collection<String> c) {
        cachedDataPackConfig = ImmutableList.copyOf(c);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void clearResourcesIfNotLocal(CallbackInfo ci) {
        if(!this.isLocalServer())
            cachedResources = null;
    }
}
