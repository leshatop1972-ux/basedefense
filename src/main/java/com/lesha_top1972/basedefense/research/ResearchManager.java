package com.lesha_top1972.basedefense.research;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/** Монеты исследований: подсчёт и списание (идут на улучшение жителей). */
public final class ResearchManager {

    private ResearchManager() {
    }

    public static void init() {
        // Исследований больше нет — монеты идут на улучшение жителей
    }

    /** Сколько монет исследований у игрока в инвентаре (работает и на клиенте). */
    public static int countCoins(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(ModItems.RESEARCH_COIN)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void removeCoins(PlayerEntity player, int amount) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size() && amount > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(ModItems.RESEARCH_COIN)) {
                int take = Math.min(amount, stack.getCount());
                stack.decrement(take);
                amount -= take;
            }
        }
        inventory.markDirty();
    }
}
