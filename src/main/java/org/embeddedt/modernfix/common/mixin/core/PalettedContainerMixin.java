package org.embeddedt.modernfix.common.mixin.core;

import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.embeddedt.modernfix.chunk.ExtendedPalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public class PalettedContainerMixin<T> implements ExtendedPalettedContainer<T> {
    @Shadow
    private volatile PalettedContainer.Data<T> data;

    @Override
    public Palette<T> mfix$getPalette() {
        return this.data.palette();
    }
}
