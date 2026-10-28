package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.effect.BossEffectsHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Под полоской здоровья босса — ряд его эффектов (BossEffectsHud). Чтобы ряд не налез на следующую
 * полоску, шаг между полосками (ванильные «10 + высота шрифта») увеличивается на высоту ряда.
 */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {

    @Unique
    private static int basedefense$extra;

    @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V",
            at = @At("TAIL"))
    private void basedefense$drawEffects(DrawContext context, int x, int y, BossBar bossBar, CallbackInfo ci) {
        basedefense$extra = BossEffectsHud.draw(context, x, y, bossBar);
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 10), require = 0)
    private int basedefense$spacing(int value) {
        int extra = basedefense$extra;
        basedefense$extra = 0;
        return value + extra;
    }
}
