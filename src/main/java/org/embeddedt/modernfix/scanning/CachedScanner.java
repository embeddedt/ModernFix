package org.embeddedt.modernfix.scanning;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CachedScanner {
    private static final Path SCAN_CACHE_FOLDER = FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("scanCacheV1");

    private static File getCacheFileLocation(ModFile file) {
        Path modPath = FMLPaths.MODSDIR.get().relativize(file.getFilePath());
        return SCAN_CACHE_FOLDER.resolve(modPath).toFile();
    }

    private static MessageDigest modFileDigest = LamdbaExceptionUtils.uncheck(() -> MessageDigest.getInstance("SHA-256"));

    private static byte[] computeModFileHash(ModFile file) {
        modFileDigest.reset();
        byte[] buffer = new byte[8192];
        int bytesRead;
        try(BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(file.getFilePath()))) {
            while((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
                modFileDigest.update(buffer, 0, bytesRead);
            }
        } catch(IOException e) {
            throw new RuntimeException("Failed to read mod file", e);
        }
        return modFileDigest.digest();
    }

    private static String getCurrentLangVersion(ModFile file) {
        IModLanguageProvider loader = file.getLoader();
        String currentLangVersion = loader.getClass().getPackage().getImplementationVersion();
        if(currentLangVersion == null)
            currentLangVersion = "[none]";
        return currentLangVersion;
    }

    static class SerializedClassData implements Serializable {
        public String classTypeDesc;
        public String parentTypeDesc;
        public ArrayList<String> interfacesTypeDesc;
    }

    static class SerializedAnnotationData implements Serializable {
        public String annotationTypeDesc;
        public ElementType targetTypeDesc;
        public String classTypeDesc;
        public String memberName;
        public Map<String, Object> annotationData;
    }

    private static ModFileScanData deserializeScanData(ModFile file, ObjectInputStream stream) throws IOException, ClassNotFoundException {
        ModFileScanData result = new ModFileScanData();
        result.addModFileInfo(file.getModFileInfo());
        /* Read all the classes */
        ArrayList<SerializedClassData> classDataList = (ArrayList<SerializedClassData>)stream.readObject();
        Set<ModFileScanData.ClassData> classDataSet = result.getClasses();
        for(SerializedClassData data : classDataList) {
            classDataSet.add(new ModFileScanData.ClassData(
                    Type.getType(data.classTypeDesc),
                    Type.getType(data.parentTypeDesc),
                    data.interfacesTypeDesc.stream().map(Type::getType).collect(Collectors.toSet())));
        }
        /* Read all the annotations */
        ArrayList<SerializedAnnotationData> annotationDataList = (ArrayList<SerializedAnnotationData>)stream.readObject();
        Set<ModFileScanData.AnnotationData> annotationDataSet = result.getAnnotations();
        for(SerializedAnnotationData data : annotationDataList) {
            annotationDataSet.add(new ModFileScanData.AnnotationData(
                    Type.getType(data.annotationTypeDesc),
                    data.targetTypeDesc,
                    Type.getType(data.classTypeDesc),
                    data.memberName,
                    data.annotationData
            ));
        }
        return result;
    }

    public static ModFileScanData getCachedDataForFile(ModFile file) {
        byte[] currentHash = computeModFileHash(file);
        String currentLangVersion = getCurrentLangVersion(file);
        try(ObjectInputStream stream = new ObjectInputStream(new FileInputStream(getCacheFileLocation(file)))) {
            byte[] modFileHash = (byte[])stream.readObject();
            if(!Arrays.equals(modFileHash, currentHash)) {
                return null;
            }
            String langVersion = stream.readUTF();
            if(!langVersion.equals(currentLangVersion))
                return null;
            return deserializeScanData(file, stream);
        } catch(IOException | ClassNotFoundException e) {
            if(!(e instanceof FileNotFoundException))
                e.printStackTrace();
            return null;
        }
    }

    private static Field classDataTypeField, classDataParentField, classDataInterfacesField;

    public static void saveCachedDataForFile(ModFile file, ModFileScanData scanData) {
        try(ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(getCacheFileLocation(file)))) {
            stream.writeObject(computeModFileHash(file));
            stream.writeObject(getCurrentLangVersion(file));
            /* serialize scan data */
            ArrayList<SerializedClassData> serializedClassDataList = new ArrayList<>();
            for(ModFileScanData.ClassData data : scanData.getClasses()) {
                SerializedClassData sData = new SerializedClassData();
                if(classDataTypeField == null) {
                    classDataTypeField = ModFileScanData.ClassData.class.getDeclaredField("clazz");
                    classDataTypeField.setAccessible(true);
                }
                sData.classTypeDesc = ((Type)classDataTypeField.get(data)).getDescriptor();
                if(classDataTypeField == null) {
                    classDataTypeField = ModFileScanData.ClassData.class.getDeclaredField("clazz");
                    classDataTypeField.setAccessible(true);
                }
                sData.classTypeDesc = ((Type)classDataTypeField.get(data)).getDescriptor();
                if(classDataInterfacesField == null) {
                    classDataInterfacesField = ModFileScanData.ClassData.class.getDeclaredField("interfaces");
                    classDataInterfacesField.setAccessible(true);
                }
                sData.interfacesTypeDesc = ((Set<Type>)classDataInterfacesField.get(data)).stream().map(Type::getDescriptor).collect(Collectors.toCollection(ArrayList::new));
                serializedClassDataList.add(sData);
            }
            stream.writeObject(serializedClassDataList);
            ArrayList<SerializedAnnotationData> serializedAnnotationDataList = new ArrayList<>();
            for(ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
                SerializedAnnotationData sData = new SerializedAnnotationData();
                sData.annotationTypeDesc = data.getAnnotationType().getDescriptor();
                sData.targetTypeDesc = data.getTargetType();
                sData.classTypeDesc = data.getClassType().getDescriptor();
                sData.memberName = data.getMemberName();
                sData.annotationData = data.getAnnotationData();
                serializedAnnotationDataList.add(sData);
;            }
            stream.writeObject(serializedAnnotationDataList);
        } catch(IOException | ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }
}
