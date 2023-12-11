package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EyeOfEnder.class)
@ClientOnlyMixin
public interface EyeOfEnderAccess {
    @Accessor
    void setLife(int life);
}
