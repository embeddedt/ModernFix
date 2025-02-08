package org.embeddedt.modernfix.dynamicresources;

import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.UnbakedBlockStateModel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MissingBlockModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handles loading models dynamically, rather than at startup time.
 */
public class DynamicModelProvider {
    private final LoadingCache<ResourceLocation, Optional<BlockStateModelLoader.LoadedModels>> loadedStateDefinitions =
            this.makeLoadingCache(this::loadBlockStateDefinition);

    private final LoadingCache<ResourceLocation, Optional<UnbakedModel>> loadedBlockModels =
            this.makeLoadingCache(this::loadBlockModel);

    private final LoadingCache<ModelResourceLocation, Optional<BakedModel>> loadedBakedModels =
            this.makeLoadingCache(this::loadBakedModel);

    private final LoadingCache<ResourceLocation, Optional<ClientItem>> loadedClientItemProperties =
            this.makeLoadingCache(this::loadClientItemProperties);

    private final LoadingCache<ResourceLocation, Optional<ItemModel>> loadedItemModels =
           this.makeLoadingCache(this::loadItemModel);

    private final LoadingCache<ResourceLocation, Optional<BakedModel>> loadedStandaloneModels =
            this.makeLoadingCache(this::loadStandaloneModel);

    private final BakedModel missingModel;
    private final ItemModel missingItemModel;
    private final UnbakedModel unbakedMissingModel;
    private final Function<ResourceLocation, StateDefinition<Block, BlockState>> stateMapper;
    private final ResourceManager resourceManager;
    private final ModelBakery.TextureGetter textureGetter;
    private final DynamicResolver resolver;
    private final EntityModelSet entityModelSet;
    private final ItemModelGenerator itemModelGenerator;

    private final Map<ModelResourceLocation, BakedModel> mrlModelOverrides = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, ItemModel> itemStackModelOverrides = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, BakedModel> standaloneModelOverrides = new ConcurrentHashMap<>();
    private final Map<ModelResourceLocation, UnbakedBlockStateModel> unbakedBlockStateModelOverrides = new ConcurrentHashMap<>();

    private final List<DynamicModelProvider.DynamicModelPlugin> pluginList = new ArrayList<>();

    private static final boolean DEBUG_DYNAMIC_MODEL_LOADING = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    public DynamicModelProvider(ResourceManager resourceManager, EntityModelSet entityModelSet,
                                Map<ResourceLocation, AtlasSet.StitchResult> atlasMap) {
        this.unbakedMissingModel = MissingBlockModel.missingModel();
        this.entityModelSet = entityModelSet;
        var missing = atlasMap.get(TextureAtlas.LOCATION_BLOCKS).missing();
        this.textureGetter = new ModelBakery.TextureGetter() {
            @Override
            public TextureAtlasSprite get(ModelDebugName modelDebugName, Material material) {
                var atlas = atlasMap.get(material.atlasLocation());
                var sprite = atlas.getSprite(material.texture());
                if (sprite != null) {
                    return sprite;
                } else {
                    ModernFix.LOGGER.warn("Unable to find sprite '{}' referenced by model '{}'", material.texture(), modelDebugName.get());
                    return missing;
                }
            }

            @Override
            public TextureAtlasSprite reportMissingReference(ModelDebugName modelDebugName, String string) {
                return missing;
            }
        };
        this.stateMapper = BlockStateModelLoader.definitionLocationToBlockMapper();
        this.resourceManager = resourceManager;
        this.resolver = new DynamicResolver();
        this.itemModelGenerator = new ItemModelGenerator();
        this.missingModel = this.bakeMissingModel();
        this.missingItemModel = new MissingItemModel(this.missingModel);
        try {
            Class.forName("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin");
            pluginList.add(new FabricDynamicModelHandler(this, this.resourceManager));
        } catch(Exception ignored) {
            // Fabric API likely not present
        }
    }

    public BakedModel getMissingBakedModel() {
        return this.missingModel;
    }

    public ItemModel getMissingItemModel() {
        return this.missingItemModel;
    }

