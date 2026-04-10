package org.embeddedt.modernfix.common.mixin.perf.forge_registry_alloc;

import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.neoforged.neoforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.AbstractList;
import java.util.stream.Collector;
import java.util.stream.Stream;

@Mixin(DebugLevelSource.class)
public class DebugLevelSourceMixin {
    /**
     * @author embeddedt
     * @reason Reuse the existing blockstate list held by Forge instead of making a new one
     */
    @Redirect(method = "initValidStates", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;", ordinal = 0), remap = false)
    private static Object getStateList(Stream<?> instance, Collector<?, ?, ?> arCollector) {
        var idMapper = GameData.getBlockStateIDMap();
        return new AbstractList<>() {
            @Override
            public int size() {
                return idMapper.size();
            }

            @Override
            public Object get(int index) {
                var o = idMapper.byId(index);
                if (o == null) {
                    throw new IndexOutOfBoundsException();
                }
                return o;
            }
        };
    }
}
