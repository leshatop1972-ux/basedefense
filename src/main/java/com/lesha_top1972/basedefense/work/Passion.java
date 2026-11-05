package com.lesha_top1972.basedefense.work;

/** Увлечение навыком: чем сильнее, тем быстрее растёт (множитель опыта). Без увлечения — вдвое медленнее. */
public enum Passion {
    NONE(0.5f),
    MINOR(1.0f),
    MAJOR(1.5f);

    public final float xpMult;

    Passion(float xpMult) {
        this.xpMult = xpMult;
    }
}
