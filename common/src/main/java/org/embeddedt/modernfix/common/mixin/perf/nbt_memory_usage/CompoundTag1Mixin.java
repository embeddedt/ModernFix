package org.embeddedt.modernfix.common.mixin.perf.nbt_memory_usage;

import net.minecraft.nbt.Tag;
import org.embeddedt.modernfix.util.CanonizingStringMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

@Mixin(targets = "net/minecraft/nbt/CompoundTag$1")
public class CompoundTag1Mixin {
    @ModifyVariable(method = "loadCompound", at = @At(value = "INVOKE_ASSIGN", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;", remap = false))
    private static Map<String, Tag> modifyMap(Map<String, Tag> map) {
        CanonizingStringMap<Tag> newMap =  new CanonizingStringMap<>();
        if(map != null)
            newMap.putAll(map);
        return newMap;
    }
}