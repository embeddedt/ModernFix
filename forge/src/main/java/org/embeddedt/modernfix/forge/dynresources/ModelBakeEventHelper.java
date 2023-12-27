package org.embeddedt.modernfix.forge.dynresources;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.embeddedt.modernfix.util.ForwardingInclDefaultsMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Stores a list of all known default block/item models in the game, and provides a namespaced version
 * of the model registry that emulates vanilla keySet behavior.
 */
public class ModelBakeEventHelper {
    // TODO: make into config option
    private static final Set<String> INCOMPATIBLE_MODS = ImmutableSet.of("industrialforegoing", "vampirism", "elevatorid");
    private final Map<ResourceLocation, BakedModel> modelRegistry;
    private final Set<ResourceLocation> topLevelModelLocations;
    private final MutableGraph<String> dependencyGraph;
    public ModelBakeEventHelper(Map<ResourceLocation, BakedModel> modelRegistry) {
        this.modelRegistry = modelRegistry;
        this.topLevelModelLocations = new HashSet<>(modelRegistry.keySet());
        for(Block block : ForgeRegistries.BLOCKS) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                topLevelModelLocations.add(ModelLocationCache.get(state));
            }
        }
        for(Item item : ForgeRegistries.ITEMS) {
            topLevelModelLocations.add(ModelLocationCache.get(item));
        }
        this.dependencyGraph = GraphBuilder.undirected().build();
        ModList.get().forEachModContainer((id, mc) -> {
            this.dependencyGraph.addNode(id);
            for(IModInfo.ModVersion version : mc.getModInfo().getDependencies()) {
                this.dependencyGraph.addNode(version.getModId());
            }
        });
        for(String id : this.dependencyGraph.nodes()) {
            Optional<? extends ModContainer> mContainer = ModList.get().getModContainerById(id);
            if(mContainer.isPresent()) {
                for(IModInfo.ModVersion version : mContainer.get().getModInfo().getDependencies()) {
                    // avoid self-loops
                    if(!Objects.equals(id, version.getModId()))
                        this.dependencyGraph.putEdge(id, version.getModId());
                }
            }
        }
    }

    private static final Set<String> WARNED_MOD_IDS = new HashSet<>();

    /**
     * Create a model registry that warns if keySet, entrySet, values are accessed.
     * @param modId the mod that the event is being fired for
     * @return a wrapper around the model registry
     */
    private Map<ResourceLocation, BakedModel> createWarningRegistry(String modId) {
        return new ForwardingInclDefaultsMap<ResourceLocation, BakedModel>() {
            @Override
            protected Map<ResourceLocation, BakedModel> delegate() {
                return modelRegistry;
            }

            private void logWarning() {
                if(!WARNED_MOD_IDS.add(modId))
                    return;
                ModernFix.LOGGER.warn("Mod '{}' is accessing Map#keySet/entrySet/values/replaceAll on the model registry map inside its event handler." +
                        " This probably won't work as expected with dynamic resources on. Prefer using Map#get/put and constructing ModelResourceLocations another way.", modId);
            }

            @Override
            public Set<ResourceLocation> keySet() {
                logWarning();
                return super.keySet();
            }

            @Override
            public Set<Entry<ResourceLocation, BakedModel>> entrySet() {
                logWarning();
                return super.entrySet();
            }

            @Override
            public Collection<BakedModel> values() {
                logWarning();
                return super.values();
            }

            @Override
            public void replaceAll(BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
                logWarning();
                super.replaceAll(function);
            }
        };
    }

    public Map<ResourceLocation, BakedModel> wrapRegistry(String modId) {
        final Set<String> modIdsToInclude = new HashSet<>();
        modIdsToInclude.add(modId);
        try {
            modIdsToInclude.addAll(this.dependencyGraph.adjacentNodes(modId));
        } catch(IllegalArgumentException ignored) { /* sanity check */ }
        modIdsToInclude.remove("minecraft");
        if(modIdsToInclude.stream().noneMatch(INCOMPATIBLE_MODS::contains))
            return createWarningRegistry(modId);
        Set<ResourceLocation> ourModelLocations = Sets.filter(this.topLevelModelLocations, loc -> modIdsToInclude.contains(loc.getNamespace()));
        BakedModel missingModel = modelRegistry.get(ModelBakery.MISSING_MODEL_LOCATION);
        return new ForwardingMap<ResourceLocation, BakedModel>() {
            @Override
            protected Map<ResourceLocation, BakedModel> delegate() {
                return modelRegistry;
            }

            @Override
            public BakedModel get(@Nullable Object key) {
                BakedModel model = super.get(key);
                if(model == null && key != null && modIdsToInclude.contains(((ResourceLocation)key).getNamespace())) {
                    ModernFix.LOGGER.warn("Model {} is missing, but was requested in model bake event. Returning missing model", key);
                    return missingModel;
                }
                return model;
            }

            @Override
            public Set<ResourceLocation> keySet() {
                return ourModelLocations;
            }

            @Override
            public boolean containsKey(@Nullable Object key) {
                return ourModelLocations.contains(key) || super.containsKey(key);
            }

            @Override
            public void replaceAll(BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
                for(ResourceLocation location : keySet()) {
                    put(location, function.apply(location, get(location)));
                }
            }
        };
    }
}
