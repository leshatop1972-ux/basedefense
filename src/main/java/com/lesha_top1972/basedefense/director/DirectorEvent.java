package com.lesha_top1972.basedefense.director;

/**
 * События «Режиссёра мира». Название и описание — в lang: {@code director.basedefense.<id>} и {@code .desc}.
 * Номера (порядок) не менять — по ним событие хранится в сохранении и уходит клиенту; новые — только в конец.
 */
public enum DirectorEvent {
    /** Дни 1–3: мирное время (в рулетке не участвует). */
    PEACE("peace", Kind.CALM, When.DAY),
    NONE("none", Kind.CALM, When.DAY),
    METEOR("meteor", Kind.CALM, When.DAY),
    CARAVAN("caravan", Kind.CALM, When.DAY),
    /** Наводнение — убрано из игры (не выпадает); константа оставлена, чтобы не сбить номера. */
    FLOOD("flood", Kind.WORLD, When.ALL_DAY),
    SNOWFALL("snowfall", Kind.WORLD, When.DAY),
    HEATWAVE("heatwave", Kind.WORLD, When.DAY),
    STORM("storm", Kind.WORLD, When.ALL_DAY),
    WAVE("wave", Kind.WAVE, When.NIGHT),
    ETERNAL_FOG("eternal_fog", Kind.WAVE, When.ALL_DAY),
    /** Особые волны. */
    BLOOD_MOON("blood_moon", Kind.WAVE, When.NIGHT),
    IRON_MARCH("iron_march", Kind.WAVE, When.NIGHT),
    UNDERMINING("undermining", Kind.WAVE, When.NIGHT),
    FROST_NIGHT("frost_night", Kind.WAVE, When.NIGHT),
    BLOOD_NIGHT("blood_night", Kind.WAVE, When.ALL_DAY),
    STRONG_WAVE("strong_wave", Kind.WAVE, When.NIGHT),
    BOSS_WAVE("boss_wave", Kind.BOSS, When.NIGHT);

    /** Тип: от него цвет в интерфейсе. */
    public enum Kind { CALM, WORLD, WAVE, BOSS }

    /** Когда происходит. */
    public enum When { DAY, NIGHT, ALL_DAY }

    public final String id;
    public final Kind kind;
    public final When when;

    DirectorEvent(String id, Kind kind, When when) {
        this.id = id;
        this.kind = kind;
        this.when = when;
    }

    public String key() {
        return "director.basedefense." + id;
    }

    /** Этой ночью идёт волна на Базу (и спать нельзя). */
    public boolean hasNightWave() {
        return kind == Kind.BOSS || (kind == Kind.WAVE && this != ETERNAL_FOG);
    }

    public boolean isSpecialWave() {
        return this == BLOOD_MOON || this == IRON_MARCH || this == UNDERMINING || this == FROST_NIGHT;
    }

    public static DirectorEvent byOrdinal(int n) {
        DirectorEvent[] all = values();
        return n >= 0 && n < all.length ? all[n] : NONE;
    }

    public static DirectorEvent byId(String id) {
        for (DirectorEvent e : values()) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }
}
