package org.embeddedt.modernfix.mixin.perf.flatten_model_predicates;

import com.google.common.collect.Streams;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.multipart.ICondition;
import net.minecraft.client.renderer.model.multipart.OrCondition;
import net.minecraft.state.StateContainer;
import org.embeddedt.modernfix.predicate.StatePropertyPredicateHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(OrCondition.class)
public class OrConditionMixin {
    @Shadow @Final private Iterable<? extends ICondition> conditions;

    /**
     * @author JellySquid
     * @reason Flatten predicates
     */
    @Overwrite
    public Predicate<BlockState> getPredicate(StateContainer<Block, BlockState> stateManager) {
        return StatePropertyPredicateHelper.anyMatch(Streams.stream(this.conditions).map((multipartModelSelector) -> {
            return multipartModelSelector.getPredicate(stateManager);
        }).collect(Collectors.toList()));
    }
}
