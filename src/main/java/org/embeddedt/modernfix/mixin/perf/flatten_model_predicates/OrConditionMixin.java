package org.embeddedt.modernfix.mixin.perf.flatten_model_predicates;

import com.google.common.collect.Streams;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.client.renderer.block.model.multipart.OrCondition;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.predicate.StatePropertyPredicateHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(OrCondition.class)
public class OrConditionMixin {
    @Shadow @Final private Iterable<? extends Condition> conditions;

    /**
     * @author JellySquid
     * @reason Flatten predicates
     */
    @Overwrite
    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> stateManager) {
        return StatePropertyPredicateHelper.anyMatch(Streams.stream(this.conditions).map((multipartModelSelector) -> {
            return multipartModelSelector.getPredicate(stateManager);
        }).collect(Collectors.toList()));
    }
}
