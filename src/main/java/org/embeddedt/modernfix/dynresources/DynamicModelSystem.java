package org.embeddedt.modernfix.dynresources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.UnbakedModelParser;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.BlockStateDefinitionsAccessor;
import org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.IdMapperAccessor;
import org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.ModelDiscoveryAccessor;

import java.io.Reader;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class DynamicModelSystem {
    private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
    private static final FileToIdConverter BLOCKSTATE_LISTER = FileToIdConverter.json("blockstates");
    private static final FileToIdConverter ITEM_LISTER = FileToIdConverter.json("items");

    public static final boolean DEBUG_DYNAMIC_MODEL_LOADING = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    private interface ResultLoader<RESOURCE, RESULT> {
        RESULT load(Identifier file, @Nullable RESOURCE resource) throws Exception;
    }

    private static <RESOURCE, RESULT> Map<Identifier, RESULT> createCachedResourceBackedMap(Map<Identifier, RESOURCE> resourceMap,
                                                                                  FileToIdConverter converter,
                                                                                  String debugName,
                                                                                  ResultLoader<RESOURCE, RESULT> loader) {
        LoadingCache<Identifier, RESULT> resultCache = CacheBuilder.newBuilder().softValues().maximumSize(1000).build(new CacheLoader<>() {
            @Override
            public RESULT load(Identifier id) throws Exception {
                var file = converter.idToFile(id);
                var resource = resourceMap.get(file);
                if (DEBUG_DYNAMIC_MODEL_LOADING) {
                    ModernFix.LOGGER.info("Loading {} {}", debugName, id);
                }
                return loader.load(file, resource);
            }
        });
        Set<Identifier> idSet = resourceMap.keySet().stream().map(converter::fileToId).collect(Collectors.toUnmodifiableSet());
        return Maps.asMap(idSet, key -> key != null ? resultCache.getUnchecked(key) : null);
    }
    
    public static Map<Identifier, UnbakedModel> createDynamicUnbakedModelMap(Map<Identifier, Resource> resourceMap) {
        return createCachedResourceBackedMap(resourceMap, MODEL_LISTER, "unbaked model", (id, resource) -> {
            Objects.requireNonNull(resource, "unbaked model not present");
            try (Reader reader = resource.openAsReader()) {
                return UnbakedModelParser.parse(reader);
            }
        });
    }

    public interface SingleBlockStateEntryLoader {
        BlockStateModelLoader.LoadedModels loadEntry(Identifier identifier, List<Resource> blockstateResources);
    }

    public static Set<BlockState> getAllBlockStates() {
        var blockStateSet = ((IdMapperAccessor<BlockState>) Block.BLOCK_STATE_REGISTRY).getReferenceMap().keySet();
        return new AbstractSet<>() {
            @Override
            public Iterator<BlockState> iterator() {
                // We explicitly override iterator() and handle it differently so that mods iterating the maps
                // are likely to work with the same block many times in a row, which hits our caches better
                return BuiltInRegistries.BLOCK.stream().flatMap(b -> b.getStateDefinition().getPossibleStates().stream()).iterator();
            }

            @Override
            public boolean contains(Object o) {
                return blockStateSet.contains(o);
            }

            @Override
            public int size() {
                return blockStateSet.size();
            }
        };
    }

    public static BlockStateModelLoader.LoadedModels createDynamicBlockStateLoadedModels(Map<Identifier, List<Resource>> resourceMap, SingleBlockStateEntryLoader entryLoader) {
        var blockStateDefinitions = createCachedResourceBackedMap(resourceMap, BLOCKSTATE_LISTER, "blockstate definition", entryLoader::loadEntry);
        var staticDefinitions = BlockStateDefinitionsAccessor.getStaticDefinitions();
        var staticIdentifiers = staticDefinitions.entrySet()
                .stream()
                .flatMap(e -> e.getValue().getPossibleStates().stream().map(s -> Map.entry(s, e.getKey())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var blockStateSet = new DisjointSetUnion<>(getAllBlockStates(), staticIdentifiers.keySet());
        return new BlockStateModelLoader.LoadedModels(Maps.asMap(blockStateSet, state -> {
            var identifier = staticIdentifiers.get(state);
            if (identifier == null) {
                identifier = state.getBlock().builtInRegistryHolder().getKey().identifier();
            }
            var loadedModels = blockStateDefinitions.get(identifier);
            return loadedModels.models().get(state);
        }));
    }

    public interface SingleClientItemEntryLoader {
        @Nullable ClientItem loadEntry(Identifier resourceId, Resource resource);
    }

    public static ClientItemInfoLoader.LoadedClientInfos createDynamicClientInfos(Map<Identifier, Resource> resourceMap, SingleClientItemEntryLoader entryLoader) {
        var clientItems = createCachedResourceBackedMap(resourceMap, ITEM_LISTER, "client item info", entryLoader::loadEntry);
        return new ClientItemInfoLoader.LoadedClientInfos(clientItems);
    }

    public record DynamicResolver(Map<Identifier, UnbakedModel> inputModels,
                                          BlockStateModelLoader.LoadedModels loadedModels,
                                          ClientItemInfoLoader.LoadedClientInfos loadedClientInfos,
                                          StandaloneModelLoader.LoadedModels standaloneModels) {

        private ResolvedModel resolveModel(Identifier id) {
            var discovery = new ModelDiscovery(inputModels, MissingCuboidModel.missingModel());
            discovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
            if (!id.equals(ItemModelGenerator.GENERATED_ITEM_MODEL_ID)) {
                UnbakedModel unbaked = inputModels.get(id);
                if (unbaked != null) {
                    var wrapper = discovery.createAndQueueWrapper(id, unbaked);
                    ((ModelDiscoveryAccessor)discovery).mfix$getModelWrappers().put(id, wrapper);
                } else {
                    ModernFix.LOGGER.warn("Cannot find the root model for {}", id);
                }
            }
            var resolved = discovery.resolve();
            return resolved.getOrDefault(id, discovery.missingModel());
        }

        public ModelManager.ResolvedModels resolvedModels() {
            var resolvedMissingModel = new ModelDiscovery(inputModels, MissingCuboidModel.missingModel()).missingModel();
            LoadingCache<Identifier, ResolvedModel> resolvedModelCache = CacheBuilder.newBuilder().softValues().maximumSize(1000).build(new CacheLoader<>() {
                @Override
                public ResolvedModel load(Identifier key) {
                    return resolveModel(key);
                }
            });
            return new ModelManager.ResolvedModels(resolvedMissingModel, Maps.asMap(inputModels.keySet(), resolvedModelCache::getUnchecked));
        }
    }

    public static class BlockGroupingMap extends AbstractObject2IntMap<BlockState> {
        private final BlockColors blockColors;
        private final BlockStateModelLoader.LoadedModels loadedModels;

        record GroupKey(Object equalityGroup, List<Object> coloringValues) {}

        private final Object2IntMap<GroupKey> groupKeyToId;

        public BlockGroupingMap(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
            this.blockColors = blockColors;
            this.loadedModels = loadedModels;
            this.groupKeyToId = new Object2IntOpenHashMap<>();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public ObjectSet<Entry<BlockState>> object2IntEntrySet() {
            return ObjectSets.emptySet();
        }

        @Override
        public int getInt(Object key) {
            // TODO: Implement
            return -1;
        }
    }

    private static final Object NULL_BAKED = new Object();

    public static <K, U, V> Map<K, V> createDynamicBakedRegistry(Map<K, U> input, BiFunction<K, U, V> baker) {
        // TODO: support persistence of overrides
        LoadingCache<K, Object> bakedCache = CacheBuilder.newBuilder().softValues().maximumSize(1000).build(new CacheLoader<>() {
            @Override
            public Object load(K key) throws Exception {
                var unbaked = input.get(key);
                if (unbaked == null) {
                    return NULL_BAKED;
                }

                if (DEBUG_DYNAMIC_MODEL_LOADING) {
                    ModernFix.LOGGER.info("Baking {}", key);
                }

                var bakerResult = baker.apply(key, unbaked);
                if (bakerResult == null) {
                    ModernFix.LOGGER.warn("Baker has returned null for {}", key);
                    return NULL_BAKED;
                }

                return bakerResult;
            }
        });
        return new DynamicRegistryMap<>(input.keySet(), k -> {
            if (k != null) {
                Object value = bakedCache.getUnchecked(k);
                if (value == NULL_BAKED) {
                    value = null;
                }
                return (V) value;
            } else {
                return null;
            }
        });
    }
}
