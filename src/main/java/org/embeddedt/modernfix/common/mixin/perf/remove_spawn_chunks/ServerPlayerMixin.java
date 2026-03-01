package org.embeddedt.modernfix.common.mixin.perf.remove_spawn_chunks;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    /**
     * @author embeddedt
     * @reason do not waste time loading the wrong chunks and placing the player there just to correct it later
     */
    @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;fudgeSpawnLocation(Lnet/minecraft/server/level/ServerLevel;)V"))
    private boolean skipFudgingForSPOwner(ServerPlayer player, ServerLevel targetLevel) {
        return targetLevel.getServer().getWorldData().getLoadedPlayerTag() == null
                || !targetLevel.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
