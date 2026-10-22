package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.structure.HouseType;
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

    /** Дом строителя — строится сам; строитель потом строит остальные дома. */
    public static final Block BUILDER_HOUSE = house("builder_house", HouseType.BUILDER);
    /** Дом фермера (огород). */
    public static final Block FARMER_HOUSE = house("farmer_house", HouseType.FARMER);
    /** Дом скотовода (загон). */
    public static final Block RANCHER_HOUSE = house("rancher_house", HouseType.RANCHER);
    /** Дом повара (летняя кухня). */
    public static final Block COOK_HOUSE = house("cook_house", HouseType.COOK);
    /** Дом шахтёра (вход в шахту). */
    public static final Block MINER_HOUSE = house("miner_house", HouseType.MINER);
    /** Кузница (горн). */
    public static final Block BLACKSMITH_HOUSE = house("blacksmith_house", HouseType.BLACKSMITH);
    /** Дом лесоруба (посадка деревьев). */
    public static final Block LUMBERJACK_HOUSE = house("lumberjack_house", HouseType.LUMBERJACK);

    private ModBlocks() {
    }

    private static Block house(String name, HouseType type) {
        return register(name, new HouseBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.0f), type));
    }

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(Basedefense.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        // Вкладка «Функциональные блоки» — после верстака
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.addAfter(
                Items.CRAFTING_TABLE, BASE, BUILDER_HOUSE, FARMER_HOUSE, RANCHER_HOUSE,
                COOK_HOUSE, MINER_HOUSE, BLACKSMITH_HOUSE, LUMBERJACK_HOUSE));
    }
}
