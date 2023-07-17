package org.embeddedt.modernfix.forge.classloading;

import com.google.common.base.Joiner;
import com.google.common.collect.*;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.*;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ModernFixResourceFinder {
    private static Multimap<String, String> urlsForClass = null;
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

    private static final Joiner SLASH_JOINER = Joiner.on('/');

    public static synchronized void init() throws ReflectiveOperationException {
        ImmutableMultimap.Builder<String, String> urlBuilder = ImmutableMultimap.builder();
        Interner<String> pathInterner = Interners.newStrongInterner();
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
                                String strPath = pathInterner.intern(SLASH_JOINER.join(path));
                                urlBuilder.put(strPath, fileInfo.getMods().get(0).getModId());
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

    private static final Pattern SLASH_REPLACER = Pattern.compile("/+");

    public static Enumeration<URL> findAllURLsForResource(String input) {
        String pathInput = SLASH_REPLACER.matcher(input).replaceAll("/");
        Collection<String> urlList = urlsForClass.get(pathInput);
        if(!urlList.isEmpty()) {
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
