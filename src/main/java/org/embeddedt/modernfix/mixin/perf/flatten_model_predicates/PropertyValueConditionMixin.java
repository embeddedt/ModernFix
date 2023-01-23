package org.embeddedt.modernfix.mixin.perf.flatten_model_predicates;

import com.google.common.base.Splitter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.multipart.PropertyValueCondition;
import net.minecraft.state.Property;
import net.minecraft.state.StateContainer;
import org.embeddedt.modernfix.predicate.single.SingleMatchAny;
import org.embeddedt.modernfix.predicate.single.SingleMatchOne;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(PropertyValueCondition.class)
public class PropertyValueConditionMixin {
    @Shadow @Final private String key;

    @Shadow @Final private String value;

    @Shadow @Final private static Splitter PIPE_SPLITTER;

    /**
     * @author JellySquid
     * @reason De-duplication
     */
    @Overwrite
    public Predicate<BlockState> getPredicate(StateContainer<Block, BlockState> stateManager) {
        Property<?> property = stateManager.getProperty(this.key);

        if (property == null) {
            throw new RuntimeException(String.format("Unknown property '%s' on '%s'", this.key, stateManager.getOwner().toString()));
        }

        String valueString = this.value;
        boolean negate = !valueString.isEmpty() && valueString.charAt(0) == '!';

        if (negate) {
            valueString = valueString.substring(1);
        }

        List<String> split = PIPE_SPLITTER.splitToList(valueString);

        if (split.isEmpty()) {
            throw new RuntimeException(String.format("Empty value '%s' for property '%s' on '%s'", this.value, this.key, stateManager.getOwner().toString()));
        }

        Predicate<BlockState> predicate;

        if (split.size() == 1) {
            predicate = new SingleMatchOne(property, this.getPropertyValue(stateManager, property, valueString));
        } else {
            predicate = SingleMatchAny.create(property, split.stream()
                    .map(str -> this.getPropertyValue(stateManager, property, str))
                    .collect(Collectors.toList()));
        }

        return negate ? predicate.negate() : predicate;
    }

    private Object getPropertyValue(StateContainer<Block, BlockState> stateFactory, Property<?> property, String valueString) {
        Object value = property.getValue(valueString)
                .orElse(null);

        if (value == null) {
            throw new RuntimeException(String.format("Unknown value '%s' for property '%s' on '%s' in '%s'",
                    valueString, this.key, stateFactory.getOwner().toString(), this.value));
        }

        return value;
    }
}
