package org.embeddedt.modernfix.mixin.perf.resourcepacks;

import com.google.common.base.Joiner;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.util.CachedResourcePath;
import org.embeddedt.modernfix.util.FileUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ModFileResourcePack.class)
public abstract class ModFileResourcePackMixin {
    @Shadow public abstract Set<String> getNamespaces(PackType type);

    @Shadow(remap = false) @Final private ModFile modFile;
    private EnumMap<PackType, Set<String>> namespacesByType;
    private EnumMap<PackType, HashMap<String, List<CachedResourcePath>>> rootListingByNamespaceAndType;
    private Set<CachedResourcePath> containedPaths;
    private boolean useNamespaceCaches;
    private FileSystem resourcePackFS;
    private static Joiner slashJoiner = Joiner.on('/');

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheResources(ModFile modFile, CallbackInfo ci) {
        this.resourcePackFS = modFile.getLocator().findPath(modFile, "").getFileSystem();
        this.useNamespaceCaches = false;
        this.namespacesByType = new EnumMap<>(PackType.class);
        for(PackType type : PackType.values()) {
            this.namespacesByType.put(type, this.getNamespaces(type));
        }
        this.useNamespaceCaches = true;
        this.rootListingByNamespaceAndType = new EnumMap<>(PackType.class);
        this.containedPaths = new HashSet<>();
        for(PackType type : PackType.values()) {
            Set<String> namespaces = this.namespacesByType.get(type);
            HashMap<String, List<CachedResourcePath>> rootListingForNamespaces = new HashMap<>();
            for(String namespace : namespaces) {
                try {
                    Path root = modFile.getLocator().findPath(modFile, type.getDirectory(), namespace).toAbsolutePath();
                    try (Stream<Path> stream = Files.walk(root)) {
                        ArrayList<CachedResourcePath> rootListingPaths = new ArrayList<>();
                        stream
                                .map(path -> root.relativize(path.toAbsolutePath()))
                                .filter(this::isValidCachedResourcePath)
                                .forEach(path -> {
                                    CachedResourcePath listing = new CachedResourcePath(path);
                                    if(!listing.getFileName().endsWith(".mcmeta")) {
                                        rootListingPaths.add(listing);
                                    }
                                    this.containedPaths.add(new CachedResourcePath(new String[] { type.getDirectory(), namespace }, listing));
                                });
                        rootListingPaths.trimToSize();
                        rootListingForNamespaces.put(namespace, rootListingPaths);
                    }
                } catch(IOException e) {
                    rootListingForNamespaces.put(namespace, Collections.emptyList());
                }
            }
            this.rootListingByNamespaceAndType.put(type, rootListingForNamespaces);
        }
    }

    private boolean isValidCachedResourcePath(Path path) {
        if(path.getFileName() == null || path.getNameCount() == 0) {
            return false;
        }
        String str = path.toString();
        for(int i = 0; i < str.length(); i++) {
            if(!ResourceLocation.validPathChar(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        if(useNamespaceCaches) {
            cir.setReturnValue(this.namespacesByType.get(type));
        }
    }

    @Inject(method = "hasResource(Ljava/lang/String;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void useCacheForExistence(String path, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.containedPaths.contains(new CachedResourcePath(path)));
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
        if(!pathIn.endsWith("/"))
            pathIn = pathIn + "/";
        final String testPath = pathIn;
        cir.setReturnValue(this.rootListingByNamespaceAndType.get(type).getOrDefault(resourceNamespace, Collections.emptyList()).stream().
                filter(path -> path.getNameCount() <= maxDepth). // Make sure the depth is within bounds
                filter(path -> path.getFullPath().startsWith(testPath)). // Make sure the target path is inside this one
                filter(path -> filter.test(path.getFileName())). // Test the file name against the predicate
                // Finally we need to form the RL, so use the first name as the domain, and the rest as the path
                // It is VERY IMPORTANT that we do not rely on Path.toString as this is inconsistent between operating systems
                map(path -> new ResourceLocation(resourceNamespace, path.getFullPath())).
                collect(Collectors.toList()));
    }
}
