package org.embeddedt.modernfix.common.mixin.perf.nbt_memory_usage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.embeddedt.modernfix.util.CanonizingStringMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(CompoundTag.class)
public class CompoundTagMixin {
    @Shadow @Final
    private Map<String, Tag> tags;

    /**
     * Ensure that the default backing map is a CanonizingStringMap.
     */
    @ModifyArg(method = "<init>()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;<init>(Ljava/util/Map;)V"), index = 0)
    private static Map<String, Tag> useCanonizingStringMap(Map<String, Tag> incoming) {
        CanonizingStringMap<Tag> newMap = new CanonizingStringMap<>();
        newMap.putAll(incoming);
        return newMap;
    }

    /**
     * @author embeddedt
     * @reason use more efficient method when copying canonizing string map
     */
    @Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), cancellable = true)
    public void copyEfficient(CallbackInfoReturnable<Tag> cir) {
        if(this.tags instanceof CanonizingStringMap) {
            cir.setReturnValue(new CompoundTag(CanonizingStringMap.deepCopy((CanonizingStringMap<Tag>)this.tags, Tag::copy)));
        }
    }
}
