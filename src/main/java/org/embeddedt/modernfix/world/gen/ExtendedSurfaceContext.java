package org.embeddedt.modernfix.world.gen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ExtendedSurfaceContext {
    ThreadLocal<Set<ResourceKey<Biome>>> COMPUTED_POSSIBLE_BIOMES = new ThreadLocal<>();

    @Nullable
    Set<ResourceKey<Biome>> mfix$getPossibleBiomes();

    void mfix$applyPossibleBiomes();
}
