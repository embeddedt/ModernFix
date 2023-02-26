package org.embeddedt.modernfix.structure;

import com.mojang.datafixers.DataFixer;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.codec.binary.Hex;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.FileUtil;

import java.io.*;
import java.security.MessageDigest;

public class CachingStructureManager {
    private static ThreadLocal<MessageDigest> digestThreadLocal = ThreadLocal.withInitial(() -> LamdbaExceptionUtils.uncheck(() -> MessageDigest.getInstance("SHA-256")));
    private static final File STRUCTURE_CACHE_FOLDER = FileUtil.childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("structureCacheV1").toFile());

    static {
        STRUCTURE_CACHE_FOLDER.mkdirs();
    }

    public static StructureTemplate readStructure(ResourceLocation location, DataFixer datafixer, InputStream stream) throws IOException {
        CompoundTag tag = readStructureTag(location, datafixer, stream);
        StructureTemplate template = new StructureTemplate();
        template.load(tag);
        return template;
    }

    private static CompoundTag readStructureTag(ResourceLocation location, DataFixer datafixer, InputStream stream) throws IOException {
        byte[] structureBytes = toBytes(stream);
        CompoundTag currentTag = NbtIo.readCompressed(new ByteArrayInputStream(structureBytes));
        if (!currentTag.contains("DataVersion", 99)) {
            currentTag.putInt("DataVersion", 500);
        }
        int currentDataVersion = currentTag.getInt("DataVersion");
        if(currentDataVersion < SharedConstants.getCurrentVersion().getWorldVersion()) {
            /* Needs upgrade, try looking up from cache */
            MessageDigest hasher = digestThreadLocal.get();
            hasher.reset();
            String hash = new String(Hex.encodeHex(hasher.digest(structureBytes)));
            CompoundTag cachedUpgraded = getCachedUpgraded(location, hash);
            if(cachedUpgraded != null && cachedUpgraded.getInt("DataVersion") == SharedConstants.getCurrentVersion().getWorldVersion()) {
                ModernFix.LOGGER.warn("Using cached upgraded version of {}", location);
                currentTag = cachedUpgraded;
            } else {
                ModernFix.LOGGER.warn("Structure {} is being run through DFU (hash {}), this will cause launch time delays", location, hash);
                currentTag = NbtUtils.update(datafixer, DataFixTypes.STRUCTURE, currentTag, currentDataVersion,
                        SharedConstants.getCurrentVersion().getWorldVersion());
                currentTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
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
        File theFile = getCachePath(location, hash);
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
