package org.embeddedt.modernfix.forge.packet;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;

public class PacketHandler {
    private static final int PROTOCOL_VERSION = 1;
    public static final SimpleChannel INSTANCE = ChannelBuilder
            .named(new ResourceLocation(ModernFix.MODID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .serverAcceptedVersions(Channel.VersionTest.ACCEPT_MISSING.or(Channel.VersionTest.exact(PROTOCOL_VERSION)))
            .clientAcceptedVersions(Channel.VersionTest.ACCEPT_MISSING.or(Channel.VersionTest.exact(PROTOCOL_VERSION)))
            .simpleChannel();

    public static void register() {
        INSTANCE.messageBuilder(EntityIDSyncPacket.class).encoder(EntityIDSyncPacket::serialize).decoder(EntityIDSyncPacket::deserialize).consumerNetworkThread((msg, ctx) -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ctx.enqueueWork(() -> ModernFixClient.handleEntityIDSync(msg));
                ctx.setPacketHandled(true);
            });
        }).add();
    }
}
