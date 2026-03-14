package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
@ClientOnlyMixin
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void sendConfigFinishedSentinelPacket(Connection connection, ServerPlayer player, CallbackInfo ci) {
        if (connection.isMemoryConnection()) {
            FriendlyByteBuf friendlybytebuf = new FriendlyByteBuf(Unpooled.buffer());
            player.connection.send(new ClientboundCustomPayloadPacket(IDeferrableIntegratedServer.CLIENT_LOAD_SENTINEL, friendlybytebuf));
        }
    }
}
