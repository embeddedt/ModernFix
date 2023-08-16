package org.embeddedt.modernfix.dynamicresources;

import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.client.resources.model.ModelBakery.BLOCK_ENTITY_MARKER;
import static net.minecraft.client.resources.model.ModelBakery.GENERATION_MARKER;

public class ModelBakeryHelpers {
    /**
     * The maximum number of baked models kept in memory at once.
     */
    public static final int MAX_BAKED_MODEL_COUNT = 10000;
    /**
     * The maximum number of unbaked models kept in memory at once.
     */
    public static final int MAX_UNBAKED_MODEL_COUNT = 10000;
    /**
     * The time in seconds after which a model becomes eligible for eviction if not used.
     */
    public static final int MAX_MODEL_LIFETIME_SECS = 300;

    /**
     * These folders will have all textures stitched onto the atlas when dynamic resources is enabled.
     */
    public static String[] getExtraTextureFolders() {
        return new String[] {
                "attachment",
                "bettergrass",
                "block",
                "blocks",
                "cape",
                "entity/bed",
                "entity/chest",
                "item",
                "items",
                "model",
                "models",
                "part",
                "pipe",
                "ropebridge",
                "runes",
                "solid_block",
                "spell_effect",
                "spell_projectile"
        };
    }

    private static JsonElement parseStream(InputStream stream) {
        JsonParser parser = new JsonParser();
        JsonReader jsonReader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        jsonReader.setLenient(true);
        return parser.parse(jsonReader);
    }

    private static void gatherAdditionalViaManualScan(List<PackResources> untrustedPacks, Set<ResourceLocation> knownLocations,
                                               Collection<ResourceLocation> uncertainLocations, String filePrefix) {
        if(untrustedPacks.size() > 0) {
            /* Now make a fallback resource manager and use it on the remaining packs to see if they actually contain these files */
            FallbackResourceManager frm = new FallbackResourceManager(PackType.CLIENT_RESOURCES, "dummy");
            for (int i = untrustedPacks.size() - 1; i >= 0; i--) {
                frm.push(untrustedPacks.get(i));
            }
            for (ResourceLocation blockstate : uncertainLocations) {
                if (knownLocations.contains(blockstate))
                    continue; // don't check ones we know exist
                ResourceLocation fileLocation = new ResourceLocation(blockstate.getNamespace(), filePrefix + blockstate.getPath() + ".json");
                Optional<Resource> resource = frm.getResource(fileLocation);
                if(resource.isPresent())
                    knownLocations.add(blockstate);
            }
        }
    }

    private static final int ERROR_THRESHOLD = 200;

    private static void logOrSuppressError(Object2IntOpenHashMap<String> suppressionMap, String type, ResourceLocation location, Throwable e) {
        int numErrors;
        synchronized (suppressionMap) {
            numErrors = suppressionMap.computeInt(location.getNamespace(), (k, oldVal) -> (oldVal == null ? 1 : oldVal + 1));
        }
        if(numErrors <= ERROR_THRESHOLD)
            ModernFix.LOGGER.error("Error reading {} {}: {}", type, location, e);
    }

    /**
     * Some mods (cough, EBE) inject their custom resource pack into the namespaced resource managers, but not into the
     * main list contained inside the parent resource manager, so we need to scan each of the namespaced managers as
     * well.
     */
    private static void checkFallbacks(MultiPackResourceManager manager, List<PackResources> resourcePackList) {
        ReferenceSet<PackResources> knownPacks = new ReferenceOpenHashSet<>(resourcePackList);
        Map<String, FallbackResourceManager> namespacedMap = manager.namespacedManagers;
        namespacedMap.values().stream().flatMap(FallbackResourceManager::listPacks).forEach(pack -> {
            if(knownPacks.add(pack)) {
                /* the pack was not previously known, add to our list */
                ModernFix.LOGGER.debug("Injecting unlisted pack '{}': {}", pack.getName(), pack.getClass().getName());
                resourcePackList.add(pack);
            }
        });
    }

