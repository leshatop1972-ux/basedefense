package com.lesha_top1972.basedefense.settlement;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.HashMap;
import java.util.Map;

/**
 * Что можно сдать в Базу и сколько это стоит.
 * Чтобы поменять очки еды — правь таблицу FOOD ниже.
 */
public final class SettlementItems {

    /** Очки запаса еды за 1 предмет. */
    private static final Map<Item, Integer> FOOD = new HashMap<>();

    static {
        // Хлеб и выпечка
        FOOD.put(Items.BREAD, 10);
        FOOD.put(Items.COOKIE, 2);
        FOOD.put(Items.PUMPKIN_PIE, 12);
        FOOD.put(Items.CAKE, 25);
        FOOD.put(Items.WHEAT, 1);
        FOOD.put(Items.HAY_BLOCK, 9);

        // Жареное мясо («стейки» любых мобов)
        FOOD.put(Items.COOKED_BEEF, 15);
        FOOD.put(Items.COOKED_PORKCHOP, 15);
        FOOD.put(Items.COOKED_MUTTON, 15);
        FOOD.put(Items.COOKED_CHICKEN, 15);
        FOOD.put(Items.COOKED_RABBIT, 15);

        // Сырое мясо
        FOOD.put(Items.BEEF, 5);
        FOOD.put(Items.PORKCHOP, 5);
        FOOD.put(Items.MUTTON, 5);
        FOOD.put(Items.CHICKEN, 4);
        FOOD.put(Items.RABBIT, 4);

        // Рыба
        FOOD.put(Items.COOKED_COD, 8);
        FOOD.put(Items.COOKED_SALMON, 10);
        FOOD.put(Items.COD, 3);
        FOOD.put(Items.SALMON, 3);

        // Овощи и фрукты
        FOOD.put(Items.BAKED_POTATO, 4);
        FOOD.put(Items.POTATO, 2);
        FOOD.put(Items.CARROT, 3);
        FOOD.put(Items.GOLDEN_CARROT, 12);
        FOOD.put(Items.BEETROOT, 1);
        FOOD.put(Items.APPLE, 4);
        FOOD.put(Items.GOLDEN_APPLE, 30);
        FOOD.put(Items.ENCHANTED_GOLDEN_APPLE, 60);
        FOOD.put(Items.MELON_SLICE, 1);
        FOOD.put(Items.MELON, 9);
        FOOD.put(Items.SWEET_BERRIES, 1);
        FOOD.put(Items.GLOW_BERRIES, 1);
        FOOD.put(Items.DRIED_KELP, 1);
        FOOD.put(Items.DRIED_KELP_BLOCK, 9);
        FOOD.put(Items.HONEY_BOTTLE, 6);

        // Супы и рагу
        FOOD.put(Items.MUSHROOM_STEW, 10);
        FOOD.put(Items.BEETROOT_SOUP, 10);
        FOOD.put(Items.RABBIT_STEW, 20);
    }

    private SettlementItems() {
    }

    /** Сколько очков еды даёт 1 предмет (0 — не еда для поселения). */
    public static int foodValue(ItemStack stack) {
        return FOOD.getOrDefault(stack.getItem(), 0);
    }

    /** Сколько железных самородков засчитывается за 1 предмет (слиток = 9). */
    public static int nuggetValue(ItemStack stack) {
        if (stack.isOf(Items.IRON_NUGGET)) {
            return 1;
        }
        if (stack.isOf(Items.IRON_INGOT)) {
            return 9;
        }
        return 0;
    }

    public static boolean isPlanks(ItemStack stack) {
        return stack.isIn(ItemTags.PLANKS);
    }

    public static boolean isCobblestone(ItemStack stack) {
        return stack.isOf(Items.COBBLESTONE);
    }

    /** Принимает ли База этот предмет. Работает и на клиенте. */
    public static boolean isAccepted(ItemStack stack) {
        return foodValue(stack) > 0 || nuggetValue(stack) > 0 || isPlanks(stack) || isCobblestone(stack);
    }
}
