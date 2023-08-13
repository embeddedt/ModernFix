package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.embeddedt.modernfix.chunk.SafeBlockGetter;
import org.embeddedt.modernfix.duck.ISafeBlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 100)
public class BlockStateBaseMixin {
    @ModifyVariable(method = "getOffset", at = @At("HEAD"), argsOnly = true, index = 1)
    private BlockGetter useSafeGetter(BlockGetter g) {
        if(g instanceof ISafeBlockGetter) {
            SafeBlockGetter replacement = ((ISafeBlockGetter) g).mfix$getSafeBlockGetter();
            if(replacement.shouldUse())
                return replacement;
        }
        return g;
    }
}
