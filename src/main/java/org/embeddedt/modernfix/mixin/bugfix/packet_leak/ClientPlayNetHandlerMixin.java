package org.embeddedt.modernfix.mixin.bugfix.packet_leak;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.embeddedt.modernfix.duck.IClientNetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetHandlerMixin implements IClientNetHandler {
    private FriendlyByteBuf savedCopy = null;
    /**
     * @author embeddedt
     * @reason Release the packet buffer at the end. Needed in f
     */
    @Redirect(method = "handleCustomPayload", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;getData()Lnet/minecraft/network/FriendlyByteBuf;"))
    private FriendlyByteBuf saveCopyForRelease(ClientboundCustomPayloadPacket instance) {
        FriendlyByteBuf copy = instance.getData();
        savedCopy = copy;
        return copy;
    }

    @Override
    public FriendlyByteBuf getCopiedCustomBuffer() {
        FriendlyByteBuf copy = savedCopy;
        savedCopy = null;
        return copy;
    }
}
