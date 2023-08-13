package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import net.minecraft.server.level.ServerLevel;
import org.embeddedt.modernfix.chunk.SafeBlockGetter;
import org.embeddedt.modernfix.duck.ISafeBlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements ISafeBlockGetter {
    @Unique
    private final SafeBlockGetter mfix$safeBlockGetter = new SafeBlockGetter((ServerLevel)(Object)this);

    @Override
    public SafeBlockGetter mfix$getSafeBlockGetter() {
        return mfix$safeBlockGetter;
    }
}
