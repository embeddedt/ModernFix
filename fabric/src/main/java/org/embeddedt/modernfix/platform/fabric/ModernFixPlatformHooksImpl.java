package org.embeddedt.modernfix.platform.fabric;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFixFabric;
import org.embeddedt.modernfix.api.constants.IntegrationConstants;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.embeddedt.modernfix.util.CommonModUtil;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModernFixPlatformHooksImpl implements ModernFixPlatformHooks {
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    private static String verString;

    public String getVersionString() {
        if(verString == null) {
            ModContainer mfModContainer = FabricLoader.getInstance().getModContainer("modernfix").get();
            verString = mfModContainer.getMetadata().getVersion().getFriendlyString();
        }
        return verString;
    }

    public boolean modPresent(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).isPresent();
    }

    public boolean isDevEnv() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public MinecraftServer getCurrentServer() {
        return ModernFixFabric.theServer.get();
    }

    public boolean isEarlyLoadingNormally() {
        return true;
    }

    public boolean isLoadingNormally() {
        return true;
    }

    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public void sendPacket(ServerPlayer player, Object packet) {
        //PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public void injectPlatformSpecificHacks() {
    }

    public void applyASMTransformers(String mixinClassName, ClassNode targetClass) {

    }

    public void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        if(FabricLoader.getInstance().isModLoaded("fabric-command-api-v2"))
            CommandRegistrationCallback.EVENT.register((dispatcher, arg, env) -> handler.accept(dispatcher));
    }

    private static Multimap<String, String> modOptions;
    public Multimap<String, String> getCustomModOptions() {
        if(modOptions == null) {
            modOptions = ArrayListMultimap.create();
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                ModMetadata meta = container.getMetadata();
                if (meta.containsCustomValue(IntegrationConstants.INTEGRATIONS_KEY)) {
                    CustomValue integrations = meta.getCustomValue(IntegrationConstants.INTEGRATIONS_KEY);
                    if (integrations.getType() != CustomValue.CvType.OBJECT) {
                        continue;
                    }
                    for (Map.Entry<String, CustomValue> entry : integrations.getAsObject()) {
                        if(entry.getValue().getType() != CustomValue.CvType.STRING)
                            continue;
                        modOptions.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        return modOptions;
    }

    public void registerCreativeSearchTrees(SearchRegistry registry, SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier, BiConsumer<SearchRegistry.Key<ItemStack>, List<ItemStack>> populator) {
        CreativeModeTabs.searchTab().setSearchTreeBuilder((list) -> {
            populator.accept(SearchRegistry.CREATIVE_NAMES, list);
            populator.accept(SearchRegistry.CREATIVE_TAGS, list);
        });
    }

    public void onLaunchComplete() {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spark_profile_launch.OnFabric")) {
            CommonModUtil.runWithoutCrash(() -> SparkLaunchProfiler.stop("launch"), "Failed to stop profiler");
        }
    }

    public String getPlatformName() {
        return "Fabric";
    }
}
