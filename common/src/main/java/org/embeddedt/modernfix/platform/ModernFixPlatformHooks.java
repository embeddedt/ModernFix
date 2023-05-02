package org.embeddedt.modernfix.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;

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
}
