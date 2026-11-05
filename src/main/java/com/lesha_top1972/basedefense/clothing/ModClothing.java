package com.lesha_top1972.basedefense.clothing;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Вся одежда мода (11 обычных и 8 тёплых вещей) и рюкзаки. Числа: cold — греет (нижняя граница комфорта ниже на столько °C), heat — в ней жарче
 * (верхняя граница ниже). Без одежды комфорт 15…27.
 * <p>Полный обычный комплект (картуз, туника, штаны, башмаки) ≈ 1…25 °C; летний (шляпа, жилет, шорты, сандалии)
 * ≈ 8…31 °C; полный тёплый (меховая шапка, тулуп, ватные штаны, валенки) ≈ −24…10 °C.
 * Крафт — на верстаке (рецепты в data/basedefense/recipes).
 */
public final class ModClothing {

    public static final List<ClothingItem> ALL = new ArrayList<>();

    // ---------- Обычная одежда (11) ----------
    public static final ClothingItem STRAW_HAT = reg("straw_hat", ClothingType.HEAD, Warmth.NORMAL, 1.0f, -1.5f);
    public static final ClothingItem CAP = reg("cap", ClothingType.HEAD, Warmth.NORMAL, 2.0f, 0.3f);
    public static final ClothingItem SHIRT = reg("shirt", ClothingType.CHEST, Warmth.NORMAL, 5.0f, 1.0f);
    public static final ClothingItem VEST = reg("vest", ClothingType.CHEST, Warmth.NORMAL, 3.5f, 0.5f);
    public static final ClothingItem TUNIC = reg("tunic", ClothingType.CHEST, Warmth.NORMAL, 6.0f, 1.3f);
    public static final ClothingItem TROUSERS = reg("trousers", ClothingType.LEGS, Warmth.NORMAL, 4.0f, 0.6f);
    public static final ClothingItem SHORTS = reg("shorts", ClothingType.LEGS, Warmth.NORMAL, 1.5f, -0.5f);
    public static final ClothingItem SKIRT = reg("skirt", ClothingType.LEGS, Warmth.NORMAL, 3.0f, 0.2f);
    public static final ClothingItem SHOES = reg("shoes", ClothingType.FEET, Warmth.NORMAL, 2.0f, 0.2f);
    public static final ClothingItem SANDALS = reg("sandals", ClothingType.FEET, Warmth.NORMAL, 0.8f, -0.3f);
    /** Дождевик: под дождём и снегом не холоднее (осадки −3° не действуют). */
    public static final ClothingItem RAINCOAT = reg("raincoat", ClothingType.CHEST, Warmth.NORMAL, 3.0f, 0.8f);

    // ---------- Тёплая одежда (8) ----------
    public static final ClothingItem FUR_HAT = reg("fur_hat", ClothingType.HEAD, Warmth.WARM, 6.0f, 2.5f);
    public static final ClothingItem WOOL_SWEATER = reg("wool_sweater", ClothingType.CHEST, Warmth.WARM, 11.0f, 5.0f);
    public static final ClothingItem PADDED_JACKET = reg("padded_jacket", ClothingType.CHEST, Warmth.WARM, 13.0f, 6.0f);
    public static final ClothingItem SHEEPSKIN_COAT = reg("sheepskin_coat", ClothingType.CHEST, Warmth.WARM, 16.0f, 7.5f);
    public static final ClothingItem WOOL_PANTS = reg("wool_pants", ClothingType.LEGS, Warmth.WARM, 9.0f, 4.0f);
    public static final ClothingItem PADDED_TROUSERS = reg("padded_trousers", ClothingType.LEGS, Warmth.WARM, 11.0f, 5.0f);
    public static final ClothingItem FELT_BOOTS = reg("felt_boots", ClothingType.FEET, Warmth.WARM, 6.0f, 2.5f);
    /** Вязаная шапка — полегче ушанки: для осени и весны. */
    public static final ClothingItem KNIT_HAT = reg("knit_hat", ClothingType.HEAD, Warmth.WARM, 4.0f, 1.5f);

    // ---------- Рюкзаки ----------
    /** Обычный: 2 ряда (18 ячеек). */
    public static final BackpackItem BACKPACK = Registry.register(Registries.ITEM,
            new Identifier(Basedefense.MOD_ID, "backpack"), new BackpackItem(2, 0, new Item.Settings().maxCount(1)));
    /** Большой: 3 ряда (27 ячеек), скорость −10%. */
    public static final BackpackItem BIG_BACKPACK = Registry.register(Registries.ITEM,
            new Identifier(Basedefense.MOD_ID, "big_backpack"), new BackpackItem(3, -0.1, new Item.Settings().maxCount(1)));

    /** Разметчик шахты (поселенцы с работой «Добыча» копают размеченное). */
    public static final com.lesha_top1972.basedefense.work.MineMarkerItem MINE_MARKER = Registry.register(Registries.ITEM,
            new Identifier(Basedefense.MOD_ID, "mine_marker"),
            new com.lesha_top1972.basedefense.work.MineMarkerItem(new Item.Settings().maxCount(1)));

    private ModClothing() {
    }

    private static ClothingItem reg(String name, ClothingType type, Warmth warmth, float cold, float heat) {
        ClothingItem item = Registry.register(Registries.ITEM, new Identifier(Basedefense.MOD_ID, name),
                new ClothingItem(type, warmth, cold, heat, new Item.Settings().maxCount(1)));
        ALL.add(item);
        return item;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(MINE_MARKER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            for (ClothingItem item : ALL) {
                entries.add(item);
            }
            entries.add(BACKPACK);
            entries.add(BIG_BACKPACK);
        });
    }
}
