package org.embeddedt.modernfix.mixin.perf.faster_texture_stitching;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.texture.Stitcher;
import org.embeddedt.modernfix.textures.StbStitcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mixin(Stitcher.class)
public class StitcherMixin {
    @Shadow @Final private Set<Stitcher.Holder> texturesToBeStitched;

    @Shadow private int storageX;

    @Shadow private int storageY;

    @Shadow @Final private static Comparator<Stitcher.Holder> HOLDER_COMPARATOR;
    private List<StbStitcher.LoadableSpriteInfo> loadableSpriteInfos;

    /**
     * @author embeddedt, SuperCoder79
     * @reason Use improved STB stitcher instead of the vanilla implementation, for performance
     */
    @Overwrite
    public void stitch() {
        ObjectArrayList<Stitcher.Holder> holderList = new ObjectArrayList<>(this.texturesToBeStitched);
        holderList.sort(HOLDER_COMPARATOR);
        Stitcher.Holder[] aholder = holderList.toArray(new Stitcher.Holder[0]);

        Pair<Pair<Integer, Integer>, List<StbStitcher.LoadableSpriteInfo>> packingInfo = StbStitcher.packRects(aholder);
        this.storageX = packingInfo.getFirst().getFirst();
        this.storageY = packingInfo.getFirst().getSecond();
        this.loadableSpriteInfos = packingInfo.getSecond();
    }

    /**
     * @author embeddedt, SuperCoder79
     * @reason We setup the image ourselves in the StbStitcher, so we just feed this information back into the vanilla code
     */
    @Overwrite
    public void gatherSprites(Stitcher.SpriteLoader spriteLoader) {
        for(StbStitcher.LoadableSpriteInfo info : loadableSpriteInfos) {
            spriteLoader.load(info.info, info.width, info.height, info.x, info.y);
        }
    }
}
