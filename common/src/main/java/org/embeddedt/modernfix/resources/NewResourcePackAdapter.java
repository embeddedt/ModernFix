package org.embeddedt.modernfix.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.Collection;
import java.util.function.Function;

public class NewResourcePackAdapter {
    public static void sendToOutput(Function<ResourceLocation, IoSupplier<InputStream>> streamCreator, PackResources.ResourceOutput output, Collection<ResourceLocation> locations) {
        for(ResourceLocation rl : locations) {
            output.accept(rl, streamCreator.apply(rl));
        }
    }
}
