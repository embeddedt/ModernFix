package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {

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

    @Shadow @Final @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Final @Mutable private Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;

    @Shadow @Final public static BlockModel GENERATION_MARKER;

    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;

    @Shadow @Final public static BlockModel BLOCK_ENTITY_MARKER;

    private Cache<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> loadedBakedModels;
    private Cache<ResourceLocation, UnbakedModel> loadedModels;


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

    /**
     * @author embeddedt
     * @reason don't load any models initially, just set up initial data structures
     */
    @Overwrite
    protected void processLoading(ProfilerFiller arg, int maxMipLevels) {
        ModelLoaderRegistry.onModelLoadingStart();
        try {
            this.missingModel = this.loadBlockModel(MISSING_MODEL_LOCATION);
        } catch (IOException var10) {
            ModernFix.LOGGER.error("Error loading missing model, should never happen :(", var10);
            throw new RuntimeException(var10);
        }
        // Gather model materials
        Set<Material> initialMaterials = new HashSet<>(UNREFERENCED_TEXTURES);
        gatherModelMaterials(initialMaterials);
        ForgeHooksClient.gatherFluidTextures(initialMaterials);
        Map<ResourceLocation, List<Material>> map = initialMaterials.stream().collect(Collectors.groupingBy(Material::atlasLocation));
        this.atlasPreparations = Maps.newHashMap();
        for(Map.Entry<ResourceLocation, List<Material>> entry : map.entrySet()) {
            TextureAtlas atlas = new TextureAtlas(entry.getKey());
            TextureAtlas.Preparations atlastexture$sheetdata = atlas.prepareToStitch(this.resourceManager, entry.getValue().stream().map(Material::texture), arg, maxMipLevels);
            this.atlasPreparations.put(entry.getKey(), Pair.of(atlas, atlastexture$sheetdata));
        }
    }

    /**
     * Scan the models folder and try to load, parse, and get materials from as many models as possible.
     */
    private void gatherModelMaterials(Set<Material> materialSet) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Collection<ResourceLocation> allModels = this.resourceManager.listResources("models", path -> path.endsWith(".json"));
        List<CompletableFuture<Pair<ResourceLocation, JsonElement>>> modelBytes = new ArrayList<>();
        for(ResourceLocation fileLocation : allModels) {
            modelBytes.add(CompletableFuture.supplyAsync(() -> {
                try(Resource resource = this.resourceManager.getResource(fileLocation)) {
                    JsonParser parser = new JsonParser();
                    // strip models/ and .json from the name
                    ResourceLocation model = new ResourceLocation(fileLocation.getNamespace(), fileLocation.getPath().substring(7, fileLocation.getPath().length()-5));
                    return Pair.of(model, parser.parse(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)));
                } catch(IOException | JsonParseException e) {
                    ModernFix.LOGGER.error("Error reading model {}: {}", fileLocation, e);
                    return Pair.of(fileLocation, null);
                }
            }, Util.backgroundExecutor()));
        }
        allModels.clear();
        CompletableFuture.allOf(modelBytes.toArray(new CompletableFuture[0])).join();
        Set<Pair<String, String>> errorSet = Sets.newLinkedHashSet();
        Map<ResourceLocation, BlockModel> basicModels = new HashMap<>();
        try {
            basicModels.put(MISSING_MODEL_LOCATION, this.loadBlockModel(MISSING_MODEL_LOCATION));
            basicModels.put(new ResourceLocation("builtin/generated"), GENERATION_MARKER);
            basicModels.put(new ResourceLocation("builtin/entity"), BLOCK_ENTITY_MARKER);
        } catch(IOException e) {
            throw new RuntimeException("Exception when populating built-in models", e);
        }
        for(CompletableFuture<Pair<ResourceLocation, JsonElement>> future : modelBytes) {
            Pair<ResourceLocation, JsonElement> pair = future.join();
            try {
                if(pair.getSecond() != null) {
                    BlockModel model = ModelLoaderRegistry.ExpandedBlockModelDeserializer.INSTANCE.fromJson(pair.getSecond(), BlockModel.class);
                    model.name = pair.getFirst().toString();
                    basicModels.put(pair.getFirst(), model);
                }
            } catch(Throwable e) {
                ModernFix.LOGGER.warn("Unable to parse {}: {}", pair.getFirst(), e);
            }
        }
        modelBytes.clear();
        Function<ResourceLocation, UnbakedModel> modelGetter = loc -> basicModels.getOrDefault(loc, (BlockModel)this.missingModel);
        for(BlockModel model : basicModels.values()) {
            materialSet.addAll(model.getMaterials(modelGetter, errorSet));
        }
        //errorSet.stream().filter(pair -> !pair.getSecond().equals(MISSING_MODEL_LOCATION_STRING)).forEach(pair -> LOGGER.warn("Unable to resolve texture reference: {} in {}", pair.getFirst(), pair.getSecond()));
        stopwatch.stop();
        ModernFix.LOGGER.info("Resolving model textures took " + stopwatch);
    }

    @Inject(method = "uploadTextures", at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;topLevelModels:Ljava/util/Map;", ordinal = 0), cancellable = true)
    private void skipBake(TextureManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<AtlasSet> cir) {
        profiler.pop();
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


    /**
     * @author embeddedt
     * @reason synchronize
     */
    @Overwrite
    public synchronized UnbakedModel getModel(ResourceLocation modelLocation) {
        if(modelLocation.equals(MISSING_MODEL_LOCATION))
            return missingModel;
        if (this.unbakedCache.containsKey(modelLocation)) {
            return this.unbakedCache.get(modelLocation);
        } else if (this.loadingStack.contains(modelLocation)) {
            throw new IllegalStateException("Circular reference while loading " + modelLocation);
        } else {
            this.loadingStack.add(modelLocation);
            UnbakedModel iunbakedmodel = missingModel;

            while(!this.loadingStack.isEmpty()) {
                ResourceLocation resourcelocation = this.loadingStack.iterator().next();

                try {
                    if (!this.unbakedCache.containsKey(resourcelocation)) {
                        if(debugDynamicModelLoading)
                            LOGGER.info("Loading {}", resourcelocation);
                        this.loadModel(resourcelocation);
                    }
                } catch (ModelBakery.BlockStateDefinitionException var9) {
                    LOGGER.warn(var9.getMessage());
                    this.unbakedCache.put(resourcelocation, iunbakedmodel);
                } catch (Exception var10) {
                    LOGGER.warn("Unable to load model: '{}' referenced from: {}: {}", resourcelocation, modelLocation, var10);
                    this.unbakedCache.put(resourcelocation, iunbakedmodel);
                } finally {
                    this.loadingStack.remove(resourcelocation);
                }
            }

            return this.unbakedCache.getOrDefault(modelLocation, iunbakedmodel);
        }
    }

    @Overwrite
    public synchronized BakedModel getBakedModel(ResourceLocation arg, ModelState arg2, Function<Material, TextureAtlasSprite> textureGetter) {
        Triple<ResourceLocation, Transformation, Boolean> triple = Triple.of(arg, arg2.getRotation(), arg2.isUvLocked());
        if (this.bakedCache.containsKey(triple)) {
            return this.bakedCache.get(triple);
        } else if (this.atlasSet == null) {
            throw new IllegalStateException("bake called too early");
        } else {
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
                ibakedmodel = iunbakedmodel.bake((ModelBakery) (Object) this, textureGetter, arg2, arg);
            }
            this.bakedCache.put(triple, ibakedmodel);
            return ibakedmodel;
        }
    }
}
