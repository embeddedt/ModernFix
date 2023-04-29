package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistryEntry;
import net.minecraftforge.resource.DelegatingResourcePack;
import net.minecraftforge.resource.PathResourcePack;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* high priority so that our injectors are added before other mods' */
@Mixin(value = ModelBakery.class, priority = 600)
public abstract class ModelBakeryMixin implements IExtendedModelBakery {

    private static final boolean debugDynamicModelLoading = Boolean.getBoolean("modernfix.debugDynamicModelLoading");

    @Shadow @Final @Mutable public Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow protected abstract BlockModel loadBlockModel(ResourceLocation location) throws IOException;

    @Shadow @Final protected static Set<Material> UNREFERENCED_TEXTURES;
    @Shadow private Map<ResourceLocation, Pair<TextureAtlas, TextureAtlas.Preparations>> atlasPreparations;
    @Shadow @Final protected ResourceManager resourceManager;
    @Shadow @Nullable private AtlasSet atlasSet;
    @Shadow @Final private Set<ResourceLocation> loadingStack;

    @Shadow protected abstract void loadModel(ResourceLocation blockstateLocation) throws Exception;

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private static Splitter COMMA_SPLITTER;
    @Shadow @Final private static Splitter EQUAL_SPLITTER;
    @Shadow @Nullable static <T extends Comparable<T>> T getValueHelper(Property<T> property, String value) {
        throw new AssertionError();
    }

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;

    @Shadow @Final public static BlockModel GENERATION_MARKER;

    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;

    @Shadow @Final public static BlockModel BLOCK_ENTITY_MARKER;

    @Shadow public abstract UnbakedModel getModel(ResourceLocation modelLocation);

    @Shadow @Nullable public abstract BakedModel bake(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> sprites);

    private Cache<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> loadedBakedModels;
    private Cache<ResourceLocation, UnbakedModel> loadedModels;