    public static void gatherModelMaterials(ResourceManager manager, Predicate<PackResources> isTrustedPack,
                                            Set<Material> materialSet, Set<ResourceLocation> blockStateFiles,
                                            Set<ResourceLocation> modelFiles, UnbakedModel missingModel,
                                            Function<JsonElement, BlockModel> modelDeserializer,
                                            Function<ResourceLocation, UnbakedModel> bakeryModelGetter) {
        if(!ModernFixPlatformHooks.INSTANCE.isLoadingNormally())
            return;
        Stopwatch stopwatch = Stopwatch.createStarted();
        final Object2IntOpenHashMap<String> blockstateErrors = new Object2IntOpenHashMap<>();
        /*
         * First, gather all vanilla packs, and use listResources on them. This will allow us to (hopefully) avoid
         * scanning most packs a lot.
         */
        List<PackResources> allPackResources = new ArrayList<>(manager.listPacks().collect(Collectors.toList()));
        if(manager instanceof MultiPackResourceManager) {
            checkFallbacks((MultiPackResourceManager)manager, allPackResources);
        }
        Collections.reverse(allPackResources);
        ObjectOpenHashSet<ResourceLocation> allAvailableModels = new ObjectOpenHashSet<>(), allAvailableStates = new ObjectOpenHashSet<>();
        /* try to fix CME in some runtime packs by forcing generation */
        for(PackResources pack : allPackResources) {
            try(InputStream stream = pack.getResource(PackType.CLIENT_RESOURCES, new ResourceLocation("modernfix", "dummy.json"))) {
            } catch(Exception ignored) {
            }
        }
        allPackResources.removeIf(pack -> {
            for(String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                Collection<ResourceLocation> allBlockstates = pack.getResources(PackType.CLIENT_RESOURCES, namespace, "blockstates", p -> p.getPath().endsWith(".json"));
                for(ResourceLocation blockstate : allBlockstates) {
                    allAvailableStates.add(new ResourceLocation(blockstate.getNamespace(), blockstate.getPath().replace("blockstates/", "").replace(".json", "")));
                }
                Collection<ResourceLocation> allModels = pack.getResources(PackType.CLIENT_RESOURCES, namespace, "models", p -> p.getPath().endsWith(".json"));
                for(ResourceLocation blockstate : allModels) {
                    allAvailableModels.add(new ResourceLocation(blockstate.getNamespace(), blockstate.getPath().replace("models/", "").replace(".json", "")));
                }
            }
            if(!isTrustedPack.test(pack)) {
                ModernFix.LOGGER.debug("Pack with class {} needs manual scan", pack.getClass().getName());
                return false;
            }
            return true;
        });

        gatherAdditionalViaManualScan(allPackResources, allAvailableStates, blockStateFiles, "blockstates/");
        // We now have a list of all blockstates known to exist. Delete anything that we don't have
        blockStateFiles.retainAll(allAvailableStates);
        allAvailableStates.clear();
        allAvailableStates.trim();

        ConcurrentLinkedQueue<Pair<ResourceLocation, JsonElement>> blockStateLoadedFiles = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> blockStateData = new ArrayList<>();
        for(ResourceLocation blockstate : blockStateFiles) {
            ResourceLocation fileLocation = new ResourceLocation(blockstate.getNamespace(), "blockstates/" + blockstate.getPath() + ".json");
            List<Resource> resources = manager.getResourceStack(fileLocation);
            if(resources.isEmpty())
                continue;
            blockStateData.add(CompletableFuture.runAsync(() -> {
                try {
                    for(Resource resource : resources) {
                        JsonParser parser = new JsonParser();
                        try(InputStream stream = resource.open()) {
                            blockStateLoadedFiles.add(Pair.of(blockstate, parseStream(stream)));
                        } catch(JsonParseException e) {
                            logOrSuppressError(blockstateErrors, "blockstate", blockstate, e);
                        }
                    }
                } catch(IOException e) {
                    logOrSuppressError(blockstateErrors, "blockstate", blockstate, e);
                }
            }, ModernFix.resourceReloadExecutor()));
        }
        blockStateFiles = null;
        CompletableFuture.allOf(blockStateData.toArray(new CompletableFuture[0])).join();
        for(Pair<ResourceLocation, JsonElement> pair : blockStateLoadedFiles) {
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
        blockStateLoadedFiles.clear();

        modelFiles.addAll(allAvailableModels);
        /* figure out which models we should actually load */
        gatherAdditionalViaManualScan(allPackResources, allAvailableModels, modelFiles, "models/");
        modelFiles.retainAll(allAvailableModels);
        allAvailableModels.clear();
        allAvailableModels.trim();

        Map<ResourceLocation, BlockModel> basicModels = new HashMap<>();
        basicModels.put(ModelBakery.MISSING_MODEL_LOCATION, (BlockModel)missingModel);
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
                    Optional<Resource> resource = manager.getResource(fileLocation);
                    try(InputStream stream = resource.orElseThrow().open()) {
                        return Pair.of(model, parseStream(stream));
                    } catch(IOException | NoSuchElementException | JsonParseException e) {
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

                        BlockModel model = modelDeserializer.apply(pair.getSecond());
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
        blockstateErrors.clear();
        modelFiles = null;
        Function<ResourceLocation, UnbakedModel> modelGetter = loc -> {
            UnbakedModel m = basicModels.get(loc);
            /* fallback to vanilla loader if missing */
            return m != null ? m : bakeryModelGetter.apply(loc);
        };
        for(BlockModel model : basicModels.values()) {
            try {
                materialSet.addAll(model.getMaterials(modelGetter, errorSet));
            } catch(Throwable e) {
                ModernFix.LOGGER.error("Model {} threw error while getting materials", model.name, e);
            }
        }
        //errorSet.stream().filter(pair -> !pair.getSecond().equals(MISSING_MODEL_LOCATION_STRING)).forEach(pair -> LOGGER.warn("Unable to resolve texture reference: {} in {}", pair.getFirst(), pair.getSecond()));
        stopwatch.stop();
        ModernFix.LOGGER.info("Resolving model textures took " + stopwatch);
    }

    private static <T extends Comparable<T>, V extends T> BlockState setPropertyGeneric(BlockState state, Property<T> prop, Object o) {
        return state.setValue(prop, (V)o);
    }

    private static <T extends Comparable<T>> T getValueHelper(Property<T> property, String value) {
        return property.getValue(value).orElse((T) null);
    }

    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);

    public static ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location) {
        if(Objects.equals(location.getVariant(), "inventory"))
            return ImmutableList.of();
        Set<Property<?>> fixedProperties = new HashSet<>();
        BlockState fixedState = stateDefinition.any();
        for(String s : COMMA_SPLITTER.split(location.getVariant())) {
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
        // check if there is only one possible state
        if(fixedProperties.size() == stateDefinition.getProperties().size()) {
            return ImmutableList.of(fixedState);
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

    public static ModernFixClientIntegration bakedModelWrapper(BiFunction<ResourceLocation, Pair<UnbakedModel, BakedModel>, BakedModel> consumer) {
        return new ModernFixClientIntegration() {
            @Override
            public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery) {
                return consumer.apply(location, Pair.of(baseModel, originalModel));
            }
        };
    }
}
