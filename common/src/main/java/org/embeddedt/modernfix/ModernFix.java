package org.embeddedt.modernfix;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.command.ModernFixCommands;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.util.ClassInfoManager;

import java.lang.management.ManagementFactory;
import java.util.concurrent.*;

// The value here should match an entry in the META-INF/mods.toml file
public class ModernFix {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger("ModernFix");

    public static final String MODID = "modernfix";

    public static ModernFix INSTANCE;

    // Used to skip computing the blockstate caches twice
    public static boolean runningFirstInjection = false;

    private static ExecutorService resourceReloadService = null;

    static {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dedicated_reload_executor.ReloadExecutor")) {
            resourceReloadService = Util.makeExecutor("ResourceReload");
        } else {
            resourceReloadService = Util.backgroundExecutor();
        }
    }

    public static ExecutorService resourceReloadExecutor() {
        return resourceReloadService;
    }


    public ModernFix() {
        INSTANCE = this;
        ModernFixPlatformHooks.onServerCommandRegister(ModernFixCommands::register);
    }

    public void onServerStarted() {
        if(ModernFixPlatformHooks.isDedicatedServer()) {
            float gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            ModernFix.LOGGER.warn("Dedicated server took " + gameStartTime + " seconds to load");
        }
        ClassInfoManager.clear();
    }

    public void onLoadComplete() {
        ClassInfoManager.clear();
    }

    public void onServerDead(MinecraftServer server) {
        /* Clear as much data from the integrated server as possible, in case a mod holds on to it */
        try {
            for(ServerLevel level : server.getAllLevels()) {
                ChunkMap chunkMap = level.getChunkSource().chunkMap;
                chunkMap.updatingChunkMap.clear();
                chunkMap.visibleChunkMap.clear();
                chunkMap.pendingUnloads.clear();
            }
        } catch(RuntimeException e) {
            ModernFix.LOGGER.error("Couldn't clear chunk data", e);
        }
    }
}
