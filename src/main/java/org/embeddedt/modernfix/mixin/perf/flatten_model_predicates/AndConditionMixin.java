package org.embeddedt.modernfix.mixin.perf.flatten_model_predicates;

import com.google.common.collect.Streams;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.multipart.AndCondition;
import net.minecraft.client.renderer.model.multipart.ICondition;
import net.minecraft.state.StateContainer;
import org.embeddedt.modernfix.predicate.StatePropertyPredicateHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(AndCondition.class)
public class AndConditionMixin {
    @Shadow @Final private Iterable<? extends ICondition> conditions;

    /**
     * @author JellySquid
     * @reason Flatten predicates
     */
    @Overwrite
    public Predicate<BlockState> getPredicate(StateContainer<Block, BlockState> stateManager) {
        return StatePropertyPredicateHelper.allMatch(Streams.stream(this.conditions).map((multipartModelSelector) -> {
            return multipartModelSelector.getPredicate(stateManager);
        }).collect(Collectors.toList()));
    }
}
