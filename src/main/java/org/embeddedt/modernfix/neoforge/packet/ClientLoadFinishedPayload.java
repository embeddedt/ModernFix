package org.embeddedt.modernfix.neoforge.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.embeddedt.modernfix.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;

public enum ClientLoadFinishedPayload implements CustomPacketPayload {
    INSTANCE;

    public static final CustomPacketPayload.Type<ClientLoadFinishedPayload> TYPE = new CustomPacketPayload.Type<>(IDeferrableIntegratedServer.CLIENT_LOAD_SENTINEL);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientLoadFinishedPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
