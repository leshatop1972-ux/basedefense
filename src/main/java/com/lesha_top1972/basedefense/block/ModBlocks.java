package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Все блоки мода. */
public final class ModBlocks {

    public static final Block BASE = register("base",
            new BaseBlock(FabricBlockSettings.copyOf(Blocks.CRAFTING_TABLE).strength(3.0f)));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(Basedefense.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        // Кладём «Базу» во вкладку «Функциональные блоки» сразу после верстака.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.addAfter(Items.CRAFTING_TABLE, BASE));
    }
}
