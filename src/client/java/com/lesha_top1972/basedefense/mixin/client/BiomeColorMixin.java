package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.season.SeasonColors;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Времена года: цвет листвы и травы биома (только клиент; см. SeasonColors). */
@Mixin(Biome.class)
public abstract class BiomeColorMixin {

    @Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
    private void basedefense$seasonFoliage(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SeasonColors.foliage(cir.getReturnValueI()));
    }

    @Inject(method = "getGrassColorAt", at = @At("RETURN"), cancellable = true)
    private void basedefense$seasonGrass(double x, double z, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SeasonColors.grass(cir.getReturnValueI()));
    }
}
