package org.embeddedt.modernfix.common.mixin.perf.thread_priorities;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.ServerResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
@ClientOnlyMixin
public class IntegratedServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void adjustServerPriority(Thread pServerThread, Minecraft pMinecraft, RegistryAccess.RegistryHolder pRegistryHolder, LevelStorageSource.LevelStorageAccess pStorageSource, PackRepository pPackRepository, ServerResources pResources, WorldData pWorldData, MinecraftSessionService pSessionService, GameProfileRepository pProfileRepository, GameProfileCache pProfileCache, ChunkProgressListenerFactory pProgressListenerfactory, CallbackInfo ci) {
        int pri = 4; //ModernFixConfig.INTEGRATED_SERVER_PRIORITY.get();
        pServerThread.setPriority(pri);
    }
}
