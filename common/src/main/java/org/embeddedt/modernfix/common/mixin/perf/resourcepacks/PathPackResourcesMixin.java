package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import org.embeddedt.modernfix.resources.ICachingResourcePack;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.embeddedt.modernfix.util.PackTypeHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

@Mixin(value = PathPackResources.class, priority = 1100)
public abstract class PathPackResourcesMixin implements ICachingResourcePack {

    @Shadow @Final private Path root;
    private PackResourcesCacheEngine cacheEngine;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheResources(PackLocationInfo location, Path root, CallbackInfo ci) {
        invalidateCache();
    }

    private PackResourcesCacheEngine generateResourceCache() {
        synchronized (this) {
            PackResourcesCacheEngine engine = this.cacheEngine;
            if(engine != null)
                return engine;
            this.cacheEngine = engine = new PackResourcesCacheEngine((type) -> this.root.resolve(type.getDirectory()));
            return engine;
        }
    }

    @Override
    public void invalidateCache() {
        this.cacheEngine = null;
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        PackResourcesCacheEngine engine = cacheEngine;
        if(engine != null) {
            Set<String> namespaces = engine.getNamespaces(type);
            if(namespaces != null)
                cir.setReturnValue(namespaces);
        }
    }

    @Redirect(method = "getRootResource", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"))
    private boolean useCacheForExistence(Path path, LinkOption[] options, String[] originalPaths) {
        // the cache only stores things with a namespace and pack type
        if(originalPaths.length < 3 || (!Objects.equals(originalPaths[0], "assets") && !Objects.equals(originalPaths[0], "data")))
            return Files.exists(path, options);
        else
            return this.generateResourceCache().hasResource(originalPaths);
    }

    /**
     * @author embeddedt
     * @reason Use cached listing of mod resources
     */
    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    private void fastGetResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput, CallbackInfo ci)
    {
        if(!PackTypeHelper.isVanillaPackType(type))
            return;
        ci.cancel();
        this.generateResourceCache().collectResources(type, namespace, path.split("/"), Integer.MAX_VALUE, resourceOutput);
    }
}
