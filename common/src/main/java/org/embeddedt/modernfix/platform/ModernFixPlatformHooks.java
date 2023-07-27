package org.embeddedt.modernfix.platform;

import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.commands.CommandSourceStack;
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
    
    default boolean isClient() {
        throw new AssertionError();
    }

    
    default boolean isDedicatedServer() {
        throw new AssertionError();
    }

    
    default String getVersionString() {
        throw new AssertionError();
    }

    
    default boolean modPresent(String modId) {
        throw new AssertionError();
    }

    
    default boolean isDevEnv() {
        throw new AssertionError();
    }

    
    default void injectPlatformSpecificHacks() {
        throw new AssertionError();
    }

    
    default void applyASMTransformers(String mixinClassName, ClassNode targetClass) {
        throw new AssertionError();
    }

    
    default MinecraftServer getCurrentServer() {
        throw new AssertionError();
    }

    
    default boolean isEarlyLoadingNormally() {
        throw new AssertionError();
    }

    
    default boolean isLoadingNormally() {
        throw new AssertionError();
    }

    default Path getGameDirectory() {
        throw new AssertionError();
    }

    
    default void sendPacket(ServerPlayer player, Object packet) {
        throw new AssertionError();
    }

    
    default void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        throw new AssertionError();
    }

    
    default Multimap<String, String> getCustomModOptions() {
        throw new AssertionError();
    }

    default void registerCreativeSearchTrees(SearchRegistry registry, SearchRegistry.TreeBuilderSupplier<ItemStack> nameSupplier, SearchRegistry.TreeBuilderSupplier<ItemStack> tagSupplier, BiConsumer<SearchRegistry.Key<ItemStack>, List<ItemStack>> populator) {
        throw new AssertionError();
    }
    
    default void onLaunchComplete() {
        throw new AssertionError();
    }

    default String getPlatformName() {
        throw new AssertionError();
    }
}
