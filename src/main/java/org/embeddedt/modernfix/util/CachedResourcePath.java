package org.embeddedt.modernfix.util;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Streams;

import java.lang.ref.WeakReference;
import java.nio.file.Path;

public class CachedResourcePath {
    private final ImmutableList<String> pathComponents;
    private int hashCode = 0;

    private static final Interner<String> PATH_COMPONENT_INTERNER = Interners.newStrongInterner();
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');
    private static final Joiner SLASH_JOINER = Joiner.on('/');
    private WeakReference<String> fullPathCache = new WeakReference<>(null);

    public CachedResourcePath(Iterable<String> components) {
        ImmutableList.Builder<String> b = ImmutableList.builder();
        for(String s : components) {
            if(s == null || s.length() == 0)
                continue;
            b.add(PATH_COMPONENT_INTERNER.intern(s));
        }
        pathComponents = b.build();
    }

    public CachedResourcePath(Path path) {
        this(() -> Streams.stream(path.iterator()).map(Path::toString).iterator());
    }

    public CachedResourcePath(String s) {
        this(SLASH_SPLITTER.split(s));
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if(result != 0)
            return result;
        hashCode = pathComponents.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedResourcePath that = (CachedResourcePath) o;
        return pathComponents.equals(that.pathComponents);
    }

    public String getFileName() {
        return pathComponents.get(pathComponents.size() - 1);
    }

    public int getNameCount() {
        return pathComponents.size();
    }

    public String getFullPath() {
        String fPath = fullPathCache.get();
        if(fPath == null) {
            fPath = SLASH_JOINER.join(pathComponents);
            fullPathCache = new WeakReference<>(fPath);
        }
        return fPath;
    }
}
