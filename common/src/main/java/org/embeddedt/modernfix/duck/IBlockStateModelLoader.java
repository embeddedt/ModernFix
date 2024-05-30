package org.embeddedt.modernfix.duck;

import net.minecraft.resources.ResourceLocation;

public interface IBlockStateModelLoader {
    void loadSpecificBlock(ResourceLocation location);
}
