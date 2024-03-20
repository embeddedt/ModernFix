package org.embeddedt.modernfix.common.mixin.perf.ticking_chunk_alloc;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    /**
     * @author embeddedt
     * @reason Avoid allocation if the chunk contains no structures
     */
    @Redirect(method = "getMobsAt", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"), require = 0)
    private Set<?> avoidSetAllocation(Map<?, ?> instance) {
        if(instance.isEmpty()) {
            return Collections.emptySet();
        } else {
            return instance.entrySet();
        }
    }
}
