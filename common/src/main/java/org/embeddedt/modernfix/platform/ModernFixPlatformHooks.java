package org.embeddedt.modernfix.platform;

import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface ModernFixPlatformHooks {
    ModernFixPlatformHooks INSTANCE = PlatformHookLoader.findInstance();
    
    boolean isClient();
    
    boolean isDedicatedServer();
    
    String getVersionString();

    boolean modPresent(String modId);
    
    boolean isDevEnv();

    void injectPlatformSpecificHacks();

    void applyASMTransformers(String mixinClassName, ClassNode targetClass);

    MinecraftServer getCurrentServer();

    boolean isEarlyLoadingNormally();

    boolean isLoadingNormally();

    Path getGameDirectory();

    void sendPacket(ServerPlayer player, CustomPacketPayload packet);

    Multimap<String, String> getCustomModOptions();

    void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler);

    void onLaunchComplete();

    void registerCreativeSearchTrees(SearchRegistry registry, SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier, BiConsumer<SearchRegistry.Key<ItemStack>, List<ItemStack>> populator);

    String getPlatformName();
}
