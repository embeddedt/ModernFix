package org.embeddedt.modernfix;

import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MemoryReserve;
import net.minecraft.world.entity.Entity;
import org.embeddedt.modernfix.api.constants.IntegrationConstants;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.util.ClassInfoManager;
import org.embeddedt.modernfix.world.IntegratedWatchdog;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModernFixClient {
    public static ModernFixClient INSTANCE;
    public static long worldLoadStartTime = -1;
    private static int numRenderTicks;

    public static float gameStartTimeSeconds = -1;

    public static boolean recipesUpdated, tagsUpdated = false;

    public String brandingString = null;

    /**
     * The list of loaded client integrations.
     */
    public static List<ModernFixClientIntegration> CLIENT_INTEGRATIONS = new CopyOnWriteArrayList<>();

    public ModernFixClient() {
        INSTANCE = this;
        // clear reserve as it's not needed
        MemoryReserve.release();
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.branding.F3Screen")) {
            brandingString = ModernFix.NAME + " " + ModernFixPlatformHooks.INSTANCE.getVersionString();
        }
        for(String className : ModernFixPlatformHooks.INSTANCE.getCustomModOptions().get(IntegrationConstants.CLIENT_INTEGRATION_CLASS)) {
            try {
                CLIENT_INTEGRATIONS.add((ModernFixClientIntegration)Class.forName(className).getDeclaredConstructor().newInstance());
            } catch(ReflectiveOperationException | ClassCastException e) {
                ModernFix.LOGGER.error("Could not instantiate integration {}", className, e);
            }
        }

        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.FireIntegrationHook")) {
            for(ModernFixClientIntegration integration : ModernFixClient.CLIENT_INTEGRATIONS) {
                integration.onDynamicResourcesStatusChange(true);
            }
        }
    }

    public void resetWorldLoadStateMachine() {
        numRenderTicks = 0;
        worldLoadStartTime = -1;
        recipesUpdated = false;
        tagsUpdated = false;
    }

    public void onGameLaunchFinish() {
        if(gameStartTimeSeconds >= 0)
            return;
        gameStartTimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.measure_time.GameLoad"))
            ModernFix.LOGGER.warn("Game took " + gameStartTimeSeconds + " seconds to start");
        ModernFixPlatformHooks.INSTANCE.onLaunchComplete();
        ClassInfoManager.clear();
    }

    public void onRecipesUpdated() {
        recipesUpdated = true;
    }

    public void onTagsUpdated() {
        tagsUpdated = true;
    }

    public void onRenderTickEnd() {
        if(recipesUpdated
                && tagsUpdated
                && worldLoadStartTime != -1
                && Minecraft.getInstance().player != null
                && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.measure_time.WorldLoad")) {
                ModernFix.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
                ModernFix.LOGGER.warn("Total time to load game and open world was " + (timeSpentLoading + gameStartTimeSeconds) + " seconds");
            }
            resetWorldLoadStateMachine();
        }
    }

    /**
     * Check if the IDs match and remap them if not.
     * @return true if ID remap was needed
     */
    private static boolean compareAndSwitchIds(Class<? extends Entity> eClass, String fieldName, EntityDataAccessor<?> accessor, int newId) {
        if(accessor.id != newId) {
            ModernFix.LOGGER.warn("Corrected ID mismatch on {} field {}. Client had {} but server wants {}.",
                    eClass,
                    fieldName,
                    accessor.id,
                    newId);
            accessor.id = newId;
            return true;
        } else {
            ModernFix.LOGGER.debug("{} {} ID fine: {}", eClass, fieldName, newId);
            return false;
        }
    }

    public void onServerStarted(MinecraftServer server) {
        if(!ModernFixMixinPlugin.instance.isOptionEnabled("feature.integrated_server_watchdog.IntegratedWatchdog"))
            return;
        IntegratedWatchdog watchdog = new IntegratedWatchdog(server);
        watchdog.start();
    }
}
