package com.lesha_top1972.basedefense.clothing;

/**
 * Насколько одежда тёплая — задаёт комфортную температуру персонажа (°C).
 * Действует самая тёплая надетая вещь: без одежды 17…24, обычная одежда 0…22, тёплая −20…9.
 */
public enum Warmth {
    NONE(17f, 24f),
    NORMAL(0f, 22f),
    WARM(-20f, 9f);

    public final float comfortMin;
    public final float comfortMax;

    Warmth(float comfortMin, float comfortMax) {
        this.comfortMin = comfortMin;
        this.comfortMax = comfortMax;
    }
}
