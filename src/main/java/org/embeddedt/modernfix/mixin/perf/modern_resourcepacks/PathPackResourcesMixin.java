package org.embeddedt.modernfix.mixin.perf.modern_resourcepacks;

import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.resource.PathPackResources;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.embeddedt.modernfix.util.PackTypeHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * The built-in resource caching provided by Forge is overengineered and doesn't work correctly
 * in many scenarios. This is a port of the well-tested implementation from ModernFix 1.16
 * and 1.18.
 */
@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {

    @Shadow protected abstract Path resolve(String... paths);

    @Shadow @NotNull
    protected abstract Set<String> getNamespacesFromDisk(PackType type);

    private PackResourcesCacheEngine cacheEngine;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheResources(String packName, Path source, CallbackInfo ci) {
        this.cacheEngine = null;
    }

    private void generateResourceCache() {
        synchronized (this) {
            if(this.cacheEngine != null)
                return;
            this.cacheEngine = new PackResourcesCacheEngine(this::getNamespacesFromDisk, (type, namespace) -> this.resolve(type.getDirectory(), namespace));
        }
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        if(!PackTypeHelper.isVanillaPackType(type))
            return;
        if(this.cacheEngine != null) {
            Set<String> namespaces = this.cacheEngine.getNamespaces(type);
            if(namespaces != null)
                cir.setReturnValue(namespaces);
        }
    }

    @Inject(method = "hasResource(Ljava/lang/String;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void useCacheForExistence(String path, CallbackInfoReturnable<Boolean> cir) {
        this.generateResourceCache();
        cir.setReturnValue(this.cacheEngine.hasResource(path));
    }

    /**
     * @author embeddedt
     * @reason Use cached listing of mod resources
     */
    @Inject(method = "getResources", at = @At("HEAD"), cancellable = true)
    public void getResources(PackType type, String resourceNamespace, String pathIn, Predicate<ResourceLocation> filter, CallbackInfoReturnable<Collection<ResourceLocation>> cir)
    {
        if(!PackTypeHelper.isVanillaPackType(type))
            return;
        this.generateResourceCache();
        cir.setReturnValue(this.cacheEngine.getResources(type, resourceNamespace, pathIn, Integer.MAX_VALUE, filter));
    }
}
