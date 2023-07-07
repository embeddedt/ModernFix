package org.embeddedt.modernfix.testing;

import java.nio.file.Path;
import java.util.Set;

@SuppressWarnings("unused")
public class AgentHooks {
    @SuppressWarnings({"unchecked", "rawtypes" })
    public static boolean addLibraryWithCheck(Set pathSet, Object path) {
        boolean shouldAdd;
        if(path instanceof Path) {
            shouldAdd = !((Path)path).toString().contains("minecraft-merged");
        } else
            shouldAdd = true;
        return shouldAdd && pathSet.add(path);
    }
}
