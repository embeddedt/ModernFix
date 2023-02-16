package org.embeddedt.modernfix.mixin.bugfix.concurrency;

import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.BooleanSupplier;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin<R extends Runnable> extends BlockableEventLoop<R> {

    protected MinecraftMixin(String p_i50403_1_) {
        super(p_i50403_1_);
    }

    @Override
    public void managedBlock(BooleanSupplier pIsDone) {
        if(!this.isSameThread()) {
            ModernFix.LOGGER.warn("A mod is calling Minecraft.managedBlock from the wrong thread. This is most likely related to one of our parallelizations.");
            ModernFix.LOGGER.warn("ModernFix will work around this, however ideally the issue should be patched in the other mod.");
            ModernFix.LOGGER.warn("Stacktrace", new IllegalThreadStateException());
            while(!pIsDone.getAsBoolean()) {
                try {
                    Thread.sleep(100);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            super.managedBlock(pIsDone);
        }
    }
}
