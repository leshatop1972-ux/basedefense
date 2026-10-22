package com.lesha_top1972.basedefense.research;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Логика исследований. Работает только на сервере. */
public final class ResearchManager {

    private ResearchManager() {
    }

    public static boolean isResearched(ServerPlayerEntity player, Technology tech) {
        return ResearchState.get(player.getServer()).has(player.getUuid(), tech);
    }

    public static int researchMask(ServerPlayerEntity player) {
        return ResearchState.get(player.getServer()).mask(player.getUuid());
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

    private static void removeCoins(PlayerEntity player, int amount) {
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

    /** Пытается исследовать технологию: проверяет монеты, забирает их и открывает рецепт. */
    public static void tryResearch(ServerPlayerEntity player, Technology tech) {
        ResearchState state = ResearchState.get(player.getServer());
        if (state.has(player.getUuid(), tech)) {
            return;
        }
        if (countCoins(player) < tech.getCost()) {
            player.sendMessage(Text.translatable("message.basedefense.research.not_enough", tech.getCost())
                    .formatted(Formatting.RED), true);
            return;
        }

        removeCoins(player, tech.getCost());
        state.add(player.getUuid(), tech);
        player.unlockRecipes(new Identifier[]{tech.getUnlockedRecipe()});

        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.8f, 1.2f);
        player.sendMessage(Text.translatable("message.basedefense.research.done", tech.getName())
                .formatted(Formatting.GREEN), false);
    }
}
