package org.embeddedt.modernfix.resources;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.embeddedt.modernfix.util.PackTypeHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PackResourcesCacheEngine {
    private final Map<PackType, Set<String>> namespacesByType;
    private final Set<CachedResourcePath> containedPaths;

    public PackResourcesCacheEngine(Function<PackType, Set<String>> namespacesRetriever, BiFunction<PackType, String, Path> basePathRetriever) {
        this.namespacesByType = new EnumMap<>(PackType.class);
        for(PackType type : PackType.values()) {
            if(!PackTypeHelper.isVanillaPackType(type))
                continue;
            this.namespacesByType.put(type, namespacesRetriever.apply(type));
        }
        this.containedPaths = new ObjectOpenHashSet<>();
        for(PackType type : PackType.values()) {
            Collection<String> namespaces = PackTypeHelper.isVanillaPackType(type) ? this.namespacesByType.get(type) : namespacesRetriever.apply(type);
            for(String namespace : namespaces) {
                try {
                    Path root = basePathRetriever.apply(type, namespace).toAbsolutePath();
                    try (Stream<Path> stream = Files.walk(root)) {
                        stream
                                .map(path -> root.relativize(path.toAbsolutePath()))
                                .filter(PackResourcesCacheEngine::isValidCachedResourcePath)
                                .forEach(path -> {
                                    this.containedPaths.add(new CachedResourcePath(new String[] { type.getDirectory(), namespace }, path));
                                });
                    }
                } catch(IOException ignored) {
                }
            }
        }
        ((ObjectOpenHashSet<CachedResourcePath>)this.containedPaths).trim();
    }

    private static boolean isValidCachedResourcePath(Path path) {
        if(path.getFileName() == null || path.getNameCount() == 0) {
            return false;
        }
        String str = path.toString();
        if(str.length() == 0)
            return false;
        for(int i = 0; i < str.length(); i++) {
            if(!ResourceLocation.validPathChar(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public Set<String> getNamespaces(PackType type) {
        if(PackTypeHelper.isVanillaPackType(type))
            return this.namespacesByType.get(type);
        else
            return null;
    }

    public boolean hasResource(String path) {
        return this.containedPaths.contains(new CachedResourcePath(path));
    }

    public Collection<ResourceLocation> getResources(PackType type, String resourceNamespace, String pathIn, int maxDepth, Predicate<String> filter) {
        String testPath = pathIn.endsWith("/") ? pathIn : (pathIn + "/");
        ArrayList<ResourceLocation> resources = new ArrayList<>();
        String typeDirectory = CachedResourcePath.PATH_COMPONENT_INTERNER.intern(type.getDirectory());
        resourceNamespace = CachedResourcePath.PATH_COMPONENT_INTERNER.intern(resourceNamespace);
        for(CachedResourcePath cachePath : this.containedPaths) {
            if(cachePath.getNameCount() < 2)
                continue;
            if((cachePath.getNameCount() - 2) > maxDepth)
                continue;
            // we interned, so reference equality is safe
            if(cachePath.getNameAt(0) != typeDirectory || cachePath.getNameAt(1) != resourceNamespace)
                continue;
            if(cachePath.getFileName().endsWith(".mcmeta"))
                continue;
            String fullPath = cachePath.getFullPath(2);
            if(!fullPath.startsWith(testPath))
                continue;
            if(!filter.test(cachePath.getFileName()))
                continue;
            ResourceLocation foundResource = new ResourceLocation(resourceNamespace, fullPath);
            resources.add(foundResource);
        }
        return resources;
    }
}
