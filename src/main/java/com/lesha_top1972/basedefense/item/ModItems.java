package com.lesha_top1972.basedefense.item;

import com.lesha_top1972.basedefense.weapon.BloodthirsterSwordItem;
import com.lesha_top1972.basedefense.weapon.KatanaItem;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Все предметы мода (кроме предметов-блоков, они в ModBlocks). */
public final class ModItems {

    /** Монета исследований — валюта для исследований в меню Базы. */
    public static final Item RESEARCH_COIN = register("research_coin", new Item(new Item.Settings()));

    /** Строительный планшет: выбор и установка построек (крафт: верстак + бумага + чернильный мешок). */
    public static final Item BUILDING_TABLET = register("building_tablet",
            new BuildingTabletItem(new Item.Settings().maxCount(1)));

    /** Меч кровожада: урон 8, скорость 1.4; держать ЛКМ — замах двумя руками и удар сверху. */
    public static final Item BLOODTHIRSTER_SWORD = register("bloodthirster_sword",
            new BloodthirsterSwordItem(new Item.Settings().rarity(Rarity.EPIC)));

    /** Катана: серия из трёх ударов с рывком (урон 5 каждый), в покое — в ножнах у бедра. */
    public static final Item KATANA = register("katana", new KatanaItem(new Item.Settings()));

    /** Артефакт «Амулет рывка»: R — рывок на 4 блока, перезарядка 2 с. */
    public static final Item DASH_AMULET = register("dash_amulet",
            new com.lesha_top1972.basedefense.artifact.ArtifactItem(new Item.Settings().rarity(Rarity.RARE),
                    "item.basedefense.dash_amulet.tip"));

    /** Артефакт «Амулет взлёта»: Y — подброс на 3.5 блока, 3 раза подряд, потом перезарядка 6 с. */
    public static final Item LEAP_AMULET = register("leap_amulet",
            new com.lesha_top1972.basedefense.artifact.ArtifactItem(new Item.Settings().rarity(Rarity.RARE),
                    "item.basedefense.leap_amulet.tip"));

    /** Амулеты вампиризма I–III: лечение за убийство (4/6/8), у III ещё 1 HP за попадание. Не складываются. */
    public static final Item VAMPIRE_AMULET_1 = amulet("vampire_amulet_1", Rarity.UNCOMMON);
    public static final Item VAMPIRE_AMULET_2 = amulet("vampire_amulet_2", Rarity.RARE);
    public static final Item VAMPIRE_AMULET_3 = amulet("vampire_amulet_3", Rarity.EPIC);

    /** Амулеты скорости I–III: +8/+16/+25% к скорости. Не складываются. */
    public static final Item SPEED_AMULET_1 = amulet("speed_amulet_1", Rarity.UNCOMMON);
    public static final Item SPEED_AMULET_2 = amulet("speed_amulet_2", Rarity.RARE);
    public static final Item SPEED_AMULET_3 = amulet("speed_amulet_3", Rarity.EPIC);

    /** Амулеты жизни I–III: +5/+9/+13 HP. Складываются. */
    public static final Item LIFE_AMULET_1 = amulet("life_amulet_1", Rarity.UNCOMMON);
    public static final Item LIFE_AMULET_2 = amulet("life_amulet_2", Rarity.RARE);
    public static final Item LIFE_AMULET_3 = amulet("life_amulet_3", Rarity.EPIC);

    /** Амулеты уворота I–III: 7/10/13% не получить урон от атаки моба. Складываются (максимум 50%). */
    public static final Item DODGE_AMULET_1 = amulet("dodge_amulet_1", Rarity.UNCOMMON);
    public static final Item DODGE_AMULET_2 = amulet("dodge_amulet_2", Rarity.RARE);
    public static final Item DODGE_AMULET_3 = amulet("dodge_amulet_3", Rarity.EPIC);

    /** Плащ вампиризма: 25% шанс — союзники в 25 блоках лечатся на 25% нанесённого урона. */
    public static final Item VAMPIRE_CLOAK = amulet("vampire_cloak", Rarity.EPIC);

    /** Амулет мудрости: удар тратит 1/4 уровня опыта — Скорость I на 5 с и +1 HP. */
    public static final Item WISDOM_AMULET = amulet("wisdom_amulet", Rarity.RARE);

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

    /** Яйцо призыва курьера. */
    public static final Item COURIER_SPAWN_EGG = register("courier_spawn_egg",
            new SpawnEggItem(ModEntities.COURIER, 0x1E8C84, 0xC8A24A, new Item.Settings()));

    /** Яйцо призыва стражника (сталь и красный). */
    public static final Item GUARD_SPAWN_EGG = register("guard_spawn_egg",
            new SpawnEggItem(ModEntities.GUARD, 0x8C949C, 0x9E2A2A, new Item.Settings()));

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
    public static final Item MIST_ENTITY_SPAWN_EGG = register("mist_entity_spawn_egg",
            new SpawnEggItem(ModEntities.MIST_ENTITY, 0xE6E6EA, 0x8E9098, new Item.Settings()));
    public static final Item BLOOD_ENTITY_SPAWN_EGG = register("blood_entity_spawn_egg",
            new SpawnEggItem(ModEntities.BLOOD_ENTITY, 0x160C0E, 0xC41620, new Item.Settings()));

    public static final Item BLOODLETTER_SPAWN_EGG = register("bloodletter_spawn_egg",
            new SpawnEggItem(ModEntities.BLOODLETTER, 0x1A0A0E, 0xB01226, new Item.Settings()));

    private ModItems() {
    }

    private static Item amulet(String name, Rarity rarity) {
        return register(name, new com.lesha_top1972.basedefense.artifact.ArtifactItem(
                new Item.Settings().rarity(rarity), "item.basedefense." + name + ".tip"));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Basedefense.MOD_ID, name), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(RESEARCH_COIN));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BUILDING_TABLET));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(BLOODTHIRSTER_SWORD);
            entries.add(KATANA);
            entries.add(DASH_AMULET);
            entries.add(LEAP_AMULET);
            entries.add(VAMPIRE_AMULET_1);
            entries.add(VAMPIRE_AMULET_2);
            entries.add(VAMPIRE_AMULET_3);
            entries.add(SPEED_AMULET_1);
            entries.add(SPEED_AMULET_2);
            entries.add(SPEED_AMULET_3);
            entries.add(LIFE_AMULET_1);
            entries.add(LIFE_AMULET_2);
            entries.add(LIFE_AMULET_3);
            entries.add(DODGE_AMULET_1);
            entries.add(DODGE_AMULET_2);
            entries.add(DODGE_AMULET_3);
            entries.add(VAMPIRE_CLOAK);
            entries.add(WISDOM_AMULET);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(BUILDER_SPAWN_EGG);
            entries.add(FARMER_SPAWN_EGG);
            entries.add(RANCHER_SPAWN_EGG);
            entries.add(COOK_SPAWN_EGG);
            entries.add(MINER_SPAWN_EGG);
            entries.add(BLACKSMITH_SPAWN_EGG);
            entries.add(LUMBERJACK_SPAWN_EGG);
            entries.add(COURIER_SPAWN_EGG);
            entries.add(GUARD_SPAWN_EGG);
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
            entries.add(MIST_ENTITY_SPAWN_EGG);
            entries.add(BLOOD_ENTITY_SPAWN_EGG);
            entries.add(BLOODLETTER_SPAWN_EGG);
        });
    }
}
