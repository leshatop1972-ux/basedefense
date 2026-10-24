package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * «Картридж» — есть только у Бордюра: уровень = число картриджей (убил моба — +1, игрока — +3).
 * Картридж тратится на атаку «Я не сдамся». Сам по себе ничего не делает — это счётчик,
 * видимый под полоской здоровья босса. Источник правды — счётчик в CurbEntity (NBT "Cartridges").
 */
public class CartridgeEffect extends StatusEffect {

    public CartridgeEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xD8B45A);
    }
}