    private HashMap<ResourceLocation, UnbakedModel> smallLoadingCache = new HashMap<>();


    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/color/block/BlockColors;Z)V", at = @At("RETURN"))
    private void replaceTopLevelBakedModels(ResourceManager manager, BlockColors colors, boolean vanillaBakery, CallbackInfo ci) {
        this.loadedBakedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .maximumSize(1000)
                .concurrencyLevel(8)
                .removalListener(this::onModelRemoved)
                .softValues()
                .build();
        this.loadedModels = CacheBuilder.newBuilder()
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .maximumSize(1000)
                .concurrencyLevel(8)
                .removalListener(this::onModelRemoved)
                .softValues()
                .build();
        this.bakedCache = loadedBakedModels.asMap();
        this.unbakedCache = loadedModels.asMap();
        this.bakedTopLevelModels = new DynamicBakedModelProvider((ModelBakery)(Object)this, bakedCache);
    }

    private <K, V> void onModelRemoved(RemovalNotification<K, V> notification) {
        if(!debugDynamicModelLoading)
            return;
        Object k = notification.getKey();
        if(k == null)
            return;
        ResourceLocation rl;
        boolean baked = false;
        if(k instanceof ResourceLocation) {
            rl = (ResourceLocation)k;
        } else {
            rl = ((Triple<ResourceLocation, Transformation, Boolean>)k).getLeft();
            baked = true;
        }
        ModernFix.LOGGER.warn("Evicted {} model {}", baked ? "baked" : "unbaked", rl);
    }

    private UnbakedModel missingModel;

    private Set<ResourceLocation> blockStateFiles;
    private Set<ResourceLocation> modelFiles;

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBakery;loadBlockModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/BlockModel;", ordinal = 0))
    private BlockModel captureMissingModel(ModelBakery bakery, ResourceLocation location) throws IOException {
        this.missingModel = this.loadBlockModel(location);
        this.blockStateFiles = new HashSet<>();
        this.modelFiles = new HashSet<>();
        return (BlockModel)this.missingModel;
    }

    /**
     * @author embeddedt
     * @reason don't actually load the model. instead, keep track of if we need to load a blockstate or a model,
     * and save the info into the two lists
     */
    @Inject(method = "loadTopLevel", at = @At("HEAD"), cancellable = true)
    private void addTopLevelFile(ModelResourceLocation location, CallbackInfo ci) {
        ci.cancel();
        if(Objects.equals(location.getVariant(), "inventory")) {
            modelFiles.add(new ResourceLocation(location.getNamespace(), "item/" + location.getPath()));
        } else {
            blockStateFiles.add(new ResourceLocation(location.getNamespace(), location.getPath()));
        }
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;gatherFluidTextures(Ljava/util/Set;)V", remap = false), remap = false)
    private void gatherModelTextures(Set<Material> materialSet) {
        ForgeHooksClient.gatherFluidTextures(materialSet);
        gatherModelMaterials(materialSet);
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 0), remap = false)
    private void fetchStaticDefinitions(Map<ResourceLocation, StateDefinition<Block, BlockState>> map, BiConsumer<ResourceLocation, StateDefinition<Block, BlockState>> func) {
        map.forEach((loc, def) -> blockStateFiles.add(loc));
    }

    @Redirect(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;", ordinal = 0))
    private ImmutableList<BlockState> fetchBlocks(StateDefinition<Block, BlockState> def) {
        blockStateFiles.add(def.any().getBlock().getRegistryName());
        return ImmutableList.of();
    }

    private static final int ERROR_THRESHOLD = 200;

    private void logOrSuppressError(Object2IntOpenHashMap<String> suppressionMap, String type, ResourceLocation location, Throwable e) {
        int numErrors;
        synchronized (suppressionMap) {
            numErrors = suppressionMap.computeInt(location.getNamespace(), (k, oldVal) -> (oldVal == null ? 1 : oldVal + 1));
        }
        if(numErrors <= ERROR_THRESHOLD)
            ModernFix.LOGGER.error("Error reading {} {}: {}", type, location, e);
    }

    private boolean trustedResourcePack(PackResources pack) {
        return pack instanceof VanillaPackResources ||
                pack instanceof PathResourcePack ||
                pack instanceof ClientPackSource ||
                pack instanceof DelegatingResourcePack ||
                pack instanceof FolderPackResources ||
                pack instanceof FilePackResources;
    }

    private void gatherAdditionalViaManualScan(List<PackResources> untrustedPacks, Set<ResourceLocation> knownLocations,
                                               Collection<ResourceLocation> uncertainLocations, String filePrefix) {
        if(untrustedPacks.size() > 0) {
            /* Now make a fallback resource manager and use it on the remaining packs to see if they actually contain these files */
            FallbackResourceManager frm = new FallbackResourceManager(PackType.CLIENT_RESOURCES, "dummy");
            for (int i = untrustedPacks.size() - 1; i >= 0; i--) {
                frm.add(untrustedPacks.get(i));
            }
            for (ResourceLocation blockstate : uncertainLocations) {
                if (knownLocations.contains(blockstate))
                    continue; // don't check ones we know exist
                ResourceLocation fileLocation = new ResourceLocation(blockstate.getNamespace(), filePrefix + blockstate.getPath() + ".json");
                try (Resource resource = frm.getResource(fileLocation)) {
                    knownLocations.add(blockstate);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Load all blockstate JSONs and model files, collect textures.
     */
    private void gatherModelMaterials(Set<Material> materialSet) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<CompletableFuture<Pair<ResourceLocation, JsonElement>>> blockStateData = new ArrayList<>();
        final Object2IntOpenHashMap<String> blockstateErrors = new Object2IntOpenHashMap<>();
        /*
         * First, gather all vanilla packs, and use listResources on them. This will allow us to (hopefully) avoid
         * scanning most packs a lot.
         */
        List<PackResources> allPackResources = new ArrayList<>(this.resourceManager.listPacks().collect(Collectors.toList()));
        Collections.reverse(allPackResources);
        ObjectOpenHashSet<ResourceLocation> allAvailableModels = new ObjectOpenHashSet<>(), allAvailableStates = new ObjectOpenHashSet<>();
        allPackResources.removeIf(pack -> {
            if(trustedResourcePack(pack)) {
                for(String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                    Collection<ResourceLocation> allBlockstates = pack.getResources(PackType.CLIENT_RESOURCES, namespace, "blockstates", Integer.MAX_VALUE, p -> p.endsWith(".json"));
                    for(ResourceLocation blockstate : allBlockstates) {
                        allAvailableStates.add(new ResourceLocation(blockstate.getNamespace(), blockstate.getPath().replace("blockstates/", "").replace(".json", "")));
                    }
                    Collection<ResourceLocation> allModels = pack.getResources(PackType.CLIENT_RESOURCES, namespace, "models", Integer.MAX_VALUE, p -> p.endsWith(".json"));
                    for(ResourceLocation blockstate : allModels) {
                        allAvailableModels.add(new ResourceLocation(blockstate.getNamespace(), blockstate.getPath().replace("models/", "").replace(".json", "")));
                    }
                }
                return true;
            }
            ModernFix.LOGGER.debug("Pack with class {} needs manual scan", pack.getClass().getName());
            return false;
        });

        gatherAdditionalViaManualScan(allPackResources, allAvailableStates, blockStateFiles, "blockstates/");
        // We now have a list of all blockstates known to exist. Delete anything that we don't have
        blockStateFiles.retainAll(allAvailableStates);
        allAvailableStates.clear();
        allAvailableStates.trim();


        for(ResourceLocation blockstate : blockStateFiles) {
            blockStateData.add(CompletableFuture.supplyAsync(() -> {
                ResourceLocation fileLocation = new ResourceLocation(blockstate.getNamespace(), "blockstates/" + blockstate.getPath() + ".json");
                try(Resource resource = this.resourceManager.getResource(fileLocation)) {
                    JsonParser parser = new JsonParser();
                    return Pair.of(blockstate, parser.parse(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)));
                } catch(IOException | JsonParseException e) {
                    logOrSuppressError(blockstateErrors, "blockstate", blockstate, e);
                }
                return Pair.of(blockstate, null);
            }, ModernFix.resourceReloadExecutor()));
        }
        blockStateFiles = null;
        CompletableFuture.allOf(blockStateData.toArray(new CompletableFuture[0])).join();
        for(CompletableFuture<Pair<ResourceLocation, JsonElement>> result : blockStateData) {
            Pair<ResourceLocation, JsonElement> pair = result.join();
            if(pair.getSecond() != null) {
                try {
                    JsonObject obj = pair.getSecond().getAsJsonObject();
                    if(obj.has("variants")) {
                        JsonObject eachVariant = obj.getAsJsonObject("variants");
                        for(Map.Entry<String, JsonElement> entry : eachVariant.entrySet()) {
                            JsonElement variantData = entry.getValue();
                            List<JsonObject> variantModels;
                            if(variantData.isJsonArray()) {
                                variantModels = new ArrayList<>();
                                for(JsonElement model : variantData.getAsJsonArray()) {
                                    variantModels.add(model.getAsJsonObject());
                                }
                            } else
                                variantModels = Collections.singletonList(variantData.getAsJsonObject());
                            for(JsonObject variant : variantModels) {
                                modelFiles.add(new ResourceLocation(variant.get("model").getAsString()));
                            }
                        }

                    } else {
                        JsonArray multipartData = obj.get("multipart").getAsJsonArray();
                        for(JsonElement element : multipartData) {
                            JsonObject self = element.getAsJsonObject();
                            JsonElement apply = self.get("apply");
                            List<JsonObject> applyObjects;
                            if(apply.isJsonArray()) {
                                applyObjects = new ArrayList<>();
                                for(JsonElement e : apply.getAsJsonArray()) {
                                    applyObjects.add(e.getAsJsonObject());
                                }
                            } else
                                applyObjects = Collections.singletonList(apply.getAsJsonObject());
                            for(JsonObject applyEntry : applyObjects) {
                                modelFiles.add(new ResourceLocation(applyEntry.get("model").getAsString()));
                            }
                        }

                    }
                } catch(RuntimeException e) {
                    logOrSuppressError(blockstateErrors, "blockstate", pair.getFirst(), e);
                }

            }
        }
        blockstateErrors.object2IntEntrySet().forEach(entry -> {
            if(entry.getIntValue() > ERROR_THRESHOLD) {
                ModernFix.LOGGER.error("Suppressed additional {} blockstate errors for domain {}", entry.getIntValue(), entry.getKey());
            }
        });
        blockstateErrors.clear();
        blockStateData = null;

        /* figure out which models we should actually load */
        gatherAdditionalViaManualScan(allPackResources, allAvailableModels, modelFiles, "models/");
        modelFiles.retainAll(allAvailableModels);
        allAvailableModels.clear();
        allAvailableModels.trim();

        Map<ResourceLocation, BlockModel> basicModels = new HashMap<>();
        basicModels.put(MISSING_MODEL_LOCATION, (BlockModel)missingModel);
        basicModels.put(new ResourceLocation("builtin/generated"), GENERATION_MARKER);
        basicModels.put(new ResourceLocation("builtin/entity"), BLOCK_ENTITY_MARKER);
        Set<Pair<String, String>> errorSet = Sets.newLinkedHashSet();
        while(modelFiles.size() > 0) {
            List<CompletableFuture<Pair<ResourceLocation, JsonElement>>> modelBytes = new ArrayList<>();
            for(ResourceLocation model : modelFiles) {
                if(basicModels.containsKey(model))
                    continue;
                ResourceLocation fileLocation = new ResourceLocation(model.getNamespace(), "models/" + model.getPath() + ".json");
                modelBytes.add(CompletableFuture.supplyAsync(() -> {
                    try(Resource resource = this.resourceManager.getResource(fileLocation)) {
                        JsonParser parser = new JsonParser();
                        return Pair.of(model, parser.parse(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)));
                    } catch(IOException | JsonParseException e) {
                        logOrSuppressError(blockstateErrors, "model", fileLocation, e);
                        return Pair.of(fileLocation, null);
                    }
                }, ModernFix.resourceReloadExecutor()));
            }
            modelFiles.clear();
            CompletableFuture.allOf(modelBytes.toArray(new CompletableFuture[0])).join();
            UVController.useDummyUv.set(Boolean.TRUE);
            for(CompletableFuture<Pair<ResourceLocation, JsonElement>> future : modelBytes) {
                Pair<ResourceLocation, JsonElement> pair = future.join();
                try {
                    if(pair.getSecond() != null) {

                        BlockModel model = ModelLoaderRegistry.ExpandedBlockModelDeserializer.INSTANCE.fromJson(pair.getSecond(), BlockModel.class);
                        model.name = pair.getFirst().toString();
                        modelFiles.addAll(model.getDependencies());
                        basicModels.put(pair.getFirst(), model);
                        continue;
                    }
                } catch(Throwable e) {
                    logOrSuppressError(blockstateErrors, "model", pair.getFirst(), e);
                }
                basicModels.put(pair.getFirst(), (BlockModel)missingModel);
            }
            UVController.useDummyUv.set(Boolean.FALSE);
        }
        blockstateErrors.object2IntEntrySet().forEach(entry -> {
            if(entry.getIntValue() > ERROR_THRESHOLD) {
                ModernFix.LOGGER.error("Suppressed additional {} model errors for domain {}", entry.getIntValue(), entry.getKey());
            }
        });
        modelFiles = null;
        Function<ResourceLocation, UnbakedModel> modelGetter = loc -> {
            UnbakedModel m = basicModels.get(loc);
            /* fallback to vanilla loader if missing */
            return m != null ? m : this.getModel(loc);
        };
        for(BlockModel model : basicModels.values()) {
            materialSet.addAll(model.getMaterials(modelGetter, errorSet));
        }
        /* discard whatever garbage was just produced */
        loadedModels.invalidateAll();
        loadedModels.put(MISSING_MODEL_LOCATION, missingModel);
        //errorSet.stream().filter(pair -> !pair.getSecond().equals(MISSING_MODEL_LOCATION_STRING)).forEach(pair -> LOGGER.warn("Unable to resolve texture reference: {} in {}", pair.getFirst(), pair.getSecond()));
        stopwatch.stop();
        ModernFix.LOGGER.info("Resolving model textures took " + stopwatch);
    }

    @Inject(method = "uploadTextures", at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;topLevelModels:Ljava/util/Map;", ordinal = 0), cancellable = true)
    private void skipBake(TextureManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<AtlasSet> cir) {
        profiler.pop();
        // ensure missing model is a permanent override
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, this.bake(MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0, this.atlasSet::getSprite));
        cir.setReturnValue(atlasSet);
    }

    /**
     * Use the already loaded missing model instead of the cache entry (which will probably get evicted).
     */
    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private Object getMissingModel(Map map, Object rl) {
        if(rl == MISSING_MODEL_LOCATION && map == unbakedCache)
            return missingModel;
        return unbakedCache.get(rl);
    }

    @Inject(method = "cacheAndQueueDependencies", at = @At("RETURN"))
    private void addToSmallLoadingCache(ResourceLocation location, UnbakedModel model, CallbackInfo ci) {
        smallLoadingCache.put(location, model);
    }


    /**
     * @author embeddedt
     * @reason synchronize
     */
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    public void getOrLoadModelDynamic(ResourceLocation modelLocation, CallbackInfoReturnable<UnbakedModel> cir) {
        if(modelLocation.equals(MISSING_MODEL_LOCATION)) {
            cir.setReturnValue(missingModel);
            return;
        }
        UnbakedModel existing = this.unbakedCache.get(modelLocation);
        if (existing != null) {
            cir.setReturnValue(existing);
        } else {
            synchronized(this) {
                if (this.loadingStack.contains(modelLocation)) {
                    throw new IllegalStateException("Circular reference while loading " + modelLocation);
                } else {
                    this.loadingStack.add(modelLocation);
                    UnbakedModel iunbakedmodel = missingModel;

                    while(!this.loadingStack.isEmpty()) {
                        ResourceLocation resourcelocation = this.loadingStack.iterator().next();

                        try {
                            existing = this.unbakedCache.get(resourcelocation);
                            if (existing == null) {
                                if(debugDynamicModelLoading)
                                    LOGGER.info("Loading {}", resourcelocation);
                                this.loadModel(resourcelocation);
                            } else
                                smallLoadingCache.put(resourcelocation, existing);
                        } catch (ModelBakery.BlockStateDefinitionException var9) {
                            LOGGER.warn(var9.getMessage());
                            this.unbakedCache.put(resourcelocation, iunbakedmodel);
                            smallLoadingCache.put(resourcelocation, iunbakedmodel);
                        } catch (Exception var10) {
                            LOGGER.warn("Unable to load model: '{}' referenced from: {}: {}", resourcelocation, modelLocation, var10);
                            this.unbakedCache.put(resourcelocation, iunbakedmodel);
                            smallLoadingCache.put(resourcelocation, iunbakedmodel);
                        } finally {
                            this.loadingStack.remove(resourcelocation);
                        }
                    }

                    // We have to get the result from the temporary cache used for a model load
                    // As in pathological cases (e.g. Pedestals on 1.19) unbakedCache can lose
                    // the model immediately
                    UnbakedModel result = smallLoadingCache.getOrDefault(modelLocation, iunbakedmodel);
                    // We are done with loading, so clear this cache to allow GC of any unneeded models
                    smallLoadingCache.clear();
                    cir.setReturnValue(result);
                }
            }
        }
    }

    private <T extends Comparable<T>, V extends T> BlockState setPropertyGeneric(BlockState state, Property<T> prop, Object o) {
        return state.setValue(prop, (V)o);
    }
    @Redirect(method = "loadModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> loadOnlyRelevantBlockState(StateDefinition<Block, BlockState> stateDefinition, ResourceLocation location) {
        Set<Property<?>> fixedProperties = new HashSet<>();
        ModelResourceLocation mrl = (ModelResourceLocation)location;
        BlockState fixedState = stateDefinition.any();
        for(String s : COMMA_SPLITTER.split(mrl.getVariant())) {
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

    @Override
    public ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        return loadOnlyRelevantBlockState(stateDefinition, location);
    }

    private BakedModel bakedMissingModel = null;

    @Inject(method = "bake(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), cancellable = true, remap = false)
    public void getOrLoadBakedModelDynamic(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> textureGetter, CallbackInfoReturnable<BakedModel> cir) {
        Triple<ResourceLocation, Transformation, Boolean> triple = Triple.of(arg, arg2.getRotation(), arg2.isUvLocked());
        BakedModel existing = this.bakedCache.get(triple);
        if (existing != null) {
            cir.setReturnValue(existing);
        } else if (this.atlasSet == null) {
            throw new IllegalStateException("bake called too early");
        } else {
            synchronized (this) {
                if(debugDynamicModelLoading)
                    LOGGER.info("Baking {}", arg);
                UnbakedModel iunbakedmodel = this.getModel(arg);
                iunbakedmodel.getMaterials(this::getModel, new HashSet<>());
                BakedModel ibakedmodel = null;
                if (iunbakedmodel instanceof BlockModel) {
                    BlockModel blockmodel = (BlockModel)iunbakedmodel;
                    if (blockmodel.getRootModel() == GENERATION_MARKER) {
                        ibakedmodel = ITEM_MODEL_GENERATOR.generateBlockModel(textureGetter, blockmodel).bake((ModelBakery)(Object)this, blockmodel, this.atlasSet::getSprite, arg2, arg, false);
                    }
                }
                if(ibakedmodel == null) {
                    if(iunbakedmodel == missingModel) {
                        // use a shared baked missing model
                        if(bakedMissingModel == null) {
                            bakedMissingModel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
                            ((DynamicBakedModelProvider)this.bakedTopLevelModels).setMissingModel(bakedMissingModel);
                        }
                        ibakedmodel = bakedMissingModel;
                    } else
                        ibakedmodel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
                }
                DynamicModelBakeEvent event = new DynamicModelBakeEvent(arg, iunbakedmodel, ibakedmodel, (ForgeModelBakery)(Object)this);
                MinecraftForge.EVENT_BUS.post(event);
                this.bakedCache.put(triple, event.getModel());
                cir.setReturnValue(event.getModel());
            }
        }
    }
}
