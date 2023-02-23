package org.embeddedt.modernfix.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;

import java.util.function.BooleanSupplier;

public class DeferredLevelLoadingScreen extends LevelLoadingScreen {
    private final BooleanSupplier worldLoadFinished;
    public DeferredLevelLoadingScreen(StoringChunkProgressListener arg, BooleanSupplier worldLoadFinished) {
        super(arg);
        this.worldLoadFinished = worldLoadFinished;
    }

    @Override
    public void tick() {
        super.tick();
        if(this.worldLoadFinished.getAsBoolean())
            this.onClose();
    }

    @Override
    public void renderBackground(PoseStack matrixStack, int vOffset) {
        renderDirtBackground(vOffset);
    }
}
