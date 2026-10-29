package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.artifact.ArtifactItem;
import com.lesha_top1972.basedefense.block.AmuletTableBlockEntity;
import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Меню стола амулетов. Слоты: 0, 1 — амулеты (сверху слева и справа), 2 — пустой кулон (снизу),
 * 3 — результат (в центре, только забрать), дальше — инвентарь игрока.
 * Клиенту через props: какой рецепт в работе и сколько осталось. Все рецепты видны сразу.
 */
public class AmuletTableScreenHandler extends ScreenHandler {

    // ---------- Размеры окна и места слотов (верхний левый угол предмета) ----------
    public static final int W = 256;
    public static final int H = 240;
    public static final int LEFT_X = 34;
    public static final int LEFT_Y = 46;
    public static final int RIGHT_X = 110;
    public static final int RIGHT_Y = 46;
    public static final int RESULT_X = 72;
    public static final int RESULT_Y = 76;
    public static final int PENDANT_X = 72;
    public static final int PENDANT_Y = 106;
    public static final int INV_X = 48;
    public static final int INV_Y = 156;

    public static final int PROP_RECIPE = 0;      // рецепт в работе + 1 (0 — стол свободен)
    public static final int PROP_LEFT = 1;        // сколько секунд осталось
    public static final int PROP_TOTAL = 2;       // сколько секунд всего
    public static final int PROP_COUNT = 3;

    public static final int BUTTON_START = 0;

    private static final int TABLE_SLOTS = AmuletTableBlockEntity.SIZE;

    private final Inventory table;
    @Nullable
    private final AmuletTableBlockEntity entity;
    private final PropertyDelegate props;

    /** Клиент. */
    public AmuletTableScreenHandler(int syncId, PlayerInventory inventory) {
        this(syncId, inventory, new SimpleInventory(TABLE_SLOTS), null, new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Сервер. */
    public AmuletTableScreenHandler(int syncId, PlayerInventory inventory, AmuletTableBlockEntity entity) {
        this(syncId, inventory, entity, entity, serverProps(entity, inventory.player));
    }

    private AmuletTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory table,
                                     @Nullable AmuletTableBlockEntity entity, PropertyDelegate props) {
        super(ModScreenHandlers.AMULET_TABLE, syncId);
        checkSize(table, TABLE_SLOTS);
        this.table = table;
        this.entity = entity;
        this.props = props;
        table.onOpen(playerInventory.player);
        this.addProperties(props);

        this.addSlot(new AmuletSlot(table, AmuletTableBlockEntity.LEFT, LEFT_X, LEFT_Y));
        this.addSlot(new AmuletSlot(table, AmuletTableBlockEntity.RIGHT, RIGHT_X, RIGHT_Y));
        this.addSlot(new Slot(table, AmuletTableBlockEntity.PENDANT, PENDANT_X, PENDANT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(ModItems.EMPTY_PENDANT);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        this.addSlot(new Slot(table, AmuletTableBlockEntity.RESULT, RESULT_X, RESULT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    private static final class AmuletSlot extends Slot {
        AmuletSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof ArtifactItem;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private static PropertyDelegate serverProps(AmuletTableBlockEntity entity, PlayerEntity player) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROP_RECIPE -> entity.getRecipe() + 1;
                    case PROP_LEFT -> (entity.remainingTicks() + 19) / 20;
                    case PROP_TOTAL -> entity.getTotalTicks() / 20;
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

    public Inventory getTable() {
        return table;
    }

    /** Рецепт в работе или -1. */
    public int craftingRecipe() {
        return props.get(PROP_RECIPE) - 1;
    }

    public boolean isCrafting() {
        return craftingRecipe() >= 0;
    }

    public int secondsLeft() {
        return props.get(PROP_LEFT);
    }

    public int secondsTotal() {
        return props.get(PROP_TOTAL);
    }

    // ---------- Логика ----------

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BUTTON_START || entity == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        entity.start(serverPlayer);
        return true;
    }

    /** Shift+клик: амулет — в свободный верхний слот, кулон — вниз; из стола — в инвентарь. */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        int end = this.slots.size();
        if (index < TABLE_SLOTS) {
            if (!this.insertItem(stack, TABLE_SLOTS, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.isOf(ModItems.EMPTY_PENDANT)) {
            if (!this.insertItem(stack, AmuletTableBlockEntity.PENDANT, AmuletTableBlockEntity.PENDANT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ArtifactItem) {
            if (!this.insertItem(stack, AmuletTableBlockEntity.LEFT, AmuletTableBlockEntity.RIGHT + 1, false)) {
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
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTakeItem(player, stack);
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return table.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        table.onClose(player);
    }
}
