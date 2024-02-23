package org.embeddedt.modernfix.forge.mixin.perf.forge_registry_alloc;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Locale;
import java.util.Map;

@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin<V> {
    // Replace the backing maps with fastutil maps for a bit more speed, since value->holder lookups in particular
    // are a bottleneck in many areas (e.g. render type lookup)
    @Shadow @Final private Map<ResourceLocation, Holder.Reference<V>> delegatesByName = new Object2ObjectOpenHashMap<>();

    @Shadow @Final private Map<V, Holder.Reference<V>> delegatesByValue = new Object2ObjectOpenHashMap<>();

    /**
     * @author embeddedt
     * @reason stop allocating so many unneeded objects. stop.
     */
    @Overwrite
    public Holder.Reference<V> getDelegateOrThrow(ResourceLocation location) {
        Holder.Reference<V> holder = delegatesByName.get(location);

        if (holder == null) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH, "No delegate exists for location %s", location));
        }

        return holder;
    }

    /**
     * @author embeddedt
     * @reason see above
     */
    @Overwrite
    public Holder.Reference<V> getDelegateOrThrow(ResourceKey<V> rkey) {
        Holder.Reference<V> holder = delegatesByName.get(rkey.location());

        if (holder == null) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH, "No delegate exists for key %s", rkey));
        }

        return holder;
    }

    /**
     * @author embeddedt
     * @reason see above
     */
    @Overwrite
    public Holder.Reference<V> getDelegateOrThrow(V value) {
        Holder.Reference<V> holder = delegatesByValue.get(value);

        if (holder == null) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH, "No delegate exists for value %s", value));
        }

        return holder;
    }
}
