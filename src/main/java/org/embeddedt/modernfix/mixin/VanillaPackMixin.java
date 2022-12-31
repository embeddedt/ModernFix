package org.embeddedt.modernfix.mixin;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.VanillaPack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.FileWalker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Mixin(VanillaPack.class)
public class VanillaPackMixin {
    private static LoadingCache<Pair<Path, Integer>, List<Path>> pathStreamLoadingCache = CacheBuilder.newBuilder()
            .build(FileWalker.INSTANCE);

    private static EnumMap<ResourcePackType, HashMap<ResourceLocation, Boolean>> resourceContainmentCache = new EnumMap<>(ResourcePackType.class);
    static {
        for(ResourcePackType type : ResourcePackType.values()) {
            resourceContainmentCache.put(type, new HashMap<>());
        }
    }

    @Redirect(method = "collectResources", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;walk(Ljava/nio/file/Path;I[Ljava/nio/file/FileVisitOption;)Ljava/util/stream/Stream;"))
    private static Stream<Path> useCacheForLoading(Path path, int maxDepth, FileVisitOption[] fileVisitOptions) throws IOException {
        try {
            return pathStreamLoadingCache.get(Pair.of(path, maxDepth)).stream();
        } catch (ExecutionException e) {
            if(e.getCause() instanceof IOException) /* generally always should be */
                throw (IOException)e.getCause();
            else
                throw new IOException(e);
        }
    }

    @Inject(method = "resourceExists", at = @At(value = "RETURN", ordinal = 1), locals = LocalCapture.CAPTURE_FAILHARD)
    private void storeExistenceToCache(ResourcePackType type, ResourceLocation location, CallbackInfoReturnable<Boolean> cir, String s) {
        resourceContainmentCache.get(type).put(new ResourceLocation(location.getNamespace().intern(), location.getPath()), cir.getReturnValue());
    }

    @Inject(method = "resourceExists", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResource(Ljava/lang/String;)Ljava/net/URL;"), cancellable = true)
    private void useCacheForExistence(ResourcePackType type, ResourceLocation location, CallbackInfoReturnable<Boolean> cir) {
        Boolean b = resourceContainmentCache.get(type).getOrDefault(location, null);
        if(b != null)
            cir.setReturnValue(b);
    }
}
