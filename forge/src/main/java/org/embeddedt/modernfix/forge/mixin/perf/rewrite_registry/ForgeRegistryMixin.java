package org.embeddedt.modernfix.forge.mixin.perf.rewrite_registry;

import com.google.common.collect.BiMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.embeddedt.modernfix.annotation.IgnoreOutsideDev;
import org.embeddedt.modernfix.forge.registry.FastForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeRegistry.class, remap = false)
@IgnoreOutsideDev
public class ForgeRegistryMixin<V extends IForgeRegistryEntry<V>> {
    @Shadow
    @Final
    @Mutable
    private BiMap<Integer, V> ids;

    @Shadow @Final @Mutable private BiMap<ResourceKey<V>, V> keys;

    @Shadow @Final private ResourceKey<Registry<V>> key;

    @Shadow @Final @Mutable private BiMap<ResourceLocation, V> names;

    @Shadow @Final @Mutable private BiMap owners;

    private FastForgeRegistry<V> fastRegistry;

    /**
     * The following code replaces the Forge HashBiMaps with a more efficient data structure based around
     * an array list for IDs and one HashMap going from value -> information.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceBackingMaps(CallbackInfo ci) {
        this.fastRegistry = new FastForgeRegistry<>(this.key);
        this.ids = fastRegistry.getIds();
        this.keys = fastRegistry.getKeys();
        this.names = fastRegistry.getNames();
        this.owners = fastRegistry.getOwners();
    }

    @Inject(method = "freeze", at = @At("RETURN"))
    private void optimizeRegistry(CallbackInfo ci) {
        this.fastRegistry.optimize();
    }

    @Redirect(method = "sync", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;clear()V"))
    private void clearBiMap(BiMap map) {
        if(map == this.owners) {
            this.fastRegistry.clear();
        } else if(map == this.keys || map == this.names || map == this.ids) {
            // do nothing, the registry is faster at clearing everything at once
        } else
            map.clear();
    }
}
