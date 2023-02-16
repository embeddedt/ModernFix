package org.embeddedt.modernfix.mixin.safety;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = BlockColors.class, priority = 700)
public class BlockColorsMixin {
    private Lock mapLock = new ReentrantLock();
    @Inject(method = "register", at = @At("HEAD"))
    private void lockMapBeforeAccess(BlockColor pBlockColor, Block[] pBlocks, CallbackInfo ci) {
        mapLock.lock();
    }
    @Inject(method = "register", at = @At("TAIL"))
    private void unlockMap(BlockColor pBlockColor, Block[] pBlocks, CallbackInfo ci) {
        mapLock.unlock();
    }
}
