package org.embeddedt.modernfix.forge.mixin.perf.forge_registry_lambda;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = {"net/minecraftforge/registries/RegistryDelegate"})
public class RegistryDelegateMixin {
    @Shadow private ResourceLocation name;

    /**
     * @author embeddedt
     * @reason avoid allocation in hashCode()
     */
    @Overwrite(remap = false)
    public int hashCode() {
        ResourceLocation name = this.name;
        if(name != null) {
            return name.hashCode();
        } else {
            return 0;
        }
    }
}
