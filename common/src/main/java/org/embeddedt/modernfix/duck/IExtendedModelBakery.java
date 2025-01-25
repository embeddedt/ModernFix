package org.embeddedt.modernfix.duck;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;

import java.util.concurrent.locks.ReentrantLock;

public interface IExtendedModelBakery {
    void mfix$tick();
    void mfix$finishLoading();
    UnbakedModel mfix$loadUnbakedModelDynamic(ModelResourceLocation location);
    UnbakedModel mfix$getMissingModel();
    ReentrantLock mfix$getLock();
}
