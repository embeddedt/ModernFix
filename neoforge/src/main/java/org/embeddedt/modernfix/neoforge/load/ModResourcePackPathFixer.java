package org.embeddedt.modernfix.neoforge.load;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.IdentityHashMap;

public class ModResourcePackPathFixer {
    private static final IdentityHashMap<Path, IModFile> modFileByPath = new IdentityHashMap<>();

    public static synchronized IModFile getModFileByRootPath(Path path) {
        if(modFileByPath.size() == 0) {
            for(IModFileInfo info : ModList.get().getModFiles()) {
                modFileByPath.put(info.getFile().getFilePath(), info.getFile());
            }
        }
        return modFileByPath.get(path);
    }
}
