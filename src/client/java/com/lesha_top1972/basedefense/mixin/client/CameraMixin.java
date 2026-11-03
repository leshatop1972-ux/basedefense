package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.fx.CameraShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Лёгкая тряска камеры (удары «Громового тигра»): после расчёта камеры чуть сдвигаем yaw/pitch. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void basedefense$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                   float tickDelta, CallbackInfo ci) {
        float dy = CameraShake.yaw(tickDelta);
        float dp = CameraShake.pitch(tickDelta);
        if (dy != 0f || dp != 0f) {
            this.setRotation(this.getYaw() + dy, this.getPitch() + dp);
        }
    }
}
