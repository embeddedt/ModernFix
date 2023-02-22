package org.embeddedt.modernfix.load;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.screen.DeferredLevelLoadingScreen;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Handles deferring the world load screen.
 * <p></p>
 * TODO: The vanilla check that at least 441 chunks have been loaded does not check whether they are spawn chunks
 * or chunks loaded by the player. Consequently it is possible for loading to finish before every spawn chunk has
 * been loaded. However the chunk system has at least been warmed up by this point so the remaining chunks load
 * reasonably quickly.
 */
public class LoadEvents {
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
        if(ServerLifecycleHooks.getCurrentServer() instanceof IntegratedServer) {
            if(event.getGui() == null && Minecraft.getInstance().level != null && integratedWorldLoadListener != null) {
                /* this means the world is about to be displayed, check if 441 initialized */
                ServerChunkCache provider = ServerLifecycleHooks.getCurrentServer().overworld().getChunkSource();
                BooleanSupplier worldLoadDone = () -> provider.getTickingGenerated() >= 441;
                if(!worldLoadDone.getAsBoolean()) {
                    DeferredLevelLoadingScreen newScreen = new DeferredLevelLoadingScreen(Minecraft.getInstance().progressListener.get(), worldLoadDone);
                    event.setGui(newScreen);
                }
            } else if(event.getGui() instanceof LevelLoadingScreen && Minecraft.getInstance().level == null && ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_singleplayer_load.ClientEvents")) {
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
