package org.embeddedt.modernfix.common.mixin.perf.inline_methods;

import com.mojang.datafixers.util.Pair;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(BannerRenderer.class)
@ClientOnlyMixin
public abstract class BannerRendererMixin {
	
	/**
	 * @author Fury_Phoenix
	 * @reason JIT doesn't inline due to too much stack
	 */
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;)V"
		)
	)
	private static void inlineRenderCanvas(PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlay, ModelPart modelPart, Material material, boolean isBanner, List<Pair<BannerPattern, DyeColor>> patterns) {
		BannerRenderer.renderPatterns(poseStack, multiBufferSource, light, overlay, modelPart, material, isBanner, patterns, false);
	}
}
