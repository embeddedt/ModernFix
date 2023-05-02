package org.embeddedt.modernfix.common.mixin.perf.thread_priorities;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
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
    private void adjustServerPriority(Thread thread, Minecraft arg, LevelStorageSource.LevelStorageAccess arg2, PackRepository arg3, WorldStem arg4, MinecraftSessionService minecraftSessionService, GameProfileRepository gameProfileRepository, GameProfileCache arg5, ChunkProgressListenerFactory arg6, CallbackInfo ci) {
        int pri = 4;
        thread.setPriority(pri);
    }
}
