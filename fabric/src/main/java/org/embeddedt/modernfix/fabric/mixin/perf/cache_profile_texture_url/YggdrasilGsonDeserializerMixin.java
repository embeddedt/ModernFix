package org.embeddedt.modernfix.fabric.mixin.perf.cache_profile_texture_url;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.ICachedProfileTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=YggdrasilMinecraftSessionService.class, remap=false)
@ClientOnlyMixin
public abstract class YggdrasilGsonDeserializerMixin {
	/**
	 * @author Fury_Phoenix
	 * @reason Unexpected deserialization
	 * **/

	@WrapOperation
	(method = "getTextures",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;getUrl()Ljava/lang/String;"
		)
	)
	private String setCachedHash(MinecraftProfileTexture texture, Operation<String> original) {
		// Because we don't have it set from deserialization
		String url = original.call(texture);
		((ICachedProfileTexture)texture).setCachedHash(url);
		return url;
	}
}
