package org.embeddedt.modernfix.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
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
    public static TextureAtlasSprite loadTextureAtlasSprite(TextureAtlas atlasTexture,
                                                            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
                                                            Resource resource,
                                                            int atlasWidth, int atlasHeight,
                                                            int spriteX, int spriteY, int mipmapLevel,
                                                            NativeImage image) throws IOException {
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
