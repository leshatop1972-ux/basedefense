package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Все блоки мода. */
public final class ModBlocks {

    /** Кровавая лужа вокруг яиц кровожада: 0.5 урона в секунду, ничего не выпадает. */
    public static final Block BLOOD_POOL = register("blood_pool", new BloodPoolBlock(FabricBlockSettings.create()
            .mapColor(MapColor.DARK_RED).strength(0.5f).sounds(BlockSoundGroup.SLIME).luminance(s -> 3)
            .nonOpaque().dropsNothing()));

    /** Гудящий камень (холмы «Землетрясения» Набата): на нём растёт «Шум», ничего не выпадает. */
    public static final Block RESONANT_STONE = register("resonant_stone", new ResonantStoneBlock(FabricBlockSettings
            .copyOf(Blocks.STONE).mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5f, 6.0f)
            .sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 4).dropsNothing()));

    /** Стол амулетов: ПКМ — меню, из двух амулетов и пустого кулона за 8 минут делает новый амулет. */
    public static final Block AMULET_TABLE = register("amulet_table", new AmuletTableBlock(
            FabricBlockSettings.copyOf(Blocks.SMITHING_TABLE).strength(2.5f, 6.0f).nonOpaque().luminance(s -> 5)));

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
                Items.CRAFTING_TABLE, AMULET_TABLE));
    }
}
