package org.embeddedt.modernfix.duck;

import net.minecraft.client.resources.model.ModelResourceLocation;

public interface IBlockStateModelLoader {
    void loadSpecificBlock(ModelResourceLocation location);
}
