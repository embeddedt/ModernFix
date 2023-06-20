package org.embeddedt.modernfix.forge.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.forge.classloading.ClassLoadHack;
import org.embeddedt.modernfix.forge.classloading.ModFileScanDataDeduplicator;
import org.embeddedt.modernfix.forge.ModernFixConfig;
import org.embeddedt.modernfix.entity.EntityDataIDSyncHandler;
import org.embeddedt.modernfix.forge.config.ConfigFixer;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.embeddedt.modernfix.forge.registry.ObjectHolderClearer;
import org.embeddedt.modernfix.forge.util.KubeUtil;

@Mod(ModernFix.MODID)
public class ModernFixForge {
    private static ModernFix commonMod;

    public ModernFixForge() {
        commonMod = new ModernFix();
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadComplete);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Item.class, this::registerItems);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ModernFixClientForge()));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModernFixConfig.COMMON_CONFIG);
        if(ModList.get().isLoaded("kubejs"))
            MinecraftForge.EVENT_BUS.register(KubeUtil.class);
        PacketHandler.register();
        ModFileScanDataDeduplicator.deduplicate();
        ClassLoadHack.loadModClasses();
        ConfigFixer.replaceConfigHandlers();
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if(event.getPlayer() != null) {
            if(!ServerLifecycleHooks.getCurrentServer().isDedicatedServer() && event.getPlayerList().getPlayerCount() == 0)
                return;
            EntityDataIDSyncHandler.onDatapackSyncEvent(event.getPlayer());
        }
    }

    private void registerItems(RegistryEvent<Item> event) {
        if(Boolean.getBoolean("modernfix.largeRegistryTest")) {
            Item.Properties props = new Item.Properties();
            for(int i = 0; i < 1000000; i++) {
                ForgeRegistries.ITEMS.register(new Item(props).setRegistryName("modernfix", "item_" + i));
            }
        }
    }

    private static boolean dfuModPresent() {
        for(String modId : new String[] { "lazydfu", "datafixerslayer" }) {
            if(ModList.get().isLoaded(modId))
                return true;
        }
        return !FMLLoader.isProduction();
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        if(!dfuModPresent()) {
            event.enqueueWork(() -> {
                ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.COMMON_SETUP, "modernfix.no_lazydfu"));
            });
        }
        ObjectHolderClearer.clearThrowables();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDead(FMLServerStoppedEvent event) {
        commonMod.onServerDead(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        commonMod.onLoadComplete();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStarted(FMLServerStartedEvent event) {
        commonMod.onServerStarted();
    }
}
