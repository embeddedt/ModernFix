package org.embeddedt.modernfix.common.mixin.feature.remove_chat_signing;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.nio.file.Path;

@Mixin(ProfileKeyPairManager.class)
@ClientOnlyMixin
public interface ProfileKeyPairManagerMixin {
    /**
     * @author embeddedt
     * @reason never use the key pair
     */
    @Overwrite
    static ProfileKeyPairManager create(UserApiService userApiService, User user, Path gameDirectory) {
        return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
    }
}
