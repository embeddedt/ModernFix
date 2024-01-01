package org.embeddedt.modernfix.neoforge.packet;

import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;

public class PacketHandler {
    private static void registerPackets(final RegisterPayloadHandlerEvent event) {
        final IPayloadRegistrar registrar = event.registrar(ModernFix.MODID).optional();
        registrar.play(EntityIDSyncPacket.ID, EntityIDSyncPacket::new, PacketHandler::handleSyncPacket);
    }

    public static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PacketHandler::registerPackets);
    }

    private static void handleSyncPacket(EntityIDSyncPacket packet, PlayPayloadContext context) {
        context.workHandler().execute(() -> ModernFixClient.handleEntityIDSync(packet));
    }
}
