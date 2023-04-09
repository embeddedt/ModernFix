package org.embeddedt.modernfix.dynamicresources;

import dev.latvian.kubejs.script.data.KubeJSResourcePack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import org.embeddedt.modernfix.util.FileUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourcePackHandler {
    private static final List<PackHandler> packHandlers = new ArrayList<>();
    public static Collection<ResourceLocation> getExtraResources(ResourceManager manager, String path, Predicate<String> matchPredicate) {
        final String normalizedPath = FileUtil.normalize(path);
        return manager.listPacks().flatMap(pack -> packHandlers.stream().flatMap(handler -> {
            if(handler.shouldHandle(pack)) {
                return handler.getExtraResources(pack, normalizedPath, matchPredicate);
            } else
                return Stream.of();
        })).collect(Collectors.toList());
    }

    interface PackHandler {
        Stream<ResourceLocation> getExtraResources(PackResources pack, String path, Predicate<String> matchPredicate);
        boolean shouldHandle(PackResources pack);
    }

    static class KubeJSPackHandler implements PackHandler {

        @Override
        public Stream<ResourceLocation> getExtraResources(PackResources pack, String path, Predicate<String> matchPredicate) {
            KubeJSResourcePack p = (KubeJSResourcePack)pack;
            return p.getCachedResources().keySet().stream()
                    .filter(l -> l.getPath().startsWith(path))
                    .filter(l -> matchPredicate.test(l.getPath()));
        }

        @Override
        public boolean shouldHandle(PackResources pack) {
            return pack instanceof KubeJSResourcePack;
        }
    }

    static {
        if(ModList.get().isLoaded("kubejs")) {
            packHandlers.add(new KubeJSPackHandler());
        }
    }
}
