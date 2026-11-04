package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.director.VisualHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * «Снегопад»: на клиенте дождь везде рисуется снегом (снег на землю кладёт сервер — WorldEvents).
 * Времена года: «температура биома» в точке ниже на сдвиг сезона (зимой −0.8) — ванильные снег, лёд и вид осадков
 * сами следуют за сезоном (на сервере — серверный сезон, на клиенте — клиентский).
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {

    @Inject(method = "getTemperature(Lnet/minecraft/util/math/BlockPos;)F", at = @At("RETURN"), cancellable = true)
    private void basedefense$season(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float shift = com.lesha_top1972.basedefense.season.Seasons.biomeShift();
        if (shift != 0f) {
            cir.setReturnValue(cir.getReturnValueF() + shift);
        }
    }

    @Inject(method = "getPrecipitation", at = @At("RETURN"), cancellable = true)
    private void basedefense$snowfall(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (VisualHooks.snow && cir.getReturnValue() == Biome.Precipitation.RAIN && VisualHooks.onClient()) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}
