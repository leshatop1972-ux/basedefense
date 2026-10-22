package com.lesha_top1972.basedefense.research;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.block.ModBlocks;
import net.minecraft.item.ItemConvertible;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Список всех технологий (исследований). Исследования общие на весь сервер.
 * Чтобы добавить новую — допиши строку в список ниже, добавь перевод в lang
 * и рецепт, который она открывает.
 *
 * cost         — цена в монетах исследований
 * tab          — номер вкладки в меню Базы (0 = «Технологии»)
 * gridX, gridY — место квадратика в древе (в клетках)
 * recipe       — рецепт, который открывается
 * requires     — какую технологию нужно исследовать раньше (null — ничего)
 */
public enum Technology {
    FARMER_HOUSE("farmer_house", 60, 0, 0, 0, "farmer_house", null),
    RANCHER_HOUSE("rancher_house", 100, 0, 1, 0, "rancher_house", FARMER_HOUSE);

    private final String id;
    private final int cost;
    private final int tab;
    private final int gridX;
    private final int gridY;
    private final Identifier unlockedRecipe;
    @Nullable
    private final Technology requires;

    Technology(String id, int cost, int tab, int gridX, int gridY, String recipe, @Nullable Technology requires) {
        this.id = id;
        this.cost = cost;
        this.tab = tab;
        this.gridX = gridX;
        this.gridY = gridY;
        this.unlockedRecipe = new Identifier(Basedefense.MOD_ID, recipe);
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

    public Identifier getUnlockedRecipe() {
        return unlockedRecipe;
    }

    /** Технология, которую нужно исследовать раньше (или null). */
    @Nullable
    public Technology getRequires() {
        return requires;
    }

    /** Предмет-иконка (он же предмет, крафт которого открывается). */
    public ItemConvertible getIcon() {
        return switch (this) {
            case FARMER_HOUSE -> ModBlocks.FARMER_HOUSE;
            case RANCHER_HOUSE -> ModBlocks.RANCHER_HOUSE;
        };
    }

    public Text getName() {
        return Text.translatable("research.basedefense." + id);
    }

    public Text getDescription() {
        return Text.translatable("research.basedefense." + id + ".desc");
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

    public static Optional<Technology> byRecipe(Identifier recipeId) {
        for (Technology tech : values()) {
            if (tech.unlockedRecipe.equals(recipeId)) {
                return Optional.of(tech);
            }
        }
        return Optional.empty();
    }
}
