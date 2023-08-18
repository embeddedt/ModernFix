package org.embeddedt.modernfix.dynamicresources;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;

import java.util.*;
import java.util.function.BiFunction;

public class ModelBakeryHelpers {
    /**
     * The maximum number of baked models kept in memory at once.
     */
    public static final int MAX_BAKED_MODEL_COUNT = 10000;
    /**
     * The maximum number of unbaked models kept in memory at once.
     */
    public static final int MAX_UNBAKED_MODEL_COUNT = 10000;
    /**
     * The time in seconds after which a model becomes eligible for eviction if not used.
     */
    public static final int MAX_MODEL_LIFETIME_SECS = 300;

    /**
     * These folders will have all textures stitched onto the atlas when dynamic resources is enabled.
     */
    public static String[] getExtraTextureFolders() {
        return new String[] {
                "attachment",
                "bettergrass",
                "block",
                "blocks",
                "cape",
                "entity/bed",
                "entity/chest",
                "item",
                "items",
                "model",
                "models",
                "part",
                "pipe",
                "ropebridge",
                "runes",
                "solid_block",
                "spell_effect",
                "spell_projectile"
        };
    }

    private static <T extends Comparable<T>, V extends T> BlockState setPropertyGeneric(BlockState state, Property<T> prop, Object o) {
        return state.setValue(prop, (V)o);
    }

    private static <T extends Comparable<T>> T getValueHelper(Property<T> property, String value) {
        return property.getValue(value).orElse((T) null);
    }

    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);

    public static ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        if(Objects.equals(location.getVariant(), "inventory"))
            return ImmutableList.of();
        Set<Property<?>> fixedProperties = new HashSet<>();
        BlockState fixedState = stateDefinition.any();
        for(String s : COMMA_SPLITTER.split(location.getVariant())) {
            Iterator<String> iterator = EQUAL_SPLITTER.split(s).iterator();
            if (iterator.hasNext()) {
                String s1 = iterator.next();
                Property<?> property = stateDefinition.getProperty(s1);
                if (property != null && iterator.hasNext()) {
                    String s2 = iterator.next();
                    Object value = getValueHelper(property, s2);
                    if (value == null) {
                        throw new RuntimeException("Unknown value: '" + s2 + "' for blockstate property: '" + s1 + "' " + property.getPossibleValues());
                    }
                    fixedState = setPropertyGeneric(fixedState, property, value);
                    fixedProperties.add(property);
                } else if (!s1.isEmpty()) {
                    throw new RuntimeException("Unknown blockstate property: '" + s1 + "'");
                }
            }
        }
        // check if there is only one possible state
        if(fixedProperties.size() == stateDefinition.getProperties().size()) {
            return ImmutableList.of(fixedState);
        }
        // generate all possible blockstates from the remaining properties
        ArrayList<Property<?>> anyProperties = new ArrayList<>(stateDefinition.getProperties());
        anyProperties.removeAll(fixedProperties);
        ArrayList<BlockState> finalList = new ArrayList<>();
        finalList.add(fixedState);
        for(Property<?> property : anyProperties) {
            ArrayList<BlockState> newPermutations = new ArrayList<>();
            for(BlockState state : finalList) {
                for(Comparable<?> value : property.getPossibleValues()) {
                    newPermutations.add(setPropertyGeneric(state, property, value));
                }
            }
            finalList = newPermutations;
        }
        return ImmutableList.copyOf(finalList);
    }

    public static ModernFixClientIntegration bakedModelWrapper(BiFunction<ResourceLocation, Pair<UnbakedModel, BakedModel>, BakedModel> consumer) {
        return new ModernFixClientIntegration() {
            @Override
            public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery) {
                return consumer.apply(location, Pair.of(baseModel, originalModel));
            }
        };
    }
}
