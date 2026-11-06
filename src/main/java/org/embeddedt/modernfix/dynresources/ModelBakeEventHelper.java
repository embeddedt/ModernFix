package org.embeddedt.modernfix.dynresources;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.ForwardingInclDefaultsMap;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provides each mod with its own view of the baked model maps, which limits what the mod can see when iterating and
 * avoids baking every model where possible.
 */
public class ModelBakeEventHelper {

    private enum UniverseVisibility {
        /**
         * Mod can see its own models and those of dependencies/dependents.
         */
        SELF_AND_DEPS,
        /**
         * Mod can see every model.
         */
        EVERYTHING
    }
    private static final Map<String, UniverseVisibility> MOD_VISIBILITY_CONFIGURATION = ImmutableMap.<String, UniverseVisibility>builder()
            .put("eternal_starlight", UniverseVisibility.SELF_AND_DEPS) // needed as a mitigation until https://github.com/LeoMinecraftModding/eternal-starlight/pull/82 is merged
            .put("alexscaves", UniverseVisibility.SELF_AND_DEPS)
            .put("refinedstorage", UniverseVisibility.SELF_AND_DEPS)
            .put("cabletiers", UniverseVisibility.SELF_AND_DEPS)
            .build();
    private final ModelBakery.BakingResult bakingResult;
    private final Set<String> namespacesWithModels;
    private final MutableGraph<String> dependencyGraph;

    public ModelBakeEventHelper(ModelBakery.BakingResult bakingResult) {
        this.bakingResult = bakingResult;
        this.namespacesWithModels = new ObjectOpenHashSet<>(ModList.get().size());
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            this.namespacesWithModels.add(id.getNamespace());
        }
        for (Identifier id : bakingResult.itemStackModels().keySet()) {
            this.namespacesWithModels.add(id.getNamespace());
        }
        this.dependencyGraph = buildDependencyGraph();
    }

    private MutableGraph<String> buildDependencyGraph() {
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
                    if(!Objects.equals(id, version.getModId()) && !version.getModId().equals("minecraft") && namespacesWithModels.contains(version.getModId()))
                        dependencyGraph.putEdge(id, version.getModId());
                }
            }
        }
        return dependencyGraph;
    }

    private Set<String> computeVisibleModIds(String modId) {
        var set = new ObjectOpenHashSet<>(this.dependencyGraph.adjacentNodes(modId));
        set.add(modId);
        return Set.copyOf(set);
    }

    private static String getBlockStateNamespace(BlockState state) {
        return state.getBlock().builtInRegistryHolder().getKey().identifier().getNamespace();
    }

    /**
     * @param modId the mod that the event is being fired for
     * @return a baking result whose model maps are views for the given mod, writing through to the original maps
     */
    public ModelBakery.BakingResult wrapResult(String modId) {
        var config = MOD_VISIBILITY_CONFIGURATION.getOrDefault(modId, UniverseVisibility.EVERYTHING);
        final Set<String> modIdsToInclude = computeVisibleModIds(modId);
        boolean restricted = config == UniverseVisibility.SELF_AND_DEPS;
        if (restricted) {
            ModernFix.LOGGER.debug("Mod {} is restricted to seeing models from mods: [{}]", modId, String.join(", ", modIdsToInclude));
        }
        var missingModels = this.bakingResult.missingModels();
        return new ModelBakery.BakingResult(
                missingModels,
                new EmulatedModelRegistry<>(modId, this.bakingResult.blockStateModels(), BlockState.class, ModelBakeEventHelper::getBlockStateNamespace, modIdsToInclude, restricted, missingModels.block()),
                new EmulatedModelRegistry<>(modId, this.bakingResult.itemStackModels(), Identifier.class, Identifier::getNamespace, modIdsToInclude, restricted, missingModels.item()),
                this.bakingResult.itemProperties(),
                this.bakingResult.standaloneModels());
    }

    private static class EmulatedModelRegistry<K, V> extends ForwardingInclDefaultsMap<K, V> {
        private final String modId;
        private final Map<K, V> modelRegistry;
        private final Class<K> keyClass;
        private final Function<K, String> namespaceGetter;
        private final Set<String> modIdsToInclude;
        private final Set<K> ourModelLocations;
        private final V missingModel;

        private EmulatedModelRegistry(String modId, Map<K, V> modelRegistry, Class<K> keyClass, Function<K, String> namespaceGetter, Set<String> modIdsToInclude, boolean restricted, V missingModel) {
            this.modId = modId;
            this.modelRegistry = modelRegistry;
            this.keyClass = keyClass;
            this.namespaceGetter = namespaceGetter;
            this.modIdsToInclude = modIdsToInclude;
            this.ourModelLocations = restricted ? Collections.unmodifiableSet(Sets.filter(modelRegistry.keySet(), this::isFromIncludedMod)) : modelRegistry.keySet();
            this.missingModel = missingModel;
        }

        private boolean isFromIncludedMod(K key) {
            return this.modIdsToInclude.contains(this.namespaceGetter.apply(key));
        }

        private V getOrMissing(K key) {
            V model = this.modelRegistry.get(key);
            return model != null ? model : this.missingModel;
        }

        @Override
        protected Map<K, V> delegate() {
            return this.modelRegistry;
        }

        @Override
        public int size() {
            return this.ourModelLocations.size();
        }

        @Override
        public boolean isEmpty() {
            return this.ourModelLocations.isEmpty();
        }

        @Override
        public V get(@Nullable Object key) {
            V model = super.get(key);
            if(model == null && this.keyClass.isInstance(key) && isFromIncludedMod(this.keyClass.cast(key))) {
                ModernFix.LOGGER.warn("Model {} is missing, but was requested in model bake event. Returning missing model", key);
                return this.missingModel;
            }
            return model;
        }

        @Override
        public Set<K> keySet() {
            return this.ourModelLocations;
        }

        @Override
        public Collection<V> values() {
            return Collections2.transform(this.ourModelLocations, this::getOrMissing);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new DynamicModelEntrySet();
        }

        @Override
        public void forEach(BiConsumer<? super K, ? super V> action) {
            for(K location : this.ourModelLocations) {
                action.accept(location, getOrMissing(location));
            }
        }

        @Override
        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            ModernFix.LOGGER.warn("Mod '{}' is calling replaceAll on the model registry. Some hacks will be used to keep this fast, but they may not be 100% compatible.", this.modId);
            for(K location : this.ourModelLocations) {
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
                    V existing = getOrMissing(location);
                    V replacement = function.apply(location, existing);
                    if(replacement != existing) {
                        put(location, replacement);
                    }
                }
            }
        }

        private class DynamicModelEntrySet extends AbstractSet<Entry<K, V>> {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return Iterators.transform(ourModelLocations.iterator(), DynamicModelEntry::new);
            }

            @Override
            public boolean contains(Object o) {
                return o instanceof Map.Entry<?, ?> entry && ourModelLocations.contains(entry.getKey());
            }

            @Override
            public int size() {
                return ourModelLocations.size();
            }
        }

        private class DynamicModelEntry implements Entry<K, V> {
            private final K location;

            private DynamicModelEntry(K location) {
                this.location = location;
            }

            @Override
            public K getKey() {
                return this.location;
            }

            @Override
            public V getValue() {
                return getOrMissing(this.location);
            }

            @Override
            public V setValue(V value) {
                return put(this.location, value);
            }
        }
    }
}
