package org.embeddedt.modernfix.classloading;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.*;
import net.minecraftforge.forgespi.language.IModInfo;
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
    private static HashMap<String, List<Pair<String, String>>> urlsForClass = null;
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
        urlsForClass = new HashMap<>();
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
                                String strPath = path.toString();
                                Pair<String, String> pathPair = Pair.of(fileInfo.getMods().get(0).getModId(), pathInterner.intern(strPath));
                                List<Pair<String, String>> urlList = urlsForClass.get(strPath);
                                if(urlList != null) {
                                    if(urlList.size() > 1)
                                        urlList.add(pathPair);
                                    else {
                                        /* Convert singleton to real list */
                                        ArrayList<Pair<String, String>> newList = new ArrayList<>(urlList);
                                        newList.add(pathPair);
                                        urlsForClass.put(strPath, newList);
                                    }
                                } else {
                                    /* Use a singleton list initially to keep memory usage down */
                                    urlsForClass.put(strPath, Collections.singletonList(pathPair));
                                }
                            });
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        for(List<Pair<String, String>> list : urlsForClass.values()) {
            if(list instanceof ArrayList)
                ((ArrayList<Pair<String, String>>)list).trimToSize();
        }
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
        input = SLASH_REPLACER.matcher(input).replaceAll("/");
        List<Pair<String, String>> urlList = urlsForClass.get(input);
        if(urlList != null) {
            return Iterators.asEnumeration(urlList.stream().map(pair -> {
                try {
                    return new URL("modjar://" + pair.getLeft() + "/" + pair.getRight());
                } catch(MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }).iterator());
        } else {
            return Collections.emptyEnumeration();
        }
    }
}
