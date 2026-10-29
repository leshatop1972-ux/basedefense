package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.director.VisualHooks;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** «Кровавая вечная ночь»: на клиенте положение солнца — полночь (небо, звёзды, освещение). Сервер не трогаем. */
@Mixin(DimensionType.class)
public abstract class DimensionTypeMixin {

    @ModifyVariable(method = "getSkyAngle", at = @At("HEAD"), argsOnly = true)
    private long basedefense$bloodNight(long time) {
        if (VisualHooks.forceNight && VisualHooks.onClient()) {
            return time - Math.floorMod(time, 24000L) + 18000L;
        }
        return time;
    }
}
