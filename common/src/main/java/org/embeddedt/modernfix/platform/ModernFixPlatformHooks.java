package org.embeddedt.modernfix.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.function.Consumer;

public class ModernFixPlatformHooks {
    @ExpectPlatform
    public static boolean isClient() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isDedicatedServer() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getVersionString() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean modPresent(String modId) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isDevEnv() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void injectPlatformSpecificHacks() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void applyASMTransformers(String mixinClassName, ClassNode targetClass) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static MinecraftServer getCurrentServer() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isLoadingNormally() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Path getGameDirectory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendPacket(ServerPlayer player, Object packet) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        throw new AssertionError();
    }
}
