package com.lesha_top1972.basedefense.item;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Все предметы мода (кроме предметов-блоков, они в ModBlocks). */
public final class ModItems {

    /** Монета исследований — валюта для исследований в меню Базы. */
    public static final Item RESEARCH_COIN = register("research_coin", new Item(new Item.Settings()));

    /** Строительный планшет: выбор и установка построек (крафт: верстак + бумага + чернильный мешок). */
    public static final Item BUILDING_TABLET = register("building_tablet",
            new BuildingTabletItem(new Item.Settings().maxCount(1)));

    /** Яйцо призыва строителя. */
    public static final Item BUILDER_SPAWN_EGG = register("builder_spawn_egg",
            new SpawnEggItem(ModEntities.BUILDER, 0xF2B933, 0x3B5BA0, new Item.Settings()));

    /** Яйцо призыва жителя-фермера (для тестов и творческого режима). */
    public static final Item FARMER_SPAWN_EGG = register("farmer_spawn_egg",
            new SpawnEggItem(ModEntities.FARMER, 0x6E8F3E, 0xE8C25A, new Item.Settings()));

    /** Яйцо призыва жителя-скотовода. */
    public static final Item RANCHER_SPAWN_EGG = register("rancher_spawn_egg",
            new SpawnEggItem(ModEntities.RANCHER, 0xA83A2E, 0x8B5A2B, new Item.Settings()));

    /** Яйцо призыва повара. */
    public static final Item COOK_SPAWN_EGG = register("cook_spawn_egg",
            new SpawnEggItem(ModEntities.COOK, 0xF2F2F2, 0xC0392B, new Item.Settings()));

    /** Яйцо призыва шахтёра. */
    public static final Item MINER_SPAWN_EGG = register("miner_spawn_egg",
            new SpawnEggItem(ModEntities.MINER, 0x4A4A55, 0xF0B429, new Item.Settings()));

    /** Яйцо призыва кузнеца. */
    public static final Item BLACKSMITH_SPAWN_EGG = register("blacksmith_spawn_egg",
            new SpawnEggItem(ModEntities.BLACKSMITH, 0x2B2B2B, 0x8B5A2B, new Item.Settings()));

    /** Яйцо призыва лесоруба. */
    public static final Item LUMBERJACK_SPAWN_EGG = register("lumberjack_spawn_egg",
            new SpawnEggItem(ModEntities.LUMBERJACK, 0x2E5E2A, 0x8B2E2E, new Item.Settings()));

    /** Яйца призыва врагов. */
    public static final Item IRON_ZOMBIE_SPAWN_EGG = register("iron_zombie_spawn_egg",
            new SpawnEggItem(ModEntities.IRON_ZOMBIE, 0x6B6F78, 0x8A1E1E, new Item.Settings()));

    public static final Item IRON_KNIGHT_SPAWN_EGG = register("iron_knight_spawn_egg",
            new SpawnEggItem(ModEntities.IRON_KNIGHT, 0xA4AAB2, 0xB01E23, new Item.Settings()));

    public static final Item FROST_ARCHER_SPAWN_EGG = register("frost_archer_spawn_egg",
            new SpawnEggItem(ModEntities.FROST_ARCHER, 0xEAF4FB, 0x3A92D6, new Item.Settings()));

    public static final Item TATTERED_LANCER_SPAWN_EGG = register("tattered_lancer_spawn_egg",
            new SpawnEggItem(ModEntities.TATTERED_LANCER, 0x22565A, 0xD6AC4A, new Item.Settings()));

    public static final Item BLOODTHIRSTER_SPAWN_EGG = register("bloodthirster_spawn_egg",
            new SpawnEggItem(ModEntities.BLOODTHIRSTER, 0x6E0614, 0x1A0A0E, new Item.Settings()));

    public static final Item BLOODLING_SPAWN_EGG = register("bloodling_spawn_egg",
            new SpawnEggItem(ModEntities.BLOODLING, 0x96101C, 0xE04A42, new Item.Settings()));

    public static final Item BLOOD_EGG_SPAWN_EGG = register("blood_egg_spawn_egg",
            new SpawnEggItem(ModEntities.BLOOD_EGG, 0x4C0614, 0xFF4838, new Item.Settings()));

    public static final Item CURB_SPAWN_EGG = register("curb_spawn_egg",
            new SpawnEggItem(ModEntities.CURB, 0x222228, 0xE88CDE, new Item.Settings()));

    public static final Item CURB_MINION_SPAWN_EGG = register("curb_minion_spawn_egg",
            new SpawnEggItem(ModEntities.CURB_MINION, 0x606466, 0x408CFF, new Item.Settings()));

    public static final Item NABAT_SPAWN_EGG = register("nabat_spawn_egg",
            new SpawnEggItem(ModEntities.NABAT, 0x624628, 0xFFB848, new Item.Settings()));

    public static final Item NABAT_BLADE_SPAWN_EGG = register("nabat_blade_spawn_egg",
            new SpawnEggItem(ModEntities.NABAT_BLADE, 0x7A5834, 0xE8C25A, new Item.Settings()));
    public static final Item SAPPER_SPAWN_EGG = register("sapper_spawn_egg",
            new SpawnEggItem(ModEntities.SAPPER, 0x2A2522, 0xC07038, new Item.Settings()));
    public static final Item SIEGE_BEETLE_SPAWN_EGG = register("siege_beetle_spawn_egg",
            new SpawnEggItem(ModEntities.SIEGE_BEETLE, 0x281E1B, 0x8C5A34, new Item.Settings()));
    public static final Item BEETLE_MITE_SPAWN_EGG = register("beetle_mite_spawn_egg",
            new SpawnEggItem(ModEntities.BEETLE_MITE, 0x3A2A24, 0xC4562A, new Item.Settings()));

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Basedefense.MOD_ID, name), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(RESEARCH_COIN));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BUILDING_TABLET));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(BUILDER_SPAWN_EGG);
            entries.add(FARMER_SPAWN_EGG);
            entries.add(RANCHER_SPAWN_EGG);
            entries.add(COOK_SPAWN_EGG);
            entries.add(MINER_SPAWN_EGG);
            entries.add(BLACKSMITH_SPAWN_EGG);
            entries.add(LUMBERJACK_SPAWN_EGG);
            entries.add(IRON_ZOMBIE_SPAWN_EGG);
            entries.add(IRON_KNIGHT_SPAWN_EGG);
            entries.add(FROST_ARCHER_SPAWN_EGG);
            entries.add(TATTERED_LANCER_SPAWN_EGG);
            entries.add(BLOODTHIRSTER_SPAWN_EGG);
            entries.add(BLOODLING_SPAWN_EGG);
            entries.add(BLOOD_EGG_SPAWN_EGG);
            entries.add(CURB_SPAWN_EGG);
            entries.add(CURB_MINION_SPAWN_EGG);
            entries.add(NABAT_SPAWN_EGG);
            entries.add(NABAT_BLADE_SPAWN_EGG);
            entries.add(SAPPER_SPAWN_EGG);
            entries.add(SIEGE_BEETLE_SPAWN_EGG);
            entries.add(BEETLE_MITE_SPAWN_EGG);
        });
    }
}
