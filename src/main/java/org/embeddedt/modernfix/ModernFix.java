package org.embeddedt.modernfix;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.classloading.ModFileScanDataDeduplicator;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.embeddedt.modernfix.entity.EntityDataIDSyncHandler;
import org.embeddedt.modernfix.packet.PacketHandler;
import org.embeddedt.modernfix.registry.ObjectHolderClearer;
import org.embeddedt.modernfix.structure.AsyncLocator;
import org.embeddedt.modernfix.util.ClassInfoManager;
import org.embeddedt.modernfix.util.KubeUtil;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ModernFix.MODID)
public class ModernFix {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger("ModernFix");

    public static final String MODID = "modernfix";

    public static ModernFix INSTANCE;

    // Used to skip computing the blockstate caches twice
    public static boolean runningFirstInjection = false;

    public static CountDownLatch worldLoadSemaphore = null;

    private static Executor resourceReloadService = null;

    static {
        try {
            if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dedicated_reload_executor.ReloadExecutor")) {
                Method makeExecutorMethod = ObfuscationReflectionHelper.findMethod(Util.class, "func_240979_a_", String.class);
                resourceReloadService = (Executor)makeExecutorMethod.invoke(null, "ResourceReload");
            } else {
                resourceReloadService = Util.backgroundExecutor();
            }
        } catch(RuntimeException | ReflectiveOperationException e) {
            LOGGER.error("Could not create resource reload executor", e);
            resourceReloadService = Util.backgroundExecutor();
        }
    }

    public static Executor resourceReloadExecutor() {
        return resourceReloadService;
    }

    /**
     * Simple mechanism used to delay some background processes until the client is actually in-game, to reduce
     * launch time.
     */
    public static void waitForWorldLoad(BooleanSupplier exitEarly) {
        CountDownLatch latch = worldLoadSemaphore;
        if(latch != null) {
            try {
                while(!latch.await(100, TimeUnit.MILLISECONDS)) {
                    if(exitEarly.getAsBoolean())
                        return;
                }
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    public ModernFix() {
        INSTANCE = this;
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Item.class, this::registerItems);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ModernFixClient()));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModernFixConfig.COMMON_CONFIG);
        if(ModList.get().isLoaded("kubejs"))
            MinecraftForge.EVENT_BUS.register(KubeUtil.class);
        MinecraftForge.EVENT_BUS.register(EntityDataIDSyncHandler.class);
        PacketHandler.register();
        ModFileScanDataDeduplicator.deduplicate();
    }

    private void registerItems(RegistryEvent<Item> event) {
        if(Boolean.getBoolean("modernfix.largeRegistryTest")) {
            Item.Properties props = new Item.Properties();
            for(int i = 0; i < 1000000; i++) {
                ForgeRegistries.ITEMS.register(new Item(props).setRegistryName("modernfix", "item_" + i));
            }
        }
    }

    private static boolean dfuModPresent() {
        for(String modId : new String[] { "lazydfu", "datafixerslayer" }) {
            if(ModList.get().isLoaded(modId))
                return true;
        }
        return false;
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        if(!dfuModPresent()) {
            event.enqueueWork(() -> {
                ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.COMMON_SETUP, "modernfix.no_lazydfu"));
            });
        }
        ObjectHolderClearer.clearThrowables();
    }

    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        if(FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            float gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            ModernFix.LOGGER.warn("Dedicated server took " + gameStartTime + " seconds to load");
        }
        ClassInfoManager.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        ClassInfoManager.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDead(FMLServerStoppedEvent event) {
        /* Clear as much data from the integrated server as possible, in case a mod holds on to it */
        try {
            Field updatingMapField = ObfuscationReflectionHelper.findField(ChunkMap.class, "field_219251_e");
            Field visibleMapField = ObfuscationReflectionHelper.findField(ChunkMap.class, "field_219252_f");
            Field pendingUnloadsField = ObfuscationReflectionHelper.findField(ChunkMap.class, "field_219253_g");
            for(ServerLevel level : event.getServer().getAllLevels()) {
                ChunkMap chunkMap = level.getChunkSource().chunkMap;
                Long2ObjectMap<ChunkHolder> map = (Long2ObjectMap<ChunkHolder>)updatingMapField.get(chunkMap);
                map.clear();
                map = (Long2ObjectMap<ChunkHolder>)visibleMapField.get(chunkMap);
                map.clear();
                map = (Long2ObjectMap<ChunkHolder>)pendingUnloadsField.get(chunkMap);
                map.clear();
            }
        } catch(RuntimeException | IllegalAccessException e) {
            ModernFix.LOGGER.error("Couldn't clear chunk data", e);
        }
    }
}
