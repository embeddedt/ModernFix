package org.embeddedt.modernfix.forge.mixin.perf.forge_registry_alloc;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.embeddedt.modernfix.forge.registry.DelegateHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({Block.class, Item.class})
public class DelegateHolderMixin<T> implements DelegateHolder<T> {
    private Holder.Reference<T> mfix$delegate;
    private ResourceKey<Registry<T>> mfix$key;

    @Override
    public Holder.Reference<T> mfix$getDelegate(ResourceKey<Registry<T>> registryKey) {
        if(mfix$key == registryKey) {
            return mfix$delegate;
        } else {
            return null;
        }
    }

    @Override
    public void mfix$setDelegate(ResourceKey<Registry<T>> registryKey, Holder.Reference<T> holder) {
        this.mfix$delegate = holder;
        this.mfix$key = registryKey;
    }
}
