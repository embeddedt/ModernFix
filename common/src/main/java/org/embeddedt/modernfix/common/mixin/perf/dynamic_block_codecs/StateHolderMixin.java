package org.embeddedt.modernfix.common.mixin.perf.dynamic_block_codecs;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(StateHolder.class)
public class StateHolderMixin {
    private static final LoadingCache<Block, MapCodec<BlockState>> MODERNFIX_CODEC_CACHE = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .build(new CacheLoader<Block, MapCodec<BlockState>>() {
                @Override
                public MapCodec<BlockState> load(Block block) throws Exception {
                    Supplier<BlockState> stateSupplier = block::defaultBlockState;
                    MapCodec<BlockState> mapCodec = MapCodec.of(Encoder.empty(), Decoder.unit(stateSupplier));
                    for(Property<?> property : block.getStateDefinition().getProperties()) {
                        mapCodec = StateDefinition.appendPropertyCodec(mapCodec, stateSupplier, property.getName(), property);
                    }
                    return mapCodec;
                }
            });

    @Redirect(method = "codec", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static <O, S extends StateHolder<O, S>> Codec<S> obtainCodec(Codec<O> codec, String typeKey, Function<S, O> type, Function<O, ? extends Codec<S>> codecFn, Codec<O> codecMethodArg, Function<O, S> stateSupplier) {
        return codec.dispatch(typeKey, type, block -> {
            if(block instanceof Block) {
                S state = stateSupplier.apply(block);
                if(state.getValues().isEmpty())
                    return Codec.unit(state);
                MapCodec<S> mapCodec;
                try {
                    mapCodec = (MapCodec<S>)MODERNFIX_CODEC_CACHE.get((Block)block);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                return mapCodec.fieldOf("Properties").codec();
            } else {
                return codecFn.apply(block);
            }
        });
    }
}
