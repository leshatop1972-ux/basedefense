package com.lesha_top1972.basedefense.farming;

import com.lesha_top1972.basedefense.temperature.Temperature;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;

/**
 * Рост культур (пшеница, морковь, картофель, свёкла — всё, что CropBlock):
 * <ul>
 *   <li>растут вдвое дольше (проходит половина «случайных тиков» роста), а урожай со спелых — вдвое больше;</li>
 *   <li>нужен свет ≥ 9 «настоящий»: днём солнце, ночью — только лампы/факелы рядом (в ванилле ночью тоже росли).
 *       В помещении с освещением растут;</li>
 *   <li>температура (как у игрока, {@link Temperature}: сезон, биом, помещение, огонь рядом):
 *       10…30 °C — нормально; 4…10 и 30…38 — вдвое медленнее; ниже 4 и выше 38 — не растут;</li>
 *   <li>заморозки (−2 °C и ниже): с шансом 12% за тик откатываются на стадию назад, всходы (стадия 0)
 *       в сильный мороз (−8 и ниже) погибают. Жара выше 42 °C — так же сохнут.</li>
 * </ul>
 * Тёплица: стеклянная крыша, стены, свет и костёр/печь внутри — растут и зимой.
 */
public final class Crops {

    public static final int MIN_LIGHT = 9;
    public static final float GOOD_MIN = 10f;
    public static final float GOOD_MAX = 30f;
    public static final float SLOW_MIN = 4f;
    public static final float SLOW_MAX = 38f;
    public static final float FROST = -2f;
    public static final float HARD_FROST = -8f;
    public static final float SCORCH = 42f;
    public static final float WITHER_CHANCE = 0.12f;
    /** Доля тиков роста, которые проходят: нормально (вдвое дольше ванилы) и медленно. */
    public static final float GROW_NORMAL = 0.5f;
    public static final float GROW_SLOW = 0.25f;
    /** Во сколько раз больше урожай со спелой культуры. */
    public static final int HARVEST_MULTIPLIER = 2;

    private Crops() {
    }

    /** @return true — ванильный рост в этот тик пропустить. */
    public static boolean blockGrowth(CropBlock crop, BlockState state, ServerWorld w, BlockPos pos, Random r) {
        float t = Temperature.ambient(w, pos);
        if (t <= FROST || t >= SCORCH) {
            wither(crop, state, w, pos, r, t);
            return true;
        }
        if (light(w, pos) < MIN_LIGHT) {
            return true;
        }
        float pass = t >= GOOD_MIN && t <= GOOD_MAX ? GROW_NORMAL
                : t >= SLOW_MIN && t <= SLOW_MAX ? GROW_SLOW : 0f;
        return r.nextFloat() >= pass;
    }

    /** «Настоящий» свет: от блоков или от неба с учётом времени суток (ночью небо тёмное). */
    public static int light(ServerWorld w, BlockPos pos) {
        int block = w.getLightLevel(LightType.BLOCK, pos);
        int sky = w.getLightLevel(LightType.SKY, pos) - w.getAmbientDarkness();
        return Math.max(block, sky);
    }

    private static void wither(CropBlock crop, BlockState state, ServerWorld w, BlockPos pos, Random r, float t) {
        if (r.nextFloat() >= WITHER_CHANCE) {
            return;
        }
        int age = ((com.lesha_top1972.basedefense.mixin.CropBlockAccessor) crop).basedefense$getAge(state);
        if (age > 0) {
            w.setBlockState(pos, crop.withAge(age - 1), Block.NOTIFY_LISTENERS);
        } else if (t <= HARD_FROST || t >= SCORCH) {
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        } else {
            return;
        }
        w.spawnParticles(t < 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.01);
    }
}
