package com.lesha_top1972.basedefense.research;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Логика исследований. Исследования общие на весь сервер. */
public final class ResearchManager {

    private ResearchManager() {
    }

    public static void init() {
        // Рецептов больше нет: исследования открывают постройки в строительном планшете
    }

    public static boolean isResearched(MinecraftServer server, Technology tech) {
        return ResearchState.get(server).has(tech);
    }

    public static int researchMask(MinecraftServer server) {
        return ResearchState.get(server).mask();
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

    /** Пытается исследовать технологию: проверяет цепочку и монеты, забирает их и открывает постройку в планшете для всех. */
    public static void tryResearch(ServerPlayerEntity player, Technology tech) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResearchState state = ResearchState.get(server);
        if (state.has(tech)) {
            return;
        }
        if (!tech.isAvailable(state.mask())) {
            player.sendMessage(Text.translatable("message.basedefense.research.requires", tech.getRequires().getName())
                    .formatted(Formatting.RED), true);
            return;
        }
        if (countCoins(player) < tech.getCost()) {
            player.sendMessage(Text.translatable("message.basedefense.research.not_enough", tech.getCost())
                    .formatted(Formatting.RED), true);
            return;
        }

        removeCoins(player, tech.getCost());
        state.add(tech);

        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.8f, 1.2f);
        server.getPlayerManager().broadcast(
                Text.translatable("message.basedefense.research.done", player.getDisplayName(), tech.getName())
                        .formatted(Formatting.GREEN), false);
    }
}
