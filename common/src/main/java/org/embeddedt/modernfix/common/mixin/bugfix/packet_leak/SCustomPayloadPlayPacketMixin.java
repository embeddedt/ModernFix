package org.embeddedt.modernfix.common.mixin.bugfix.packet_leak;

import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundCustomPayloadPacket.class)
@ClientOnlyMixin
public class SCustomPayloadPlayPacketMixin {
    @Shadow private FriendlyByteBuf data;

    private boolean needsRelease;

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void markNotOwned(ResourceLocation pIdentifier, FriendlyByteBuf pData, CallbackInfo ci) {
        this.needsRelease = false;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void markOwned(FriendlyByteBuf p_148837_1_, CallbackInfo ci) {
        this.needsRelease = true;
    }

    @Redirect(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientGamePacketListener;handleCustomPayload(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V"))
    private void handleAndFree(ClientGamePacketListener instance, ClientboundCustomPayloadPacket sCustomPayloadPlayPacket) {
        /* in 1.16, this method creates a copy inside it, but handles freeing correctly */
        instance.handleCustomPayload(sCustomPayloadPlayPacket);
        if(this.needsRelease)
            this.data.release(); /* free our own copy of the data if needed */
    }
}
