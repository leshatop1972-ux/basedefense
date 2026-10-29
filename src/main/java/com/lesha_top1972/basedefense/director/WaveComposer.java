package com.lesha_top1972.basedefense.director;

import com.lesha_top1972.basedefense.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Состав волны (акт I) по правилам пользователя; раскладка — на WaveManager.PARTS частей. Количество — на одного игрока, игроков N — всё ×N.
 * Добавки мобов от шкалы НЕТ (пользователь убрал).
 * Усиленная волна — мобов на 35% больше.
 */
public final class WaveComposer {

    /** Строка состава: тип, от, до. */
    private record Row(EntityType<?> type, int min, int max) {
    }

    private WaveComposer() {
    }

    private static List<Row> rows(DirectorEvent e) {
        return switch (e) {
            case BLOOD_MOON, BLOOD_NIGHT -> List.of(
                    new Row(ModEntities.BLOOD_ENTITY, 10, 12),
                    new Row(ModEntities.BLOODLING, 15, 25),
                    new Row(ModEntities.BLOODLETTER, 4, 8),
                    new Row(ModEntities.BLOOD_EGG, 3, 4),
                    new Row(ModEntities.BEETLE_MITE, 10, 20));
            case IRON_MARCH -> List.of(
                    new Row(ModEntities.IRON_ZOMBIE, 10, 14),
                    new Row(ModEntities.IRON_KNIGHT, 3, 5));
            case UNDERMINING -> List.of(
                    new Row(ModEntities.SAPPER, 4, 6),
                    new Row(ModEntities.SIEGE_BEETLE, 2, 3),
                    new Row(ModEntities.BEETLE_MITE, 6, 10),
                    new Row(ModEntities.IRON_ZOMBIE, 3, 4));
            case FROST_NIGHT -> List.of(
                    new Row(ModEntities.FROST_ARCHER, 5, 7),
                    new Row(ModEntities.IRON_ZOMBIE, 5, 7),
                    new Row(ModEntities.IRON_KNIGHT, 1, 2));
            default -> List.of(                       // обычная, усиленная, боссовая
                    new Row(ModEntities.IRON_ZOMBIE, 3, 5),
                    new Row(ModEntities.IRON_KNIGHT, 1, 2),
                    new Row(ModEntities.SAPPER, 1, 2),
                    new Row(ModEntities.SIEGE_BEETLE, 1, 2),
                    new Row(ModEntities.BLOODLETTER, 1, 1),
                    new Row(ModEntities.FROST_ARCHER, 2, 2),
                    new Row(ModEntities.BLOODLING, 5, 8));
        };
    }

    /** Части волны (по очереди, каждая со своей стороны). */
    public static List<List<EntityType<?>>> compose(DirectorEvent e, int points, int players, Random random) {
        List<Row> rows = rows(e);
        List<EntityType<?>> all = new ArrayList<>();
        int n = Math.max(1, players);
        for (Row r : rows) {
            int count = (r.min() + random.nextInt(r.max() - r.min() + 1)) * n;
            for (int i = 0; i < count; i++) {
                all.add(r.type());
            }
        }
        if (e == DirectorEvent.STRONG_WAVE) {
            int more = Math.round(all.size() * 0.35f);
            int base = all.size();
            for (int i = 0; i < more; i++) {
                all.add(all.get(random.nextInt(base)));
            }
        }
        // перемешать и разложить по частям поровну (тяжёлые мобы не скапливаются в одной части)
        for (int i = all.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            EntityType<?> t = all.get(i);
            all.set(i, all.get(j));
            all.set(j, t);
        }
        List<List<EntityType<?>>> parts = new ArrayList<>();
        for (int p = 0; p < WaveManager.PARTS; p++) {
            parts.add(new ArrayList<>());
        }
        for (int i = 0; i < all.size(); i++) {
            parts.get(i % WaveManager.PARTS).add(all.get(i));
        }
        return parts;
    }
}
