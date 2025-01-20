package org.embeddedt.modernfix.common.mixin.feature.blockentity_incorrect_thread;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelThreadAccessor {
    @Accessor
    Thread getThread();
}
