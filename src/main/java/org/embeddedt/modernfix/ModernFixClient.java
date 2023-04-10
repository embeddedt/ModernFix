package org.embeddedt.modernfix;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.NetworkEvent;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.load.LoadEvents;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;
import org.embeddedt.modernfix.screen.DeferredLevelLoadingScreen;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.sql.Ref;
import java.util.*;
import java.util.function.Supplier;

public class ModernFixClient {
    public static long worldLoadStartTime;
    private static int numRenderTicks;

    public static float gameStartTimeSeconds = -1;

    private static boolean recipesUpdated, tagsUpdated = false;

    private String brandingString = null;

    public ModernFixClient() {
        // clear reserve as it's not needed
        // TODO: port 1.18+
        // ObfuscationReflectionHelper.setPrivateValue(Minecraft.class, null, new byte[0], "field_71444_a");
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
        recipesUpdated = false;
        tagsUpdated = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMultiplayerConnect(ScreenEvent.Init.Pre event) {
        if(event.getScreen() instanceof ConnectScreen && !event.isCanceled()) {
            worldLoadStartTime = System.nanoTime();
        } else if (event.getScreen() instanceof TitleScreen && gameStartTimeSeconds < 0) {
            gameStartTimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            ModernFix.LOGGER.warn("Game took " + gameStartTimeSeconds + " seconds to start");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRecipesUpdated(RecipesUpdatedEvent event) {
        recipesUpdated = true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTagsUpdated(TagsUpdatedEvent event) {
        tagsUpdated = true;
    }

    @SubscribeEvent
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END
                && recipesUpdated
                && tagsUpdated
                && !(Minecraft.getInstance().screen instanceof DeferredLevelLoadingScreen)
                && worldLoadStartTime != -1
                && Minecraft.getInstance().player != null
                && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            ModernFix.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
            ModernFix.LOGGER.warn("Total time to load game and open world was " + (timeSpentLoading + gameStartTimeSeconds) + " seconds");
            resetWorldLoadStateMachine();
            if(ModernFix.worldLoadSemaphore != null)
                ModernFix.worldLoadSemaphore.countDown();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderOverlay(CustomizeGuiOverlayEvent.DebugText event) {
        if(brandingString != null && Minecraft.getInstance().options.renderDebug) {
            event.getLeft().add("");
            event.getLeft().add(brandingString);
        }
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) {
            DebugScreenOverlay overlay = ObfuscationReflectionHelper.getPrivateValue(ForgeGui.class, (ForgeGui)Minecraft.getInstance().gui, "debugOverlay");
            if(overlay != null) {
                Minecraft.getInstance().tell(overlay::clearChunkCache);
            }
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

    /**
     * Horrendous hack to allow tracking every synced entity data manager.
     *
     * This is to ensure we can perform ID fixup on already constructed managers.
     */
    public static final Set<SynchedEntityData> allEntityDatas = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Field entriesArrayField;
    static {
        Field field;
        try {
            field = SynchedEntityData.class.getDeclaredField("entriesArray");
            field.setAccessible(true);
        } catch(ReflectiveOperationException e) {
            field = null;
        }
        entriesArrayField = field;
    }

    /**
     * Extremely hacky method to detect and correct mismatched entity data parameter IDs on the client and server.
     *
     * The technique is far from ideal, but it should detect reliably and also not break already constructed entities.
     */
    public static void handleEntityIDSync(EntityIDSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        Map<Class<? extends Entity>, List<Pair<String, Integer>>> info = packet.getFieldInfo();
        context.get().enqueueWork(() -> {
            boolean fixNeeded = false;
            for(Map.Entry<Class<? extends Entity>, List<Pair<String, Integer>>> entry : info.entrySet()) {
                Class<? extends Entity> eClass = entry.getKey();
                for(Pair<String, Integer> field : entry.getValue()) {
                    String fieldName = field.getFirst();
                    int newId = field.getSecond();
                    try {
                        Field f = eClass.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        EntityDataAccessor<?> accessor = (EntityDataAccessor<?>)f.get(null);
                        if(compareAndSwitchIds(eClass, fieldName, accessor, newId))
                            fixNeeded = true;
                    } catch(NoSuchFieldException e) {
                        ModernFix.LOGGER.warn("Couldn't find field on {}: {}", eClass, fieldName);
                    } catch(ReflectiveOperationException e) {
                        throw new RuntimeException("Unexpected exception", e);
                    }
                }
            }
            /* Now the ID mappings on synced entity data instances are probably all wrong. Fix that. */
            List<SynchedEntityData> dataEntries;
            synchronized (allEntityDatas) {
                if(fixNeeded) {
                    dataEntries = new ArrayList<>(allEntityDatas);
                    for(SynchedEntityData manager : dataEntries) {
                        Int2ObjectOpenHashMap<SynchedEntityData.DataItem<?>> fixedMap = new Int2ObjectOpenHashMap<>();
                        List<SynchedEntityData.DataItem<?>> items = new ArrayList<>(manager.itemsById.values());
                        for(SynchedEntityData.DataItem<?> item : items) {
                            fixedMap.put(item.getAccessor().id, item);
                        }
                        manager.lock.writeLock().lock();
                        try {
                            manager.itemsById.replaceAll((id, parameter) -> fixedMap.get((int)id));
                            if(entriesArrayField != null) {
                                try {
                                    SynchedEntityData.DataItem<?>[] dataArray = new SynchedEntityData.DataItem[items.size()];
                                    for(int i = 0; i < dataArray.length; i++) {
                                        dataArray[i] = fixedMap.get(i);
                                    }
                                    entriesArrayField.set(manager, dataArray);
                                } catch(ReflectiveOperationException e) {
                                    ModernFix.LOGGER.error(e);
                                }
                            }
                        } finally {
                            manager.lock.writeLock().unlock();
                        }
                    }
                }
                allEntityDatas.clear();
            }
        });

        context.get().setPacketHandled(true);
    }
}
