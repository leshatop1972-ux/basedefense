package com.lesha_top1972.basedefense.weapon;

/**
 * Тайминги тесака Громового тигра (тики). СОЗДАНО tools/cleaver_gen.py из клипов анимации — правь скрипт.
 */
public final class CleaverTimings {

    /** Длина ударов серии: обычные и взрывные. */
    public static final int[] N_LEN = {20, 20, 25};
    public static final int[] B_LEN = {21, 20, 30};
    /** Когда бьёт (тики от начала удара). */
    public static final int[] N_HIT = {6, 5, 8};
    public static final int[] B_HIT = {6, 5, 8};
    /** «Тигриный выпад»: зарядка, длина, взрыв на острие. */
    public static final int S1_CHARGE = 20;
    public static final int S1_LEN = 28;
    public static final int S1_BLAST = 7;
    /** «Тесак в узел»: зарядка, длина, три разреза, удар в землю. */
    public static final int S2_CHARGE = 24;
    public static final int S2_LEN = 42;
    public static final int[] S2_CUTS = {3, 7, 12};
    public static final int S2_SMASH = 22;
    public static final int S2_HOP = 17;
    /** Перезарядка: длина и момент, когда патроны в барабане. */
    public static final int RELOAD_LEN = 38;
    public static final int RELOAD_LOAD = 19;

    private CleaverTimings() {
    }
}
