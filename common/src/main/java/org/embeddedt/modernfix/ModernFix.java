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
import org.embeddedt.modernfix.resources.ReloadExecutor;
import org.embeddedt.modernfix.util.ClassInfoManager;
import org.embeddedt.modernfix.world.IntegratedWatchdog;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;

// The value here should match an entry in the META-INF/mods.toml file
public class ModernFix {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger("ModernFix");

    public static final String MODID = "modernfix";

    public static ModernFix INSTANCE;

    // Used to skip computing the blockstate caches twice
    public static boolean runningFirstInjection = false;

    private static Executor resourceReloadService = null;

    static {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dedicated_reload_executor.ReloadExecutor")) {
            resourceReloadService = ReloadExecutor.createCustomResourceReloadExecutor();
        } else {
            resourceReloadService = Util.backgroundExecutor();
        }
    }

    public static Executor resourceReloadExecutor() {
        return resourceReloadService;
    }


    public ModernFix() {
        INSTANCE = this;
        ModernFixPlatformHooks.onServerCommandRegister(ModernFixCommands::register);
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spam_thread_dump.ThreadDumper")) {
            Thread t = new Thread() {
                public void run() {
                    while(true) {
                        LOGGER.error("------ DEBUG THREAD DUMP (occurs every 60 seconds) ------");
                        LOGGER.error(IntegratedWatchdog.obtainThreadDump());
                        try { Thread.sleep(60000); } catch(InterruptedException e) {}
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
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
