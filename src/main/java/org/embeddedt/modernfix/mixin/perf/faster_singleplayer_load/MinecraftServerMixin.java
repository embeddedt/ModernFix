package org.embeddedt.modernfix.mixin.perf.faster_singleplayer_load;

import net.minecraft.Util;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.embeddedt.modernfix.ModernFixClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow protected long nextTickTime;

    @Shadow public abstract ServerLevel overworld();

    @Shadow protected abstract void updateMobSpawningFlags();

    /**
     * @author embeddedt
     * @reason defer the 441 chunk load until *after* join game packets are sent to the client, in order to allow
     * mods that process advancements, etc. to work on that at the same time
     */
    @Inject(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;", ordinal = 0), cancellable = true)
    private void skipInitialChunkLoad(ChunkProgressListener arg, CallbackInfo ci) {
        if(((Object)this) instanceof IntegratedServer) {
            ci.cancel();
            ModernFixClient.integratedWorldLoadListener = arg;
            this.nextTickTime = Util.getMillis();
            this.overworld().getChunkSource().getLightEngine().setTaskPerBatch(5);
            this.updateMobSpawningFlags();
        }
    }
}
