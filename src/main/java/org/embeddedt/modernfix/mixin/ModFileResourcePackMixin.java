package org.embeddedt.modernfix.mixin;

import com.google.common.base.Joiner;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ModFileResourcePack.class)
public abstract class ModFileResourcePackMixin {
    @Shadow public abstract Set<String> getNamespaces(ResourcePackType type);

    @Shadow(remap = false) @Final private ModFile modFile;
    private EnumMap<ResourcePackType, Set<String>> namespacesByType;
    private EnumMap<ResourcePackType, HashMap<String, List<Path>>> rootListingByNamespaceAndType;
    private Set<String> containedPaths;
    private boolean useNamespaceCaches;
    private FileSystem resourcePackFS;
    private static Joiner slashJoiner = Joiner.on('/');

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cacheResources(ModFile modFile, CallbackInfo ci) {
        ModernFix.LOGGER.warn("Generating resource cache for " + modFile.getFileName());
        this.resourcePackFS = modFile.getLocator().findPath(modFile, "").getFileSystem();
        this.useNamespaceCaches = false;
        this.namespacesByType = new EnumMap<>(ResourcePackType.class);
        for(ResourcePackType type : ResourcePackType.values()) {
            this.namespacesByType.put(type, this.getNamespaces(type));
        }
        this.useNamespaceCaches = true;
        this.rootListingByNamespaceAndType = new EnumMap<>(ResourcePackType.class);
        this.containedPaths = new HashSet<>();
        for(ResourcePackType type : ResourcePackType.values()) {
            Set<String> namespaces = this.namespacesByType.get(type);
            HashMap<String, List<Path>> rootListingForNamespaces = new HashMap<>();
            for(String namespace : namespaces) {
                try {
                    Path root = modFile.getLocator().findPath(modFile, type.getDirectory(), namespace).toAbsolutePath();
                    try (Stream<Path> stream = Files.walk(root)) {
                        List<Path> paths = stream
                                .map(path -> root.relativize(path.toAbsolutePath()))
                                .filter(path -> !path.toString().endsWith(".mcmeta"))
                                .collect(Collectors.toList());
                        rootListingForNamespaces.put(namespace, paths);
                        for(Path path : paths) {
                            String mergedPath = slashJoiner.join(type.getDirectory(), namespace, path);
                            this.containedPaths.add(mergedPath);
                        }
                    }
                } catch(IOException e) {
                    rootListingForNamespaces.put(namespace, Collections.emptyList());
                }
            }
            this.rootListingByNamespaceAndType.put(type, rootListingForNamespaces);
        }
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void useCacheForNamespaces(ResourcePackType type, CallbackInfoReturnable<Set<String>> cir) {
        if(useNamespaceCaches) {
            cir.setReturnValue(this.namespacesByType.get(type));
        }
    }

    @Inject(method = "hasResource(Ljava/lang/String;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void useCacheForExistence(String path, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.containedPaths.contains(path));
    }

    /**
     * @author embeddedt
     * @reason Use cached listing of mod resources
     */
    @Overwrite
    public Collection<ResourceLocation> getResources(ResourcePackType type, String resourceNamespace, String pathIn, int maxDepth, Predicate<String> filter)
    {
        Path inputPath = this.resourcePackFS.getPath(pathIn);
        return this.rootListingByNamespaceAndType.get(type).getOrDefault(resourceNamespace, Collections.emptyList()).stream().
                filter(path -> path.getNameCount() <= maxDepth). // Make sure the depth is within bounds
                filter(path -> path.startsWith(inputPath)). // Make sure the target path is inside this one
                filter(path -> filter.test(path.getFileName().toString())). // Test the file name against the predicate
                // Finally we need to form the RL, so use the first name as the domain, and the rest as the path
                // It is VERY IMPORTANT that we do not rely on Path.toString as this is inconsistent between operating systems
                // Join the path names ourselves to force forward slashes
                map(path -> new ResourceLocation(resourceNamespace, slashJoiner.join(path))).
                collect(Collectors.toList());
    }
}
