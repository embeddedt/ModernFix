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

import java.io.IOException;
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

    TextureAtlasSprite loadTextureAtlasSprite(TextureAtlas atlasTexture,
                                                            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
                                                            Resource resource,
                                                            int atlasWidth, int atlasHeight,
                                                            int spriteX, int spriteY, int mipmapLevel,
                                                            NativeImage image) throws IOException;

    Path getGameDirectory();

    void sendPacket(ServerPlayer player, Object packet);
    
    void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler);

    Multimap<String, String> getCustomModOptions();

    void onLaunchComplete();

    String getPlatformName();
}
