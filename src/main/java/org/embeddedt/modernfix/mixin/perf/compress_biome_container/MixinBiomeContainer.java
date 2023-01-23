package org.embeddedt.modernfix.mixin.perf.compress_biome_container;

import it.unimi.dsi.fastutil.objects.Reference2ShortMap;
import it.unimi.dsi.fastutil.objects.Reference2ShortOpenHashMap;
import net.minecraft.util.BitArray;
import net.minecraft.util.IObjectIntIterable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.biome.provider.BiomeProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeContainer.class)
public class MixinBiomeContainer {
    @Mutable
    @Shadow
    @Final
    private Biome[] biomes;

    @Shadow
    @Final
    private IObjectIntIterable<Biome> biomeRegistry;

    @Shadow
    @Final
    private static int WIDTH_BITS;

    private Biome[] palette;
    private BitArray intArray;

    @Inject(method = "<init>(Lnet/minecraft/util/IObjectIntIterable;[I)V", at = @At("RETURN"))
    private void reinit1(IObjectIntIterable p_i241970_1_, int[] p_i241970_2_, CallbackInfo ci) {
        this.createCompact();
    }

    @Inject(method = "<init>(Lnet/minecraft/util/IObjectIntIterable;[Lnet/minecraft/world/biome/Biome;)V", at = @At("RETURN"))
    private void reinit2(IObjectIntIterable p_i241971_1_, Biome[] p_i241971_2_, CallbackInfo ci) {
        this.createCompact();
    }

    @Inject(method = "<init>(Lnet/minecraft/util/IObjectIntIterable;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/provider/BiomeProvider;)V", at = @At("RETURN"))
    private void reinit3(IObjectIntIterable p_i241968_1_, ChunkPos p_i241968_2_, BiomeProvider p_i241968_3_, CallbackInfo ci) {
        this.createCompact();
    }

    @Inject(method = "<init>(Lnet/minecraft/util/IObjectIntIterable;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/provider/BiomeProvider;[I)V", at = @At("RETURN"))
    private void reinit4(IObjectIntIterable p_i241969_1_, ChunkPos p_i241969_2_, BiomeProvider p_i241969_3_, int[] p_i241969_4_, CallbackInfo ci) {
        this.createCompact();
    }

    private void createCompact() {
        if (this.intArray != null || this.biomes[0] == null) {
            return;
        }

        Reference2ShortOpenHashMap<Biome> paletteTable = this.createPalette();
        Biome[] paletteIndexed = new Biome[paletteTable.size()];

        for (Reference2ShortMap.Entry<Biome> entry : paletteTable.reference2ShortEntrySet()) {
            paletteIndexed[entry.getShortValue()] = entry.getKey();
        }

        int packedIntSize = Math.max(2, MathHelper.ceillog2(paletteTable.size()));
        BitArray integerArray = new BitArray(packedIntSize, BiomeContainer.BIOMES_SIZE);

        Biome prevBiome = null;
        short prevId = -1;

        for (int i = 0; i < this.biomes.length; i++) {
            Biome biome = this.biomes[i];
            short id;

            if (prevBiome == biome) {
                id = prevId;
            } else {
                id = paletteTable.getShort(biome);

                if (id < 0) {
                    throw new IllegalStateException("Palette is missing entry: " + biome);
                }

                prevId = id;
                prevBiome = biome;
            }

            integerArray.set(i, id);
        }

        this.palette = paletteIndexed;
        this.intArray = integerArray;
        this.biomes = null;
    }

    private Reference2ShortOpenHashMap<Biome> createPalette() {
        Reference2ShortOpenHashMap<Biome> map = new Reference2ShortOpenHashMap<>();
        map.defaultReturnValue(Short.MIN_VALUE);

        Biome prevObj = null;
        short id = 0;

        for (Biome obj : this.biomes) {
            if (obj == prevObj) {
                continue;
            }

            if (map.getShort(obj) < 0) {
                map.put(obj, id++);
            }

            prevObj = obj;
        }

        return map;
    }

    /**
     * @author JellySquid
     * @reason Use paletted lookup
     */
    @Overwrite
    public int[] writeBiomes() {
        int size = this.intArray.getSize();
        int[] array = new int[size];

        for(int i = 0; i < size; ++i) {
            array[i] = this.biomeRegistry.getId(this.palette[this.intArray.get(i)]);
        }

        return array;
    }

    /**
     * @author JellySquid
     * @reason Use paletted lookup
     */
    @Overwrite
    public Biome getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        int x = biomeX & BiomeContainer.HORIZONTAL_MASK;
        int y = MathHelper.clamp(biomeY, 0, BiomeContainer.VERTICAL_MASK);
        int z = biomeZ & BiomeContainer.HORIZONTAL_MASK;

        return this.palette[this.intArray.get(y << WIDTH_BITS + WIDTH_BITS | z << WIDTH_BITS | x)];
    }
}
