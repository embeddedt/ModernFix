package org.embeddedt.modernfix.forge.dynresources;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ModelLocationBuilder {
    private final Map<Property<?>, PropertyData> propertyToOptionStrings = new Object2ObjectOpenHashMap<>();
    private final StringBuilder builder = new StringBuilder();

    private record PropertyData(List<String> nameValuePairs, int maxPairLength) {}

    public void generateForBlock(Set<ResourceLocation> destinationSet, Block block, ResourceLocation baseLocation) {
        var props = block.getStateDefinition().getProperties();
        List<List<String>> optionsList = new ArrayList<>(props.size());
        int requiredBuilderSize = Math.max(0, props.size() - 1); // commas
        for (var prop : props) {
            var data = propertyToOptionStrings.computeIfAbsent(prop, ModelLocationBuilder::computePropertyOptions);
            optionsList.add(data.nameValuePairs);
            requiredBuilderSize += data.maxPairLength;
        }
        var product = Lists.cartesianProduct(optionsList);
        int count = product.size();
        int tupleEntryCount = optionsList.size();
        StringBuilder stringbuilder = this.builder;
        stringbuilder.ensureCapacity(requiredBuilderSize);
        for (int i = 0; i < count; i++) {
            stringbuilder.setLength(0);
            var result = product.get(i);
            for (int j = 0; j < tupleEntryCount; j++) {
                if (j != 0) {
                    stringbuilder.append(',');
                }
                stringbuilder.append(result.get(j));
            }
            destinationSet.add(new ModelResourceLocation(baseLocation, stringbuilder.toString()));
        }
    }

    private static PropertyData computePropertyOptions(Property<?> prop) {
        List<String> valuesList = new ArrayList<>(prop.getPossibleValues().size());
        int maxLength = 0;
        for (var val : prop.getPossibleValues()) {
            String pair = prop.getName() + "=" + getValueName(prop, val);
            valuesList.add(pair.toLowerCase(Locale.ROOT));
            maxLength = Math.max(pair.length(), maxLength);
        }
        return new PropertyData(List.copyOf(valuesList), maxLength);
    }

    private static <T extends Comparable<T>> String getValueName(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }
}
