package com.lesha_top1972.basedefense.clothing;

import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.Nullable;

/**
 * Меню рюкзака: как сундук на 2 или 3 ряда (окно — ванильное, GenericContainerScreen) плюс инвентарь игрока.
 * Вещи читаются из NBT рюкзака и пишутся обратно при каждом изменении. Рюкзак в рюкзак положить нельзя.
 * owner — номер сущности хозяина (−1 — сам игрок); клиенту приходят ряды и хозяин (для вкладок).
 */
public class BackpackScreenHandler extends GenericContainerScreenHandler {

    private final int ownerId;
    @Nullable
    private final Inventory clothes;
    @Nullable
    private final ItemStack backpack;
    @Nullable
    private final Entity owner;

    /** Клиент. */
    public BackpackScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, buf.readByte(), buf.readVarInt());
    }

    private BackpackScreenHandler(int syncId, PlayerInventory playerInventory, int rows, int ownerId) {
        this(syncId, playerInventory, new SimpleInventory(rows * 9), rows, ownerId, null, null, null);
    }

    /** Сервер. */
    public BackpackScreenHandler(int syncId, PlayerInventory playerInventory, Contents contents, int ownerId,
                                 Inventory clothes, ItemStack backpack, Entity owner) {
        this(syncId, playerInventory, (Inventory) contents, contents.size() / 9, ownerId, clothes, backpack, owner);
    }

    private BackpackScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, int rows, int ownerId,
                                  @Nullable Inventory clothes, @Nullable ItemStack backpack, @Nullable Entity owner) {
        super(ModScreenHandlers.BACKPACK, syncId, playerInventory, inventory, rows);
        this.ownerId = ownerId;
        this.clothes = clothes;
        this.backpack = backpack;
        this.owner = owner;
        // Ячейки рюкзака: рюкзак внутрь не кладётся
        for (int i = 0; i < rows * 9; i++) {
            Slot old = this.slots.get(i);
            Slot slot = new Slot(inventory, i, old.x, old.y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return !(stack.getItem() instanceof BackpackItem);
                }
            };
            slot.id = i;
            this.slots.set(i, slot);
        }
    }

    public int getOwnerId() {
        return ownerId;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (clothes == null || backpack == null || owner == null) {
            return true;
        }
        return clothes.getStack(Clothing.BACKPACK) == backpack && owner.isAlive()
                && (owner == player || player.squaredDistanceTo(owner) < 8.0 * 8.0);
    }

    /** Вещи рюкзака: читаются из NBT предмета, при изменении пишутся обратно. */
    public static class Contents extends SimpleInventory {
        private final ItemStack stack;
        /** Пока читаем из NBT — обратно не пишем (иначе затрём ещё не прочитанные ячейки). */
        private boolean loading;

        public Contents(ItemStack stack, int size) {
            super(size);
            this.stack = stack;
            this.loading = true;
            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains("Items")) {
                DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
                Inventories.readNbt(nbt, list);
                for (int i = 0; i < size; i++) {
                    super.setStack(i, list.get(i));
                }
            }
            this.loading = false;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (loading) {
                return;
            }
            DefaultedList<ItemStack> list = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
            for (int i = 0; i < this.size(); i++) {
                list.set(i, this.getStack(i));
            }
            Inventories.writeNbt(stack.getOrCreateNbt(), list);
        }

        @Override
        public boolean isValid(int slot, ItemStack s) {
            return !(s.getItem() instanceof BackpackItem);
        }

        @Override
        public void onClose(PlayerEntity player) {
            markDirty();
            super.onClose(player);
        }
    }
}
