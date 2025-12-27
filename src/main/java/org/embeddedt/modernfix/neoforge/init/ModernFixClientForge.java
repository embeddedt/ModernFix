package org.embeddedt.modernfix.neoforge.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.screen.ModernFixConfigScreen;
import org.jspecify.annotations.Nullable;

public class ModernFixClientForge {
    private static ModernFixClient commonMod;

    public ModernFixClientForge(ModContainer modContainer, IEventBus modBus) {
        commonMod = new ModernFixClient();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRenderOverlay);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (mc, screen) -> new ModernFixConfigScreen(screen));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if(false) {
            event.enqueueWork(() -> {
                ModLoader.addLoadingIssue(ModLoadingIssue.warning("modernfix.connectedness_dynresoruces"));
            });
        }
    }

    private static final Identifier MODERNFIX_VERSION = Identifier.fromNamespaceAndPath(ModernFix.MODID, "version");

    private void onRenderOverlay(RegisterDebugEntriesEvent event) {
        event.register(MODERNFIX_VERSION, new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
                if (commonMod.brandingString != null) {
                    displayer.addToGroup(MODERNFIX_VERSION, commonMod.brandingString);
                }
            }
        });
        event.includeInProfile(MODERNFIX_VERSION, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) {
            DebugScreenOverlay overlay = Minecraft.getInstance().getDebugOverlay();
            Minecraft.getInstance().schedule(overlay::clearChunkCache);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartedEvent event) {
        commonMod.onServerStarted(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTickEnd(RenderFrameEvent.Post event) {
        commonMod.onRenderTickEnd();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRecipes(RecipesReceivedEvent e) {
        commonMod.onRecipesUpdated();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTags(TagsUpdatedEvent e) {
        commonMod.onTagsUpdated();
    }
}
