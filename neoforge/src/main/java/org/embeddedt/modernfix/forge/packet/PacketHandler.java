package org.embeddedt.modernfix.forge.packet;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.simple.SimpleChannel;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModernFix.MODID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    public static void register() {
        int id = 1;
        INSTANCE.registerMessage(id++, EntityIDSyncPacket.class, EntityIDSyncPacket::serialize, EntityIDSyncPacket::deserialize, PacketHandler::handleSyncPacket);
    }

    private static void handleSyncPacket(EntityIDSyncPacket packet, NetworkEvent.Context contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            contextSupplier.enqueueWork(() -> ModernFixClient.handleEntityIDSync(packet));
            contextSupplier.setPacketHandled(true);
        });
    }
}
