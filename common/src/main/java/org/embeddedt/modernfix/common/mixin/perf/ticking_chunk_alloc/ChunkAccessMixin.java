package org.embeddedt.modernfix.common.mixin.perf.ticking_chunk_alloc;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.Map;

@Mixin(value = ChunkAccess.class, priority = 800)
public class ChunkAccessMixin {
    @Shadow @Final private Map<?, ?> structuresRefences;
    private Map<?, ?> mfix$structureRefsView;

    /**
     * @author embeddedt
     * @reason Cache returned map view to avoid allocations, return empty map when possible
     * so that iterator() calls don't allocate
     * <p></p>
     * Note: technically, this introduces an API change, as the return value may no longer be a live view
     * of the structure references of the chunk. It's unlikely this will affect anything in practice.
     */
    @Overwrite
    public Map<?, ?> getAllReferences() {
        if(this.structuresRefences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<?, ?> view = this.mfix$structureRefsView;
        if(view == null) {
            this.mfix$structureRefsView = view = Collections.unmodifiableMap(this.structuresRefences);
        }
        return view;
    }
}
