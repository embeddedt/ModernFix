package org.embeddedt.modernfix.forge.dynresources;

import com.google.common.collect.ForwardingMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModelBakeEventHelper {
    public static Map<ResourceLocation, BakedModel> wrapRegistry(Map<ResourceLocation, BakedModel> modelRegistry) {
        Set<ResourceLocation> topLevelModelLocations = new HashSet<>(modelRegistry.keySet());
        for(Block block : ForgeRegistries.BLOCKS) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                topLevelModelLocations.add(ModelLocationCache.get(state));
            }
        }
        for(Item item : ForgeRegistries.ITEMS) {
            topLevelModelLocations.add(ModelLocationCache.get(item));
        }
        return new ForwardingMap<ResourceLocation, BakedModel>() {
            @Override
            protected Map<ResourceLocation, BakedModel> delegate() {
                return modelRegistry;
            }

            @Override
            public Set<ResourceLocation> keySet() {
                return topLevelModelLocations;
            }

            @Override
            public boolean containsKey(@Nullable Object key) {
                return topLevelModelLocations.contains(key) || super.containsKey(key);
            }

            @Override
            public BakedModel put(ResourceLocation key, BakedModel value) {
                topLevelModelLocations.add(key);
                return super.put(key, value);
            }
        };
    }
}
