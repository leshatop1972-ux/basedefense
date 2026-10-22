package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.BuilderEntity;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Меню жителя (открывается кликом по жителю).
 * Житель работает сам, добычу складывает в сундук своего дома.
 * Кнопки: 2 — улучшить, 3 — отдать еду из руки (только у повара).
 * Состояние жителя передаётся клиенту через PropertyDelegate.
 */
public class SettlerScreenHandler extends ScreenHandler {

    public static final int BUTTON_UPGRADE = 2;
    public static final int BUTTON_INGREDIENTS = 3;   // только у повара

    public static final int PROP_LEVEL = 0;
    public static final int PROP_HAS_CHEST = 1;   // 1 — у жителя есть сундук
    public static final int PROP_STORED = 2;      // сколько добычи ждёт места в сундуке
    public static final int PROP_FROZEN = 3;
    public static final int PROP_WORK_FOOD = 4;   // сколько ест в день
    public static final int PROP_ROLLS = 5;        // сколько раз в день выбирается добыча
    public static final int PROP_ACCEPTS = 6;      // принимает продукты (повар) — 1
    public static final int PROP_INGREDIENTS = 7;  // сколько очков продуктов у жителя
    public static final int PROP_INGREDIENTS_DAY = 8; // сколько продуктов уходит за день
    // Только у строителя
    public static final int PROP_BUILD_STATUS = 9;    // 0 — не строитель; см. BuilderEntity.STATUS_*
    public static final int PROP_BUILD_HOUSE = 10;    // какой дом строит (номер предмета-блока, -1 — нет)
    public static final int PROP_BUILD_PROGRESS = 11; // готовность дома, %
    public static final int PROP_BUILD_MISSING = 12;  // какого материала не хватает (номер предмета, -1 — нет)
    public static final int PROP_PROFESSION = 13;     // номер типа жителя (для названия профессии)
    // Номер сущности жителя (для портрета в меню). Значения уходят клиенту как short,
    // поэтому номер делится на две половины по 16 бит.
    public static final int PROP_ENTITY_LO = 14;
    public static final int PROP_ENTITY_HI = 15;
    public static final int PROP_COUNT = 16;

    /** Житель (только на сервере; на клиенте null). */
    @Nullable
    private final SettlerEntity settler;
    private final PropertyDelegate props;

    /** Конструктор для клиента. */
    public SettlerScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.SETTLER, syncId);
        this.settler = null;
        this.props = new ArrayPropertyDelegate(PROP_COUNT);
        this.addProperties(props);
    }

    /** Конструктор для сервера. */
    public SettlerScreenHandler(int syncId, PlayerInventory playerInventory, SettlerEntity settler) {
        super(ModScreenHandlers.SETTLER, syncId);
        this.settler = settler;
        this.props = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROP_LEVEL -> settler.getLevel();
                    case PROP_HAS_CHEST -> settler.hasChest() ? 1 : 0;
                    case PROP_STORED -> Math.min(Short.MAX_VALUE, settler.getStoredCount());
                    case PROP_FROZEN -> settler.getServer() != null
                            && SettlementManager.isFrozen(settler.getServer()) ? 1 : 0;
                    case PROP_WORK_FOOD -> settler.getFoodPerDay();
                    case PROP_ROLLS -> settler.getLootRolls();
                    case PROP_ACCEPTS -> settler.acceptsIngredients() ? 1 : 0;
                    case PROP_INGREDIENTS -> settler.getIngredients();
                    case PROP_INGREDIENTS_DAY -> settler.getIngredientsPerDay();
                    case PROP_BUILD_STATUS -> settler instanceof BuilderEntity b ? b.getBuildStatus() : 0;
                    case PROP_BUILD_HOUSE -> settler instanceof BuilderEntity b && b.getCurrentHouse() != null
                            ? Registries.ITEM.getRawId(Registries.ITEM.get(
                                    new Identifier(Basedefense.MOD_ID, b.getCurrentHouse().getId()))) : -1;
                    case PROP_BUILD_PROGRESS -> settler instanceof BuilderEntity b ? b.getBuildProgress() : 0;
                    case PROP_PROFESSION -> Registries.ENTITY_TYPE.getRawId(settler.getType());
                    case PROP_ENTITY_LO -> (short) (settler.getId() & 0xFFFF);
                    case PROP_ENTITY_HI -> (short) ((settler.getId() >>> 16) & 0xFFFF);
                    case PROP_BUILD_MISSING -> settler instanceof BuilderEntity b && b.getMissingItem() != null
                            ? Registries.ITEM.getRawId(b.getMissingItem()) : -1;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return PROP_COUNT;
            }
        };
        this.addProperties(props);
        settler.setInteractingPlayer(playerInventory.player);
    }

    public int getProp(int index) {
        return props.get(index);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (settler == null) {
            return false;
        }
        switch (id) {
            case BUTTON_UPGRADE -> settler.tryUpgrade(player);
            case BUTTON_INGREDIENTS -> settler.takeIngredientsFromHand(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (settler != null && settler.getInteractingPlayer() == player) {
            settler.setInteractingPlayer(null);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Меню закроется, если житель погиб или игрок отошёл дальше 8 блоков
        return settler == null || (settler.isAlive() && player.squaredDistanceTo(settler) < 8 * 8);
    }
}