    private static final Supplier<Set<ModelResourceLocation>> TOP_LEVEL_LOCATIONS_SUPPLIER = Suppliers.memoizeWithExpiration(() -> {
        Set<ModelResourceLocation> set = new HashSet<>();
        // Skip going through ModelLocationCache because most of the accesses will be misses
        BuiltInRegistries.BLOCK.entrySet().forEach(entry -> {
            var location = entry.getKey().location();
            for(BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                set.add(BlockModelShaper.stateToModelLocation(location, state));
            }
        });
        return Collections.unmodifiableSet(set);
    }, 2, TimeUnit.MINUTES);

    public Map<ModelResourceLocation, BakedModel> getTopLevelEmulatedRegistry() {
        return new EmulatedRegistry<>(ModelResourceLocation.class, this.loadedBakedModels, TOP_LEVEL_LOCATIONS_SUPPLIER, this.mrlModelOverrides);
    }

    public Map<ResourceLocation, BakedModel> getStandaloneEmulatedRegistry() {
        return new EmulatedRegistry<>(ResourceLocation.class, this.loadedStandaloneModels, Set::of, this.standaloneModelOverrides);
    }

    public Map<ResourceLocation, ItemModel> getItemModelEmulatedRegistry() {
        return new EmulatedRegistry<>(ResourceLocation.class, this.loadedItemModels, BuiltInRegistries.ITEM::keySet, this.itemStackModelOverrides);
    }

    public Map<ResourceLocation, ClientItem.Properties> getItemPropertiesEmulatedRegistry() {
        return Maps.transformValues(new EmulatedRegistry<>(ResourceLocation.class, this.loadedClientItemProperties, BuiltInRegistries.ITEM::keySet, Map.of()), ClientItem::properties);
    }

    private <K, V> LoadingCache<K, Optional<V>> makeLoadingCache(Function<K, Optional<V>> loadingFunction) {
        return CacheBuilder.newBuilder()
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .maximumSize(1000)
                .concurrencyLevel(8)
                .softValues()
                .build(new CacheLoader<>() {
                    @Override
                    public Optional<V> load(K key) {
                        return loadingFunction.apply(key);
                    }
                });
    }

    private static class EmulatedRegistry<K, V> implements Map<K, V> {
        private final LoadingCache<K, Optional<V>> realCache;
        private final Supplier<Set<K>> keys;
        private final Map<K, V> overrides;
        private final Class<K> keyClass;

        public EmulatedRegistry(Class<K> keyClass, LoadingCache<K, Optional<V>> realCache, Supplier<Set<K>> keys, Map<K, V> overrides) {
            this.keyClass = keyClass;
            this.realCache = realCache;
            this.keys = keys;
            this.overrides = overrides;
        }

        @Override
        public V get(Object key) {
            if (this.keyClass.isAssignableFrom(key.getClass())) {
                return this.realCache.getUnchecked((K)key).orElse(null);
            } else {
                return null;
            }
        }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            if (this.keyClass.isAssignableFrom(key.getClass())) {
                return this.realCache.getUnchecked((K)key).orElse(defaultValue);
            } else {
                return defaultValue;
            }
        }

        @Override
        public V put(K key, V value) {
            V oldValue = this.realCache.getUnchecked(key).orElse(null);
            this.overrides.put(key, value);
            this.realCache.invalidate(key);
            return oldValue;
        }

        @Override
        public V remove(Object key) {
            this.overrides.remove(key);
            this.realCache.invalidate(key);
            return null;
        }

        @Override
        public void putAll(@NotNull Map<? extends K, ? extends V> m) {
            m.forEach(this::put);
        }

        @Override
        public void clear() {
            this.overrides.clear();
            this.realCache.invalidateAll();
        }

        @Override
        public @NotNull Set<K> keySet() {
            return keys.get();
        }

        @Override
        public @NotNull Collection<V> values() {
            return Collections.emptyList();
        }

