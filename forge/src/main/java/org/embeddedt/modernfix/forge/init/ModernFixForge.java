package org.embeddedt.modernfix.forge.init;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.classloading.ClassLoadHack;
import org.embeddedt.modernfix.forge.classloading.ModFileScanDataDeduplicator;
import org.embeddedt.modernfix.forge.ModernFixConfig;
import org.embeddedt.modernfix.entity.EntityDataIDSyncHandler;
import org.embeddedt.modernfix.forge.config.ConfigFixer;
import org.embeddedt.modernfix.forge.config.NightConfigFixer;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.embeddedt.modernfix.forge.registry.ObjectHolderClearer;
import org.embeddedt.modernfix.forge.util.KubeUtil;

import java.util.List;

@Mod(ModernFix.MODID)
public class ModernFixForge {
    private static ModernFix commonMod;
    public static boolean launchDone = false;

    public ModernFixForge() {
        commonMod = new ModernFix();
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if(FMLEnvironment.dist == Dist.DEDICATED_SERVER && event.phase == TickEvent.Phase.END && ModernFixForge.launchDone) {
            NightConfigFixer.runReloads();
        }
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

    private static final List<Pair<List<String>, String>> MOD_WARNINGS = ImmutableList.of(
            Pair.of(ImmutableList.of("lazydfu", "datafixerslayer"), "modernfix.no_lazydfu"),
            Pair.of(ImmutableList.of("ferritecore"), "modernfix.no_ferritecore")
    );

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.warn_missing_perf_mods.Warnings")) {
            event.enqueueWork(() -> {
                boolean atLeastOneWarning = false;
                for(Pair<List<String>, String> warning : MOD_WARNINGS) {
                    boolean isPresent = !FMLLoader.isProduction() || warning.getLeft().stream().anyMatch(name -> ModList.get().isLoaded(name));
                    if(!isPresent) {
                        atLeastOneWarning = true;
                        ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.COMMON_SETUP, warning.getRight()));
                    }
                }
                if(atLeastOneWarning)
                    ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(), ModLoadingStage.COMMON_SETUP, "modernfix.perf_mod_warning"));
            });
        }
        ObjectHolderClearer.clearThrowables();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDead(FMLServerStoppedEvent event) {
        commonMod.onServerDead(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStarted(FMLServerStartedEvent event) {
        commonMod.onServerStarted();
    }
}
