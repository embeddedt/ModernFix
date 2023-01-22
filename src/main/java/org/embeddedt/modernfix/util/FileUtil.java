package org.embeddedt.modernfix.util;

import java.io.File;

public class FileUtil {
    public static File childFile(File file) {
        file.getParentFile().mkdirs();
        return file;
    }
}
