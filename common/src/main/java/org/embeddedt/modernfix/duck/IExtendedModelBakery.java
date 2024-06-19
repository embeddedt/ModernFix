package org.embeddedt.modernfix.duck;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;

public interface IExtendedModelBakery {
    void mfix$tick();
    void mfix$finishLoading();
    UnbakedModel mfix$loadUnbakedModelDynamic(ModelResourceLocation location);
    UnbakedModel mfix$getMissingModel();
}
