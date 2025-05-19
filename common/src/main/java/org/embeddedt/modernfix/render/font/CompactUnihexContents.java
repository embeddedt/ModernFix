package org.embeddedt.modernfix.render.font;

import net.minecraft.client.gui.font.providers.UnihexProvider;

/**
 * Implements more compact storage for LineData contents.
 *
 * Credit for the idea of using flattened fields rather than a backing array goes to @AnAwesomGuy.
 */
public class CompactUnihexContents {
    private static long extract8Bytes(byte[] arr, int off) {
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l |= ((long)arr[off + i] << (i * 8));
        }
        return l;
    }

    private static byte extractByte(long compressed, int off) {
        return (byte)((compressed >> (off * 8)) & 0xFF);
    }

    private static long extract4Shorts(short[] arr, int off) {
        long l = 0;
        for (int i = 0; i < 4; i++) {
            l |= ((long)arr[off + i] << (i * 16));
        }
        return l;
    }

    private static short extractShort(long compressed, int off) {
        return (short)((compressed >> (off * 16)) & 0xFFFF);
    }

    public static class Bytes implements UnihexProvider.LineData {
        private final long b0;
        private final long b8;

        public Bytes(byte[] contents) {
            this.b0 = extract8Bytes(contents, 0);
            this.b8 = extract8Bytes(contents, 8);
        }

        @Override
        public int line(int index) {
            if (index < 0 || index >= 16) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (index < 8) {
                return extractByte(b0, index) << 24;
            } else {
                return extractByte(b8, index - 8) << 24;
            }
        }

        @Override
        public int bitWidth() {
            return 8;
        }
    }

    public static class Shorts implements UnihexProvider.LineData {
        private final long b0;
        private final long b4;
        private final long b8;
        private final long b12;

        public Shorts(short[] contents) {
            this.b0 = extract4Shorts(contents, 0);
            this.b4 = extract4Shorts(contents, 4);
            this.b8 = extract4Shorts(contents, 8);
            this.b12 = extract4Shorts(contents, 12);
        }

        @Override
        public int line(int index) {
            if (index < 0 || index >= 16) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (index < 4) {
                return extractShort(b0, index) << 16;
            } else if (index < 8) {
                return extractShort(b4, index - 4) << 16;
            } else if (index < 12) {
                return extractShort(b8, index - 8) << 16;
            } else {
                return extractShort(b12, index - 12) << 16;
            }
        }

        @Override
        public int bitWidth() {
            return 16;
        }
    }
}
