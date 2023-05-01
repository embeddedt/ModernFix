package org.embeddedt.modernfix;

import com.google.common.cache.CacheLoader;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileWalker extends CacheLoader<Pair<Path, Integer>, List<Path>> {
    public static final FileWalker INSTANCE = new FileWalker();

    @Override
    public List<Path> load(Pair<Path, Integer> key) throws Exception {
        try(Stream<Path> stream = Files.walk(key.getLeft(), key.getRight())) {
            return stream.collect(Collectors.toList());
        }
    }
}
