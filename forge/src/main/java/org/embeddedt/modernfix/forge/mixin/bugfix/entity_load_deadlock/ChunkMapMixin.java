package org.embeddedt.modernfix.forge.mixin.bugfix.entity_load_deadlock;

import com.google.common.collect.Lists;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.modernfix.forge.ducks.ILevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;
    @Shadow @Final private ServerLevel level;
    private static final ClassInstanceMultiMap<?>[] NO_ENTITY_SECTIONS = new ClassInstanceMultiMap[0];

    /**
     * Some mods try to do chunkloading inside EntityJoinWorldEvent, which causes issues. To address this we
     * defer the loading of entities from chunks till after we are out of the chunk system.
     * <br>
     * A different patch is necessary for 1.17+, if the issue can be reproduced there, as entity loading
     * works differently.
     * <br>
     * Because of the method arguments being appended to this redirect handler, it will only target the lambda inside
     * protoChunkToFullChunk.
     */
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getEntitySections()[Lnet/minecraft/util/ClassInstanceMultiMap;"))
    private ClassInstanceMultiMap<?>[] getEntitySections(LevelChunk chunk, ChunkHolder holder, ChunkAccess access) {
        ((ILevelChunk)chunk).setEntityLoadHook(() -> {
            List<Entity> list = null;
            ClassInstanceMultiMap<Entity>[] entitySections = chunk.getEntitySections();

            for (ClassInstanceMultiMap<Entity> entitySection : entitySections) {
                if(entitySection == null)
                    continue;
                for (Entity entity : entitySection.getAllInstances()) {
                    if (!(entity instanceof Player) && !this.level.loadFromChunk(entity)) {
                        if (list == null) {
                            list = Lists.newArrayList(entity);
                        } else {
                            list.add(entity);
                        }
                    }
                }
            }

            if (list != null) {
                list.forEach(chunk::removeEntity);
            }
        });
        holder.getOrScheduleFuture(ChunkStatus.FULL, (ChunkMap)(Object)this).thenRun(() -> {
            // Ensure that this code runs on the main thread, in case another worker handled the future
            this.mainThreadExecutor.execute(() -> {
                // hook will be cleared when chunk.setLoaded(false) is called, so entities will not load
                // if the chunk was already unloaded when we get here
                ((ILevelChunk)chunk).runEntityLoadHook();
            });
        });
        return NO_ENTITY_SECTIONS;
    }
}
