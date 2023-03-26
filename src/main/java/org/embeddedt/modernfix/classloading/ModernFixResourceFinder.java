package org.embeddedt.modernfix.classloading;

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
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ModernFixResourceFinder {
    private static HashMap<String, List<URL>> urlsForClass = null;
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
                                URL url = (URL)LamdbaExceptionUtils.uncheck(() -> {
                                    return new URL("modjar://" + fileInfo.getMods().get(0).getModId() + "/" + strPath);
                                });
                                List<URL> urlList = urlsForClass.get(strPath);
                                if(urlList != null) {
                                    if(urlList.size() > 1)
                                        urlList.add(url);
                                    else {
                                        /* Convert singleton to real list */
                                        ArrayList<URL> newList = new ArrayList<>(urlList);
                                        newList.add(url);
                                        urlsForClass.put(strPath, newList);
                                    }
                                } else {
                                    /* Use a singleton list initially to keep memory usage down */
                                    urlsForClass.put(strPath, Collections.singletonList(url));
                                }
                            });
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        for(List<URL> list : urlsForClass.values()) {
            if(list instanceof ArrayList)
                ((ArrayList<URL>)list).trimToSize();
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
        List<URL> urlList = urlsForClass.get(input);
        if(urlList != null)
            return Collections.enumeration(urlList);
        else {
            return Collections.emptyEnumeration();
        }
    }
}
