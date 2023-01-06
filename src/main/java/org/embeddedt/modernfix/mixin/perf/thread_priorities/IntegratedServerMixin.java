package org.embeddedt.modernfix.mixin.perf.thread_priorities;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.DataPackRegistries;
import net.minecraft.resources.ResourcePackList;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.chunk.listener.IChunkStatusListenerFactory;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.SaveFormat;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void adjustServerPriority(Thread pServerThread, Minecraft pMinecraft, DynamicRegistries.Impl pRegistryHolder, SaveFormat.LevelSave pStorageSource, ResourcePackList pPackRepository, DataPackRegistries pResources, IServerConfiguration pWorldData, MinecraftSessionService pSessionService, GameProfileRepository pProfileRepository, PlayerProfileCache pProfileCache, IChunkStatusListenerFactory pProgressListenerfactory, CallbackInfo ci) {
        int pri = ModernFixConfig.INTEGRATED_SERVER_PRIORITY.get();
        ModernFix.LOGGER.info("Changing server thread priority to " + pri);
        pServerThread.setPriority(pri);
    }
}
