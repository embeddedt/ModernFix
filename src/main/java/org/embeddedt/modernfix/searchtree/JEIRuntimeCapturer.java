package org.embeddedt.modernfix.searchtree;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.library.runtime.JeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.Optional;

@JeiPlugin
public class JEIRuntimeCapturer implements IModPlugin {
    private static WeakReference<JeiRuntime> runtimeHandle = new WeakReference<>(null);

    public static Optional<JeiRuntime> runtime() {
        return Optional.ofNullable(runtimeHandle.get());
    }

    @Override
    public ResourceLocation getPluginUid() {
        return null;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtimeHandle = new WeakReference<>((JeiRuntime)jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtimeHandle = new WeakReference<>(null);
    }
}
