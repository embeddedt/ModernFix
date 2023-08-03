package org.embeddedt.modernfix.forge.classloading;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModValidator;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ATInjector {
    public static void injectModATs() {
        CommonModUtil.runWithoutCrash(() -> {
            ModValidator validator = ObfuscationReflectionHelper.getPrivateValue(FMLLoader.class, null, "modValidator");
            List<ModFile> modFiles = ObfuscationReflectionHelper.getPrivateValue(ModValidator.class, validator, "candidateMods");
            List<Pair<ModFile, Path>> list = modFiles.stream()
                    .filter(file -> file.getAccessTransformer().isPresent())
                    .map(file -> Pair.of(file, file.getAccessTransformer().get()))
                    .collect(Collectors.toList());
            if(list.size() > 0) {
                ModernFixMixinPlugin.instance.logger.warn("Applying ATs from {} mods despite being in errored state, this might cause a crash!", list.size());
                for(var pair : list) {
                    try {
                        FMLLoader.addAccessTransformer(pair.getRight(), pair.getLeft());
                    } catch(RuntimeException e) {
                        ModernFixMixinPlugin.instance.logger.error("Exception occured applying AT from {}", pair.getLeft().getFileName(), e);
                    }
                }
            }
        }, "applying mod ATs in errored state");
    }
}
