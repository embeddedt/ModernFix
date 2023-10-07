package org.embeddedt.modernfix.forge.mixin.bugfix.forge_vehicle_packets;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V"))
    private void movePlayerUsingPositionRider(ServerPlayer player, double x, double y, double z, float yRot, float xRot, ServerboundMoveVehiclePacket packet) {
        if(player == this.player) {
            // use positionRider
            Vec3 oldPos = this.player.position();
            this.player.getRootVehicle().positionRider(this.player);
            this.player.xo = oldPos.x;
            this.player.yo = oldPos.y;
            this.player.zo = oldPos.z;
        } else
            player.absMoveTo(x, y, z, yRot, xRot);
    }
}
