package org.embeddedt.modernfix.resources;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.PackTypeHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The core of the resource pack cache system.
 */
public class PackResourcesCacheEngine {
    private static final Joiner SLASH_JOINER = Joiner.on('/');
    private static final ConcurrentHashMap<String, String> PATH_COMPONENT_INTERNER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String[]> CACHED_SPLIT_PATHS = new ConcurrentHashMap<>();

    static class Node {
        Map<String, Node> children;

        void optimize() {
            if (children != null) {
                for (var entry : children.entrySet()) {
                    var oldNode = entry.getValue();
                    oldNode.optimize();
                    if (oldNode.children == null) {
                        entry.setValue(EMPTY);
                    }
                }
                children = Map.copyOf(children);
            } else {
                children = Map.of();
            }
        }

        void collectResources(String namespace, Path baseNioPath, String[] pathComponents, int curIndex, int maxDepth, PackResources.ResourceOutput output) {
            if (curIndex > maxDepth) {
                return;
            }
            if (curIndex < pathComponents.length) {
                String component;
                do {
                    component = pathComponents[curIndex];
                    if (!component.isEmpty()) {
                        break;
                    }
                    curIndex++;
                    maxDepth++;
                } while(true);

                Node n = getChild(component);
                if (n != null) {
                    n.collectResources(namespace, baseNioPath, pathComponents, curIndex + 1, maxDepth, output);
                }
            } else {
                // We reached the desired path. Collect all resources
                this.outputResources(namespace, baseNioPath, String.join("/", pathComponents), output);
            }

        }

        void outputResources(String namespace, Path baseNioPath, String path, PackResources.ResourceOutput output) {
            if (children.isEmpty()) {
                // This is a terminal node.
                Identifier location = Identifier.fromNamespaceAndPath(namespace, path);
                output.accept(location, () -> Files.newInputStream(baseNioPath.resolve(path)));
            } else {
                for (var entry : children.entrySet()) {
                    entry.getValue().outputResources(namespace, baseNioPath, path + "/" + entry.getKey(), output);
                }
            }
        }

        @Nullable
        Node getChild(String name) {
            return children.get(name);
        }
    }

    private static final Node EMPTY = new Node();

    private final Node root = new Node();
    private final Map<PackType, Path> rootPathsByType = new Object2ObjectOpenHashMap<>();

    private volatile boolean cacheGenerationFlag = false;
    private List<Runnable> cacheGenerationTasks = new ArrayList<>();
    private Path debugPath;

    public PackResourcesCacheEngine(Function<PackType, Path> basePathRetriever) {
        // used for log message
        this.debugPath = basePathRetriever.apply(PackType.CLIENT_RESOURCES).toAbsolutePath();
        this.root.children = new Object2ObjectOpenHashMap<>();
        for(PackType type : PackType.values()) {
            var typeRoot = new Node();
            this.root.children.put(type.getDirectory(), typeRoot);
            Path root = basePathRetriever.apply(type);
            this.rootPathsByType.put(type, root);
            cacheGenerationTasks.add(() -> {
                try {
                    try (Stream<Path> stream = Files.find(root, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())) {
                        stream
                                .map(path -> root.relativize(path.toAbsolutePath()))
                                .filter(PackResourcesCacheEngine::isValidCachedResourcePath)
                                .forEach(path -> {
                                    var node = typeRoot;
                                    int nameCount = path.getNameCount();
                                    for (int i = 0; i < nameCount; i++) {
                                        String key = path.getName(i).toString();
                                        if (i < (nameCount - 1)) {
                                            key = PATH_COMPONENT_INTERNER.computeIfAbsent(key, Function.identity());
                                        }
                                        if (node.children == null) {
                                            node.children = new Object2ObjectOpenHashMap<>();
                                        }
                                        node = node.children.computeIfAbsent(key, $ -> new Node());
                                    }
                                });
                    }
                } catch(IOException ignored) {
                }
            });
        }
        cacheGenerationTasks.add(this.root::optimize);
    }

    private static boolean isValidCachedResourcePath(Path path) {
        if(path.getFileName() == null || path.getNameCount() == 0) {
            return false;
        }
        String str = SLASH_JOINER.join(path);
        if(str.length() == 0)
            return false;
        for(int i = 0; i < str.length(); i++) {
            if(!Identifier.validPathChar(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public Set<String> getNamespaces(PackType type) {
        awaitLoad();
        if(PackTypeHelper.isVanillaPackType(type)) {
            var namespaceToNodeMap = this.root.getChild(type.getDirectory()).children;
            var results = new ObjectOpenHashSet<String>();
            for (var entry : namespaceToNodeMap.entrySet()) {
                // Entries without children are files, not folders
                if (!entry.getValue().children.isEmpty()) {
                    results.add(entry.getKey());
                }
            }
            return results;
        } else
            return null;
    }

    private void doGenerateCache() {
        Stopwatch watch = Stopwatch.createStarted();
        for(Runnable r : this.cacheGenerationTasks) {
            r.run();
        }
        watch.stop();
        ModernFix.LOGGER.debug("Generated cache for {} in {}", debugPath, watch);
        debugPath = null;
        cacheGenerationTasks = ImmutableList.of();
    }

    private void awaitLoad() {
        if(!this.cacheGenerationFlag) {
            synchronized (this) {
                if(!this.cacheGenerationFlag) {
                    this.doGenerateCache();
                    this.cacheGenerationFlag = true;
                }
            }
        }
    }

    public boolean hasResource(String[] paths) {
        awaitLoad();
        var node = this.root;
        for (String path : paths) {
            if (path.isEmpty()) {
                continue;
            }
            node = node.children.get(path);
            if (node == null) {
                //ModernFix.LOGGER.info("Does not have " + String.join("/", paths));
                return false;
            }
        }
        return true;
    }

    public void collectResources(PackType type, String resourceNamespace, String[] components, int maxDepth, PackResources.ResourceOutput output) {
        if(!PackTypeHelper.isVanillaPackType(type))
            throw new IllegalArgumentException("Only vanilla PackTypes are supported");
        awaitLoad();
        var node = this.root.getChild(type.getDirectory());
        if (node == null) {
            return;
        }
        node = node.getChild(resourceNamespace);
        if (node == null) {
            return;
        }
        node.collectResources(resourceNamespace, this.rootPathsByType.get(type).resolve(resourceNamespace), components, 0, maxDepth, output);
    }

    private static String[] decompose(String path) {
        String[] components = path.split("/");
        for (int i = 0; i < components.length; i++) {
            components[i] = PATH_COMPONENT_INTERNER.computeIfAbsent(components[i], Function.identity());
        }
        return components;
    }

    public static String[] decomposeCached(String path) {
        return CACHED_SPLIT_PATHS.computeIfAbsent(path, PackResourcesCacheEngine::decompose);
    }
}
