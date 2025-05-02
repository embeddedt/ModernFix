package org.embeddedt.modernfix.neoforge.dynresources;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.ForwardingInclDefaultsMap;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    private enum UniverseVisibility {
        /**
         * Mod cannot see any view of the universe of model locations.
         */
        NONE,
        /**
         * Mod can see its own model locations and those of dependencies/dependents.
         */
        SELF_AND_DEPS,
        /**
         * Mod can see every model location.
         */
        EVERYTHING
    }
    private static final Map<String, UniverseVisibility> MOD_VISIBILITY_CONFIGURATION = ImmutableMap.<String, UniverseVisibility>builder()
            .build();
    private final Map<ModelResourceLocation, BakedModel> modelRegistry;
    private final Set<ModelResourceLocation> topLevelModelLocations;
    private final MutableGraph<String> dependencyGraph;

    public ModelBakeEventHelper(Map<ModelResourceLocation, BakedModel> modelRegistry) {
        this.modelRegistry = modelRegistry;
        this.topLevelModelLocations = new ObjectLinkedOpenHashSet<>();
        // Skip going through ModelLocationCache because most of the accesses will be misses
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            var location = entry.getKey().location();
            for(BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                topLevelModelLocations.add(BlockModelShaper.stateToModelLocation(location, state));
            }
        });
        BuiltInRegistries.ITEM.keySet().forEach(key -> topLevelModelLocations.add(new ModelResourceLocation(key, "inventory")));
        this.topLevelModelLocations.addAll(modelRegistry.keySet());
        // We add all standard item model locations here so that mods like JAOPCA that assume their remapping logic
        // triggers loading of them (which it doesn't with dynamic resources on), will still detect the presence
        // of their extra models in the emulated registry.
        var itemModelLister = FileToIdConverter.json("models/item");
        itemModelLister.listMatchingResources(Minecraft.getInstance().getResourceManager()).keySet().forEach(itemModel -> {
            this.topLevelModelLocations.add(ModelResourceLocation.inventory(itemModelLister.fileToId(itemModel)));
        });
        this.dependencyGraph = buildDependencyGraph();
    }

    private static MutableGraph<String> buildDependencyGraph() {
        MutableGraph<String> dependencyGraph = GraphBuilder.undirected().build();
        ModList.get().forEachModContainer((id, mc) -> {
            dependencyGraph.addNode(id);
            for(IModInfo.ModVersion version : mc.getModInfo().getDependencies()) {
                dependencyGraph.addNode(version.getModId());
            }
        });
        for(String id : dependencyGraph.nodes()) {
            Optional<? extends ModContainer> mContainer = ModList.get().getModContainerById(id);
            if(mContainer.isPresent()) {
                for(IModInfo.ModVersion version : mContainer.get().getModInfo().getDependencies()) {
                    // avoid self-loops
                    if(!Objects.equals(id, version.getModId()))
                        dependencyGraph.putEdge(id, version.getModId());
                }
            }
        }
        return dependencyGraph;
    }

    private static final Set<String> WARNED_MOD_IDS = new HashSet<>();

    /**
     * Create a model registry that warns if keySet, entrySet, values are accessed.
     * @param modId the mod that the event is being fired for
     * @return a wrapper around the model registry
     */
    private Map<ModelResourceLocation, BakedModel> createWarningRegistry(String modId) {
        return new ForwardingInclDefaultsMap<ModelResourceLocation, BakedModel>() {
            @Override
            protected Map<ModelResourceLocation, BakedModel> delegate() {
                return modelRegistry;
            }

            private void logWarning() {
                if(!WARNED_MOD_IDS.add(modId))
                    return;
                ModernFix.LOGGER.warn("Mod '{}' is accessing Map#keySet/entrySet/values/replaceAll on the model registry map inside its event handler." +
                        " This probably won't work as expected with dynamic resources on. Prefer using Map#get/put and constructing ModelResourceLocations another way.", modId);
            }

            @Override
            public Set<ModelResourceLocation> keySet() {
                logWarning();
                return super.keySet();
            }

            @Override
            public Set<Entry<ModelResourceLocation, BakedModel>> entrySet() {
                logWarning();
                return super.entrySet();
            }

            @Override
            public Collection<BakedModel> values() {
                logWarning();
                return super.values();
            }

            @Override
            public void replaceAll(BiFunction<? super ModelResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
                logWarning();
                super.replaceAll(function);
            }
        };
    }

    public Map<ModelResourceLocation, BakedModel> wrapRegistry(String modId) {
        var config = MOD_VISIBILITY_CONFIGURATION.getOrDefault(modId, UniverseVisibility.EVERYTHING);
        if (config == UniverseVisibility.NONE) {
            return createWarningRegistry(modId);
        }
        final Set<String> modIdsToInclude = new HashSet<>();
        modIdsToInclude.add(modId);
        try {
            modIdsToInclude.addAll(this.dependencyGraph.adjacentNodes(modId));
        } catch(IllegalArgumentException ignored) { /* sanity check */ }
        modIdsToInclude.remove("minecraft");
        Set<ModelResourceLocation> ourModelLocations;
        if (config == UniverseVisibility.SELF_AND_DEPS) {
            ourModelLocations = Sets.filter(this.topLevelModelLocations, loc -> modIdsToInclude.contains(loc.id().getNamespace()));
        } else {
            ourModelLocations = this.topLevelModelLocations;
        }
        BakedModel missingModel = modelRegistry.get(ModelBakery.MISSING_MODEL_LOCATION);
        return new EmulatedModelRegistry(modId, modIdsToInclude, missingModel, ourModelLocations);
    }

    public class EmulatedModelRegistry extends ForwardingMap<ModelResourceLocation, BakedModel> {
        private final Set<String> modIdsToInclude;
        private final BakedModel missingModel;
        private final Set<ModelResourceLocation> ourModelLocations;
        private final String modId;

        private EmulatedModelRegistry(String modId, Set<String> modIdsToInclude, BakedModel missingModel, Set<ModelResourceLocation> ourModelLocations) {
            this.modId = modId;
            this.modIdsToInclude = modIdsToInclude;
            this.missingModel = missingModel;
            this.ourModelLocations = ourModelLocations;
        }

        @Override
        protected Map<ModelResourceLocation, BakedModel> delegate() {
            return modelRegistry;
        }

        @Override
        public BakedModel get(@Nullable Object key) {
            BakedModel model = super.get(key);
            if(model == null && key instanceof ModelResourceLocation mrl && modIdsToInclude.contains(mrl.id().getNamespace())) {
                ModernFix.LOGGER.warn("Model {} is missing, but was requested in model bake event. Returning missing model", key);
                return missingModel;
            }
            return model;
        }

        @Override
        public Set<ModelResourceLocation> keySet() {
            return Collections.unmodifiableSet(ourModelLocations);
        }

        @Override
        public boolean containsKey(@Nullable Object key) {
            return ourModelLocations.contains(key) || super.containsKey(key);
        }

        @Override
        public Set<Entry<ModelResourceLocation, BakedModel>> entrySet() {
            return new DynamicModelEntrySet(this, ourModelLocations);
        }

        @Override
        public void replaceAll(BiFunction<? super ModelResourceLocation, ? super BakedModel, ? extends BakedModel> function) {
            ModernFix.LOGGER.warn("Mod '{}' is calling replaceAll on the model registry. Some hacks will be used to keep this fast, but they may not be 100% compatible.", modId);
            List<ModelResourceLocation> locations = new ArrayList<>(ourModelLocations);
            for(ModelResourceLocation location : locations) {
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
    }

    private static class DynamicModelEntrySet extends AbstractSet<Map.Entry<ModelResourceLocation, BakedModel>> {
        private final Map<ModelResourceLocation, BakedModel> modelRegistry;
        private final Set<ModelResourceLocation> modelLocations;

        private DynamicModelEntrySet(Map<ModelResourceLocation, BakedModel> modelRegistry, Set<ModelResourceLocation> modelLocations) {
            this.modelRegistry = modelRegistry;
            this.modelLocations = modelLocations;
        }

        @Override
        public Iterator<Map.Entry<ModelResourceLocation, BakedModel>> iterator() {
            var iter = this.modelLocations.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Map.Entry<ModelResourceLocation, BakedModel> next() {
                    return new DynamicModelEntry(iter.next());
                }
            };
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

        private class DynamicModelEntry implements Map.Entry<ModelResourceLocation, BakedModel> {
            private final ModelResourceLocation location;

            private DynamicModelEntry(ModelResourceLocation location) {
                this.location = location;
            }

            @Override
            public ModelResourceLocation getKey() {
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
