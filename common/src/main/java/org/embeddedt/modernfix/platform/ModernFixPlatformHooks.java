package org.embeddedt.modernfix.platform;

import com.google.common.collect.Multimap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
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

    String getPlatformName();
}
