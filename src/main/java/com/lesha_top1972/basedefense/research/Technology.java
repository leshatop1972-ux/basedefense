package com.lesha_top1972.basedefense.research;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Список всех технологий (исследований). Исследования общие на весь сервер.
 * Каждая технология открывает постройку в строительном планшете.
 * Дом строителя доступен сразу, исследовать его не нужно.
 * Чтобы добавить новую — допиши строку в список ниже и добавь перевод в lang.
 *
 * cost         — цена в монетах исследований
 * tab          — номер вкладки в меню Базы (0 = «Хозяйство»)
 * gridX, gridY — место квадратика в древе (в клетках)
 * house        — постройка, которая открывается в планшете
 * requires     — какую технологию нужно исследовать раньше (null — ничего)
 */
public enum Technology {
    // Исследования хранятся по id, поэтому порядок в списке можно менять
    FARMER_HOUSE("farmer_house", 60, 0, 0, 0, HouseType.FARMER, null),
    TOWN_SQUARE("town_square", 80, 0, 0, 1, HouseType.TOWN_SQUARE, FARMER_HOUSE),
    RANCHER_HOUSE("rancher_house", 100, 0, 1, 0, HouseType.RANCHER, FARMER_HOUSE),
    COOK_HOUSE("cook_house", 120, 0, 2, 0, HouseType.COOK, RANCHER_HOUSE),
    MINER_HOUSE("miner_house", 150, 0, 2, 1, HouseType.MINER, RANCHER_HOUSE),
    BLACKSMITH_HOUSE("blacksmith_house", 200, 0, 3, 1, HouseType.BLACKSMITH, MINER_HOUSE),
    LUMBERJACK_HOUSE("lumberjack_house", 130, 0, 2, 2, HouseType.LUMBERJACK, MINER_HOUSE);

    private final String id;
    private final int cost;
    private final int tab;
    private final int gridX;
    private final int gridY;
    private final HouseType house;
    @Nullable
    private final Technology requires;

    Technology(String id, int cost, int tab, int gridX, int gridY, HouseType house, @Nullable Technology requires) {
        this.id = id;
        this.cost = cost;
        this.tab = tab;
        this.gridX = gridX;
        this.gridY = gridY;
        this.house = house;
        this.requires = requires;
    }

    public String getId() {
        return id;
    }

    public int getCost() {
        return cost;
    }

    public int getTab() {
        return tab;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    /** Постройка, которую открывает технология. */
    public HouseType getHouseType() {
        return house;
    }

    /** Технология, которая открывает постройку (null — постройка доступна сразу). */
    @Nullable
    public static Technology forHouse(HouseType house) {
        for (Technology tech : values()) {
            if (tech.house == house) {
                return tech;
            }
        }
        return null;
    }

    /** Технология, которую нужно исследовать раньше (или null). */
    @Nullable
    public Technology getRequires() {
        return requires;
    }

    /** Предмет-иконка (блок постройки). */
    public ItemConvertible getIcon() {
        return ModBlocks.houseBlock(house);
    }

    public Text getName() {
        return Text.translatable("research.basedefense." + id);
    }

    public Text getDescription() {
        return Text.translatable("research.basedefense." + id + ".desc");
    }

    /** Подробно о работе жителя (показывается по наведению на синий кружок «?»). */
    public Text getWorkInfo() {
        return Text.translatable("research.basedefense." + id + ".work");
    }

    /** Бит этой технологии в общей «маске» исследований. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Доступна ли для исследования при такой маске (предыдущая уже открыта). */
    public boolean isAvailable(int mask) {
        return requires == null || (mask & requires.bit()) != 0;
    }

    @Nullable
    public static Technology byIndex(int index) {
        Technology[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }
}
