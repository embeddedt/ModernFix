package org.embeddedt.modernfix.dynamicresources;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

public class ModelBakeryHelpers {

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
}
