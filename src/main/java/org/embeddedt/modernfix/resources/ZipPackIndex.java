package org.embeddedt.modernfix.resources;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An index over a zip file's central directory that allows efficient namespace listing
 * and resource enumeration without iterating all entries on every call.
 *
 * <p>The index is built once at construction time by memory-mapping the zip's central
 * directory and parsing it into a {@link DirNode} tree. All subsequent queries run in
 * O(depth + k) time where k is the number of matching results.
 *
 * <p>The caller is responsible for opening and closing the {@link ZipFile}; this class
 * only holds a read-only view of the zip's metadata via a mmap'd buffer.
 */
public class ZipPackIndex {

    // -------------------------------------------------------------------------
    // Zip structural constants (identical to EfficientZipFileSystem in blacksmith)
    // -------------------------------------------------------------------------

    private static final int EOCD_SIGNATURE          = 0x06054b50;
    private static final int EOCD_SIZE               = 22;
    private static final int EOCD_OFF_CD_SIZE         = 12;
    private static final int EOCD_OFF_CD_OFFSET       = 16;
    private static final int EOCD_MAX_COMMENT_LENGTH  = 65535;

    private static final int CD_ENTRY_SIGNATURE      = 0x02014b50;
    private static final int CD_ENTRY_HEADER_SIZE    = 46;
    private static final int CD_OFF_FILENAME_LENGTH  = 28;
    private static final int CD_OFF_EXTRA_LENGTH     = 30;
    private static final int CD_OFF_COMMENT_LENGTH   = 32;

    private static final IntList EMPTY_OFFSETS = IntList.of();

    // -------------------------------------------------------------------------
    // DirNode
    // -------------------------------------------------------------------------

    static final class DirNode {
        Map<String, DirNode> childDirs;
        IntList fileChildOffsets; // offsets into cdBuffer for each direct file child

        DirNode() {
            childDirs = new Object2ObjectOpenHashMap<>();
            fileChildOffsets = EMPTY_OFFSETS;
        }

