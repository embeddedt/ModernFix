package org.embeddedt.modernfix.mixin.devenv;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/* Disable waiting for spawn chunk load */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"))
    private int noSpawnChunkWait(ServerChunkCache cache) {
        return 441;
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V"))
    private void noWaitTick(MinecraftServer server) {

    }
}
