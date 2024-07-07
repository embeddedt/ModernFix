package org.embeddedt.modernfix.forge.dynresources;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.ForwardingInclDefaultsMap;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Stores a list of all known default block/item models in the game, and provides a namespaced version
 * of the model registry that emulates vanilla keySet behavior.
 */
public class ModelBakeEventHelper {
    // TODO: make into config option
    private static final Set<String> INCOMPATIBLE_MODS = ImmutableSet.of(
            "industrialforegoing",
            "mekanism",
            "vampirism",
            "elevatorid",
            "embers");
    private final Map<ResourceLocation, BakedModel> modelRegistry;
    private final Set<ResourceLocation> topLevelModelLocations;
    private final MutableGraph<String> dependencyGraph;
    public ModelBakeEventHelper(Map<ResourceLocation, BakedModel> modelRegistry) {
        this.modelRegistry = modelRegistry;
        this.topLevelModelLocations = new HashSet<>(modelRegistry.keySet());
        // Skip going through ModelLocationCache because most of the accesses will be misses
        ForgeRegistries.BLOCKS.getEntries().forEach(entry -> {
            var location = entry.getKey().location();
            for(BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                topLevelModelLocations.add(BlockModelShaper.stateToModelLocation(location, state));
            }
        });
        ForgeRegistries.ITEMS.getKeys().forEach(key -> topLevelModelLocations.add(new ModelResourceLocation(key, "inventory")));
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
            public Set<Entry<ResourceLocation, BakedModel>> entrySet() {
                return new DynamicModelEntrySet(this, ourModelLocations);
            }

            @Override
            public void replaceAll(BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
                ModernFix.LOGGER.warn("Mod '{}' is calling replaceAll on the model registry. Some hacks will be used to keep this fast, but they may not be 100% compatible.", modId);
                List<ResourceLocation> locations = new ArrayList<>(keySet());
                for(ResourceLocation location : locations) {
                    /*
                     * Fetching every model is insanely slow. So we call the function with a null object first, since it
                     * probably isn't expecting that. If we get an exception thrown, or it returns nonnull, then we know
                     * it actually cares about the given model.
                     */
                    boolean needsReplacement;
                    try {
                        needsReplacement = function.apply(location, null) != null;
                    } catch(Throwable e) {
                        needsReplacement = true;
                    }
                    if(needsReplacement) {
                        BakedModel existing = get(location);
                        BakedModel replacement = function.apply(location, existing);
                        if(replacement != existing) {
                            put(location, replacement);
                        }
                    }
                }
            }
        };
    }

    private static class DynamicModelEntrySet extends AbstractSet<Map.Entry<ResourceLocation, BakedModel>> {
        private final Map<ResourceLocation, BakedModel> modelRegistry;
        private final Set<ResourceLocation> modelLocations;

        private DynamicModelEntrySet(Map<ResourceLocation, BakedModel> modelRegistry, Set<ResourceLocation> modelLocations) {
            this.modelRegistry = modelRegistry;
            this.modelLocations = modelLocations;
        }

        @Override
        public Iterator<Map.Entry<ResourceLocation, BakedModel>> iterator() {
            return Iterators.transform(Iterators.unmodifiableIterator(this.modelLocations.iterator()), DynamicModelEntry::new);
        }

        @Override
        public boolean contains(Object o) {
            if(o instanceof Map.Entry entry) {
                return modelRegistry.containsKey(entry.getKey());
            } else {
                return false;
            }
        }

        @Override
        public int size() {
            return modelRegistry.size();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        private class DynamicModelEntry implements Map.Entry<ResourceLocation, BakedModel> {
            private final ResourceLocation location;

            private DynamicModelEntry(ResourceLocation location) {
                this.location = location;
            }

            @Override
            public ResourceLocation getKey() {
                return this.location;
            }

            @Override
            public BakedModel getValue() {
                return modelRegistry.get(this.location);
            }

            @Override
            public BakedModel setValue(BakedModel value) {
                return modelRegistry.put(this.location, value);
            }
        }
    }
}
