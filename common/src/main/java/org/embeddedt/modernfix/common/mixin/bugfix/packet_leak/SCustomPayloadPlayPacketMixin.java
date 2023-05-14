package org.embeddedt.modernfix.common.mixin.bugfix.packet_leak;

import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IClientNetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundCustomPayloadPacket.class)
@ClientOnlyMixin
public abstract class SCustomPayloadPlayPacketMixin implements IClientNetHandler {
    @Shadow private FriendlyByteBuf data;

    @Shadow public abstract FriendlyByteBuf getData();

    private FriendlyByteBuf usedByteBuf = null;

    private boolean needsRelease;

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void markNotOwned(ResourceLocation pIdentifier, FriendlyByteBuf pData, CallbackInfo ci) {
        this.needsRelease = false;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void markOwned(FriendlyByteBuf p_148837_1_, CallbackInfo ci) {
        this.needsRelease = true;
    }

    @Override
    public FriendlyByteBuf getCopiedCustomBuffer() {
        FriendlyByteBuf buf = this.getData();
        usedByteBuf = buf;
        return buf;
    }

    @Redirect(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientGamePacketListener;handleCustomPayload(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V"))
    private void handleAndFree(ClientGamePacketListener instance, ClientboundCustomPayloadPacket sCustomPayloadPlayPacket) {
        usedByteBuf = null;
        try {
            instance.handleCustomPayload(sCustomPayloadPlayPacket);
        } finally {
            FriendlyByteBuf buf = usedByteBuf;
            if(buf != null && buf.refCnt() > 0) {
                buf.release();
            }
        }
        if(this.needsRelease && this.data.refCnt() > 0)
            this.data.release();
    }
}
