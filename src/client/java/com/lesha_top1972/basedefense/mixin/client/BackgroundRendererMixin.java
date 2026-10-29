package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.director.DirectorVisuals;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** События «Режиссёра»: вечный туман (близкая дальность, свет разгоняет) и кровавый/серый цвет тумана. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void basedefense$eventColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                                               float skyDarkness, CallbackInfo ci) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }
        Vec3d c = DirectorVisuals.tint(new Vec3d(red, green, blue), tickDelta, false);
        red = (float) c.x;
        green = (float) c.y;
        blue = (float) c.z;
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void basedefense$eventFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                             boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE || DirectorVisuals.fog(tickDelta) <= 0f) {
            return;
        }
        RenderSystem.setShaderFogStart(DirectorVisuals.fogStart(tickDelta, RenderSystem.getShaderFogStart()));
        RenderSystem.setShaderFogEnd(DirectorVisuals.fogEnd(tickDelta, RenderSystem.getShaderFogEnd()));
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
