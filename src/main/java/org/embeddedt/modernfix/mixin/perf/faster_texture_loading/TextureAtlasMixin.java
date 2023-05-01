package org.embeddedt.modernfix.mixin.perf.faster_texture_loading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.ModLoader;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(TextureAtlas.class)
@ClientOnlyMixin
public abstract class TextureAtlasMixin {
    @Shadow protected abstract ResourceLocation getResourceLocation(ResourceLocation location);

    @Shadow protected abstract Collection<TextureAtlasSprite.Info> getBasicSpriteInfos(ResourceManager resourceManager, Set<ResourceLocation> spriteLocations);

    private Map<ResourceLocation, Pair<Resource, NativeImage>> loadedImages;
    private boolean usingFasterLoad;
    /**
     * @author embeddedt
     * @reason simplify texture loading by loading whole image once, avoid slow PngInfo code
     */
    @Redirect(method = "prepareToStitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlas;getBasicSpriteInfos(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/Set;)Ljava/util/Collection;"))
    private Collection<TextureAtlasSprite.Info> loadImages(TextureAtlas atlas, ResourceManager manager, Set<ResourceLocation> imageLocations) {
        usingFasterLoad = ModLoader.isLoadingStateValid();
        // bail if Forge is erroring to avoid AT crashes
        if(!usingFasterLoad) {
            return getBasicSpriteInfos(manager, imageLocations);
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        ConcurrentLinkedQueue<TextureAtlasSprite.Info> results = new ConcurrentLinkedQueue<>();
        loadedImages = new ConcurrentHashMap<>();
        for(ResourceLocation location : imageLocations) {
            if(MissingTextureAtlasSprite.getLocation().equals(location))
                continue;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    ResourceLocation fileLocation = this.getResourceLocation(location);
                    Resource resource = manager.getResource(fileLocation);
                    NativeImage image = NativeImage.read(resource.getInputStream());
                    AnimationMetadataSection animData = resource.getMetadata(AnimationMetadataSection.SERIALIZER);
                    if (animData == null) {
                        animData = AnimationMetadataSection.EMPTY;
                    }
                    Pair<Integer, Integer> dimensions = animData.getFrameSize(image.getWidth(), image.getHeight());
                    loadedImages.put(location, Pair.of(resource, image));
                    results.add(new TextureAtlasSprite.Info(location, dimensions.getFirst(), dimensions.getSecond(), animData));
                } catch(IOException e) {
                    ModernFix.LOGGER.error("Using missing texture, unable to load {} : {}", location, e);
                } catch(RuntimeException e) {
                    ModernFix.LOGGER.error("Unable to parse metadata from {} : {}", location, e);
                }
            }, ModernFix.resourceReloadExecutor()));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }

    @Inject(method = "prepareToStitch", at = @At("RETURN"))
    private void clearLoadedImages(CallbackInfoReturnable<TextureAtlas.Preparations> cir) {
        loadedImages = Collections.emptyMap();
    }

    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite$Info;IIIII)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
        at = @At("HEAD"), cancellable = true)
    private void loadFromExisting(ResourceManager resourceManager, TextureAtlasSprite.Info spriteInfo, int width, int height, int mipmapLevel, int originX, int originY, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        if(!usingFasterLoad)
            return;
        Pair<Resource, NativeImage> pair = loadedImages.get(spriteInfo.name());
        if(pair == null) {
            ModernFix.LOGGER.error("Texture {} was not loaded in early stage", spriteInfo.name());
            cir.setReturnValue(null);
        } else {
            TextureAtlasSprite sprite = null;
            try {
                sprite = ForgeHooksClient.loadTextureAtlasSprite((TextureAtlas)(Object)this, resourceManager, spriteInfo, pair.getFirst(), width, height, originX, originY, mipmapLevel, pair.getSecond());
                if(sprite == null)
                    sprite = new TextureAtlasSprite((TextureAtlas)(Object)this, spriteInfo, mipmapLevel, width, height, originX, originY, pair.getSecond());
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Error loading texture {}: {}", spriteInfo.name(), e);
            } finally {
                try {
                    pair.getFirst().close();
                } catch(IOException ignored) {
                    // not much we can do
                }
            }
            cir.setReturnValue(sprite);
        }
    }
}
