package org.embeddedt.modernfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModernFixClient {

    public static long worldLoadStartTime;
    private static int numRenderTicks;

    public void resetWorldLoadStateMachine() {
        numRenderTicks = 0;
        worldLoadStartTime = -1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMultiplayerConnect(GuiOpenEvent event) {
        if(event.getGui() instanceof ConnectingScreen && !event.isCanceled()) {
            worldLoadStartTime = System.nanoTime();
        }
    }

    @SubscribeEvent
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END && worldLoadStartTime != -1 && Minecraft.getInstance().player != null && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            ModernFix.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
            resetWorldLoadStateMachine();
        }
    }

}
