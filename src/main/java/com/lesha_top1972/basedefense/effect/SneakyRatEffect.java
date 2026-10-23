package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * «Подлая крыса» (10 с): наносишь на 20% меньше урона (LivingEntityCombatMixin) и теряешь
 * по 0.5 HP каждые 2 секунды. В начале экран на секунду заливает мутно‑зелёным (клиент, RatOverlay).
 * Вешает «Оборванный улан» с шансом 8% на того, кто его бьёт (не на дуэлянта).
 */
public class SneakyRatEffect extends StatusEffect {

    public SneakyRatEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8A9A2A);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!entity.getWorld().isClient) {
            entity.damage(entity.getDamageSources().magic(), 0.5f);
        }
    }
}
