package org.embeddedt.modernfix.neoforge.datagen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import org.embeddedt.modernfix.ModernFix;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@EventBusSubscriber(value = Dist.CLIENT)
public class RuntimeDatagen {
    private static final String RESOURCES_OUT_DIR = getPropertyOrBlank("modernfix.datagen.output");
    private static final String RESOURCES_IN_DIR = getPropertyOrBlank("modernfix.datagen.existing");
    private static final String MODS_LIST = getPropertyOrBlank("modernfix.datagen.mods");
    private static final String EXISTING_MODS_LIST = getPropertyOrBlank("modernfix.datagen.existing_mods");
    private static final boolean IS_FLAT = Boolean.getBoolean("modernfix.datagen.flat");

    private static String getPropertyOrBlank(String name) {
        String val = System.getProperty(name);
        if(val == null || val.length() == 0)
            return "";
        else
            return val;
    }

    public static boolean isDatagenAvailable() {
        return RESOURCES_OUT_DIR.length() > 0;
    }

    public static void runRuntimeDatagen() {
        ObfuscationReflectionHelper.setPrivateValue(DatagenModLoader.class, null, true, "runningDataGen");
        Set<String> mods = new HashSet<>(Arrays.stream(MODS_LIST.split(",")).collect(Collectors.toSet()));
        ModernFix.LOGGER.info("Beginning runtime datagen for " + mods.size() + " mods...");
        Set<String> existingMods = new HashSet<>(Arrays.stream(EXISTING_MODS_LIST.split(",")).collect(Collectors.toSet()));
        Set<Path> existingPacks = new HashSet<>(Arrays.stream(RESOURCES_IN_DIR.split(",")).map(Paths::get).collect(Collectors.toSet()));
        Path path = Paths.get(RESOURCES_OUT_DIR);
        CompletableFuture<HolderLookup.Provider> lookupProvider = CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());
        GatherDataEvent.DataGeneratorConfig dataGeneratorConfig = new GatherDataEvent.DataGeneratorConfig(mods, path, Collections.emptyList(),
                lookupProvider, true, true, true, true, true, mods.isEmpty() || IS_FLAT);
        if (!mods.contains("forge")) {
            //If we aren't generating data for forge, automatically add forge as an existing so mods can access forge's data
            existingMods.add("forge");
        }
        ExistingFileHelper existingFileHelper = new ExistingFileHelper(existingPacks, existingMods, true, null, null);
        /* Inject the client pack resources from us */
        MultiPackResourceManager manager = ObfuscationReflectionHelper.getPrivateValue(ExistingFileHelper.class, existingFileHelper, "clientResources");
        List<PackResources> oldPacks = new ArrayList<>(manager.listPacks().collect(Collectors.toList()));
        oldPacks.add(Minecraft.getInstance().getVanillaPackResources());
        ObfuscationReflectionHelper.setPrivateValue(ExistingFileHelper.class, existingFileHelper, new MultiPackResourceManager(PackType.CLIENT_RESOURCES, oldPacks), "clientResources");
        ModLoader.runEventGenerator(mc->new GatherDataEvent(mc, dataGeneratorConfig.makeGenerator(p->dataGeneratorConfig.isFlat() ? p : p.resolve(mc.getModId()), dataGeneratorConfig.getMods().contains(mc.getModId())), dataGeneratorConfig, existingFileHelper));
        dataGeneratorConfig.runAll();
        ObfuscationReflectionHelper.setPrivateValue(DatagenModLoader.class, null, false, "runningDataGen");
        ModernFix.LOGGER.info("Finished runtime datagen.");
    }

    @SubscribeEvent
    public static void onInitTitleScreen(ScreenEvent.Init.Post event) {
        if(isDatagenAvailable() && event.getScreen() instanceof TitleScreen) {
            TitleScreen screen = (TitleScreen)event.getScreen();
            screen.addRenderableWidget(Button.builder(Component.literal("DG"), (arg) -> {
                runRuntimeDatagen();
            }).pos(screen.width / 2 - 100 - 50, screen.height / 4 + 48).size(50, 20).build());
        }
    }
}
