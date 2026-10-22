package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.structure.HousePlans;
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

    /** База: одна на сервер, сломать нельзя (прочность как у бедрока). */
    public static final Block BASE = register("base",
            new BaseBlock(FabricBlockSettings.copyOf(Blocks.CRAFTING_TABLE).strength(-1.0f, 3600000.0f)));

    /** Плотный блок дерева — материал для крафта. */
    public static final Block DENSE_WOOD = register("dense_wood",
            new Block(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(3.0f)));

    /** Конструкторский блок — деревянный ящик, материал для крафта. */
    public static final Block CONSTRUCTOR_BLOCK = register("constructor_block",
            new Block(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.5f)));

    /** Дом фермера — при установке строит домик с огородом. */
    public static final Block FARMER_HOUSE = register("farmer_house",
            new HouseBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.0f), HousePlans::farmer));

    /** Дом скотовода — при установке строит домик с загоном. */
    public static final Block RANCHER_HOUSE = register("rancher_house",
            new HouseBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.0f), HousePlans::rancher));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(Basedefense.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        // Вкладка «Функциональные блоки» — после верстака
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.addAfter(
                Items.CRAFTING_TABLE, BASE, DENSE_WOOD, CONSTRUCTOR_BLOCK, FARMER_HOUSE, RANCHER_HOUSE));
    }
}
