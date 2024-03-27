package org.embeddedt.modernfix.forge.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.config.NightConfigFixer;
import org.embeddedt.modernfix.screen.ModernFixConfigScreen;

import java.util.ArrayList;
import java.util.List;

public class ModernFixClientForge {
    private static ModernFixClient commonMod;

    public ModernFixClientForge() {
        commonMod = new ModernFixClient();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        ModLoadingContext.get().registerExtensionPoint(
                ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, screen) -> new ModernFixConfigScreen(screen)
        );
    }

    private KeyMapping configKey;

    private void clientSetup(FMLClientSetupEvent event) {
        configKey = new KeyMapping("key.modernfix.config", KeyConflictContext.UNIVERSAL, InputConstants.UNKNOWN, "key.modernfix");
        ClientRegistry.registerKeyBinding(configKey);
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.ConnectednessCheck")
            && ModList.get().isLoaded("connectedness")) {
            event.enqueueWork(() -> {
                ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.SIDED_SETUP, "modernfix.connectedness_dynresoruces"));
            });
        }
    }

    @SubscribeEvent
    public void onConfigKey(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.START && configKey != null && configKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new ModernFixConfigScreen(Minecraft.getInstance().screen));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientChat(ClientChatEvent event) {
        if(event.getMessage() != null && event.getMessage().trim().equals("/mfrc")) {
            NightConfigFixer.runReloads();
            event.setCanceled(true);
            // add it to chat history
            Minecraft.getInstance().gui.getChat().addRecentChat(event.getMessage());
        }
    }

    private static final List<String> brandingList = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if(commonMod.brandingString != null && Minecraft.getInstance().options.renderDebug) {
            if(brandingList.size() == 0) {
                brandingList.add("");
                brandingList.add(commonMod.brandingString);
            }
            int targetIdx = 0, numSeenBlanks = 0;
            List<String> right = event.getRight();
            while(targetIdx < right.size()) {
                String s = right.get(targetIdx);
                if(s == null || s.length() == 0) {
                    numSeenBlanks++;
                }
                if(numSeenBlanks == 3)
                    break;
                targetIdx++;
            }
            right.addAll(targetIdx, brandingList);
        }
    }

    @SubscribeEvent
    public void onDisconnect(WorldEvent.Unload event) {
        if(event.getWorld().isClientSide()) {
            DebugScreenOverlay overlay = ObfuscationReflectionHelper.getPrivateValue(ForgeIngameGui.class, (ForgeIngameGui)Minecraft.getInstance().gui, "debugOverlay");
            if(overlay != null) {
                Minecraft.getInstance().tell(overlay::clearChunkCache);
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartedEvent event) {
        commonMod.onServerStarted(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END)
            commonMod.onRenderTickEnd();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRecipes(RecipesUpdatedEvent e) {
        commonMod.onRecipesUpdated();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTags(TagsUpdatedEvent e) {
        commonMod.onTagsUpdated();
    }
}
