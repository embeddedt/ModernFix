package org.embeddedt.modernfix.forge.mixin.bugfix.biome_dictionary_cme;

import net.minecraftforge.common.BiomeDictionary;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

@Mixin(value = BiomeDictionary.Type.class, remap = false)
public class BiomeDictionaryTypeMixin {
    @Shadow
    @Mutable
    @Final
    private static Map<String, BiomeDictionary.Type> byName;

    /**
     * @author embeddedt
     * @reason Biome types are created concurrently so the backing map needs to be thread-safe
     */
    @Redirect(method = "<clinit>", at = @At(value = "FIELD", target = "Lnet/minecraftforge/common/BiomeDictionary$Type;byName:Ljava/util/Map;", opcode = Opcodes.PUTSTATIC))
    private static void useConcurrentMap(Map<String, BiomeDictionary.Type> treeMap) {
        byName = new ConcurrentSkipListMap<>();
    }
}
