package org.embeddedt.modernfix.mixin.bugfix.packet_leak;

import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import net.minecraft.util.ResourceLocation;
import org.embeddedt.modernfix.duck.IClientNetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SCustomPayloadPlayPacket.class)
public class SCustomPayloadPlayPacketMixin {
    @Shadow private PacketBuffer data;

    private boolean needsRelease;

    @Inject(method = "<init>(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/network/PacketBuffer;)V", at = @At("RETURN"))
    private void markNotOwned(ResourceLocation pIdentifier, PacketBuffer pData, CallbackInfo ci) {
        this.needsRelease = false;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void markOwned(PacketBuffer p_148837_1_, CallbackInfo ci) {
        this.needsRelease = true;
    }

    @Redirect(method = "handle(Lnet/minecraft/client/network/play/IClientPlayNetHandler;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/play/IClientPlayNetHandler;handleCustomPayload(Lnet/minecraft/network/play/server/SCustomPayloadPlayPacket;)V"))
    private void handleAndFree(IClientPlayNetHandler instance, SCustomPayloadPlayPacket sCustomPayloadPlayPacket) {
        try {
            instance.handleCustomPayload(sCustomPayloadPlayPacket);
        } finally {
            PacketBuffer copied = ((IClientNetHandler)instance).getCopiedCustomBuffer();
            if(copied != null)
                copied.release();
        }
        if(this.needsRelease)
            this.data.release();
    }
}
