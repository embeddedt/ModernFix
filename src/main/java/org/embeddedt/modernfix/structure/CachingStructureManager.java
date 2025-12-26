package org.embeddedt.modernfix.structure;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.util.FileUtil;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

public class CachingStructureManager {
    private static ThreadLocal<MessageDigest> digestThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch(NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });
    private static final File STRUCTURE_CACHE_FOLDER = FileUtil.childFile(ModernFixPlatformHooks.INSTANCE.getGameDirectory().resolve("modernfix").resolve("structureCacheV1").toFile());

    static {
        STRUCTURE_CACHE_FOLDER.mkdirs();
    }

    public static StructureTemplate readStructure(ResourceLocation location, DataFixer datafixer, InputStream stream, HolderGetter<Block> blockGetter) throws IOException {
        CompoundTag tag = readStructureTag(location, datafixer, stream);
        StructureTemplate template = new StructureTemplate();
        template.load(blockGetter, tag);
        return template;
    }

    private static String encodeHex(byte[] byteArray) {
        StringBuilder sb = new StringBuilder();
        for(byte b : byteArray) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static final Set<String> laggyStructureMods = new ObjectOpenHashSet<>();

    private static final int MAX_HASH_LENGTH = 9;

    private static String truncateHash(String hash) {
        return hash.substring(0, MAX_HASH_LENGTH + 1);
    }

    public static CompoundTag readStructureTag(ResourceLocation location, DataFixer datafixer, InputStream stream) throws IOException {
        byte[] structureBytes = toBytes(stream);
        CompoundTag currentTag = NbtIo.readCompressed(new ByteArrayInputStream(structureBytes));
        if (!currentTag.contains("DataVersion", 99)) {
            currentTag.putInt("DataVersion", 500);
        }
        int currentDataVersion = currentTag.getInt("DataVersion");
        int requiredMinimumDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        if(currentDataVersion < requiredMinimumDataVersion) {
            /* Needs upgrade, try looking up from cache */
            MessageDigest hasher = digestThreadLocal.get();
            hasher.reset();
            String hash = encodeHex(hasher.digest(structureBytes));
            CompoundTag cachedUpgraded = getCachedUpgraded(location, truncateHash(hash));
            if(cachedUpgraded != null && cachedUpgraded.getInt("DataVersion") == requiredMinimumDataVersion) {
                ModernFix.LOGGER.debug("Using cached upgraded version of {}", location);
                currentTag = cachedUpgraded;
            } else {
                /*
                synchronized (laggyStructureMods) {
                    if(laggyStructureMods.add(location.getNamespace())) {
                        ModernFix.LOGGER.warn("The namespace {} contains an outdated structure file, which can cause worldgen lag. Please view debug.log for the full filename, determine which mod provides the structure, and report to the mod/datapack author, including the debug log.", location.getNamespace());
                    }
                }
                */
                ModernFix.LOGGER.debug("Structure {} is being run through DFU (hash {}), this will cause launch time delays", location, hash);
                currentTag = DataFixTypes.STRUCTURE.update(datafixer, currentTag, currentDataVersion,
                        SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                currentTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                saveCachedUpgraded(location, hash, currentTag);
            }
        }
        return currentTag;
    }

    private static File getCachePath(ResourceLocation location, String hash) {
        String fileName = location.getNamespace() + "_" + location.getPath().replace('/', '_') + "_" + hash + ".nbt";
        return new File(STRUCTURE_CACHE_FOLDER, fileName);
    }

    private static synchronized CompoundTag getCachedUpgraded(ResourceLocation location, String hash) {
        File theFile = getCachePath(location, hash);
        try {
            return NbtIo.readCompressed(theFile);
        } catch(FileNotFoundException e) {
            return null;
        } catch(IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static synchronized void saveCachedUpgraded(ResourceLocation location, String hash, CompoundTag tagToSave) {
        File theFile = getCachePath(location, truncateHash(hash));
        try {
            NbtIo.writeCompressed(tagToSave, theFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] toBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] tmp = new byte[16384];
        int n;
        while ((n = stream.read(tmp, 0, tmp.length)) != -1) {
            buffer.write(tmp, 0, n);
        }

        return buffer.toByteArray();
    }
}
