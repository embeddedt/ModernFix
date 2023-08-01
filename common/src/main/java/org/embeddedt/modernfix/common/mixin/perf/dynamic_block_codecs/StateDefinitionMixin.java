package org.embeddedt.modernfix.common.mixin.perf.dynamic_block_codecs;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StateDefinition.class)
public class StateDefinitionMixin<O, S extends StateHolder<O, S>> {
    @Shadow @Final private O owner;

    @ModifyVariable(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newLinkedHashMap()Ljava/util/LinkedHashMap;"), ordinal = 0)
    private MapCodec<?> replaceMapCodec(MapCodec<?> codec) {
        if(this.owner instanceof Block)
            return null;
        return codec;
    }
}
