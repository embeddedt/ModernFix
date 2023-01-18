package org.embeddedt.modernfix.classloading;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

public class ModernFixResourceFinder {
    private static HashMap<String, ArrayList<URL>> urlsForClass = null;
    public static void init() {
        urlsForClass = new HashMap<>();
        for(ModFileInfo fileInfo : LoadingModList.get().getModFiles()) {
            ModFile file = fileInfo.getFile();
            IModLocator locator = file.getLocator();
            Path rootPath = locator.findPath(file, ".");
            System.out.println(rootPath.getParent().toAbsolutePath());
        }
        for(ArrayList<URL> list : urlsForClass.values()) {
            list.trimToSize();
        }
    }
    public static Enumeration<URL> findAllURLsForResource(String input) {
        ArrayList<URL> urlList = urlsForClass.get(input);
        if(urlList != null)
            return Collections.enumeration(urlList);
        else
            return Collections.emptyEnumeration();
    }
}
