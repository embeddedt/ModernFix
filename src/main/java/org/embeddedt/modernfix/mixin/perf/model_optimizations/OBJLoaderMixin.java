package org.embeddedt.modernfix.mixin.perf.model_optimizations;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjMaterialLibrary;
import net.minecraftforge.client.model.obj.ObjModel;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ObjLoader.class)
@ClientOnlyMixin
public class OBJLoaderMixin {
    @Final
    @Mutable
    @Shadow(remap = false) private Map<ResourceLocation, ObjMaterialLibrary> materialCache;

    @Final
    @Mutable
    @Shadow(remap = false) private Map<ObjModel.ModelSettings, ObjModel> modelCache;

    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraftforge/client/model/obj/ObjLoader;materialCache:Ljava/util/Map;", remap = false))
    private void useConcMap1(ObjLoader instance, Map<ResourceLocation, ObjMaterialLibrary> value) {
        this.materialCache = new ConcurrentHashMap<>();
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraftforge/client/model/obj/ObjLoader;modelCache:Ljava/util/Map;", remap = false))
    private void useConcMap2(ObjLoader instance, Map<ResourceLocation, ObjMaterialLibrary> value) {
        this.modelCache = new ConcurrentHashMap<>();
    }
}
