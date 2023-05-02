package org.embeddedt.modernfix.forge.dynamicresources;

import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when a model is baked dynamically. Intended to be used as a replacement for ModelBakeEvent
 * if mods want to replace a model.
 * <p></p>
 * Note that this event can fire many times for the same resource location, as models are unloaded
 * if unused/under memory pressure.
 */
public class DynamicModelBakeEvent extends Event {
    private final ResourceLocation location;
    private BakedModel model;
    private final UnbakedModel unbakedModel;
    private final ModelBaker modelLoader;
    private final ModelBakery modelBakery;
    public DynamicModelBakeEvent(ResourceLocation location, UnbakedModel unbakedModel, BakedModel model, ModelBaker loader, ModelBakery bakery) {
        this.location = location;
        this.model = model;
        this.unbakedModel = unbakedModel;
        this.modelLoader = loader;
        this.modelBakery = bakery;
    }

    public ResourceLocation getLocation() {
        return this.location;
    }

    public BakedModel getModel() {
        return this.model;
    }

    public UnbakedModel getUnbakedModel() {
        return this.unbakedModel;
    }

    public ModelBaker getModelLoader() {
        return this.modelLoader;
    }

    public ModelBakery getModelBakery() {
        return this.modelBakery;
    }

    public void setModel(BakedModel model) {
        this.model = model;
    }
}
