package org.embeddedt.modernfix.resources;

import com.google.common.base.Splitter;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.embeddedt.modernfix.util.FileUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public class CachedResourcePath {
    private final String[] pathComponents;

    public static final Interner<String> PATH_COMPONENT_INTERNER = Interners.newStrongInterner();
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');
    private static final String[] NO_PREFIX = new String[0];

    public CachedResourcePath(String[] prefix, Path path) {
        this(prefix, path, path.getNameCount(), true);
    }

    public CachedResourcePath(String s) {
        // normalize so we can guarantee there are no empty sections
        this(NO_PREFIX, SLASH_SPLITTER.splitToList(FileUtil.normalize(s)), false);
    }

    public <T> CachedResourcePath(String[] prefixElements, Collection<T> collection, boolean intern) {
        this(prefixElements, collection, collection.size(), intern);
    }

    public <T> CachedResourcePath(String[] prefixElements, Iterable<T> path, int count, boolean intern) {
        String[] components = new String[prefixElements.length + count];
        int i = 0;
        while(i < prefixElements.length) {
            components[i] = intern ? PATH_COMPONENT_INTERNER.intern(prefixElements[i]) : prefixElements[i];
            i++;
        }
        for(Object component : path) {
            String s = component.toString();
            if(s.length() == 0)
                continue;
            components[i] = intern ? PATH_COMPONENT_INTERNER.intern(s) : s;
            i++;
        }
        pathComponents = components;
    }

    public CachedResourcePath(String[] prefixElements, CachedResourcePath other) {
        String[] components = new String[prefixElements.length + other.pathComponents.length];
        int i = 0;
        while(i < prefixElements.length) {
            components[i] = PATH_COMPONENT_INTERNER.intern(prefixElements[i]);
            i++;
        }
        System.arraycopy(other.pathComponents, 0, components, i, other.pathComponents.length);
        pathComponents = components;
    }

    /**
     * DOES NOT INTERN!
     */
    public CachedResourcePath(String[] pathComponents) {
        this.pathComponents = pathComponents;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pathComponents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedResourcePath that = (CachedResourcePath) o;
        return Arrays.equals(pathComponents, that.pathComponents);
    }

    public String getFileName() {
        return pathComponents[pathComponents.length - 1];
    }

    public int getNameCount() {
        return pathComponents.length;
    }

    public String getNameAt(int i) {
        return pathComponents[i];
    }

    public String getFullPath(int startIndex) {
        StringBuilder sb = new StringBuilder();
        for(int i = startIndex; i < pathComponents.length; i++) {
            sb.append(pathComponents[i]);
            if(i != (pathComponents.length - 1))
                sb.append('/');
        }
        return sb.toString();
    }
}
