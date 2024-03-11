package org.embeddedt.modernfix.forge.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface DelegateHolder<T> {
    Holder.Reference<T> mfix$getDelegate(ResourceKey<Registry<T>> registryKey);
    void mfix$setDelegate(ResourceKey<Registry<T>> registryKey, Holder.Reference<T> holder);
}
