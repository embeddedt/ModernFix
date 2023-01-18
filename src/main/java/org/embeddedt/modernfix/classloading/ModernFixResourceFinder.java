package org.embeddedt.modernfix.classloading;

import net.minecraftforge.fml.loading.LoadingModList;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

public class ModernFixResourceFinder {
    public static Enumeration<URL> findAllURLsForResource(String input) {
        System.out.println(input);
        return Collections.emptyEnumeration(); //LoadingModList.get().findAllURLsForResource(input);
    }
}
