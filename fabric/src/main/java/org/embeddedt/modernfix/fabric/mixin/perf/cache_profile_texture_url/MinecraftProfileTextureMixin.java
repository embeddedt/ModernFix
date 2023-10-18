package org.embeddedt.modernfix.fabric.mixin.perf.cache_profile_texture_url;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import org.apache.commons.io.FilenameUtils;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.ICachedProfileTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.net.MalformedURLException;
import java.net.URL;

@Mixin(value = MinecraftProfileTexture.class, remap = false)
@ClientOnlyMixin
public abstract class MinecraftProfileTextureMixin implements ICachedProfileTexture {
	/**
	 * @author Fury_Phoenix
	 * @see org.embeddedt.modernfix.common.mixin.perf.cache_profile_texture_url.YggdrasilGsonDeserializerMixin#setCachedURL
	 **/
	
	private String modernfix$cachedHash;

	@Inject(method = "<init>(Ljava/lang/String;Ljava/util/Map;)V", at = @At("RETURN"), cancellable = false)
	private void cacheHash(String url, Map<String, String> metadata, CallbackInfo ci) {
		try {
			this.modernfix$cachedHash = FilenameUtils.getBaseName(new URL(url).getPath());
		} catch (MalformedURLException e) {}
	}
	
	public void setCachedHash(String url) {
		this.cacheHash(url, null, null);
	}
	
	// Overwrite - nuke new entirely
	public String getHash() {
		if (this.modernfix$cachedHash == null) {
			throw new IllegalArgumentException("Invalid profile texture url");
		}
		return this.modernfix$cachedHash;
	}
}
