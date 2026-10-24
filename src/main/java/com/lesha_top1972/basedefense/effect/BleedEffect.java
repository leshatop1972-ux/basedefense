package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * «Кровотёк» (4 уровня, 7 с): если носитель бьёт кого‑либо — сам получает чистый урон
 * (без брони, зачарований, сопротивления и неуязвимости): I — 0.5, II — 1, III — 2, IV — 4,
 * и эффект пропадает. Вешают кровожад (25% за попадание) и мелкие кровожады (15%);
 * повторное попадание поднимает уровень и обновляет 7 секунд (см. {@link ModEffects#addBleed}).
 */
public class BleedEffect extends StatusEffect {

    public BleedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8A0A14);
    }
}
