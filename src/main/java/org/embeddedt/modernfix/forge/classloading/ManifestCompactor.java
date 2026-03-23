package org.embeddedt.modernfix.forge.classloading;

import cpw.mods.jarhandling.impl.Jar;
import net.neoforged.fml.loading.LoadingModList;

import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;

public class ManifestCompactor {
    public static void compactManifests() {
        for (var mfi : LoadingModList.get().getModFiles()) {
            if (!(mfi.getFile().getSecureJar() instanceof Jar jar)) {
                continue;
            }
            var manifest = jar.moduleDataProvider().getManifest();
            if (manifest == null) {
                continue;
            }
            var entries = manifest.getEntries();
            var entryKeys = new HashSet<>(entries.keySet());
            var digests = Set.of(new Attributes.Name("SHA-256-Digest"), new Attributes.Name("SHA-384-Digest"));
            entryKeys.forEach(key -> entries.compute(key, (k, attrs) -> {
                if (attrs != null && attrs.keySet().stream().anyMatch(n -> n != null && !digests.contains(n))) {
                    return attrs;
                } else {
                    return null;
                }
            }));
        }
    }
}
