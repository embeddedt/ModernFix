package org.embeddedt.modernfix.common.mixin.perf.mojang_registry_size;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MappedRegistry.class)
public class MappedRegistryMixin {
    /**
     * Avoid copying the ID list to a slightly larger one every time an entry is added to the registry.
     * The original behavior causes O(n) time complexity for registration.
     */
    @Redirect(
            method = "registerMapping(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/Holder$Reference;",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectList;size(I)V", remap = false),
            require = 0
    )
    private void setSizeSmart(ObjectList<?> list, int size) {
        if(list instanceof ObjectArrayList && size > list.size()) {
            int requestedSize = size;
            /* choose next power of two, or this value if it is a power of two */
            int p2Size = Integer.highestOneBit(size);
            if(p2Size != size)
                size = p2Size << 1;
            // grow backing array to power-of-two size, this will return instantly in most cases
            ((ObjectArrayList<?>)list).ensureCapacity(size);
            // write null entries to fill size, to match the behavior of list.size(int)
            while(list.size() < requestedSize)
                list.add(null);
        } else {
            list.size(size);
        }
    }
}
