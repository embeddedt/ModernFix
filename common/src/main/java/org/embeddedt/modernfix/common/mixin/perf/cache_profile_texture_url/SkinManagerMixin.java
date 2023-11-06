package org.embeddedt.modernfix.common.mixin.perf.cache_profile_texture_url;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Mixin(targets = {"net/minecraft/client/resources/SkinManager$TextureCache" })
public class SkinManagerMixin {
    @Unique
    private final Cache<MinecraftProfileTexture, String> mfix$hashCache = CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS)
            .concurrencyLevel(1)
            .build();

    @Redirect(method = { "getOrLoad", "registerTexture" },
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;getHash()Ljava/lang/String;", remap = false))
    private String useCachedHash(MinecraftProfileTexture texture) {
        // avoid lambda allocation for common case
        String hash = mfix$hashCache.getIfPresent(texture);
        if(hash != null)
            return hash;

        try {
            return mfix$hashCache.get(texture, texture::getHash);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
