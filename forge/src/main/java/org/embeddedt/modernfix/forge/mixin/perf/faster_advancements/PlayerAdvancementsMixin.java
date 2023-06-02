package org.embeddedt.modernfix.forge.mixin.perf.faster_advancements;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.Set;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow protected abstract boolean shouldBeVisible(Advancement advancement);

    @Shadow @Final private Set<Advancement> visible;

    @Shadow @Final private Set<Advancement> visibilityChanged;

    @Shadow @Final private Map<Advancement, AdvancementProgress> advancements;

    @Shadow @Final private Set<Advancement> progressChanged;

    /**
     * Avoids checking the same advancement many times.
     */
    private void ensureVisibilityDfs(Advancement advancement, Set<Advancement> visited) {
        if(visited.add(advancement)) {
            boolean bl = this.shouldBeVisible(advancement);
            boolean bl2 = this.visible.contains(advancement);
            if (bl && !bl2) {
                this.visible.add(advancement);
                this.visibilityChanged.add(advancement);
                if (this.advancements.containsKey(advancement)) {
                    this.progressChanged.add(advancement);
                }
            } else if (!bl && bl2) {
                this.visible.remove(advancement);
                this.visibilityChanged.add(advancement);
            }

            for(Advancement child : advancement.getChildren()) {
                ensureVisibilityDfs(child, visited);
            }

            Advancement parent = advancement.getParent();
            if (bl != bl2 && parent != null) {
                ensureVisibilityDfs(parent, visited);
            }
        }
    }

    /**
     * @author embeddedt
     * @reason avoid checking the same advancement many times
     */
    @Overwrite
    private void ensureVisibility(Advancement advancement) {
        ensureVisibilityDfs(advancement, new ReferenceOpenHashSet<>());
    }
}
