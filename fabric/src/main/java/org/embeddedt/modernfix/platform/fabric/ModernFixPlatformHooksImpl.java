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
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.ModernFixFabric;
import org.embeddedt.modernfix.api.constants.IntegrationConstants;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModernFixPlatformHooksImpl {
    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    private static String verString;

    public static String getVersionString() {
        if(verString == null) {
            ModContainer mfModContainer = FabricLoader.getInstance().getModContainer("modernfix").get();
            verString = mfModContainer.getMetadata().getVersion().getFriendlyString();
        }
        return verString;
    }

    public static boolean modPresent(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).isPresent();
    }

    public static boolean isDevEnv() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static MinecraftServer getCurrentServer() {
        return ModernFixFabric.theServer;
    }

    public static boolean isLoadingNormally() {
        return true;
    }

    public static Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static void sendPacket(ServerPlayer player, Object packet) {
        //PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void injectPlatformSpecificHacks() {
    }

    public static void applyASMTransformers(String mixinClassName, ClassNode targetClass) {

    }

    public static void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        CommandRegistrationCallback.EVENT.register((dispatcher, arg, env) -> handler.accept(dispatcher));
    }

    private static Multimap<String, String> modOptions;
    public static Multimap<String, String> getCustomModOptions() {
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

    public static void registerCreativeSearchTrees(SearchRegistry registry, SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier, BiConsumer<SearchRegistry.Key<ItemStack>, List<ItemStack>> populator) {
        /* no-op on Fabric */
    }
}
