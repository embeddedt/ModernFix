package org.embeddedt.modernfix.mixin.bugfix.concurrency;

import net.minecraft.tags.StaticTagHelper;
import net.minecraft.tags.TagCollection;
import net.minecraft.tags.TagContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Mixin(StaticTagHelper.class)
public class StaticTagHelperMixin<T> {
    @SuppressWarnings("rawtypes")
    @Shadow @Mutable
    @Final private List wrappers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void useCOWArrayList(Function<TagContainer, TagCollection<T>> function, CallbackInfo ci) {
        this.wrappers = new CopyOnWriteArrayList<>();
    }
}
