package com.lesha_top1972.basedefense.clothing;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Два слоя экипировки: одежда (снизу) и броня (сверху). Одежда — 4 слота: голова, тело, ноги, ступни
 * ({@link ClothingType}), плюс слот рюкзака ({@link BackpackItem}). У игрока эти слоты — в конце меню инвентаря
 * (после артефактов), на отдельной панели над окном инвентаря (открывается маленькой кнопкой у брони).
 * У поселенца — панель слева от его меню.
 * При смерти одежда выпадает (с keepInventory остаётся).
 */
public final class Clothing {

    /** Слоты одежды (0–3: голова, тело, ноги, ступни) + слот рюкзака (4). */
    public static final int CLOTHES = 4;
    public static final int BACKPACK = 4;
    public static final int SLOTS = 5;

    /** Панель у игрока — над окном инвентаря (координаты слотов относительно окна). */
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = -21;
    public static final int PLAYER_BACKPACK_X = PLAYER_X + 4 * 18 + 8;
    /** Панель у поселенца — слева от его меню, столбиком. */
    public static final int SETTLER_X = -21;
    public static final int SETTLER_Y = 30;
    public static final int SETTLER_BACKPACK_Y = SETTLER_Y + 4 * 18 + 8;

    /** Клиент: включён вид «одежда» в инвентаре игрока (слоты одежды видны, ванильные слоты брони скрыты). */
    @Nullable
    public static BooleanSupplier playerView;
    /** Клиент: включён вид «одежда» в меню поселенца. */
    @Nullable
    public static BooleanSupplier settlerView;

    private Clothing() {
    }

    public static SimpleInventory of(PlayerEntity p) {
        return ((ClothingHolder) p).basedefense$clothing();
    }

    public static boolean playerViewOn() {
        return playerView != null && playerView.getAsBoolean();
    }

    /** Рюкзак в слоте рюкзака или пусто. */
    public static ItemStack backpack(net.minecraft.inventory.Inventory clothes) {
        if (clothes == null || clothes.size() <= BACKPACK) {
            return ItemStack.EMPTY;
        }
        ItemStack s = clothes.getStack(BACKPACK);
        return s.getItem() instanceof BackpackItem ? s : ItemStack.EMPTY;
    }

    public static boolean settlerViewOn() {
        return settlerView != null && settlerView.getAsBoolean();
    }

    public static void init() {
        // Возрождение: одежда остаётся, если игрок не умирал (возврат из Энда) или включён keepInventory
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && !oldPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
                return;
            }
            SimpleInventory from = of(oldPlayer);
            SimpleInventory to = of(newPlayer);
            for (int i = 0; i < from.size(); i++) {
                to.setStack(i, from.getStack(i).copy());
            }
        });
    }

    /** Смерть без keepInventory: одежда выпадает. */
    public static void dropOnDeath(PlayerEntity p) {
        SimpleInventory inv = of(p);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                p.dropItem(stack.copy(), true, false);
            }
        }
        inv.clear();
    }

    public static NbtList toNbt(SimpleInventory inv) {
        NbtList list = new NbtList();
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                NbtCompound c = inv.getStack(i).writeNbt(new NbtCompound());
                c.putByte("Slot", (byte) i);
                list.add(c);
            }
        }
        return list;
    }

    public static void fromNbt(SimpleInventory inv, NbtList list) {
        inv.clear();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            int slot = c.getByte("Slot");
            if (slot >= 0 && slot < inv.size()) {
                inv.setStack(slot, ItemStack.fromNbt(c));
            }
        }
    }
}
