package com.lesha_top1972.basedefense.director;

import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.Map;

/**
 * Цифры «Режиссёра мира»: шансы событий по актам (в десятых долях процента, сумма — 1000),
 * очки шкалы, усиления мобов по актам, боссы актов.
 */
public final class DirectorTables {

    /** С какого дня начинаются события (дни 1–3 — мирные). */
    public static final int START_DAY = 4;
    public static final int MAX_ACT = 4;

    // ---------- Шкала ----------
    public static final int SCALE_MAX = 1000;
    public static final int POINTS_DAY = 100;        // за каждый пережитый день
    public static final int POINTS_XP = 2;           // за каждое получение опыта любым игроком
    public static final int POINTS_SETTLER = 50;     // за каждого нового жителя поселения

    /**
     * Шансы акта I (‰). 1% из суммы процентов пользователя (99%) отдан «ничего не произойдёт».
     * Наводнение убрано (решение пользователя) — его 7% тоже отданы «ничего».
     */
    private static final Map<DirectorEvent, Integer> ACT1 = new EnumMap<>(DirectorEvent.class);

    static {
        ACT1.put(DirectorEvent.NONE, 230);
        ACT1.put(DirectorEvent.METEOR, 50);
        ACT1.put(DirectorEvent.CARAVAN, 50);
        ACT1.put(DirectorEvent.SNOWFALL, 70);
        ACT1.put(DirectorEvent.HEATWAVE, 70);
        ACT1.put(DirectorEvent.STORM, 70);
        ACT1.put(DirectorEvent.WAVE, 200);
        ACT1.put(DirectorEvent.ETERNAL_FOG, 60);
        ACT1.put(DirectorEvent.BLOOD_MOON, 25);      // особые волны — 10% на четыре вида
        ACT1.put(DirectorEvent.IRON_MARCH, 25);
        ACT1.put(DirectorEvent.UNDERMINING, 25);
        ACT1.put(DirectorEvent.FROST_NIGHT, 25);
        ACT1.put(DirectorEvent.BLOOD_NIGHT, 50);
        ACT1.put(DirectorEvent.STRONG_WAVE, 50);
    }

    private DirectorTables() {
    }

    /** Таблица шансов акта. Акты II–IV пока как I (пул событий пользователь распишет позже). */
    public static Map<DirectorEvent, Integer> table(int act) {
        return ACT1;
    }

    public static DirectorEvent roll(int act, Random random) {
        Map<DirectorEvent, Integer> t = table(act);
        int total = 0;
        for (int w : t.values()) {
            total += w;
        }
        int r = random.nextInt(Math.max(1, total));
        for (Map.Entry<DirectorEvent, Integer> e : t.entrySet()) {
            r -= e.getValue();
            if (r < 0) {
                return e.getKey();
            }
        }
        return DirectorEvent.NONE;
    }

    /** Добавка к здоровью мобов мода на волнах (0.10 = +10%). */
    public static float hpBonus(int act) {
        return switch (act) {
            case 2 -> 0.10f;
            case 3 -> 0.25f;
            case 4 -> 0.35f;
            default -> 0f;
        };
    }

    /** Добавка к урону мобов мода на волнах. */
    public static float damageBonus(int act) {
        return switch (act) {
            case 2 -> 0.05f;
            case 3 -> 0.10f;
            case 4 -> 0.15f;
            default -> 0f;
        };
    }

    /** Босс в конце акта (id сущности мода). */
    public static String boss(int act) {
        return switch (act) {
            case 2 -> "curb";
            case 3 -> "bloodthirster";
            case 4 -> "nabat";
            default -> "tattered_lancer";
        };
    }

    public static String roman(int act) {
        return switch (act) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(act);
        };
    }
}
