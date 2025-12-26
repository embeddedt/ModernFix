package org.embeddedt.modernfix.common.mixin.perf.smart_ingredient_sync;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    /**
     * @author embeddedt
     * @reason Ensure the tag packet is always sent before the recipe packet (like it is after /reload).
     */
    @Redirect(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
    slice = @Slice(
            from = @At(value = "NEW", target = "(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraftforge/event/OnDatapackSyncEvent;"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;)V")
    ), expect = 2, allow = 2, require = 2)
    private void modernfix$switchTagAndRecipeOrder(ServerGamePacketListenerImpl instance, Packet<?> packet, @Local(ordinal = 0, argsOnly = true) Connection netManager, @Local(ordinal = 0, argsOnly = true) ServerPlayer player, @Share("deferred") LocalRef<Packet<?>> pktRef) {
        if (!(packet instanceof ClientboundUpdateRecipesPacket) && !(packet instanceof ClientboundUpdateTagsPacket)) {
            throw new AssertionError("Mixin injected in wrong place");
        }
        if (!PacketHandler.INGREDIENT_SYNC.isRemotePresent(netManager)) {
            instance.send(packet);
            return;
        }

        if (packet instanceof ClientboundUpdateRecipesPacket) {
            pktRef.set(packet);
        } else {
            ModernFix.LOGGER.info("Using enhanced recipe sync for player {}", player.getName().getString());
            // send tags
            instance.send(packet);
            // send recipes
            instance.send(pktRef.get());
        }
    }
}
