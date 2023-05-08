package org.embeddedt.modernfix.resources;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.PackTypeHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The core of the resource pack cache system.
 *
 * Using a dedicated set and also separate lists is important; testing without this showed a huge performance
 * drop.
 */
public class PackResourcesCacheEngine {
    private final Map<PackType, Set<String>> namespacesByType;
    private final Set<CachedResourcePath> containedPaths;
    private final EnumMap<PackType, Map<String, List<CachedResourcePath>>> resourceListings;
    private CompletableFuture<Void> cacheFuture;

    public PackResourcesCacheEngine(Function<PackType, Set<String>> namespacesRetriever, BiFunction<PackType, String, Path> basePathRetriever) {
        this.namespacesByType = new EnumMap<>(PackType.class);
        for(PackType type : PackType.values()) {
            if(!PackTypeHelper.isVanillaPackType(type))
                continue;
            this.namespacesByType.put(type, namespacesRetriever.apply(type));
        }
        this.containedPaths = new ObjectOpenHashSet<>();
        this.resourceListings = new EnumMap<>(PackType.class);
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        Stopwatch watch = Stopwatch.createStarted();
        // used for log message
        Path debugPath = basePathRetriever.apply(PackType.CLIENT_RESOURCES, "minecraft").toAbsolutePath();
        for(PackType type : PackType.values()) {
            Collection<String> namespaces = PackTypeHelper.isVanillaPackType(type) ? this.namespacesByType.get(type) : namespacesRetriever.apply(type);
            future = future.thenRunAsync(() -> {
                ImmutableMap.Builder<String, List<CachedResourcePath>> packTypedMap = ImmutableMap.builder();
                for(String namespace : namespaces) {
                    try {
                        ImmutableList.Builder<CachedResourcePath> namespacedList = ImmutableList.builder();
                        Path root = basePathRetriever.apply(type, namespace).toAbsolutePath();
                        String[] prefix = new String[] { type.getDirectory(), namespace };
                        try (Stream<Path> stream = Files.walk(root)) {
                            stream
                                    .map(path -> root.relativize(path.toAbsolutePath()))
                                    .filter(PackResourcesCacheEngine::isValidCachedResourcePath)
                                    .forEach(path -> {
                                        CachedResourcePath cachedPath = new CachedResourcePath(prefix, path);
                                        synchronized (this.containedPaths) {
                                            this.containedPaths.add(cachedPath);
                                        }
                                        if(!cachedPath.getFileName().endsWith(".mcmeta"))
                                            namespacedList.add(cachedPath);
                                    });
                        }
                        packTypedMap.put(namespace, namespacedList.build());
                    } catch(IOException ignored) {
                    }
                }
                synchronized (this.resourceListings) {
                    this.resourceListings.put(type, packTypedMap.build());
                }
            }, ModernFix.resourceReloadExecutor());
        }
        future = future.thenRunAsync(() -> {
            ((ObjectOpenHashSet<CachedResourcePath>)this.containedPaths).trim();
            watch.stop();
        }, ModernFix.resourceReloadExecutor());
        this.cacheFuture = future;
        // print debug message in separate task to prevent slowing down rest of load
        future.thenRunAsync(() -> {
            ModernFix.LOGGER.debug("Generated cache for {} in {}", debugPath, watch);
        }, ModernFix.resourceReloadExecutor());
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

    private void awaitLoad() {
        if(this.cacheFuture != null) {
            this.cacheFuture.join();
            this.cacheFuture = null;
        }
    }

    public boolean hasResource(String path) {
        awaitLoad();
        return this.containedPaths.contains(new CachedResourcePath(path));
    }

    public Collection<ResourceLocation> getResources(PackType type, String resourceNamespace, String pathIn, int maxDepth, Predicate<ResourceLocation> filter) {
        if(!PackTypeHelper.isVanillaPackType(type))
            throw new IllegalArgumentException("Only vanilla PackTypes are supported");
        awaitLoad();
        List<CachedResourcePath> paths = resourceListings.get(type).getOrDefault(resourceNamespace, Collections.emptyList());
        if(paths.isEmpty())
            return Collections.emptyList();
        String testPath = pathIn.endsWith("/") ? pathIn : (pathIn + "/");
        ArrayList<ResourceLocation> resources = new ArrayList<>();
        for(CachedResourcePath cachePath : paths) {
            if((cachePath.getNameCount() - 2) > maxDepth)
                continue;
            String fullPath = cachePath.getFullPath(2);
            if(!fullPath.startsWith(testPath))
                continue;
            ResourceLocation foundResource = new ResourceLocation(resourceNamespace, fullPath);
            if(!filter.test(foundResource))
                continue;
            resources.add(foundResource);
        }
        return resources;
    }
}
