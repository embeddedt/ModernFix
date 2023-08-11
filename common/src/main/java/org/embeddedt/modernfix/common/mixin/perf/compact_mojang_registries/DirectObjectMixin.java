package org.embeddedt.modernfix.common.mixin.perf.compact_mojang_registries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.embeddedt.modernfix.registry.DirectStorageRegistryObject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ Block.class, Item.class })
public class DirectObjectMixin implements DirectStorageRegistryObject {
    private ResourceLocation mfix$resourceKey;

    @Override
    public ResourceLocation mfix$getResourceKey() {
        return mfix$resourceKey;
    }

    @Override
    public void mfix$setResourceKey(ResourceLocation key) {
        mfix$resourceKey = key;
    }
}
