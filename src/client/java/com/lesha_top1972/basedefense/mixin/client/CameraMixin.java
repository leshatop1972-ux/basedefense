package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.fx.LancerFx;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тряска камеры от ударов улана (LancerFx). Слабеет с настройкой «Эффекты искажения». */
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
        float[] s = LancerFx.shakeOffset(tickDelta);
        if (s != null) {
            this.setRotation(this.getYaw() + s[0], this.getPitch() + s[1]);
        }
    }
}
