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

    /** Яйцо призыва жителя-фермера (для тестов и творческого режима). */
    public static final Item FARMER_SPAWN_EGG = register("farmer_spawn_egg",
            new SpawnEggItem(ModEntities.FARMER, 0x6E8F3E, 0xE8C25A, new Item.Settings()));

    /** Яйцо призыва жителя-скотовода. */
    public static final Item RANCHER_SPAWN_EGG = register("rancher_spawn_egg",
            new SpawnEggItem(ModEntities.RANCHER, 0xA83A2E, 0x8B5A2B, new Item.Settings()));

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Basedefense.MOD_ID, name), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(RESEARCH_COIN));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(FARMER_SPAWN_EGG);
            entries.add(RANCHER_SPAWN_EGG);
        });
    }
}
