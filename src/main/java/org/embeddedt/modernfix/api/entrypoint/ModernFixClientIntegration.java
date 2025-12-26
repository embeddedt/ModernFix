package org.embeddedt.modernfix.api.entrypoint;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;


/**
 * Implement this interface in a mod class and add it to "modernfix:integration_v1" in your mod metadata file
 * to integrate with ModernFix's features.
 */
public interface ModernFixClientIntegration {
    /**
     * Called when the dynamic resources status has changed during a model reload so mods know whether to run their
     * normal codepath or the dynamic version.
     *
     * @param enabled whether dynamic resources is enabled
     */
    default void onDynamicResourcesStatusChange(boolean enabled) {
    }

    /**
     * Called to allow mods to observe the loading of an unbaked model and either make changes to it or wrap it with their
     * own instance.
     * @param location the ResourceLocation of the model (this may be a ModelResourceLocation)
     * @param originalModel the original model
     * @param bakery the model bakery - do not touch internal fields as they probably don't behave the way you expect
     * with dynamic resources on
     * @return the model which should actually be loaded for this resource location
     */
    default UnbakedModel onUnbakedModelLoad(ResourceLocation location, UnbakedModel originalModel, ModelBakery bakery) {
        return originalModel;
    }

    /**
     * Called to allow mods to observe the use of an unbaked model at bake time and either make changes to it or wrap it with their
     * own instance.
     * @param location the ResourceLocation of the model (this may be a ModelResourceLocation)
     * @param originalModel the original model
     * @param bakery the model bakery - do not touch internal fields as they probably don't behave the way you expect
     * with dynamic resources on
     * @return the model which should actually be loaded for this resource location
     */
    default UnbakedModel onUnbakedModelPreBake(ResourceLocation location, UnbakedModel originalModel, ModelBakery bakery) {
        return originalModel;
    }

    /**
     * Called to allow mods to observe the loading of a baked model and either make changes to it or wrap it with their
     * own instance.
     * @param location the ResourceLocation of the model (this may be a ModelResourceLocation)
     * @param originalModel the original model
     * @param bakery the model bakery - do not touch internal fields as they probably don't behave the way you expect
     * with dynamic resources on
     * @return the model which should actually be loaded for this resource location
     */
    @Deprecated
    default BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery) {
        return originalModel;
    }

    /**
     * Called to allow mods to observe the loading of a baked model and either make changes to it or wrap it with their
     * own instance.
     * @param location the ResourceLocation of the model (this may be a ModelResourceLocation)
     * @param originalModel the original model
     * @param bakery the model bakery - do not touch internal fields as they probably don't behave the way you expect
     * with dynamic resources on
     * @param textureGetter function to retrieve textures for this model
     * @return the model which should actually be loaded for this resource location
     */
    default BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery, Function<Material, TextureAtlasSprite> textureGetter) {
        return onBakedModelLoad(location, baseModel, originalModel, state, bakery);
    }
}
