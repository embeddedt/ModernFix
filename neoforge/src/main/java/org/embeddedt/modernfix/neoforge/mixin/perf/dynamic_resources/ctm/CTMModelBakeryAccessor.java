package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources.ctm;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelBakery.class)
@ClientOnlyMixin
public interface CTMModelBakeryAccessor {
    @Accessor("bakedCache")
    Map<ModelBakery.BakedCacheKey, BakedModel> mfix$getBakedCache();
}
