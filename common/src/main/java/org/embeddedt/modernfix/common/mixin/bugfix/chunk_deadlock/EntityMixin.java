package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
    /**
     * @author embeddedt
     * @reason When an entity is added to the world via the worldgen load path (ChunkMap#postLoadProtoChunk calling
     * ServerLevel#addWorldGenChunkEntities), attempts to add a passenger result in a deadlock when the sculk event
     * tries to raytrace blocks. To fix this, we skip firing the sculk event if the chunk the entity is within is not
     * loaded.
     */
    @WrapWithCondition(method = "addPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/entity/Entity;)V"))
    private boolean onlyAddIfSelfChunkLoaded(Entity instance, GameEvent event, Entity entity) {
        var chunkPos = instance.chunkPosition();
        if (instance.level() instanceof ServerLevel serverLevel && serverLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null) {
            ModernFix.LOGGER.warn("Skipped emitting ENTITY_MOUNT game event for entity {} as it would cause deadlock", instance.toString());
            return false;
        } else {
            return true;
        }
    }
}
