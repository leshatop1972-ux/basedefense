package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.structure.HouseType;
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

import java.util.EnumMap;
import java.util.Map;

/** Все блоки мода. */
public final class ModBlocks {

    /** Блок каждой постройки (заполняется при регистрации домов). */
    private static final Map<HouseType, Block> HOUSE_BLOCKS = new EnumMap<>(HouseType.class);

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
    /** Центральная площадь (колокол; сюда ходят жители, к ней ведут тропинки). */
    public static final Block TOWN_SQUARE = house("town_square", HouseType.TOWN_SQUARE);
    /** Здание Базы (склад, сюда переносится блок «База», живёт курьер). */
    public static final Block BASE_HOUSE = house("base_house", HouseType.BASE);
    /** Казарма (койки стражников, военкомат, плац). */
    public static final Block BARRACKS = house("barracks", HouseType.BARRACKS);

    /** Военкомат: стол вербовщика в казарме. ПКМ — вербовка стражников. Не крафтится, ничего не выпадает. */
    public static final Block RECRUITMENT_OFFICE = register("recruitment_office", new RecruitmentOfficeBlock(
            FabricBlockSettings.copyOf(Blocks.SPRUCE_PLANKS).strength(2.5f, 6.0f).nonOpaque().dropsNothing()));

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

    private static Block house(String name, HouseType type) {
        Block block = register(name, new HouseBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.0f), type));
        HOUSE_BLOCKS.put(type, block);
        return block;
    }

    /** Блок (стройплощадка) этой постройки. */
    public static Block houseBlock(HouseType type) {
        return HOUSE_BLOCKS.get(type);
    }

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(Basedefense.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        // Вкладка «Функциональные блоки» — после верстака.
        // Блоки домов не крафтятся (их ставит строительный планшет), здесь они для творческого режима
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.addAfter(
                Items.CRAFTING_TABLE, BASE, BUILDER_HOUSE, FARMER_HOUSE, RANCHER_HOUSE,
                COOK_HOUSE, MINER_HOUSE, BLACKSMITH_HOUSE, LUMBERJACK_HOUSE, TOWN_SQUARE, BASE_HOUSE, BARRACKS,
                RECRUITMENT_OFFICE, AMULET_TABLE));
    }
}
