package org.embeddedt.modernfix;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.command.ModernFixCommands;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.resources.ReloadExecutor;
import org.embeddedt.modernfix.util.ClassInfoManager;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;

// The value here should match an entry in the META-INF/mods.toml file
public class ModernFix {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger("ModernFix");

    public static final String MODID = "modernfix";

    public static String NAME = "ModernFix";

    public static ModernFix INSTANCE;

    // Used to skip computing the blockstate caches twice
    public static boolean runningFirstInjection = false;

    private static ExecutorService resourceReloadService = null;

    static {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dedicated_reload_executor.ReloadExecutor")) {
            resourceReloadService = ReloadExecutor.createCustomResourceReloadExecutor();
        } else {
            resourceReloadService = Util.backgroundExecutor();
        }
    }

    public static ExecutorService resourceReloadExecutor() {
        return resourceReloadService;
    }


    public ModernFix() {
        INSTANCE = this;
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.snapshot_easter_egg.NameChange") && !SharedConstants.getCurrentVersion().isStable())
            NAME = "PreemptiveFix";
        ModernFixPlatformHooks.INSTANCE.onServerCommandRegister(ModernFixCommands::register);
    }

    public void onServerStarted() {
        if(ModernFixPlatformHooks.INSTANCE.isDedicatedServer()) {
            float gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.measure_time.ServerLoad"))
                ModernFix.LOGGER.warn("Dedicated server took " + gameStartTime + " seconds to load");
            ModernFixPlatformHooks.INSTANCE.onLaunchComplete();
        }
        ClassInfoManager.clear();
    }

    @SuppressWarnings("ConstantValue")
    public void onServerDead(MinecraftServer server) {
        /* Clear as much data from the integrated server as possible, in case a mod holds on to it */
        try {
            for(ServerLevel level : server.getAllLevels()) {
                ChunkMap chunkMap = level.getChunkSource().chunkMap;
                // Null check for mods that replace chunk system
                if(chunkMap.updatingChunkMap != null)
                    chunkMap.updatingChunkMap.clear();
                if(chunkMap.visibleChunkMap != null)
                    chunkMap.visibleChunkMap.clear();
                if(chunkMap.pendingUnloads != null)
                    chunkMap.pendingUnloads.clear();
            }
        } catch(RuntimeException e) {
            ModernFix.LOGGER.error("Couldn't clear chunk data", e);
        }
    }
}
