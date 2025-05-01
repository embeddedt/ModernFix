package org.embeddedt.modernfix.forge.packet;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;

import java.util.function.Supplier;

public class PacketHandler {
    public static final SimpleChannel INSTANCE = buildChannel("main", "1");
    public static final SimpleChannel INGREDIENT_SYNC = buildChannel("ingredient_sync", "1");
    public static final ThreadLocal<Boolean> CLIENT_HAS_SMART_INGREDIENT_SYNC = ThreadLocal.withInitial(() -> false);

    private static SimpleChannel buildChannel(String name, String version) {
        return NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ModernFix.MODID, name),
                () -> version,
                NetworkRegistry.acceptMissingOr(version),
                NetworkRegistry.acceptMissingOr(version)
        );
    }

    public static void register() {
        INSTANCE.registerMessage(1, EntityIDSyncPacket.class, EntityIDSyncPacket::serialize, EntityIDSyncPacket::deserialize, PacketHandler::handleSyncPacket);
    }

    private static void handleSyncPacket(EntityIDSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            contextSupplier.get().enqueueWork(() -> ModernFixClient.handleEntityIDSync(packet));
            contextSupplier.get().setPacketHandled(true);
        });
    }
}
