package org.embeddedt.modernfix.mixin.bugfix.packet_leak;

import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import org.embeddedt.modernfix.duck.IClientNetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetHandler.class)
public class ClientPlayNetHandlerMixin implements IClientNetHandler {
    private PacketBuffer savedCopy = null;
    /**
     * @author embeddedt
     * @reason Release the packet buffer at the end. Needed in f
     */
    @Redirect(method = "handleCustomPayload", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/SCustomPayloadPlayPacket;getData()Lnet/minecraft/network/PacketBuffer;"))
    private PacketBuffer saveCopyForRelease(SCustomPayloadPlayPacket instance) {
        PacketBuffer copy = instance.getData();
        savedCopy = copy;
        return copy;
    }

    @Override
    public PacketBuffer getCopiedCustomBuffer() {
        PacketBuffer copy = savedCopy;
        savedCopy = null;
        return copy;
    }
}
