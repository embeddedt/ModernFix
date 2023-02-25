package org.embeddedt.modernfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.load.LoadEvents;
import org.embeddedt.modernfix.screen.DeferredLevelLoadingScreen;

import java.lang.management.ManagementFactory;
import java.util.Optional;

public class ModernFixClient {
    public static long worldLoadStartTime;
    private static int numRenderTicks;

    public static float gameStartTimeSeconds = -1;

    private String brandingString = null;

    public ModernFixClient() {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_singleplayer_load.ClientEvents")) {
            MinecraftForge.EVENT_BUS.register(new LoadEvents());
        }
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.branding.F3Screen")) {
            Optional<? extends ModContainer> mfContainer = ModList.get().getModContainerById("modernfix");
            if(mfContainer.isPresent())
                brandingString = "ModernFix " + mfContainer.get().getModInfo().getVersion().toString();
        }
    }

    public void resetWorldLoadStateMachine() {
        numRenderTicks = 0;
        worldLoadStartTime = -1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMultiplayerConnect(ScreenOpenEvent event) {
        if(event.getScreen() instanceof ConnectScreen && !event.isCanceled()) {
            worldLoadStartTime = System.nanoTime();
        } else if (event.getScreen() instanceof TitleScreen && gameStartTimeSeconds < 0) {
            gameStartTimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            ModernFix.LOGGER.warn("Game took " + gameStartTimeSeconds + " seconds to start");
        }
    }

    @SubscribeEvent
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END && !(Minecraft.getInstance().screen instanceof DeferredLevelLoadingScreen) && worldLoadStartTime != -1 && Minecraft.getInstance().player != null && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            ModernFix.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
            ModernFix.LOGGER.warn("Total time to load game and open world was " + (timeSpentLoading + gameStartTimeSeconds) + " seconds");
            resetWorldLoadStateMachine();
            if(ModernFix.worldLoadSemaphore != null)
                ModernFix.worldLoadSemaphore.countDown();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if(brandingString != null && Minecraft.getInstance().options.renderDebug) {
            event.getLeft().add("");
            event.getLeft().add(brandingString);
        }
    }
}
