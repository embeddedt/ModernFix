package org.embeddedt.modernfix.common.mixin.perf.resourcefullib_highlight_deduplication;

import com.teamresourceful.resourcefullib.client.highlights.HighlightHandler;
import com.teamresourceful.resourcefullib.client.highlights.base.Highlight;
import com.teamresourceful.resourcefullib.client.highlights.base.HighlightLine;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mixin(HighlightHandler.class)
@RequiresMod("resourcefullib")
@ClientOnlyMixin
public class HighlightHandlerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"), remap = false)
    private void deduplicateHighlights(CallbackInfo ci) {
        Map<BlockState, Highlight> stateCache;

        try {
            var stateCacheMap = this.getClass().getDeclaredField("STATE_CACHE");
            if (stateCacheMap.get(null) instanceof HashMap<?,?> hashMap) {
                stateCache = (Map<BlockState, Highlight>)hashMap;
            } else {
                throw new ReflectiveOperationException("Unexpected map type");
            }
        } catch (ReflectiveOperationException e) {
            ModernFix.LOGGER.error("Not applying Resourceful Lib patch due to reflection error", e);
            return;
        }

        ObjectOpenHashSet<Vector3f> pointCache = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<HighlightLine> lineCache = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<List<HighlightLine>> listCache = new ObjectOpenHashSet<>();
        Function<HighlightLine, HighlightLine> deduplicator = l -> {
            if (!lineCache.contains(l)) {
                l = new HighlightLine(
                        pointCache.addOrGet(l.start()),
                        pointCache.addOrGet(l.end()),
                        pointCache.addOrGet(l.normal())
                );
            }
            return lineCache.addOrGet(l);
        };

        stateCache.replaceAll((rl, highlight) -> {
            if (highlight == null) {
                return null;
            }
            List<HighlightLine> newList = highlight.lines();
            if (!listCache.contains(newList)) {
                newList = newList.stream().map(deduplicator).toList();
            }
            return new Highlight(highlight.id(), listCache.addOrGet(newList));
        });

        ModernFix.LOGGER.info("Deduplicated ResourcefulLib highlights ({} points, {} lines)", pointCache.size(), lineCache.size());
    }
}
