package org.embeddedt.modernfix.common.mixin.feature.remove_chat_signing;

import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ProfileKeyPairManager.class)
@ClientOnlyMixin
public class ProfileKeyPairManagerMixin {
    /**
     * @author embeddedt
     * @reason never use the key pair
     */
    @Overwrite
    private CompletableFuture<Optional<?>> readOrFetchProfileKeyPair(Optional<?> optional) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
