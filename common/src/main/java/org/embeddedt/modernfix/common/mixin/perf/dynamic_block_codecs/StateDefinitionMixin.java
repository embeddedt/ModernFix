package org.embeddedt.modernfix.common.mixin.perf.dynamic_block_codecs;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;
import java.util.function.Function;

@Mixin(StateDefinition.class)
public class StateDefinitionMixin<O, S extends StateHolder<O, S>> {
    @Shadow @Final private O owner;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static <O, S extends StateHolder<O, S>> StateDefinition.Factory<O, S> replaceMapCodec(StateDefinition.Factory<O, S> factory, Function<O, S> function, O object, StateDefinition.Factory<O, S> factory2, Map<String, Property<?>> map) {
        if(object instanceof Block)
            return (o, m, c) -> factory.create(o, m, null);
        return factory;
    }
}
