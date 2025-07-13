package org.embeddedt.modernfix.common.mixin.bugfix.concurrency;

import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(value = MappedRegistry.class, priority = 500)
public abstract class MappedRegistryMixin<T> {
    @Shadow private volatile Map<TagKey<T>, HolderSet.Named<T>> tags;

    @Shadow protected abstract HolderSet.Named<T> createTag(TagKey<T> key);

    /**
     * @author embeddedt (issue found by Uncandango)
     * @reason vanilla does not use the correct double-checked locking paradigm, which leads to race conditions
     */
    @Overwrite
    public HolderSet.Named<T> getOrCreateTag(TagKey<T> key) {
        HolderSet.Named<T> named = this.tags.get(key);
        if (named == null) {
            // synchronize and check again - this is the bugfix
            synchronized (this) {
                named = this.tags.get(key);
                if (named == null) {
                    named = this.createTag(key);
                    Map<TagKey<T>, HolderSet.Named<T>> map = new IdentityHashMap<>(this.tags);
                    map.put(key, named);
                    this.tags = map;
                }
            }
        }
        return named;
    }
}