        @Override
        public int size() {
            return keys.get().size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(Object key) {
            return keys.get().contains(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return false;
        }

        @Override
        public @NotNull Set<Entry<K, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return Iterators.transform(keys.get().iterator(), key -> new Entry<>() {
                        @Override
                        public K getKey() {
                            return key;
                        }

                        @Override
                        public V getValue() {
                            return get(key);
                        }

                        @Override
                        public V setValue(V value) {
                            return put(key, value);
                        }
                    });
                }

                @Override
                public int size() {
                    return keys.get().size();
                }
            };
        }

        @Override
        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            for(K location : keys.get()) {
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
                    V existing = get(location);
                    V replacement = function.apply(location, existing);
                    if(replacement != existing) {
                        put(location, replacement);
                    }
                }
            }
        }
    }

    private Optional<BlockStateModelLoader.LoadedModels> loadBlockStateDefinition(ResourceLocation location) {
        StateDefinition<Block, BlockState> stateDefinition = this.stateMapper.apply(location);
        if(stateDefinition == null) {
            return Optional.empty();
        }
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Loading blockstate definition '{}'", location);
        }
        List<Resource> resources = resourceManager.getResourceStack(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "blockstates/" + location.getPath() + ".json"));
        List<BlockStateModelLoader.LoadedBlockModelDefinition> loadedDefinitions = new ArrayList<>(resources.size());
        for(Resource resource : resources) {
            try(Reader reader = resource.openAsReader()) {
                JsonObject jsonObject = GsonHelper.parse(reader);
                BlockModelDefinition blockModelDefinition = BlockModelDefinition.fromJsonElement(jsonObject);
                loadedDefinitions.add(new BlockStateModelLoader.LoadedBlockModelDefinition(resource.sourcePackId(), blockModelDefinition));
            } catch(Exception e) {
                ModernFix.LOGGER.error("Failed to load blockstate definition {} from pack '{}'", location, resource.sourcePackId(), e);
            }
        }
        var loadedModels = new HashMap<>(BlockStateModelLoader.loadBlockStateDefinitionStack(location, stateDefinition, loadedDefinitions, this.unbakedMissingModel).models());
        if (!pluginList.isEmpty()) {
            loadedModels.replaceAll((mrl, oldModel) -> {
                UnbakedBlockStateModel ubm = oldModel.model();
                for (var plugin : pluginList) {
                    ubm = plugin.modifyBlockModelOnLoad(ubm, mrl, oldModel.state());
                }
                if (ubm == oldModel.model()) {
                    return oldModel;
                } else {
                    return new BlockStateModelLoader.LoadedModel(oldModel.state(), ubm);
                }
            });
        }
        return Optional.of(new BlockStateModelLoader.LoadedModels(loadedModels));
    }

    private BakedModel bakeMissingModel() {
        this.resolver.clearResolver();
        this.unbakedMissingModel.resolveDependencies(this.resolver);
        var modelBaker = new DynamicBaker(() -> "missing");
        return UnbakedModel.bakeWithTopModelValues(this.unbakedMissingModel, modelBaker, BlockModelRotation.X0_Y0);
    }

    private BakedModel bakeModel(UnbakedModel model, ResourceLocation location) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Baking model '{}'", location);
        }
        synchronized (this) {
            this.resolver.clearResolver();
            model.resolveDependencies(this.resolver);
            var modelBaker = new DynamicBaker(location::toString);
            for (var plugin : pluginList) {
                model = plugin.modifyModelBeforeBake(model, location, BlockModelRotation.X0_Y0, modelBaker);
            }
            var bakedModel = UnbakedModel.bakeWithTopModelValues(model, modelBaker, BlockModelRotation.X0_Y0);
            for (var plugin : pluginList) {
                bakedModel = plugin.modifyModelAfterBake(bakedModel, model, location, BlockModelRotation.X0_Y0, modelBaker);
            }
            return bakedModel;
        }
    }

    private BakedModel bakeModel(UnbakedBlockStateModel model, ModelResourceLocation mrl) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Baking model '{}'", mrl);
        }
        synchronized (this) {
            this.resolver.clearResolver();
            model.resolveDependencies(this.resolver);
            var modelBaker = new DynamicBaker(mrl::toString);
            for (var plugin : pluginList) {
                model = plugin.modifyBlockModelBeforeBake(model, mrl, modelBaker);
            }
            var bakedModel = model.bake(modelBaker);
            for (var plugin : pluginList) {
                bakedModel = plugin.modifyBlockModelAfterBake(bakedModel, model, mrl, modelBaker);
            }
            return bakedModel;
        }
    }

    private Optional<BakedModel> loadBakedModel(ModelResourceLocation location) {
        var override = this.mrlModelOverrides.get(location);
        if (override != null) {
            return Optional.of(override);
        }
        if (location.variant().equals("standalone") || location.variant().equals("fabric_resource")) {
            return this.loadStandaloneModel(location.id());
        } else {
            Optional<UnbakedBlockStateModel> unbakedModelOpt = Optional.ofNullable(this.unbakedBlockStateModelOverrides.get(location));
            if (unbakedModelOpt.isEmpty()) {
                var optLoadedModels = this.loadedStateDefinitions.getUnchecked(location.id());
                unbakedModelOpt = optLoadedModels.map(loadedModels -> {
                    var loadedModel = loadedModels.models().get(location);
                    if(loadedModel != null) {
                        return loadedModel.model();
                    } else {
                        return null;
                    }
                });
            }
            return unbakedModelOpt.map(unbakedModel -> {
                return this.bakeModel(unbakedModel, location);
            });
        }
    }

    private Optional<BakedModel> loadStandaloneModel(ResourceLocation location) {
        var override = this.standaloneModelOverrides.get(location);
        if (override != null) {
            return Optional.of(override);
        }
        return this.loadedBlockModels.getUnchecked(location).map(unbakedModel -> {
            return this.bakeModel(unbakedModel, location);
        });
    }

    private Optional<UnbakedModel> loadBlockModelDefault(ResourceLocation location) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Loading block model '{}'", location);
        }
        if (location.equals(ItemModelGenerator.GENERATED_ITEM_MODEL_ID)) {
            return Optional.of(this.itemModelGenerator);
        } else if (location.equals(MissingBlockModel.LOCATION)) {
            return Optional.of(this.unbakedMissingModel);
        }
        var resource = this.resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "models/" + location.getPath() + ".json"));
        if(resource.isPresent()) {
            try(Reader reader = resource.get().openAsReader()) {
                BlockModel blockModel = BlockModel.fromStream(reader);
                return Optional.of(blockModel);
            } catch(Exception e) {
                ModernFix.LOGGER.error("Failed to load block model {} from '{}'", location, resource.get().sourcePackId(), e);
                return Optional.empty();
            }
        } else {
            ModernFix.LOGGER.warn("Model '{}' does not exist in any resource packs", location);
            return Optional.empty();
        }
    }

    private Optional<UnbakedModel> loadBlockModel(ResourceLocation location) {
        Optional<UnbakedModel> value = loadBlockModelDefault(location);
        for (var plugin : this.pluginList) {
            value = plugin.modifyModelOnLoad(value, location);
        }
        return value;
    }


    private Optional<ClientItem> loadClientItemProperties(ResourceLocation location) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Loading client item '{}'", location);
        }
        var resource = this.resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "items/" + location.getPath() + ".json"));
        if(resource.isPresent()) {
            try(Reader reader = resource.get().openAsReader()) {
                ClientItem clientItem = ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
                return Optional.of(clientItem);
            } catch(Exception e) {
                ModernFix.LOGGER.error("Failed to load client item {} from '{}'", location, resource.get().sourcePackId(), e);
                return Optional.empty();
            }
        } else {
            ModernFix.LOGGER.warn("Client item '{}' does not exist in any resource packs", location);
            return Optional.empty();
        }
    }

    private Optional<ItemModel> loadItemModel(ResourceLocation location) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Loading item model '{}'", location);
        }
        var override = this.itemStackModelOverrides.get(location);
        if (override != null) {
            return Optional.of(override);
        }
        return this.loadedClientItemProperties.getUnchecked(location).map(clientItem -> {
            var bakingContext = new ItemModel.BakingContext(new DynamicBaker(location::toString), this.entityModelSet, this.missingItemModel);
            return clientItem.model().bake(bakingContext);
        });
    }

    public BakedModel getModel(ModelResourceLocation location) {
        return this.loadedBakedModels.getUnchecked(location).orElse(this.missingModel);
    }

    public ClientItem.Properties getClientItemProperties(ResourceLocation location) {
        return this.loadedClientItemProperties.getUnchecked(location).map(ClientItem::properties).orElse(ClientItem.Properties.DEFAULT);
    }

    public ItemModel getItemModel(ResourceLocation location) {
        return this.loadedItemModels.getUnchecked(location).orElse(this.missingItemModel);
    }

    public BakedModel getStandaloneModel(ResourceLocation location) {
        return this.loadedStandaloneModels.getUnchecked(location).orElse(this.missingModel);
    }

    public void addUnbakedBlockStateOverride(ModelResourceLocation location, UnbakedBlockStateModel model) {
        this.unbakedBlockStateModelOverrides.put(location, model);
    }

    private class DynamicBaker implements ModelBaker {
        private final ModelDebugName modelDebugName;

        private DynamicBaker(ModelDebugName modelDebugName) {
            this.modelDebugName = modelDebugName;
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState transform) {
            return DynamicModelProvider.this.loadedBlockModels.getUnchecked(location).map(unbakedModel -> {
                DynamicModelProvider.this.resolver.clearResolver();
                unbakedModel.resolveDependencies(DynamicModelProvider.this.resolver);
                return UnbakedModel.bakeWithTopModelValues(unbakedModel, this, transform);
            }).orElse(DynamicModelProvider.this.missingModel);
        }

        @Override
        public SpriteGetter sprites() {
            return DynamicModelProvider.this.textureGetter.bind(this.modelDebugName);
        }

        @Override
        public ModelDebugName rootName() {
            return this.modelDebugName;
        }
    }

    /**
     * Based on the Mojang impl but with some changes to make it slightly more efficient.
     */
    private class DynamicResolver implements UnbakedModel.Resolver {
        private final Set<ResourceLocation> stack = new ObjectOpenHashSet<>(4);
        private final Set<ResourceLocation> resolvedModels = new ObjectOpenHashSet<>();

        @Override
        public UnbakedModel resolve(ResourceLocation resourceLocation) {
            if (this.stack.contains(resourceLocation)) {
                ModernFix.LOGGER.warn("Detected model loading loop: {}->{}", this.stacktraceToString(), resourceLocation);
                return DynamicModelProvider.this.unbakedMissingModel;
            } else {
                UnbakedModel unbakedModel = DynamicModelProvider.this.loadedBlockModels.getUnchecked(resourceLocation).orElse(DynamicModelProvider.this.unbakedMissingModel);
                if (this.resolvedModels.add(resourceLocation)) {
                    this.stack.add(resourceLocation);
                    unbakedModel.resolveDependencies(this);
                    this.stack.remove(resourceLocation);
                }

                return unbakedModel;
            }
        }

        private String stacktraceToString() {
            return this.stack.stream().map(ResourceLocation::toString).collect(Collectors.joining("->"));
        }

        public void clearResolver() {
            this.stack.clear();
            this.resolvedModels.clear();
        }
    }

    public static WeakReference<DynamicModelProvider> currentReloadingModelProvider = new WeakReference<>(null);

    public interface ModelManagerExtension {
        DynamicModelProvider mfix$getModelProvider();
    }

    public interface DynamicModelPlugin {
        Optional<UnbakedModel> modifyModelOnLoad(Optional<UnbakedModel> model, ResourceLocation id);
        UnbakedBlockStateModel modifyBlockModelOnLoad(UnbakedBlockStateModel model, ModelResourceLocation id, BlockState state);

        UnbakedModel modifyModelBeforeBake(UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker);
        BakedModel modifyModelAfterBake(BakedModel bakedModel, UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker);

        UnbakedBlockStateModel modifyBlockModelBeforeBake(UnbakedBlockStateModel model, ModelResourceLocation id, ModelBaker baker);
        BakedModel modifyBlockModelAfterBake(BakedModel bakedModel, UnbakedBlockStateModel model, ModelResourceLocation id, ModelBaker baker);
    }
}
