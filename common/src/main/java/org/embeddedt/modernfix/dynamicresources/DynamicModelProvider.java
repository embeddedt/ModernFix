package org.embeddedt.modernfix.dynamicresources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MissingBlockModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvedModel;
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

/**
 * Handles loading models dynamically, rather than at startup time.
 */
public class DynamicModelProvider {
    private final LoadingCache<ResourceLocation, Optional<BlockStateModelLoader.LoadedModels>> loadedStateDefinitions =
            this.makeLoadingCache(this::loadBlockStateDefinition);

    private final LoadingCache<ResourceLocation, Optional<UnbakedModel>> loadedBlockModels =
            this.makeLoadingCache(this::loadBlockModel);

    private final LoadingCache<ResourceLocation, Optional<ModelDiscovery.ModelWrapper>> resolvedBlockModels =
            this.makeLoadingCache(this::resolveBlockModel);

    private final LoadingCache<BlockState, Optional<BlockStateModel>> loadedBakedModels =
            this.makeLoadingCache(this::loadBakedModel);

    private final LoadingCache<ResourceLocation, Optional<ClientItem>> loadedClientItemProperties =
            this.makeLoadingCache(this::loadClientItemProperties);

    private final LoadingCache<ResourceLocation, Optional<ItemModel>> loadedItemModels =
           this.makeLoadingCache(this::loadItemModel);

    /*
    private final LoadingCache<ResourceLocation, Optional<BakedModel>> loadedStandaloneModels =
            this.makeLoadingCache(this::loadStandaloneModel);

     */

    private final BlockStateModel missingModel;
    private final ModelDiscovery.ModelWrapper resolvedMissingModel;
    private final ItemModel missingItemModel;
    private final UnbakedModel unbakedMissingModel;
    private final Function<ResourceLocation, StateDefinition<Block, BlockState>> stateMapper;
    private final ResourceManager resourceManager;
    private final SpriteGetter textureGetter;
    private final EntityModelSet entityModelSet;
    private final ItemModelGenerator itemModelGenerator;

    private final Map<BlockState, BlockStateModel> mrlModelOverrides = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, ItemModel> itemStackModelOverrides = new ConcurrentHashMap<>();
    //private final Map<ResourceLocation, BakedModel> standaloneModelOverrides = new ConcurrentHashMap<>();
    private final Map<BlockState, BlockStateModel.Unbaked> unbakedBlockStateModelOverrides = new ConcurrentHashMap<>();

    private final List<DynamicModelProvider.DynamicModelPlugin> pluginList = new ArrayList<>();

