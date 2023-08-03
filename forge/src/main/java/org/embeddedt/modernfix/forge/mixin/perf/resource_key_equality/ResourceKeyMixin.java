package org.embeddedt.modernfix.forge.mixin.perf.resource_key_equality;

import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ResourceKey.class)
public class ResourceKeyMixin {
    /**
     * @author embeddedt
     * @reason ResourceKeys are interned, so there is no reason to waste time doing any deeper comparison. This override
     * is patched in by Forge, it doesn't exist in vanilla
     */
    @Overwrite(remap = false)
    public boolean equals(Object o) {
        return o == this;
    }
}
