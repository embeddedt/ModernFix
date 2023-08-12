package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.UVController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Type;

@Mixin(BlockElementFace.Deserializer.class)
@ClientOnlyMixin
public class BlockElementFaceDeserializerMixin {

    @Redirect(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockElementFace;",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonDeserializationContext;deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;)Ljava/lang/Object;", ordinal = 0, remap = false))
    private Object skipUvsForInitialLoad(JsonDeserializationContext context, JsonElement element, Type type) {
        return UVController.useDummyUv.get() ? UVController.dummyUv : context.deserialize(element, type);
    }
}
