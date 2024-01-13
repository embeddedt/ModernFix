package org.embeddedt.modernfix.fabric.mixin.perf.resourcepacks;

import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.minecraft.server.packs.PackType;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.resources.ICachingResourcePack;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

@Mixin(ModNioResourcePack.class)
@RequiresMod("fabric-resource-loader-v0")
public abstract class ModNioResourcePackMixin implements ICachingResourcePack {
    @Shadow public abstract Set<String> getNamespaces(PackType type);

    @Shadow(remap = false) @Final private Path basePath;
    private PackResourcesCacheEngine cacheEngine;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cacheResources(CallbackInfo ci) {
        invalidateCache();
        PackResourcesCacheEngine.track(this);
    }

    @Override
    public void invalidateCache() {
        this.cacheEngine = null;
        this.cacheEngine = new PackResourcesCacheEngine(this::getNamespaces, (type, namespace) -> {
            return basePath.resolve(type.getDirectory()).resolve(namespace);
        });
    }

    // this check wastes CPU time, it is checked later anyway
    @Redirect(method = "getPath", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"), remap = false)
    private boolean checkExists(Path p, LinkOption[] opts) {
        return true;
    }

    // TODO might be redundant
    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        if(cacheEngine != null) {
            Set<String> namespaces = cacheEngine.getNamespaces(type);
            if(namespaces != null)
                cir.setReturnValue(namespaces);
        }
    }

    @Inject(method = "hasResource", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/resource/loader/ModNioResourcePack;getPath(Ljava/lang/String;)Ljava/nio/file/Path;"), cancellable = true)
    private void useCacheForExistence(String path, CallbackInfoReturnable<Boolean> cir) {
        if(cacheEngine != null && (path.startsWith("assets/") || path.startsWith("data/")))
            cir.setReturnValue(this.cacheEngine.hasResource(path));
    }
}
