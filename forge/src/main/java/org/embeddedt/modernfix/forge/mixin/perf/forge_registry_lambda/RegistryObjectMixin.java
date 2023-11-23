package org.embeddedt.modernfix.forge.mixin.perf.forge_registry_lambda;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RegistryObject.class, remap = false)
public class RegistryObjectMixin<T extends IForgeRegistryEntry<? super T>> {
    @Shadow private @Nullable T value;

    @Shadow @Final private ResourceLocation name;

    /**
     * @author embeddedt
     * @reason avoid lambda allocation on every call
     */
    @Overwrite
    public T get() {
        T ret = this.value;
        if(ret == null) {
            throw new NullPointerException("Registry Object not present: " + this.name);
        }
        return ret;
    }
}
