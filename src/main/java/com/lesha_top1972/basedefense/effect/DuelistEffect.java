package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * «Дуэлянт» — метка «Оборванного улана» (30 с, только на одном игроке за раз).
 * Улан, ударив дуэлянта, лечится на 1; дуэлянт наносит улану на 20% больше урона
 * (логика в TatteredLancerEntity). По краям экрана — синие полоски (клиент, DuelistOverlay).
 */
public class DuelistEffect extends StatusEffect {

    public DuelistEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x3B7BFF);
    }
}
