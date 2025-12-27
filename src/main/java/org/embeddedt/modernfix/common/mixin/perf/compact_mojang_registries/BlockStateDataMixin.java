package org.embeddedt.modernfix.common.mixin.perf.compact_mojang_registries;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.BlockStateData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = BlockStateData.class, priority = 2000)
public class BlockStateDataMixin {
    @Unique
    private static ObjectOpenHashSet<Tag> TAG_INTERNER;

    /**
     * @author embeddedt
     * @reason Reduce memory use of these constant CompoundTags via aggressive interning.
     */
    /*
    @ModifyExpressionValue(method = "parse", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/TagParser;parseTag(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag compactTag(CompoundTag tag) {
        if (TAG_INTERNER == null) {
            TAG_INTERNER = new ObjectOpenHashSet<>();
        }
        Map.Entry<String, Tag>[] entries = new Map.Entry[tag.size()];
        int i = 0;
        for (var key : tag.getAllKeys()) {
            Tag t = tag.get(key);
            if (t instanceof CompoundTag ct) {
                t = compactTag(ct);
            }
            t = TAG_INTERNER.addOrGet(t);
            entries[i++] = Map.entry(key, t);
        }
        return new CompoundTag(Map.ofEntries(entries));
    }

     */

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void clearInterner(CallbackInfo ci) {
        if (TAG_INTERNER != null) {
            TAG_INTERNER.clear();
            TAG_INTERNER.trim();
        }
    }
}
