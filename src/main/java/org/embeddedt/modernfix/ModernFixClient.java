package org.embeddedt.modernfix;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.embeddedt.modernfix.screen.DeferredLevelLoadingScreen;

import java.lang.management.ManagementFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

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
        if(event.getGui() instanceof ConnectScreen && !event.isCanceled()) {
            worldLoadStartTime = System.nanoTime();
        } else if (event.getGui() instanceof TitleScreen && gameStartTimeSeconds < 0) {
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
        }
    }

    private boolean hasFirstPlayerJoined = false;

    @SubscribeEvent
    public void serverWillStart(FMLServerAboutToStartEvent event) {
        hasFirstPlayerJoined = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if(!hasFirstPlayerJoined && ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_singleplayer_load.ClientEvents")) {
            hasFirstPlayerJoined = true;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if(server instanceof IntegratedServer) {
                handleInitialChunkLoad();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldShow(GuiOpenEvent event) {
        if(ServerLifecycleHooks.getCurrentServer() instanceof IntegratedServer && ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_singleplayer_load.ClientEvents")) {
            if(event.getGui() == null && Minecraft.getInstance().level != null) {
                /* this means the world is being displayed, check if 441 initialized */
                ServerChunkCache provider = ServerLifecycleHooks.getCurrentServer().overworld().getChunkSource();
                BooleanSupplier worldLoadDone = () -> provider.getTickingGenerated() >= 441;
                if(!worldLoadDone.getAsBoolean()) {
                    DeferredLevelLoadingScreen newScreen = new DeferredLevelLoadingScreen(Minecraft.getInstance().progressListener.get(), worldLoadDone);
                    event.setGui(newScreen);
                }
            } else if(event.getGui() instanceof LevelLoadingScreen && Minecraft.getInstance().level == null) {
                ProgressScreen loadscreen = new ProgressScreen();
                loadscreen.progressStartNoAbort(new TranslatableComponent("connect.joining"));
                event.setGui(loadscreen);
            }
        }
    }

    public static ChunkProgressListener integratedWorldLoadListener;

    private void handleInitialChunkLoad() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel overworld = server.overworld();
        ServerChunkCache provider = overworld.getChunkSource();
        provider.getLightEngine().setTaskPerBatch(500);
        provider.addRegionTicket(TicketType.START, new ChunkPos(overworld.getSharedSpawnPos()), 11, Unit.INSTANCE);
        while(provider.getTickingGenerated() < 441) {
            server.runAllTasks();
            Thread.yield();
            LockSupport.parkNanos("waiting for world load", 100000L);
            server.nextTickTime = Util.getMillis() + 10;
        }
        for(ServerLevel serverworld1 : server.getAllLevels()) {
            ForcedChunksSavedData forcedchunkssavedata = serverworld1.getDataStorage().get(ForcedChunksSavedData::new, "chunks");
            if (forcedchunkssavedata != null) {
                LongIterator longiterator = forcedchunkssavedata.getChunks().iterator();

                while(longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    ChunkPos chunkpos = new ChunkPos(i);
                    serverworld1.getChunkSource().updateChunkForced(chunkpos, true);
                }

                ForgeChunkManager.reinstatePersistentChunks(serverworld1, forcedchunkssavedata);
            }
        }
        server.runAllTasks();
        server.nextTickTime = Util.getMillis() + 10;
        provider.getLightEngine().setTaskPerBatch(5);
        if(integratedWorldLoadListener != null) {
            integratedWorldLoadListener.stop();
            integratedWorldLoadListener = null;
        }
    }

}
