package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.UnbakedModel;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelDiscovery.ModelWrapper.class)
@ClientOnlyMixin
public interface ModelDiscoveryModelWrapperAccessor {
    @Accessor("wrapped")
    UnbakedModel mfix$getWrapped();
}