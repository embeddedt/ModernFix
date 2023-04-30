package org.embeddedt.modernfix.mixin.perf.resourcepacks;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.FileWalker;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.FileUtil;
import org.embeddedt.modernfix.util.PackTypeHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Mixin(VanillaPackResources.class)
public class VanillaPackMixin {
    @Shadow @Final private static Map<PackType, FileSystem> JAR_FILESYSTEM_BY_TYPE;
    private static LoadingCache<Pair<Path, Integer>, List<Path>> pathStreamLoadingCache = CacheBuilder.newBuilder()
            .build(FileWalker.INSTANCE);

    private static Set<String> containedPaths = null;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheContainedPaths(String[] p_i47912_1_, CallbackInfo ci) {
        if(containedPaths != null)
            return;
        containedPaths = new ObjectOpenHashSet<>();
        Joiner slashJoiner = Joiner.on('/');
        for(PackType type : PackType.values()) {
            if(!PackTypeHelper.isVanillaPackType(type))
                continue;
            FileSystem fs = JAR_FILESYSTEM_BY_TYPE.get(type);
            if(fs == null)
                throw new IllegalStateException("No filesystem for vanilla " + type.name() + " assets");
            try {
                Path root = fs.getPath(type.getDirectory()).toAbsolutePath();
                try(Stream<Path> stream = Files.walk(root)) {
                    stream
                            .map(path -> root.relativize(path.toAbsolutePath()))
                            .forEach(path -> containedPaths.add(slashJoiner.join(type.getDirectory(), path)));
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        ((ObjectOpenHashSet<String>)containedPaths).trim();
    }

    @Redirect(method = "getResources(Ljava/util/Collection;ILjava/lang/String;Ljava/nio/file/Path;Ljava/lang/String;Ljava/util/function/Predicate;)V", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;walk(Ljava/nio/file/Path;I[Ljava/nio/file/FileVisitOption;)Ljava/util/stream/Stream;"))
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

    @Inject(method = "hasResource", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResource(Ljava/lang/String;)Ljava/net/URL;"), cancellable = true)
    private void useCacheForExistence(PackType type, ResourceLocation location, CallbackInfoReturnable<Boolean> cir) {
        if(!PackTypeHelper.isVanillaPackType(type))
            return;
        cir.setReturnValue(containedPaths.contains(type.getDirectory() + "/" + location.getNamespace() + "/" + FileUtil.normalize(location.getPath())));
    }
}
