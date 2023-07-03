package org.embeddedt.modernfix.forge.mixin.bugfix.entity_load_deadlock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.modernfix.forge.ducks.ILevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    private static final ClassInstanceMultiMap<?>[] NO_ENTITY_SECTIONS = new ClassInstanceMultiMap[0];

    /**
     * Need to ensure entities aren't removed from the level when they were never added.
     */
    @Redirect(method = "unload", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getEntitySections()[Lnet/minecraft/util/ClassInstanceMultiMap;"))
    private ClassInstanceMultiMap<?>[] skipUnloadIfNeverLoaded(LevelChunk chunk) {
        if(!((ILevelChunk)chunk).getEntitiesWereLoaded()) {
            return NO_ENTITY_SECTIONS;
        }
        return chunk.getEntitySections();
    }
}
