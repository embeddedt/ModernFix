package org.embeddedt.modernfix.mixin.perf.nbt_memory_usage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.embeddedt.modernfix.util.CanonizingStringMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(CompoundTag.class)
public class CompoundTagMixin {
    @Shadow @Final @Mutable
    private Map<String, Tag> tags;

    /**
     * Ensure that the backing map is always a CanonizingStringMap.
     */
    @Redirect(method = "<init>(Ljava/util/Map;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/nbt/CompoundTag;tags:Ljava/util/Map;", ordinal = 0))
    private void replaceTagMap(CompoundTag tag, Map<String, Tag> incomingMap) {
        if(incomingMap instanceof CanonizingStringMap)
            this.tags = incomingMap;
        else {
            this.tags = new CanonizingStringMap<>();
            this.tags.putAll(incomingMap);
        }
    }

    /**
     * @author embeddedt
     * @reason use more efficient method when copying canonizing string map
     */
    @Inject(method = "copy()Lnet/minecraft/nbt/Tag;", at = @At("HEAD"), cancellable = true)
    public void copyEfficient(CallbackInfoReturnable<Tag> cir) {
        if(this.tags instanceof CanonizingStringMap) {
            cir.setReturnValue(new CompoundTag(CanonizingStringMap.deepCopy((CanonizingStringMap<Tag>)this.tags, Tag::copy)));
        }
    }
}
