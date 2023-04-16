package org.embeddedt.modernfix;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.embeddedt.modernfix.entity.EntityDataIDSyncHandler;
import org.embeddedt.modernfix.packet.PacketHandler;
import org.embeddedt.modernfix.registry.ObjectHolderClearer;
import org.embeddedt.modernfix.structure.AsyncLocator;
import org.embeddedt.modernfix.util.KubeUtil;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ModernFixClient()));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModernFixConfig.COMMON_CONFIG);
        if(ModList.get().isLoaded("kubejs"))
            MinecraftForge.EVENT_BUS.register(KubeUtil.class);
        MinecraftForge.EVENT_BUS.register(EntityDataIDSyncHandler.class);
        PacketHandler.register();
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
    }
}
