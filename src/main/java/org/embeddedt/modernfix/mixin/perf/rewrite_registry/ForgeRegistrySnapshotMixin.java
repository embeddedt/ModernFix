package org.embeddedt.modernfix.mixin.perf.rewrite_registry;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(ForgeRegistry.Snapshot.class)
public class ForgeRegistrySnapshotMixin {
    @Shadow @Final @Mutable public Map<ResourceLocation, Integer> ids;

    @Shadow @Final @Mutable public Set<ResourceLocation> dummied;

    /**
     * The only good reason to use tree maps here is to keep the order the same. But we are tracking IDs
     * anyway so order shouldn't matter. We replace the maps that will be most used.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceSnapshotMaps(CallbackInfo ci) {
        this.ids = new Object2ObjectOpenHashMap<>();
        this.dummied = new ObjectOpenHashSet<>();
    }
}
