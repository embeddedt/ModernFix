package org.embeddedt.modernfix.platform.forge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import cpw.mods.modlauncher.*;
import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.forge.classloading.FastAccessTransformerList;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.dfu.DFUBlaster;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.embeddedt.modernfix.util.DummyList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModernFixPlatformHooksImpl {
    public static boolean isClient() {
        return FMLLoader.getDist() == Dist.CLIENT;
    }

    public static boolean isDedicatedServer() {
        return FMLLoader.getDist().isDedicatedServer();
    }

    private static String verString;

    public static String getVersionString() {
        if(verString == null) {
            verString = LoadingModList.get().getModFileById("modernfix").getMods().get(0).getVersion().toString();
        }
        return verString;
    }

    public static boolean modPresent(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    public static boolean isDevEnv() {
        return !FMLLoader.isProduction() && FMLLoader.getLoadingModList().getModFileById("modernfix").getFile().getProvider() instanceof ExplodedDirectoryLocator;
    }

    public static MinecraftServer getCurrentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static boolean isLoadingNormally() {
        return ModLoader.isLoadingStateValid();
    }


    public static TextureAtlasSprite loadTextureAtlasSprite(TextureAtlas atlasTexture,
                                                            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
                                                            Resource resource,
                                                            int atlasWidth, int atlasHeight,
                                                            int spriteX, int spriteY, int mipmapLevel,
                                                            NativeImage image) throws IOException {
        TextureAtlasSprite tas = ForgeHooksClient.loadTextureAtlasSprite(atlasTexture, resourceManager, textureInfo, resource, atlasWidth, atlasHeight, spriteX, spriteY, mipmapLevel, image);
        if(tas == null) {
            tas = TASConstructor.construct(atlasTexture, resourceManager, textureInfo, resource, atlasWidth, atlasHeight, spriteX, spriteY, mipmapLevel, image);
        }
        return tas;
    }

    static class TASConstructor {
        private static final MethodHandle textureAtlasSpriteConstruct;
        static {
            try {
                Constructor<?> constructor = TextureAtlasSprite.class.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                textureAtlasSpriteConstruct = MethodHandles.lookup().unreflectConstructor(constructor);
            } catch(IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        static TextureAtlasSprite construct(TextureAtlas atlasTexture,
                                            ResourceManager resourceManager, TextureAtlasSprite.Info textureInfo,
                                            Resource resource,
                                            int atlasWidth, int atlasHeight,
                                            int spriteX, int spriteY, int mipmapLevel,
                                            NativeImage image) {
            try {
                return (TextureAtlasSprite)textureAtlasSpriteConstruct.invokeExact(atlasTexture, textureInfo, mipmapLevel, atlasWidth, atlasHeight, spriteX, spriteY, image);
            } catch(Throwable e) {
                throw new AssertionError("MethodHandle failed", e);
            }
        }
    }

    public static Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    public static void sendPacket(ServerPlayer player, Object packet) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void injectPlatformSpecificHacks() {
        FastAccessTransformerList.attemptReplace();
        DFUBlaster.blastMaps();

        /* https://github.com/FabricMC/Mixin/pull/99 */
        try {
            Field groupMembersField = InjectorGroupInfo.class.getDeclaredField("members");
            groupMembersField.setAccessible(true);
            Field noGroupField = InjectorGroupInfo.Map.class.getDeclaredField("NO_GROUP");
            noGroupField.setAccessible(true);
            InjectorGroupInfo noGroup = (InjectorGroupInfo)noGroupField.get(null);
            groupMembersField.set(noGroup, new DummyList<>());
        } catch(RuntimeException | ReflectiveOperationException e) {
            ModernFixMixinPlugin.instance.logger.error("Failed to patch mixin memory leak", e);
        }
    }

    public static void applyASMTransformers(String mixinClassName, ClassNode targetClass) {
        if(mixinClassName.equals("org.embeddedt.modernfix.common.compress_blockstate.perf.mixin.BlockStateBaseMixin")) {
            // Delete unused fields off BlockStateBase
            Set<String> fieldsToDelete = Stream.of(
                    "field_235702_f_", // isAir
                    "field_235703_g_", // material
                    "field_235705_i_", // destroySpeed
                    "field_235706_j_", // requiresCorrectToolForDrops
                    "field_235707_k_", // canOcclude
                    "field_235708_l_", // isRedstoneConductor
                    "field_235709_m_", // isSuffocating
                    "field_235710_n_", // isViewBlocking
                    "field_235711_o_", // hasPostProcess
                    "field_235712_p_"  // emissiveRendering
            ).map(name -> ObfuscationReflectionHelper.remapName(INameMappingService.Domain.FIELD, name)).collect(Collectors.toSet());
            targetClass.fields.removeIf(field -> {
                if(fieldsToDelete.contains(field.name)) {
                    return true;
                }
                return false;
            });
            for(MethodNode m : targetClass.methods) {
                if(m.name.equals("<init>")) {
                    ListIterator<AbstractInsnNode> iter = m.instructions.iterator();
                    while(iter.hasNext()) {
                        AbstractInsnNode node = iter.next();
                        if(node.getOpcode() == Opcodes.PUTFIELD) {
                            if(fieldsToDelete.contains(((FieldInsnNode)node).name)) {
                                iter.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void onServerCommandRegister(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            handler.accept(event.getDispatcher());
        });
    }
}
