package org.embeddedt.modernfix.textures;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.lwjgl.stb.STBRPContext;
import org.lwjgl.stb.STBRPNode;
import org.lwjgl.stb.STBRPRect;
import org.lwjgl.stb.STBRectPack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/* Source: https://github.com/GTNewHorizons/lwjgl3ify/blob/f21364cd3d178aef863458a2faa1f5718a4e350d/src/main/java/me/eigenraven/lwjgl3ify/textures/StbStitcher.java */
public class StbStitcher {
    public static Pair<Pair<Integer, Integer>, List<LoadableSpriteInfo>> packRects(Stitcher.Holder[] holders) {
        int holderSize = holders.length;

        List<LoadableSpriteInfo> infoList = new ArrayList<>();

        // Allocate memory for the rectangles and the context
        try (STBRPRect.Buffer rectBuf = STBRPRect.malloc(holderSize);
             STBRPContext ctx = STBRPContext.malloc(); ) {

            // Initialize the rectangles that we'll be using in the calculation
            // While that's happening, sum up the area needed to fit all of the images
            int sqSize = 0;
            for (int j = 0; j < holderSize; ++j) {
                Stitcher.Holder holder = holders[j];

                int width = holder.width;
                int height = holder.height;

                // The ID here is just the array index, for easy lookup later
                rectBuf.get(j).set(j, (short)width, (short)height, (short)0, (short)0, false);

                sqSize += (width * height);
            }

            int size = Mth.smallestEncompassingPowerOfTwo((int) Math.sqrt(sqSize));
            int width = size * 2; // needed to fix weirdness in 1.16
            int height = size;

            // Internal node structure needed for STB
            try (STBRPNode.Buffer nodes = STBRPNode.malloc(width + 10)) {
                // Initialize the rect packer
                STBRectPack.stbrp_init_target(ctx, width, height, nodes);

                // Perform rectangle packing
                STBRectPack.stbrp_pack_rects(ctx, rectBuf);

                for (STBRPRect rect : rectBuf) {
                    Stitcher.Holder holder = holders[rect.id()];

                    // Ensure that everything is properly packed!
                    if (!rect.was_packed()) {
                        throw new StitcherException(holder.spriteInfo,
                                Stream.of(holders).map(arg -> arg.spriteInfo).collect(ImmutableList.toImmutableList()));
                    }

                    // Initialize the sprite now with the position and size that we've calculated so far
                    infoList.add(new LoadableSpriteInfo(holder.spriteInfo, width, height, rect.x(), rect.y()));
                    //holder.spriteInfo.initSprite(size, size, rect.x(), rect.y(), false);
                }

                return Pair.of(Pair.of(width, height), infoList);
            }
        }
    }

    public static class LoadableSpriteInfo {
        public final TextureAtlasSprite.Info info;
        public final int width;
        public final int height;
        public final int x;
        public final int y;

        LoadableSpriteInfo(TextureAtlasSprite.Info info, int width, int height, int x, int y) {
            this.info = info;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
        }
    }
}
