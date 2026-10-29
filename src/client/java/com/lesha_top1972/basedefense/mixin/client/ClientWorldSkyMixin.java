package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.director.DirectorVisuals;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** События «Режиссёра»: небо и облака краснеют (кровавая луна) или сереют (вечный туман). */
@Mixin(ClientWorld.class)
public abstract class ClientWorldSkyMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void basedefense$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        cir.setReturnValue(DirectorVisuals.tint(cir.getReturnValue(), tickDelta, true));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void basedefense$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        cir.setReturnValue(DirectorVisuals.tint(cir.getReturnValue(), tickDelta, true));
    }
}
