package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.obj.MaterialLibrary;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.client.model.obj.OBJModel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(OBJLoader.class)
public class OBJLoaderMixin {
    @Final
    @Mutable
    @Shadow(remap = false) private Map<ResourceLocation, MaterialLibrary> materialCache;

    @Final
    @Mutable
    @Shadow(remap = false) private Map<OBJModel.ModelSettings, OBJModel> modelCache;

    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraftforge/client/model/obj/OBJLoader;materialCache:Ljava/util/Map;", remap = false))
    private void useConcMap1(OBJLoader instance, Map<ResourceLocation, MaterialLibrary> value) {
        this.materialCache = new ConcurrentHashMap<>();
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraftforge/client/model/obj/OBJLoader;modelCache:Ljava/util/Map;", remap = false))
    private void useConcMap2(OBJLoader instance, Map<ResourceLocation, MaterialLibrary> value) {
        this.modelCache = new ConcurrentHashMap<>();
    }
}
