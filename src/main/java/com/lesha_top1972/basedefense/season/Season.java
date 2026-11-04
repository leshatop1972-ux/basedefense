package com.lesha_top1972.basedefense.season;

/**
 * Времена года (по порядку: игра начинается летом). Каждое длится {@link Seasons#DAYS} дней.
 * <ul>
 *   <li>tMin…tMax — температура воздуха (°C) в обычном лесу; в других биомах сдвигается (см. Temperature);</li>
 *   <li>biomeShift — на сколько холоднее биомы для снега и льда (ванильная «температура биома»: снег идёт ниже 0.15):
 *       зимой −0.8 — снег почти везде, где бывают осадки (кроме джунглей), вода замерзает; осенью −0.2 — снег только
 *       в тайге и горах;</li>
 *   <li>rainChance — шанс начать дождь (снег) каждый игровой час, если сейчас ясно (0 — погода как в ванилле).</li>
 * </ul>
 */
public enum Season {
    SUMMER("summer", 22f, 35f, 0f, 0f),
    AUTUMN("autumn", 0f, 18f, -0.2f, 0.22f),
    WINTER("winter", -30f, 0f, -0.8f, 0.15f),
    SPRING("spring", 3f, 22f, -0.1f, 0.12f);

    public final String id;
    public final float tMin;
    public final float tMax;
    public final float biomeShift;
    public final float rainChance;

    Season(String id, float tMin, float tMax, float biomeShift, float rainChance) {
        this.id = id;
        this.tMin = tMin;
        this.tMax = tMax;
        this.biomeShift = biomeShift;
        this.rainChance = rainChance;
    }

    public Season next() {
        return values()[(ordinal() + 1) % 4];
    }

    public Season prev() {
        return values()[(ordinal() + 3) % 4];
    }

    public static Season byId(String id) {
        for (Season s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
}
