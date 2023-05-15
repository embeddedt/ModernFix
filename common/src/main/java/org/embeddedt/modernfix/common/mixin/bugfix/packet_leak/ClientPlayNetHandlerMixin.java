package org.embeddedt.modernfix.common.mixin.bugfix.packet_leak;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IClientNetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
@ClientOnlyMixin
public class ClientPlayNetHandlerMixin {
    /**
     * @author embeddedt
     * @reason allow the other function to track use of the buffer
     */
    @Redirect(method = "handleCustomPayload", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;getData()Lnet/minecraft/network/FriendlyByteBuf;"))
    private FriendlyByteBuf saveCopyForRelease(ClientboundCustomPayloadPacket instance) {
        return ((IClientNetHandler)instance).getCopiedCustomBuffer();
    }
}
