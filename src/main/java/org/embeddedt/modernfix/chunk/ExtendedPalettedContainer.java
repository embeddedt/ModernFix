package org.embeddedt.modernfix.chunk;

import net.minecraft.world.level.chunk.Palette;

public interface ExtendedPalettedContainer<T> {
    Palette<T> mfix$getPalette();
}
