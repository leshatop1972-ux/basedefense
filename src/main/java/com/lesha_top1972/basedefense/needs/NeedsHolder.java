package com.lesha_top1972.basedefense.needs;

/** Игрок с потребностями (добавлено миксином в PlayerEntity): комфорт сохраняется в NBT, ощущаемая температура — нет. */
public interface NeedsHolder {

    float basedefense$comfort();

    void basedefense$setComfort(float value);

    /** Ощущаемая температура; NaN — ещё не считалась (сразу станет как воздух). */
    float basedefense$felt();

    void basedefense$setFelt(float value);
}
