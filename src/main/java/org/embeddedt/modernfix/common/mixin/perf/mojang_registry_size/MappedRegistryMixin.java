package org.embeddedt.modernfix.common.mixin.perf.mojang_registry_size;

import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.MappedRegistry;
import org.embeddedt.modernfix.util.RegistryListUtil;
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
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectList;size(I)V", remap = false)
    )
    private void setSizeSmart(ObjectList<?> list, int size) {
        RegistryListUtil.setSizeSmart(list, size);
    }
}
