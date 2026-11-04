package com.lesha_top1972.basedefense.temperature;

/** Самочувствие по температуре: насколько воздух вне комфортной температуры персонажа. */
public enum TempStatus {
    /** На 10° и больше холоднее комфорта. */
    FREEZING,
    /** Холоднее комфорта. */
    COLD,
    COMFORT,
    /** Жарче комфорта. */
    HOT,
    /** На 10° и больше жарче комфорта. */
    BURNING;

    /** На сколько градусов вне комфорта считается «сильно». */
    public static final float SEVERE = 10f;

    public static TempStatus of(float temp, float min, float max) {
        if (temp < min) {
            return temp <= min - SEVERE ? FREEZING : COLD;
        }
        if (temp > max) {
            return temp >= max + SEVERE ? BURNING : HOT;
        }
        return COMFORT;
    }

    public static TempStatus byIndex(int i) {
        return i >= 0 && i < values().length ? values()[i] : COMFORT;
    }
}
