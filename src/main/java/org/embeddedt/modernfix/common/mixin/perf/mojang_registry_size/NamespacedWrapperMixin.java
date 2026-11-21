package org.embeddedt.modernfix.common.mixin.perf.mojang_registry_size;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.embeddedt.modernfix.util.RegistryListUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {"net/minecraftforge/registries/NamespacedWrapper"})
public class NamespacedWrapperMixin {
    /**
     * Forge's wrapper keeps its own holder list by ID and grows it the same way as MappedRegistry.
     */
    @Redirect(
            method = "onAdded",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectList;size(I)V", remap = false),
            require = 1,
            remap = false
    )
    private void setSizeSmart(ObjectList<?> list, int size) {
        RegistryListUtil.setSizeSmart(list, size);
    }
}
