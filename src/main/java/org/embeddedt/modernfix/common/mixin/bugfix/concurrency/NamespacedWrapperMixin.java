package org.embeddedt.modernfix.common.mixin.bugfix.concurrency;

import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(targets = {"net/minecraftforge/registries/NamespacedWrapper"}, priority = 500)
public abstract class NamespacedWrapperMixin<T> {
    @Shadow(aliases = {"tags"}) private volatile Map<TagKey<T>, HolderSet.Named<T>> tags;

    @Shadow(aliases = {"createTag"}) protected abstract HolderSet.Named<T> m_211067_(TagKey<T> key);

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
                    named = this.m_211067_(key);
                    Map<TagKey<T>, HolderSet.Named<T>> map = new IdentityHashMap<>(this.tags);
                    map.put(key, named);
                    this.tags = map;
                }
            }
        }
        return named;
    }
}
