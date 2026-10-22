package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.research.Technology;
import com.lesha_top1972.basedefense.settlement.SettlementItems;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.settlement.SettlementState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Серверная часть меню базы.
 * - Передаёт клиенту исследования и состояние поселения (через PropertyDelegate).
 * - Принимает нажатие кнопки «Исследовать» (номер кнопки = номер технологии).
 * - Вкладка «Потребности»: слот, куда кладут еду, + инвентарь игрока.
 */
public class BaseScreenHandler extends ScreenHandler {

    // ---------- Номера значений, которые сервер передаёт клиенту ----------
    public static final int PROP_RESEARCH = 0;
    public static final int PROP_SETTLERS = 1;
    public static final int PROP_FOOD = 2;
    public static final int PROP_FOOD_DAY = 3;   // сколько еды поселение тратит в день
    public static final int PROP_COUNT = 4;

    // ---------- Где стоят слоты (от левого верхнего угла окна) ----------
    public static final int DEPOSIT_X = 192;
    public static final int DEPOSIT_Y = 150;
    public static final int INV_X = 12;
    public static final int INV_Y = 136;

    private final ScreenHandlerContext context;
    private final PropertyDelegate props;
    private final Inventory deposit;
    private final PlayerEntity player;

    /** Видны ли слоты (клиент включает их только на вкладке «Потребности»). */
    private boolean slotsVisible = true;

    /** Конструктор для клиента (вызывается, когда сервер просит открыть меню). */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY, new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Конструктор для сервера. */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        this(syncId, playerInventory, context, serverProps(playerInventory.player));
    }

    private BaseScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context,
                              PropertyDelegate props) {
        super(ModScreenHandlers.BASE, syncId);
        this.context = context;
        this.props = props;
        this.player = playerInventory.player;
        this.deposit = new SimpleInventory(1);
        this.addProperties(props);

        // Слот 0 — «положить в Базу»
        this.addSlot(new DepositSlot(deposit, 0, DEPOSIT_X, DEPOSIT_Y));
        // Слоты 1..27 — инвентарь игрока, 28..36 — хотбар
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new ToggleSlot(playerInventory, col + row * 9 + 9,
                        INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new ToggleSlot(playerInventory, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    /** На сервере значения каждый раз берутся из общих данных сервера. */
    private static PropertyDelegate serverProps(PlayerEntity player) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return 0;
                }
                if (index == PROP_RESEARCH) {
                    return ResearchManager.researchMask(server);
                }
                SettlementState s = SettlementState.get(server);
                return switch (index) {
                    case PROP_SETTLERS -> s.getSettlerCount();
                    case PROP_FOOD -> s.getFood();
                    case PROP_FOOD_DAY -> s.getDailyFood();
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
    }

    // ---------- Для экрана (клиент) ----------

    public int getProp(int index) {
        return props.get(index);
    }

    public void setSlotsVisible(boolean visible) {
        this.slotsVisible = visible;
    }

    /** Исследована ли технология (работает на клиенте). */
    public boolean isResearched(Technology tech) {
        return (props.get(PROP_RESEARCH) & tech.bit()) != 0;
    }

    /** Можно ли уже исследовать технологию (предыдущая в цепочке открыта). */
    public boolean isAvailable(Technology tech) {
        return tech.isAvailable(props.get(PROP_RESEARCH));
    }

    // ---------- Логика ----------

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        Technology tech = Technology.byIndex(id);
        if (tech == null) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ResearchManager.tryResearch(serverPlayer, tech);
        }
        return true;
    }

    /** Каждый тик на сервере: всё, что лежит в слоте «положить», уходит в запасы Базы. */
    @Override
    public void sendContentUpdates() {
        if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
            ItemStack stack = deposit.getStack(0);
            if (!stack.isEmpty()) {
                int used = SettlementManager.deposit(serverPlayer.getServer(), stack);
                if (used > 0) {
                    stack.decrement(used);
                    deposit.setStack(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    serverPlayer.getWorld().playSound(null, serverPlayer.getBlockPos(),
                            SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.3f);
                }
            }
        }
        super.sendContentUpdates();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index == 0) {
            // Из слота «положить» — обратно в инвентарь
            if (!this.insertItem(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (SettlementItems.isAccepted(stack)) {
            // Из инвентаря — в слот «положить»
            if (!this.insertItem(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // Что не поместилось в запасы — возвращаем игроку
        this.context.run((world, pos) -> this.dropInventory(player, this.deposit));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Меню закроется, если игрок отойдёт или блок сломают.
        return canUse(context, player, ModBlocks.BASE);
    }

    // ---------- Слоты ----------

    /** Слот инвентаря, который виден только на вкладке «Потребности». */
    private class ToggleSlot extends Slot {
        ToggleSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isEnabled() {
            return slotsVisible;
        }
    }

    /** Слот «положить в Базу»: принимает только еду. */
    private class DepositSlot extends ToggleSlot {
        DepositSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return SettlementItems.isAccepted(stack);
        }
    }
}
