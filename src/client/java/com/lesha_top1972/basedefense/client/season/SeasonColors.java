package com.lesha_top1972.basedefense.client.season;

import com.lesha_top1972.basedefense.season.Season;

/**
 * Сезонный цвет листвы и травы (через биом, BiomeColorMixin). Работает для листвы, которая красится биомом
 * (дуб, тёмный дуб, джунгли, акация, лианы); ель и берёза в ванилле своего цвета — не меняются.
 * <ul>
 *   <li>осень — листва к оранжево‑красной (за 3 дня), трава желтеет;</li>
 *   <li>зима — листва и трава белёсые, «в снегу»;</li>
 *   <li>весна — яркая молодая зелень.</li>
 * </ul>
 */
public final class SeasonColors {

    private static volatile int foliageTarget;
    private static volatile float foliageMix;
    private static volatile int grassTarget;
    private static volatile float grassMix;

    private SeasonColors() {
    }

    /** k — стадия осени 0…2. */
    static void set(Season s, int k) {
        switch (s) {
            case AUTUMN -> {
                float a = (k + 1) / 3f;
                foliageTarget = 0xD8641C;
                foliageMix = 0.85f * a;
                grassTarget = 0xB89A48;
                grassMix = 0.5f * a;
            }
            case WINTER -> {
                foliageTarget = 0xE4ECEE;
                foliageMix = 0.65f;
                grassTarget = 0xC9CEC2;
                grassMix = 0.55f;
            }
            case SPRING -> {
                foliageTarget = 0x74D83A;
                foliageMix = 0.35f;
                grassTarget = 0x7ED43A;
                grassMix = 0.35f;
            }
            default -> {
                foliageMix = 0f;
                grassMix = 0f;
            }
        }
    }

    public static int foliage(int color) {
        return foliageMix <= 0 ? color : mix(color, foliageTarget, foliageMix);
    }

    public static int grass(int color) {
        return grassMix <= 0 ? color : mix(color, grassTarget, grassMix);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }
}
