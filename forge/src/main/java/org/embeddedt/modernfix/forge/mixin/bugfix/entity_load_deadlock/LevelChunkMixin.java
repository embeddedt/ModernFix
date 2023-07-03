package org.embeddedt.modernfix.forge.mixin.bugfix.entity_load_deadlock;

import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.modernfix.forge.ducks.ILevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class LevelChunkMixin implements ILevelChunk {
    private Runnable entityLoadHook;
    private boolean entitiesWereLoaded = false;

    @Override
    public void setEntityLoadHook(@Nullable Runnable loadHook) {
        entityLoadHook = loadHook;
    }

    @Inject(method = "setLoaded", at = @At("RETURN"))
    private void clearLoadHook(boolean bl, CallbackInfo ci) {
        if(!bl)
            entityLoadHook = null;
    }

    @Override
    public void runEntityLoadHook() {
        if(entityLoadHook != null) {
            entityLoadHook.run();
            entitiesWereLoaded = true;
            entityLoadHook = null;
        }
    }

    @Override
    public boolean getEntitiesWereLoaded() {
        return entitiesWereLoaded;
    }
}
