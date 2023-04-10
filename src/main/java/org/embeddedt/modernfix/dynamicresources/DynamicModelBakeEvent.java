package org.embeddedt.modernfix.dynamicresources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Fired when a model is baked dynamically. Intended to be used as a replacement for ModelBakeEvent
 * if mods want to replace a model.
 * <p></p>
 * Note that this event can fire many times for the same resource location, as models are unloaded
 * if unused/under memory pressure.
 */
public class DynamicModelBakeEvent extends Event implements IModBusEvent {
    private final ResourceLocation location;
    private BakedModel model;
    private final UnbakedModel unbakedModel;
    private final ForgeModelBakery modelLoader;
    public DynamicModelBakeEvent(ResourceLocation location, UnbakedModel unbakedModel, BakedModel model, ForgeModelBakery loader) {
        this.location = location;
        this.model = model;
        this.unbakedModel = unbakedModel;
        this.modelLoader = loader;
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

    public ForgeModelBakery getModelLoader() {
        return this.modelLoader;
    }

    public void setModel(BakedModel model) {
        this.model = model;
    }
}
