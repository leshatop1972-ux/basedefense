package com.lesha_top1972.basedefense.artifact;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Рецепты стола амулетов: два амулета (порядок не важен) + пустой кулон → случайный результат.
 * Номер рецепта = место в списке (по нему меню знает, что сейчас в работе). Новые рецепты — в конец списка.
 */
public final class AmuletRecipes {

    /** Время крафта: 8 минут реального времени (в тиках мира). */
    public static final int CRAFT_TICKS = 8 * 60 * 20;
    /** В творческом режиме — 10 секунд, чтобы было удобно проверять. */
    public static final int CREATIVE_TICKS = 10 * 20;
    public static final int MAX_RECIPES = 30;

    /** Один возможный результат и его шанс в процентах (сумма шансов рецепта = 100). */
    public record Outcome(Item item, int chance) {
    }

    public record Recipe(Item a, Item b, List<Outcome> outcomes) {

        public boolean matches(ItemStack x, ItemStack y) {
            return (x.isOf(a) && y.isOf(b)) || (x.isOf(b) && y.isOf(a));
        }

        public Item roll(Random random) {
            int n = random.nextInt(100);
            for (Outcome o : outcomes) {
                n -= o.chance();
                if (n < 0) {
                    return o.item();
                }
            }
            return outcomes.get(0).item();
        }
    }

    private static List<Recipe> recipes;

    private AmuletRecipes() {
    }

    /** Список создаётся при первом вызове — к этому времени предметы мода уже зарегистрированы. */
    public static List<Recipe> all() {
        if (recipes == null) {
            recipes = List.of(
                    // 0: Жизнь I + Жизнь I
                    new Recipe(ModItems.LIFE_AMULET_1, ModItems.LIFE_AMULET_1, List.of(
                            new Outcome(ModItems.LIFE_AMULET_2, 90),
                            new Outcome(ModItems.VAMPIRE_AMULET_1, 5),
                            new Outcome(ModItems.LIFE_AMULET_3, 5)))
            );
        }
        return recipes;
    }

    /** Номер рецепта для этих двух амулетов или -1. */
    public static int find(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) {
            return -1;
        }
        List<Recipe> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).matches(left, right)) {
                return i;
            }
        }
        return -1;
    }

    public static Recipe get(int index) {
        List<Recipe> list = all();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