        void freeze() {
            if (fileChildOffsets instanceof IntArrayList arrayList) {
                arrayList.trim();
            }
            childDirs = childDirs.isEmpty() ? Map.of() : Map.copyOf(childDirs);
            for (DirNode child : childDirs.values()) {
                child.freeze();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Central directory buffer (memory-mapped or heap-allocated fallback). May be null for empty/invalid zips. */
    private final ByteBuffer cdBuffer;
    /** Top-level directories tracked by the index. */
    private final Set<String> trackedTopLevelDirs;
    /** Root of the directory tree, always non-null (may be empty but frozen). */
    private final DirNode root;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Build an index from the zip at the given path. Does not open a {@link ZipFile}
     * and does not keep a reference to one; the caller owns all {@link ZipFile} lifecycle.
     *
     * @throws IOException if the file cannot be read or its central directory cannot be parsed
     */
    public ZipPackIndex(Path zipPath) throws IOException {
        this.cdBuffer = readCentralDirectory(zipPath);
        // Computed here (not statically) so that any loader-injected PackType values
        // registered after class-load are included.
        Set<String> packTypeDirs = new HashSet<>();
        for (PackType type : PackType.values()) packTypeDirs.add(type.getDirectory());
        this.trackedTopLevelDirs = Set.copyOf(packTypeDirs);
        this.root = buildTree();
    }

    private static SeekableByteChannel obtainChannel(Path filePath) throws IOException {
        try {
            return FileChannel.open(filePath, StandardOpenOption.READ);
        } catch (Exception e) {
            return Files.newByteChannel(filePath);
        }
    }

    private static ByteBuffer readCentralDirectory(Path filePath) throws IOException {
        try (SeekableByteChannel channel = obtainChannel(filePath)) {
            long fileSize = channel.size();
            if (fileSize < EOCD_SIZE) return null;

            int tailSize = (int) Math.min(fileSize, (long) EOCD_SIZE + EOCD_MAX_COMMENT_LENGTH);
            ByteBuffer tail = ByteBuffer.allocate(tailSize);
            tail.order(ByteOrder.LITTLE_ENDIAN);

            long tailStart = fileSize - tailSize;
            while (tail.hasRemaining()) {
                channel.position(tailStart + tail.position());
                int n = channel.read(tail);
                if (n < 0) {
                    break;
                }
            }
            if (tail.hasRemaining()) {
                throw new IOException("Failed to read ZIP tail");
            }
            tail.flip();

            // Scan backwards for the EOCD signature and validate comment length.
            int eocdPos = -1;
            for (int i = tailSize - EOCD_SIZE; i >= 0; i--) {
                if (tail.getInt(i) == EOCD_SIGNATURE) {
                    int commentLen = Short.toUnsignedInt(tail.getShort(i + 20));
                    if (i + EOCD_SIZE + commentLen == tailSize) {
                        eocdPos = i;
                        break;
                    }
                }
            }
            if (eocdPos < 0) return null;

            long cdSize = Integer.toUnsignedLong(tail.getInt(eocdPos + EOCD_OFF_CD_SIZE));
            long cdOffset = Integer.toUnsignedLong(tail.getInt(eocdPos + EOCD_OFF_CD_OFFSET));
            if (cdSize == 0) return null;
            if (cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
                throw new IOException("ZIP64 not supported by ZipPackIndex");
            }
            if (cdOffset > fileSize - cdSize) {
                throw new IOException("Invalid central directory range");
            }

            // Try memory-mapping first; fall back to a heap copy if the OS refuses.
            if (channel instanceof FileChannel fc) {
                try {
                    ByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, cdOffset, cdSize);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    return buf;
                } catch (Exception ignored) {
                    // mmap unavailable (e.g. some Linux mount flags, container restrictions);
                    // read the central directory into a heap buffer instead.
                }
            }

            ByteBuffer buf = ByteBuffer.allocate((int) cdSize);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            while (buf.hasRemaining()) {
                channel.position(cdOffset + buf.position());
                int n = channel.read(buf);
                if (n < 0) throw new IOException("Truncated central directory during heap read");
            }
            buf.flip();
            return buf;
        }
    }

    private DirNode buildTree() throws IOException {
        var cdBuffer = this.cdBuffer;

        DirNode treeRoot = new DirNode();
        if (cdBuffer == null) {
            treeRoot.freeze();
            return treeRoot;
        }

        int pos = 0;
        int limit = cdBuffer.limit();
        while (pos + CD_ENTRY_HEADER_SIZE <= limit) {
            if (cdBuffer.getInt(pos) != CD_ENTRY_SIGNATURE) break;
            pos += indexCdEntry(pos, limit, treeRoot, cdBuffer);
        }

        treeRoot.freeze();
        return treeRoot;
    }

    /**
     * Parses the CD entry at {@code pos}, inserts it into the tree, and returns the
     * number of bytes to advance {@code pos} (i.e. the full record length).
     */
    private int indexCdEntry(int pos, int limit,
                             DirNode treeRoot,
                             ByteBuffer cdBuffer) throws IOException {
        int fileNameLen = Short.toUnsignedInt(cdBuffer.getShort(pos + CD_OFF_FILENAME_LENGTH));
        int extraLen    = Short.toUnsignedInt(cdBuffer.getShort(pos + CD_OFF_EXTRA_LENGTH));
        int commentLen  = Short.toUnsignedInt(cdBuffer.getShort(pos + CD_OFF_COMMENT_LENGTH));
        int recordLen   = CD_ENTRY_HEADER_SIZE + fileNameLen + extraLen + commentLen;
        if (pos + recordLen > limit) {
            throw new IOException("Truncated central directory");
        }

        byte[] nameBytes = new byte[fileNameLen];
        cdBuffer.get(pos + CD_ENTRY_HEADER_SIZE, nameBytes);

        DirNode current = treeRoot;
        boolean tracked = false;
        boolean skipped = false;
        int segStart = 0;

        for (int i = 0; i < fileNameLen; i++) {
            if (nameBytes[i] == '/') {
                int segLen = i - segStart;
                if (segLen > 0) {
                    String segment = new String(nameBytes, segStart, segLen, StandardCharsets.UTF_8);
                    if (!tracked) {
                        if (!trackedTopLevelDirs.contains(segment)) { skipped = true; break; }
                        tracked = true;
                    }
                    DirNode next = current.childDirs.get(segment);
                    //noinspection Java8MapApi
                    if (next == null) {
                        current.childDirs.put(segment, next = new DirNode());
                    }
                    current = next;
                }
                segStart = i + 1;
            }
        }

        // A remaining non-empty segment after the last '/' is a file basename.
        if (!skipped && tracked && segStart < fileNameLen) {
            if (current.fileChildOffsets == EMPTY_OFFSETS) {
                current.fileChildOffsets = new IntArrayList();
            }
            current.fileChildOffsets.add(pos);
        }

        return recordLen;
    }

    // -------------------------------------------------------------------------
    // CD buffer reads — absolute-position gets are thread-safe on Java 13+
    // -------------------------------------------------------------------------

    /**
     * Extract the basename (the portion after the last '/') of the entry whose
     * central-directory record starts at {@code cdOffset}.
     */
    String readBasename(int cdOffset) {
        int nameLen = Short.toUnsignedInt(cdBuffer.getShort(cdOffset + CD_OFF_FILENAME_LENGTH));
        byte[] nameBytes = new byte[nameLen];
        cdBuffer.get(cdOffset + CD_ENTRY_HEADER_SIZE, nameBytes);
        int lastSlash = -1;
        for (int i = nameBytes.length - 1; i >= 0; i--) {
            if (nameBytes[i] == '/') { lastSlash = i; break; }
        }
        return new String(nameBytes, lastSlash + 1, nameLen - lastSlash - 1, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Set<String> getTrackedTopLevelDirs() {
        return this.trackedTopLevelDirs;
    }

    /**
     * Returns all namespaces present under the given pack type directory.
     *
     * <p>Equivalent to {@code FilePackResources.getNamespaces(type)} but reads from
     * the pre-built tree rather than scanning all zip entries.
     */
    public Set<String> getNamespaces(PackType type) {
        DirNode typeNode = root.childDirs.get(type.getDirectory());
        if (typeNode == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (String ns : typeNode.childDirs.keySet()) {
            if (ns.equals(ns.toLowerCase(Locale.ROOT))) {
                result.add(ns);
            }
        }
        return result;
    }

    public boolean hasResource(String... paths) {
        var node = this.root;
        for (int i = 0; i < paths.length - 1; i++) {
            var path = paths[i];
            if (path.isEmpty()) {
                continue;
            }
            node = node.childDirs.get(path);
            if (node == null) {
                return false;
            }
        }
        String basename = paths[paths.length - 1];
        var offsets = node.fileChildOffsets;
        for (int i = 0; i < offsets.size(); i++) {
            if (basename.equals(readBasename(offsets.getInt(i)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enumerate all resources under {@code type/namespace/path/} and deliver them
     * to {@code output}.
     *
     * <p>Equivalent to {@code FilePackResources.listResources(type, namespace, path, output)}
     * but uses the pre-built tree for O(k) traversal instead of a full zip scan.
     *
     * @param zipFile the open zip file, used only to supply {@link InputStream}s on demand;
     *                the caller retains ownership of its lifecycle
     */
    public void listResources(PackType type, String namespace, String path,
                              ZipFile zipFile, PackResources.ResourceOutput output) {
        DirNode node = root.childDirs.get(type.getDirectory());
        if (node == null) return;
        node = node.childDirs.get(namespace);
        if (node == null) return;

        // Walk to the requested sub-path
        String rlSubPath;
        if (!path.isEmpty()) {
            for (String segment : path.split("/")) {
                if (segment.isEmpty()) continue;
                node = node.childDirs.get(segment);
                if (node == null) return;
            }
            rlSubPath = path + "/";
        } else {
            rlSubPath = "";
        }

        // entryPrefix = the part of the zip entry name before the ResourceLocation path
        String entryPrefix = type.getDirectory() + "/" + namespace + "/";
        collectResources(node, entryPrefix, rlSubPath, zipFile, namespace, output);
    }

    /**
     * Recursively walk {@code node}, reconstructing zip entry names as we go and
     * emitting each file to {@code output}.
     *
     * @param entryPrefix the constant prefix before the RL path, e.g. {@code "assets/minecraft/"}
     * @param rlSubPath   the RL-relative path accumulated so far, e.g. {@code "textures/block/"}
     */
    private void collectResources(DirNode node, String entryPrefix, String rlSubPath,
                                  ZipFile zipFile, String namespace,
                                  PackResources.ResourceOutput output) {
        // Emit direct file children of this node
        var offsets = node.fileChildOffsets;
        for (int i = 0; i < offsets.size(); i++) {
            String basename = readBasename(offsets.getInt(i));
            String rlPathFull = rlSubPath + basename;
            ResourceLocation rl = ResourceLocation.tryBuild(namespace, rlPathFull);
            if (rl != null) {
                ZipEntry entry = zipFile.getEntry(entryPrefix + rlPathFull);
                if (entry != null) {
                    output.accept(rl, IoSupplier.create(zipFile, entry));
                }
            }
        }
        // Recurse into subdirectories
        for (Map.Entry<String, DirNode> child : node.childDirs.entrySet()) {
            collectResources(child.getValue(), entryPrefix,
                    rlSubPath + child.getKey() + "/", zipFile, namespace, output);
        }
    }
}
