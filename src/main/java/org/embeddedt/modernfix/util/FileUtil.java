package org.embeddedt.modernfix.util;

import java.io.File;
import java.util.regex.Pattern;

public class FileUtil {
    public static File childFile(File file) {
        file.getParentFile().mkdirs();
        return file;
    }

    private static final Pattern SLASH_PATTERN = Pattern.compile("(?:\\\\+|\\/+)");

    /**
     * Normalize a path by removing double slashes, etc.
     * @param path input path
     * @return a normalized version of the path
     */
    public static String normalize(String path) {
        return SLASH_PATTERN.matcher(path).replaceAll("/");
    }
}
