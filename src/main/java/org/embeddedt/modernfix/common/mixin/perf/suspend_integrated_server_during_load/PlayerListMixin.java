package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.neoforge.packet.ClientLoadFinishedPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
@ClientOnlyMixin
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void sendConfigFinishedSentinelPacket(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (connection.isMemoryConnection()) {
            player.connection.send(ClientLoadFinishedPayload.INSTANCE);
        }
    }
}
