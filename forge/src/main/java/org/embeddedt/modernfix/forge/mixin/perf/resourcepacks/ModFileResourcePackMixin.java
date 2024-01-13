package org.embeddedt.modernfix.forge.mixin.perf.resourcepacks;

import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import org.embeddedt.modernfix.resources.ICachingResourcePack;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.embeddedt.modernfix.util.PackTypeHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

@Mixin(ModFileResourcePack.class)
public abstract class ModFileResourcePackMixin implements ICachingResourcePack {
    @Shadow public abstract Set<String> getNamespaces(PackType type);

    @Shadow(remap = false) @Final private ModFile modFile;

    private PackResourcesCacheEngine cacheEngine;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheResources(ModFile modFile, CallbackInfo ci) {
        invalidateCache();
        PackResourcesCacheEngine.track(this);
    }

    @Override
    public void invalidateCache() {
        this.cacheEngine = null;
        this.cacheEngine = new PackResourcesCacheEngine(this::getNamespaces, (type, namespace) -> {
            return modFile.getLocator().findPath(modFile, type.getDirectory(), namespace);
        });
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        if(cacheEngine != null) {
            Set<String> namespaces = cacheEngine.getNamespaces(type);
            if(namespaces != null)
                cir.setReturnValue(namespaces);
        }
    }

    @Inject(method = "hasResource(Ljava/lang/String;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void useCacheForExistence(String path, CallbackInfoReturnable<Boolean> cir) {
        if(cacheEngine != null && (path.startsWith("assets/") || path.startsWith("data/")))
            cir.setReturnValue(this.cacheEngine.hasResource(path));
    }

    @Inject(method = "getResource(Ljava/lang/String;)Ljava/io/InputStream;", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void fasterGetResource(String resourcePath, CallbackInfoReturnable<InputStream> cir, Path path) throws IOException {
        try {
            cir.setReturnValue(Files.newInputStream(path, StandardOpenOption.READ));
        } catch(NoSuchFileException e) {
            throw new ResourcePackFileNotFoundException(this.modFile.getFilePath().toFile(), resourcePath);
        }
    }

    /**
     * @author embeddedt
     * @reason Use cached listing of mod resources
     */
    @Inject(method = "getResources", at = @At("HEAD"), cancellable = true)
    private void fastGetResources(PackType type, String resourceNamespace, String pathIn, int maxDepth, Predicate<String> filter, CallbackInfoReturnable<Collection<ResourceLocation>> cir)
    {
        if(!PackTypeHelper.isVanillaPackType(type) || this.cacheEngine == null)
            return;
        cir.setReturnValue(this.cacheEngine.getResources(type, resourceNamespace, pathIn, maxDepth, filter));
    }
}
