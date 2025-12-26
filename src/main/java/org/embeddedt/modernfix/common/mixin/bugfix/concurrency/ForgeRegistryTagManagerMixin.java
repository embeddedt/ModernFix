package org.embeddedt.modernfix.common.mixin.bugfix.concurrency;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITag;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(targets = {"net/minecraftforge/registries/ForgeRegistryTagManager"})
public class ForgeRegistryTagManagerMixin<V> {
    @Shadow private volatile Map<TagKey<V>, ITag<V>> tags;

    /**
     * @author embeddedt (issue found by Uncandango)
     * @reason vanilla does not use the correct double-checked locking paradigm, which leads to race conditions
     */
    @WrapMethod(method = "getTag", remap = false)
    private ITag<V> getTagSafe(@NotNull TagKey<V> name, Operation<ITag<V>> original) {
        ITag<V> tag = this.tags.get(name);
        if (tag == null) {
            synchronized (this) {
                tag = original.call(name);
            }
        }
        return tag;
    }
}
