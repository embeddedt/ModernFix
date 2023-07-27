package org.embeddedt.modernfix.forge.classloading;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.resources.CachedResourcePath;
import org.embeddedt.modernfix.util.FileUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class ModernFixResourceFinder {
    private static Multimap<CachedResourcePath, String> urlsForClass = null;
    private static final Class<? extends IModLocator> MINECRAFT_LOCATOR;
    private static Field explodedDirModsField = null;
    private static final Logger LOGGER = LogManager.getLogger("ModernFixResourceFinder");
    static {
        try {
            MINECRAFT_LOCATOR = (Class<? extends IModLocator>)Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModDiscoverer$MinecraftLocator");
        } catch(ClassNotFoundException e) {
            /* that shouldn't happen */
            throw new RuntimeException(e);
        }
    }

    public static synchronized void init() throws ReflectiveOperationException {
        // Make sure FileUtil is classloaded now to avoid issues
        FileUtil.normalize("");
        ImmutableMultimap.Builder<CachedResourcePath, String> urlBuilder = ImmutableMultimap.builder();
        //LOGGER.info("Start building list of class locations...");
        for(ModFileInfo fileInfo : LoadingModList.get().getModFiles()) {
            ModFile file = fileInfo.getFile();
            IModLocator locator = file.getLocator();
            Iterable<Path> rootPath = getRootPathForLocator(locator, file);
            for(Path root : rootPath) {
                if(!Files.exists(root))
                    continue;
                try(Stream<Path> stream = Files.walk(root)) {
                    stream
                            .map(root::relativize)
                            .forEach(path -> {
                                CachedResourcePath p = new CachedResourcePath(CachedResourcePath.NO_PREFIX, path);
                                urlBuilder.put(p, fileInfo.getMods().get(0).getModId());
                            });
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        urlsForClass = urlBuilder.build();
        //LOGGER.info("Finish building");
    }

    private static Iterable<Path> getRootPathForLocator(IModLocator locator, ModFile file) throws ReflectiveOperationException {
        if(locator instanceof AbstractJarFileLocator) {
            FileSystem modFs = locator.findPath(file, ".").getFileSystem();
            return modFs.getRootDirectories();
        } else if (locator instanceof ExplodedDirectoryLocator) {
            if(explodedDirModsField == null) {
                explodedDirModsField = ExplodedDirectoryLocator.class.getDeclaredField("mods");
                explodedDirModsField.setAccessible(true);
            }
            Map<IModFile, Pair<Path, List<Path>>> mods = (Map<IModFile, Pair<Path, List<Path>>>)explodedDirModsField.get(locator);
            return mods.get(file).getRight();
        } else if(MINECRAFT_LOCATOR.isAssignableFrom(locator.getClass())) {
            Path mcJar = FMLLoader.getMCPaths()[0];
            if(Files.isDirectory(mcJar)) {
                return mcJar;
            } else {
                return locator.findPath(file, ".").getFileSystem().getRootDirectories();
            }
        } else
            throw new UnsupportedOperationException("Unknown ModLocator type: " + locator.getClass().getName());
    }

    public static Enumeration<URL> findAllURLsForResource(String input) {
        // fallback to Forge impl for any paths ending in a slash
        char endChar = input.length() > 0 ? input.charAt(input.length() - 1) : '/';
        if(endChar == '/' || endChar == '\\') {
            return LoadingModList.get().findAllURLsForResource(input);
        }
        // CachedResourcePath normalizes already
        Collection<String> urlList = urlsForClass.get(new CachedResourcePath(input));
        if(!urlList.isEmpty()) {
            String pathInput = FileUtil.normalize(input);
            return Iterators.asEnumeration(urlList.stream().map(modId -> {
                try {
                    return new URL("modjar://" + modId + "/" + pathInput);
                } catch(MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }).iterator());
        } else {
            return Collections.emptyEnumeration();
        }
    }
}
