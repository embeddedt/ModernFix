package org.embeddedt.modernfix.api.helpers;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
import org.embeddedt.modernfix.util.DynamicMap;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public final class ModelHelpers {
    /**
     * Allows converting a ModelResourceLocation back into the corresponding BlockState(s). Try to avoid calling this
     * multiple times if possible.
     * @param location the location of the model
     * @return a list of all blockstates related to the model
     */
    public static ImmutableList<BlockState> getBlockStateForLocation(ModelResourceLocation location) {
        Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(location.id());
        if(blockOpt.isPresent())
            return ModelBakeryHelpers.getBlockStatesForMRL(blockOpt.get().getStateDefinition(), location);
        else
            return ImmutableList.of();
    }

    /**
     * Allows converting a ModelResourceLocation back into the corresponding BlockState(s). Faster version of its
     * companion function if and only if you know the corresponding Block already for some reason.
     * @param definition the state definition for the Block
     * @param location the location of the model
     * @return a list of all blockstates related to the model
     */
    public static ImmutableList<BlockState> getBlockStateForLocation(StateDefinition<Block, BlockState> definition, ModelResourceLocation location) {
        return ModelBakeryHelpers.getBlockStatesForMRL(definition, location);
    }

    /**
     * Compatibility helper for mods to use to get a map-like view of the model bakery.
     * @param modelGetter the model getter function supplied by the integration class
     * @return a fake map of the top-level models
     */
    public static Map<ResourceLocation, BakedModel> createFakeTopLevelMap(BiFunction<ResourceLocation, ModelState, BakedModel> modelGetter) {
        return new DynamicMap<>(location -> modelGetter.apply(location, BlockModelRotation.X0_Y0));
    }

    /**
     * Provides a ModelBaker for mods to use.
     * @param bakery the ModelBakery supplied to your integration
     * @return an appropriate ModelBaker
     */
    public static ModelBaker adaptBakery(ModelBakery bakery) {
        throw new UnsupportedOperationException("TODO");
        /*
        return new ModelBaker() {
            @Override
            public UnbakedModel getModel(ResourceLocation resourceLocation) {
                return bakery.getModel(resourceLocation);
            }

            @Nullable
            @Override
            public BakedModel bake(ResourceLocation resourceLocation, ModelState modelState) {
                return ((IExtendedModelBakery)bakery).bakeDefault(resourceLocation, modelState);
            }
        };

         */
    }
}
