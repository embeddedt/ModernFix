package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.resources.Identifier;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelDiscovery.class)
@ClientOnlyMixin
public interface ModelDiscoveryAccessor {
    @Accessor("modelWrappers")
    Object2ObjectMap<Identifier, ModelDiscovery.ModelWrapper> mfix$getModelWrappers();
}
