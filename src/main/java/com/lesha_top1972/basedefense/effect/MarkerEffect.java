package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * Эффект‑«метка»: сам ничего не делает, его действие задаёт код босса (уровень — счётчик и т.п.).
 * Нужен, чтобы состояние было видно значком (у игрока — справа сверху, у босса — под полоской).
 * Набат: «Шум», «Купол», «Реквием», «Трещина», «Метка набата», «Узник».
 */
public class MarkerEffect extends StatusEffect {

    public MarkerEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }
}
