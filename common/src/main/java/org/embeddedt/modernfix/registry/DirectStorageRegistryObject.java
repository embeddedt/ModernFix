package org.embeddedt.modernfix.registry;

import net.minecraft.resources.ResourceLocation;

public interface DirectStorageRegistryObject {
    ResourceLocation mfix$getResourceKey();
    void mfix$setResourceKey(ResourceLocation key);
}
