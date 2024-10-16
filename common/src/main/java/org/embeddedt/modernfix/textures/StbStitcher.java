package org.embeddedt.modernfix.textures;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.util.Mth;
import org.embeddedt.modernfix.ModernFix;
import org.lwjgl.stb.STBRPContext;
import org.lwjgl.stb.STBRPNode;
import org.lwjgl.stb.STBRPRect;
import org.lwjgl.stb.STBRectPack;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/* Source: https://github.com/GTNewHorizons/lwjgl3ify/blob/f21364cd3d178aef863458a2faa1f5718a4e350d/src/main/java/me/eigenraven/lwjgl3ify/textures/StbStitcher.java */
public class StbStitcher {
    /* Most of this logic is to allow use of LWJGL versions where coordinates are short and versions where they are int */
    private static final MethodHandle MH_rect_shortSet, MH_rect_intSet, MH_rect_intX, MH_rect_intY, MH_rect_shortX,
        MH_rect_shortY;

    static {
        MethodHandle shortM = null, intM = null;
        List<ReflectiveOperationException> exceptions = new ArrayList<>();
        try {
            intM = publicLookup().findVirtual(STBRPRect.class, "set", methodType(STBRPRect.class,
                    int.class, /* id */
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class));
        } catch(ReflectiveOperationException e) {
            exceptions.add(e);
        }
        try {
            shortM = publicLookup().findVirtual(STBRPRect.class, "set", methodType(STBRPRect.class,
                    int.class, /* id */
                    short.class,
                    short.class,
                    short.class,
                    short.class,
                    boolean.class));
        } catch(ReflectiveOperationException e) {
            exceptions.add(e);
        }
        if(shortM == null && intM == null) {
            IllegalStateException e = new IllegalStateException("An STBRPRect set method could not be located");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
        MH_rect_shortSet = shortM;
        MH_rect_intSet = intM;
        /* Now look for X methods */
        exceptions.clear();
        try {
            intM = publicLookup().findVirtual(STBRPRect.class, "x", methodType(int.class));
        } catch(ReflectiveOperationException e) {
            exceptions.add(e);
        }
        try {
            shortM = publicLookup().findVirtual(STBRPRect.class, "x", methodType(short.class));
        } catch(ReflectiveOperationException e) {
            exceptions.add(e);
        }
        if(shortM == null && intM == null) {
            IllegalStateException e = new IllegalStateException("An STBRPRect x() method could not be located");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
        MH_rect_shortX = shortM;
        MH_rect_intX = intM;
        /* Assume that Y is the same */
        try {
            if(MH_rect_shortX != null) {
                MH_rect_shortY = publicLookup().findVirtual(STBRPRect.class, "y", methodType(short.class));
                MH_rect_intY = null;
            } else { /* it must be int */
                MH_rect_intY = publicLookup().findVirtual(STBRPRect.class, "y", methodType(int.class));
                MH_rect_shortY = null;
            }
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("An STBRPRect y() method could not be located", e);
        }
    }

    public static STBRPRect setWrapper(STBRPRect rect, int id, int width, int height, int x, int y, boolean was_packed) {
        try {
            if(MH_rect_shortSet != null)
                return (STBRPRect)MH_rect_shortSet.invokeExact(rect, id, (short)width, (short)height, (short)x, (short)y, was_packed);
            else
                return (STBRPRect)MH_rect_intSet.invokeExact(rect, id, width, height, x, y, was_packed);
        } catch(Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static int getX(STBRPRect rect) {
        try {
            if(MH_rect_shortX != null)
                return (short)MH_rect_shortX.invokeExact(rect);
            else
                return (int)MH_rect_intX.invokeExact(rect);
        } catch(Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static int getY(STBRPRect rect) {
        try {
            if(MH_rect_shortX != null)
                return (short)MH_rect_shortY.invokeExact(rect);
            else
                return (int)MH_rect_intY.invokeExact(rect);
        } catch(Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static <T extends Stitcher.Entry> Pair<Pair<Integer, Integer>, List<LoadableSpriteInfo<T>>> packRects(Stitcher.Holder<T>[] holders) {
        int holderSize = holders.length;

        List<LoadableSpriteInfo<T>> infoList = new ArrayList<>();

        // Allocate memory for the rectangles and the context
        try (STBRPRect.Buffer rectBuf = STBRPRect.malloc(holderSize);
             STBRPContext ctx = STBRPContext.malloc(); ) {

            // Initialize the rectangles that we'll be using in the calculation
            // While that's happening, sum up the area needed to fit all of the images
            int totalArea = 0;
            int longestWidth = 0, longestHeight = 0;
            for (int j = 0; j < holderSize; ++j) {
                Stitcher.Holder<T> holder = holders[j];

                int width = holder.width();
                int height = holder.height();

                // The ID here is just the array index, for easy lookup later
                STBRPRect rect = rectBuf.get(j);

                setWrapper(rect, j, width, height, 0, 0, false);

                totalArea += (width * height);
                longestWidth = Math.max(longestWidth, width);
                longestHeight = Math.max(longestHeight, height);
            }

            longestWidth = Mth.smallestEncompassingPowerOfTwo(longestWidth);
            longestHeight = Mth.smallestEncompassingPowerOfTwo(longestHeight);

            /*
             * The atlas needs to be at least this wide and tall to accommodate oddly shaped sprites. If this is
             * not enough, keep doubling the smaller of the two values until it's big enough.
             */
            while((longestWidth*longestHeight) < totalArea) {
                if(longestWidth <= longestHeight)
                    longestWidth *= 2;
                else
                    longestHeight *= 2;
            }

            /*
             * Sometimes our guess is off and we actually need a bigger atlas. We will try up to 4 times to double
             * the atlas size, if that fails then give up.
             */
            int numTries = 0;
            while(true) {
                numTries++;
                // Internal node structure needed for STB
                try (STBRPNode.Buffer nodes = STBRPNode.malloc(longestWidth + 10)) {
                    // Initialize the rect packer
                    STBRectPack.stbrp_init_target(ctx, longestWidth, longestHeight, nodes);

                    // Perform rectangle packing
                    STBRectPack.stbrp_pack_rects(ctx, rectBuf);

                    for (STBRPRect rect : rectBuf) {
                        Stitcher.Holder<T> holder = holders[rect.id()];

                        // Ensure that everything is properly packed!
                        if (!rect.was_packed()) {
                            throw new StitcherException(holder.entry(),
                                    Stream.of(holders).map(Stitcher.Holder::entry).collect(ImmutableList.toImmutableList()));
                        }
                    }

                    for (STBRPRect rect : rectBuf) {
                        Stitcher.Holder<T> holder = holders[rect.id()];

                        // Initialize the sprite now with the position and size that we've calculated so far
                        infoList.add(new LoadableSpriteInfo(holder.entry(), longestWidth, longestHeight, getX(rect), getY(rect)));
                    }

                    return Pair.of(Pair.of(longestWidth, longestHeight), infoList);
                } catch (StitcherException e) {
                    if(numTries >= 4) {
                        // If we get here, we weren't able to stitch. Throw an error.
                        ModernFix.LOGGER.error("Stitcher ran out of space with target atlas size " + longestWidth + "x" + longestHeight + ":");
                        for(Stitcher.Holder<T> h : holders) {
                            ModernFix.LOGGER.error(" - " + h.entry().name() + ", " + h.width() + "x" + h.height());
                        }
                        throw e;
                    } else {
                        // double the atlas size and try again
                        if(longestWidth <= longestHeight)
                            longestWidth *= 2;
                        else
                            longestHeight *= 2;
                    }
                }
            }
        }
    }

    public static class LoadableSpriteInfo<T extends Stitcher.Entry> {
        public final T info;
        public final int width;
        public final int height;
        public final int x;
        public final int y;

        LoadableSpriteInfo(T info, int width, int height, int x, int y) {
            this.info = info;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
        }
    }
}
