package org.embeddedt.modernfix.forge.datagen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.data.loading.DatagenModLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
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
        GatherDataEvent.DataGeneratorConfig dataGeneratorConfig = new GatherDataEvent.DataGeneratorConfig(mods, path, Collections.emptyList(),
                true, true, true, true, true, mods.isEmpty() || IS_FLAT);
        if (!mods.contains("forge")) {
            //If we aren't generating data for forge, automatically add forge as an existing so mods can access forge's data
            existingMods.add("forge");
        }
        ExistingFileHelper existingFileHelper = new ExistingFileHelper(existingPacks, existingMods, true, null, null);
        /* Inject the client pack resources from us */
        MultiPackResourceManager manager = ObfuscationReflectionHelper.getPrivateValue(ExistingFileHelper.class, existingFileHelper, "clientResources");
        List<PackResources> oldPacks = new ArrayList<>(manager.listPacks().collect(Collectors.toList()));
        oldPacks.add(Minecraft.getInstance().getClientPackSource().getVanillaPack());
        ObfuscationReflectionHelper.setPrivateValue(ExistingFileHelper.class, existingFileHelper, new MultiPackResourceManager(PackType.CLIENT_RESOURCES, oldPacks), "clientResources");
        ModLoader.get().runEventGenerator(mc->new GatherDataEvent(mc, dataGeneratorConfig.makeGenerator(p->dataGeneratorConfig.isFlat() ? p : p.resolve(mc.getModId()), dataGeneratorConfig.getMods().contains(mc.getModId())), dataGeneratorConfig, existingFileHelper));
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
