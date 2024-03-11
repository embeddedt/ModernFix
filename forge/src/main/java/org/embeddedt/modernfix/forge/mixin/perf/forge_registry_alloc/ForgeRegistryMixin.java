package org.embeddedt.modernfix.forge.mixin.perf.forge_registry_alloc;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.embeddedt.modernfix.forge.registry.DelegateHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Map;

@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin<V> {
    // Replace the backing maps with fastutil maps for a bit more speed, since value->holder lookups in particular
    // are a bottleneck in many areas (e.g. render type lookup)
    @Shadow @Final private Map<ResourceLocation, Holder.Reference<V>> delegatesByName = new Object2ObjectOpenHashMap<>();

    @Shadow @Final private Map<V, Holder.Reference<V>> delegatesByValue = new Object2ObjectOpenHashMap<>(Hash.DEFAULT_INITIAL_SIZE, 0.5F);

    @Shadow public abstract ResourceKey<Registry<V>> getRegistryKey();

    @Shadow @Final private RegistryManager stage;

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
     * @reason store delegates that are accessed extremely regularly on the registry entry itself, rather than
     * going through a map lookup
     */
    @Inject(method = "bindDelegate", at = @At("RETURN"))
    private void attachDelegate(ResourceKey<V> rkey, V value, CallbackInfoReturnable<Holder.Reference<V>> cir) {
        // Only attach delegates for the ACTIVE registry. The Forge registry system is weird and seems to keep multiple
        // copies of itself at once.
        if(this.stage == RegistryManager.ACTIVE && value instanceof DelegateHolder<?>) {
            ((DelegateHolder<V>)value).mfix$setDelegate(this.getRegistryKey(), cir.getReturnValue());
        }
    }

    /**
     * @author embeddedt
     * @reason skip map lookup for hot delegates, avoid allocations otherwise
     */
    @Overwrite
    public Holder.Reference<V> getDelegateOrThrow(V value) {
        Holder.Reference<V> holder = null;
        if (this.stage == RegistryManager.ACTIVE && value instanceof DelegateHolder<?>) {
            holder = ((DelegateHolder<V>)value).mfix$getDelegate(this.getRegistryKey());
        }

        if (holder == null) {
            holder = delegatesByValue.get(value);
            if (holder == null) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH, "No delegate exists for value %s", value));
            }
        }

        return holder;
    }
}
