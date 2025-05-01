package org.embeddedt.modernfix.neoforge.mixin.perf.smart_ingredient_sync;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.embeddedt.modernfix.neoforge.packet.SmartIngredientSyncPayload;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
public class ConnectionMixin {
    /**
     * @author embeddedt
     * @reason Provide context to the ingredient serializer about whether the enhanced sync protocol is supported.
     */
    @WrapMethod(method = "doSendPacket")
    private void modernfix$checkClientPresence(Packet<?> packet, PacketSendListener sendListener, boolean flush, Operation<Void> original) {
        if (packet instanceof ClientboundUpdateRecipesPacket && NetworkRegistry.hasChannel((Connection)(Object)this, ConnectionProtocol.PLAY, SmartIngredientSyncPayload.TYPE.id())) {
            SmartIngredientSyncPayload.CLIENT_HAS_SMART_INGREDIENT_SYNC.set(true);
            try {
                original.call(packet, sendListener, flush);
            } finally {
                SmartIngredientSyncPayload.CLIENT_HAS_SMART_INGREDIENT_SYNC.set(false);
            }
        } else {
            original.call(packet, sendListener, flush);
        }
    }
}
