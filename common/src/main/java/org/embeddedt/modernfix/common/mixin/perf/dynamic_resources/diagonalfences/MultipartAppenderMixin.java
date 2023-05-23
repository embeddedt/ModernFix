package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.diagonalfences;

import fuzs.diagonalfences.api.world.level.block.DiagonalBlock;
import fuzs.diagonalfences.client.model.MultipartAppender;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultipartAppender.class)
@RequiresMod("diagonalfences")
public abstract class MultipartAppenderMixin {
    @Shadow(remap = false)
    public static void appendDiagonalSelectors(ModelBakery modelBakery, MultiPart multiPart) {
        throw new AssertionError();
    }

    @Inject(method = "onPrepareModelBaking", at = @At("RETURN"))
    private static void setupHelper(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(new ModernFixClientIntegration() {
            @Override
            public UnbakedModel onUnbakedModelLoad(ResourceLocation location, UnbakedModel originalModel, ModelBakery bakery) {
                if(originalModel instanceof MultiPart multipart) {
                    Block block = multipart.definition.getOwner();
                    if(block instanceof FenceBlock && block instanceof DiagonalBlock diagonalBlock && diagonalBlock.hasProperties()) {
                        appendDiagonalSelectors(bakery, multipart);
                    }
                }
                return originalModel;
            }
        });
    }
}
