package org.embeddedt.modernfix.platform.forge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.CreativeModeTabSearchRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.api.constants.IntegrationConstants;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.classloading.ATInjector;
import org.embeddedt.modernfix.forge.classloading.FastAccessTransformerList;
import org.embeddedt.modernfix.forge.config.NightConfigFixer;
import org.embeddedt.modernfix.forge.init.ModernFixForge;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.embeddedt.modernfix.util.CommonModUtil;
import org.embeddedt.modernfix.util.DummyList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModernFixPlatformHooksImpl implements ModernFixPlatformHooks {
    public boolean isClient() {
        return FMLLoader.getDist() == Dist.CLIENT;
    }

    public boolean isDedicatedServer() {
        return FMLLoader.getDist().isDedicatedServer();
    }

    private static String verString;

    public String getVersionString() {
        if(verString == null) {
            try {
                verString = ModernFixMixinPlugin.class.getPackage().getImplementationVersion();
                Objects.requireNonNull(verString);
            } catch(Throwable e) {
                verString = "[unknown]";
            }
        }
        return verString;
    }

    public boolean modPresent(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    public boolean isDevEnv() {
        return !FMLLoader.isProduction();
    }

    public MinecraftServer getCurrentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public boolean isEarlyLoadingNormally() {
        return LoadingModList.get().getErrors().isEmpty();
    }

    public boolean isLoadingNormally() {
        return isEarlyLoadingNormally() && ModLoader.isLoadingStateValid();
    }

    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    public void sendPacket(ServerPlayer player, Object packet) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public void injectPlatformSpecificHacks() {
        if(!isEarlyLoadingNormally() && ModernFixMixinPlugin.instance.isOptionEnabled("bugfix.forge_at_inject_error.ATInjector")) {
            ATInjector.injectModATs();
        }
        FastAccessTransformerList.attemptReplace();

        /* https://github.com/FabricMC/Mixin/pull/99 */
        try {
            Field groupMembersField = InjectorGroupInfo.class.getDeclaredField("members");
            groupMembersField.setAccessible(true);
            Field noGroupField = InjectorGroupInfo.Map.class.getDeclaredField("NO_GROUP");
            noGroupField.setAccessible(true);
            InjectorGroupInfo noGroup = (InjectorGroupInfo)noGroupField.get(null);
            groupMembersField.set(noGroup, new DummyList<>());
        } catch(RuntimeException | ReflectiveOperationException e) {
            ModernFixMixinPlugin.instance.logger.error("Failed to patch mixin memory leak", e);
        }

        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spark_profile_launch.OnForge")) {
            CommonModUtil.runWithoutCrash(() -> SparkLaunchProfiler.start("launch"), "Failed to start profiler");
        }

        NightConfigFixer.monitorFileWatcher();
    }

    public void applyASMTransformers(String mixinClassName, ClassNode targetClass) {

    }

    public void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            handler.accept(event.getDispatcher());
        });
    }

    private static Multimap<String, String> modOptions;
    public Multimap<String, String> getCustomModOptions() {
        if(modOptions == null) {
            modOptions = ArrayListMultimap.create();
            for (ModInfo meta : LoadingModList.get().getMods()) {
                meta.getConfigElement(IntegrationConstants.INTEGRATIONS_KEY).ifPresent(optionsObj -> {
                    if(optionsObj instanceof Map) {
                        Map<Object, Object> options = (Map<Object, Object>)optionsObj;
                        options.forEach((key, value) -> {
                            if(key instanceof String && value instanceof String) {
                                modOptions.put((String)key, (String)value);
                            }
                        });
                    }
                });
            }
        }
        return modOptions;
    }

    public void registerCreativeSearchTrees(SearchRegistry registry, SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier, BiConsumer<SearchRegistry.Key<ItemStack>, List<ItemStack>> populator) {
        for (SearchRegistry.Key<ItemStack> nameKey : CreativeModeTabSearchRegistry.getNameSearchKeys().values()) {
            registry.register(nameKey, nameSupplier);
        }
        for (SearchRegistry.Key<ItemStack> tagKey : CreativeModeTabSearchRegistry.getTagSearchKeys().values()) {
            registry.register(tagKey, tagSupplier);
        }
        Map<CreativeModeTab, SearchRegistry.Key<ItemStack>> tagSearchKeys = CreativeModeTabSearchRegistry.getTagSearchKeys();
        CreativeModeTabSearchRegistry.getNameSearchKeys().forEach((tab, nameSearchKey) -> {
            SearchRegistry.Key<ItemStack> tagSearchKey = tagSearchKeys.get(tab);
            tab.setSearchTreeBuilder((contents) -> {
                populator.accept(nameSearchKey, contents);
                populator.accept(tagSearchKey, contents);
            });
        });
    }

    public void onLaunchComplete() {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spark_profile_launch.OnForge")) {
            CommonModUtil.runWithoutCrash(() -> SparkLaunchProfiler.stop("launch"), "Failed to stop profiler");
        }
        ModernFixForge.launchDone = true;
    }

    public String getPlatformName() {
        return "Forge";
    }
}
