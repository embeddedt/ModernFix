package org.embeddedt.modernfix.forge.mixin.perf.cache_profile_texture_url;

import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.SkinManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.TimeUnit;
import java.util.Map;

@Mixin(value=SkinManager.class)
@ClientOnlyMixin
public abstract class SkinManagerMixin {
	/**
	 * @author Fury_Phoenix
	 * @reason No lib mixins on (neo)forge, yet
	 * **/
	@Unique
	private final Map<MinecraftProfileTexture, String> hashCache = CacheBuilder.newBuilder()
        .expireAfterAccess(60, TimeUnit.SECONDS)
        .concurrencyLevel(1)
        // Excessive use of type hinting due to it assuming Object as the broadest correct type
        .<MinecraftProfileTexture, String>build()
        .asMap();

	@WrapOperation
	(
		method =
		"name = registerTexture " +
		// Target the lone overload that has a SkinTextureAvailableCallBack
		"desc = /\\(net\\/minecraft\\/class_1071\\$class_1072;\\)/",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;getHash()Ljava/lang/String;",
			remap = false
		),
		allow = 1
	)
	private String stashCachedHash(MinecraftProfileTexture texture, Operation<String> original) {
		return hashCache.computeIfAbsent(texture, k -> original.call());
	}
}
