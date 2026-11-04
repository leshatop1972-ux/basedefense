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
 * ({@link ClothingType}). У игрока слоты одежды — в конце его меню инвентаря (после артефактов), стоят на месте
 * слотов брони и показываются вместо них маленькой кнопкой «одежда/броня». У поселенца — в его меню, так же.
 * При смерти одежда выпадает (с keepInventory остаётся).
 */
public final class Clothing {

    public static final int SLOTS = 4;

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
