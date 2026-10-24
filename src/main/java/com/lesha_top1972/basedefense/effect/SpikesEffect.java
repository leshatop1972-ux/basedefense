package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * «Шипы» (Бордюр ставит себе защитной стойкой): тот, кто ударил носителя, получает 30% нанесённого урона
 * чистым уроном (ModEffects.reflectSpikes), у ударившего игрока на экране вспышка осколков (SpikesOverlay).
 */
public class SpikesEffect extends StatusEffect {

    public SpikesEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xBFE4FF);
    }
}
