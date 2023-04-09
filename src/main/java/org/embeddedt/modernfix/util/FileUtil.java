package org.embeddedt.modernfix.util;

import java.io.File;
import java.util.regex.Pattern;

public class FileUtil {
    public static File childFile(File file) {
        file.getParentFile().mkdirs();
        return file;
    }

    /**
     * Normalize a path by removing double slashes, etc.
     * <p></p>
     * This implementation avoids creating a new string unless there are actually double slashes present
     * in the input path.
     * @param path input path
     * @return a normalized version of the path
     */
    public static String normalize(String path) {
        char prevChar = 0;
        StringBuilder sb = null;
        for(int i = 0; i < path.length(); i++) {
            char thisChar = path.charAt(i);
            if(prevChar != '/' || thisChar != prevChar) {
                /* This character should end up in the final string. If we are using the builder, add it there. */
                if(sb != null)
                    sb.append(thisChar);
            } else {
                /* This character should not end up in the final string. We need to make a buidler if we haven't
                 * done so yet.
                 */
                if(sb == null) {
                    sb = new StringBuilder(path.length());
                    sb.append(path, 0, i);
                }
            }
            prevChar = thisChar;
        }
        return sb == null ? path : sb.toString();
    }
}
