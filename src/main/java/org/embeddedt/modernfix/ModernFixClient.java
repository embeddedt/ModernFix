package org.embeddedt.modernfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.management.ManagementFactory;

public class ModernFixClient {

    public static long worldLoadStartTime;
    private static int numRenderTicks;

    public static float gameStartTimeSeconds = -1;

    public void resetWorldLoadStateMachine() {
        numRenderTicks = 0;
        worldLoadStartTime = -1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMultiplayerConnect(GuiOpenEvent event) {
        if(event.getGui() instanceof ConnectingScreen && !event.isCanceled()) {
            worldLoadStartTime = System.nanoTime();
        } else if (event.getGui() instanceof MainMenuScreen && gameStartTimeSeconds < 0) {
            gameStartTimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            ModernFix.LOGGER.warn("Game took " + gameStartTimeSeconds + " seconds to start");
        }
    }

    @SubscribeEvent
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END && worldLoadStartTime != -1 && Minecraft.getInstance().player != null && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            ModernFix.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
            ModernFix.LOGGER.warn("Total time to load game and open world was " + (timeSpentLoading + gameStartTimeSeconds) + " seconds");
            resetWorldLoadStateMachine();
        }
    }

}
