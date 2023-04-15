package org.embeddedt.modernfix.duck;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

public interface IDynamicModelBakery {
    BakedModel bakeDefault(ResourceLocation modelLocation);
}