    private static final boolean DEBUG_DYNAMIC_MODEL_LOADING = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    public DynamicModelProvider(ResourceManager resourceManager, EntityModelSet entityModelSet,
                                Map<ResourceLocation, AtlasSet.StitchResult> atlasMap) {
        this.unbakedMissingModel = MissingBlockModel.missingModel();
        this.entityModelSet = entityModelSet;
        var missing = atlasMap.get(TextureAtlas.LOCATION_BLOCKS).missing();
        this.textureGetter = new SpriteGetter() {
            @Override
            public TextureAtlasSprite get(Material material, ModelDebugName modelDebugName) {
                var atlas = atlasMap.get(material.atlasLocation());
                var sprite = atlas.getSprite(material.texture());
                if (sprite != null) {
                    return sprite;
                } else {
                    ModernFix.LOGGER.warn("Unable to find sprite '{}' referenced by model '{}'", material.texture(), modelDebugName.debugName());
                    return missing;
                }
            }

            @Override
            public TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName) {
                return missing;
            }
        };
        this.stateMapper = BlockStateDefinitions.definitionLocationToBlockStateMapper();
        this.resourceManager = resourceManager;
        this.itemModelGenerator = new ItemModelGenerator();
        this.resolvedMissingModel = new ModelDiscovery.ModelWrapper(MissingBlockModel.LOCATION, this.unbakedMissingModel, true);
        var missingModelBaker = new ModelBaker() {
            @Override
            public ResolvedModel getModel(ResourceLocation resourceLocation) {
                throw new IllegalStateException("Missing model should not have dependencies");
            }

            @Override
            public SpriteGetter sprites() {
                return DynamicModelProvider.this.textureGetter;
            }
        };
        var textureSlots = this.resolvedMissingModel.getTopTextureSlots();
        var quadCollection = this.resolvedMissingModel.bakeTopGeometry(textureSlots, missingModelBaker, BlockModelRotation.X0_Y0);
        var particleSprite = this.resolvedMissingModel.resolveParticleSprite(textureSlots, missingModelBaker);
        this.missingModel = new SimpleModelWrapper(quadCollection, resolvedMissingModel.getTopAmbientOcclusion(), particleSprite);
        this.missingItemModel = new MissingItemModel(quadCollection.getAll(), new ModelRenderProperties(resolvedMissingModel.getTopGuiLight().lightLikeBlock(), particleSprite, resolvedMissingModel.getTopTransforms()));
        try {
            Class.forName("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin");
            // TODO
            //pluginList.add(new FabricDynamicModelHandler(this, this.resourceManager));
        } catch(Exception ignored) {
            // Fabric API likely not present
        }
    }

    public BlockStateModel getMissingBakedModel() {
        return this.missingModel;
    }

    public ItemModel getMissingItemModel() {
        return this.missingItemModel;
    }

    public Map<BlockState, BlockStateModel> getTopLevelEmulatedRegistry() {
        return new EmulatedRegistry<>(BlockState.class, this.loadedBakedModels, BlockStateSet::instance, this.mrlModelOverrides);
    }

    /*
    public Map<ResourceLocation, BakedModel> getStandaloneEmulatedRegistry() {
        return new EmulatedRegistry<>(ResourceLocation.class, this.loadedStandaloneModels, Set::of, this.standaloneModelOverrides);
    }
     */

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
                BlockModelDefinition blockModelDefinition = BlockModelDefinition.CODEC.decode(JsonOps.INSTANCE, jsonObject).getOrThrow().getFirst();
                loadedDefinitions.add(new BlockStateModelLoader.LoadedBlockModelDefinition(resource.sourcePackId(), blockModelDefinition));
            } catch(Exception e) {
                ModernFix.LOGGER.error("Failed to load blockstate definition {} from pack '{}'", location, resource.sourcePackId(), e);
            }
        }
        var loadedModels = new HashMap<>(BlockStateModelLoader.loadBlockStateDefinitionStack(location, stateDefinition, loadedDefinitions).models());
        if (!pluginList.isEmpty()) {
            loadedModels.replaceAll((mrl, oldModel) -> {
                BlockStateModel.Unbaked ubm = oldModel;
                for (var plugin : pluginList) {
                    ubm = plugin.modifyBlockModelOnLoad(oldModel, mrl);
                }
                return ubm;
            });
        }
        return Optional.of(new BlockStateModelLoader.LoadedModels(loadedModels));
    }

    private BlockStateModel bakeModel(BlockStateModel.Unbaked model, BlockState mrl) {
        if (DEBUG_DYNAMIC_MODEL_LOADING) {
            ModernFix.LOGGER.info("Baking model '{}'", mrl);
        }
        synchronized (this) {
            model.resolveDependencies(dep -> {});
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

    private Optional<BlockStateModel> loadBakedModel(BlockState state) {
        var override = this.mrlModelOverrides.get(state);
        if (override != null) {
            return Optional.of(override);
        }
        if (false) { //location.variant().equals("standalone") || location.variant().equals("fabric_resource")) {
            throw new UnsupportedOperationException(); //return this.loadStandaloneModel(location.id());
        } else {
            Optional<BlockStateModel.Unbaked> unbakedModelOpt = Optional.ofNullable(this.unbakedBlockStateModelOverrides.get(state));
            if (unbakedModelOpt.isEmpty()) {
                var optLoadedModels = this.loadedStateDefinitions.getUnchecked(state.getBlock().builtInRegistryHolder().key().location());
                unbakedModelOpt = optLoadedModels.map(loadedModels -> loadedModels.models().get(state));
            }
            return unbakedModelOpt.map(unbakedModel -> {
                return this.bakeModel(unbakedModel, state);
            });
        }
    }

    /*
    private Optional<BakedModel> loadStandaloneModel(ResourceLocation location) {
        var override = this.standaloneModelOverrides.get(location);
        if (override != null) {
            return Optional.of(override);
        }
        return this.loadedBlockModels.getUnchecked(location).map(unbakedModel -> {
            return this.bakeModel(unbakedModel, location);
        });
    }
     */

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

    private Optional<ModelDiscovery.ModelWrapper> resolveBlockModel(ResourceLocation location) {
        var unbakedOpt = this.loadedBlockModels.getUnchecked(location);
        if (unbakedOpt.isEmpty()) {
            return Optional.empty();
        }
        ModelDiscovery.ModelWrapper wrapper = new ModelDiscovery.ModelWrapper(location, unbakedOpt.get(), true);
        var parent = wrapper.wrapped().parent();
        if (parent != null) {
            Optional<ModelDiscovery.ModelWrapper> resolvedParentOpt;
            try {
                resolvedParentOpt = this.resolvedBlockModels.getUnchecked(parent);
            } catch (Exception e) {
                // Possible recursive load, etc.
                ModernFix.LOGGER.error("Error while resolving model '{}'", location, e);
                return Optional.empty();
            }
            if (resolvedParentOpt.isPresent()) {
                wrapper.parent = resolvedParentOpt.get();
            }
        }
        return Optional.of(wrapper);
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
            var bakingContext = new ItemModel.BakingContext(new DynamicBaker(location::toString), this.entityModelSet, this.missingItemModel, clientItem.registrySwapper());
            return clientItem.model().bake(bakingContext);
        });
    }

    public BlockStateModel getModel(BlockState location) {
        return this.loadedBakedModels.getUnchecked(location).orElse(this.missingModel);
    }

    public ClientItem.Properties getClientItemProperties(ResourceLocation location) {
        return this.loadedClientItemProperties.getUnchecked(location).map(ClientItem::properties).orElse(ClientItem.Properties.DEFAULT);
    }

    public ItemModel getItemModel(ResourceLocation location) {
        return this.loadedItemModels.getUnchecked(location).orElse(this.missingItemModel);
    }

    /*
    public BakedModel getStandaloneModel(ResourceLocation location) {
        return this.loadedStandaloneModels.getUnchecked(location).orElse(this.missingModel);
    }

     */

    public void addUnbakedBlockStateOverride(BlockState location, BlockStateModel.Unbaked model) {
        this.unbakedBlockStateModelOverrides.put(location, model);
    }

    private class DynamicBaker implements ModelBaker {
        private final ModelDebugName modelDebugName;

        private DynamicBaker(ModelDebugName modelDebugName) {
            this.modelDebugName = modelDebugName;
        }

        @Override
        public ResolvedModel getModel(ResourceLocation location) {
            return DynamicModelProvider.this.resolvedBlockModels.getUnchecked(location).orElse(DynamicModelProvider.this.resolvedMissingModel);
        }

        @Override
        public SpriteGetter sprites() {
            return DynamicModelProvider.this.textureGetter;
        }
    }

    public static WeakReference<DynamicModelProvider> currentReloadingModelProvider = new WeakReference<>(null);

    public interface ModelManagerExtension {
        DynamicModelProvider mfix$getModelProvider();
    }

    public interface DynamicModelPlugin {
        Optional<UnbakedModel> modifyModelOnLoad(Optional<UnbakedModel> model, ResourceLocation id);
        BlockStateModel.Unbaked modifyBlockModelOnLoad(BlockStateModel.Unbaked model, BlockState state);

        UnbakedModel modifyModelBeforeBake(UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker);
        //BakedModel modifyModelAfterBake(BakedModel bakedModel, UnbakedModel model, ResourceLocation id, ModelState state, ModelBaker baker);

        BlockStateModel.Unbaked modifyBlockModelBeforeBake(BlockStateModel.Unbaked model, BlockState state, ModelBaker baker);
        BlockStateModel modifyBlockModelAfterBake(BlockStateModel bakedModel, BlockStateModel.Unbaked unbaked, BlockState state, ModelBaker baker);
    }
}
