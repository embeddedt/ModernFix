package org.embeddedt.modernfix.platform;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
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

    
    default TextureAtlasSprite loadTextureAtlasSprite(TextureAtlas atlasTexture,
                                                            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
                                                            Resource resource,
                                                            int atlasWidth, int atlasHeight,
                                                            int spriteX, int spriteY, int mipmapLevel,
                                                            NativeImage image) {
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

    
    default void onLaunchComplete() {

    }

    
    default String getPlatformName() {
        throw new AssertionError();
    }
}
