package com.lesha_top1972.basedefense.item;

import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.military.SquadState;
import com.lesha_top1972.basedefense.screen.SquadScreenHandler;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Повестка — управление отрядами стражников.
 * - ПКМ — меню отрядов (кто в каком отряде, выбор отряда для Повестки, отзыв с точки).
 * - ЛКМ по блоку с выбранным отрядом — отряд бежит туда и обороняет точку (см. military/GuardEvents).
 * - ПКМ по стражнику — записать его в выбранный отряд или выписать.
 * Выбранный отряд хранится в NBT предмета ("Squad", 0..2; нет — не выбран).
 */
public class SummonsItem extends Item {

    public SummonsItem(Settings settings) {
        super(settings);
    }

    /** Выбранный отряд (−1 — не выбран). */
    public static int getSquad(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains("Squad") ? stack.getNbt().getInt("Squad") : -1;
    }

    public static void setSquad(ItemStack stack, int squad) {
        if (squad < 0 || squad >= SquadState.SQUADS) {
            stack.removeSubNbt("Squad");
        } else {
            stack.getOrCreateNbt().putInt("Squad", squad);
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            SquadScreenHandler.open(sp, hand);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player != null && !context.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            SquadScreenHandler.open(sp, context.getHand());
        }
        return ActionResult.success(context.getWorld().isClient);
    }

    /** ПКМ по стражнику: в выбранный отряд (если он уже там — выписать). */
    public static void toggleMember(ServerPlayerEntity player, ItemStack stack, GuardEntity guard) {
        int squad = getSquad(stack);
        if (squad < 0) {
            player.sendMessage(Text.translatable("message.basedefense.squad.pick_first").formatted(Formatting.GOLD), true);
            return;
        }
        SquadState state = SquadState.get(player.getServer());
        String roman = SquadState.roman(squad);
        if (state.squadOf(guard.getUuid()) == squad) {
            state.remove(guard.getUuid());
            player.sendMessage(Text.translatable("message.basedefense.squad.left", guard.getDisplayName(), roman)
                    .formatted(Formatting.YELLOW), true);
        } else if (state.assign(guard.getUuid(), guard.getName().getString(), squad)) {
            player.sendMessage(Text.translatable("message.basedefense.squad.joined", guard.getDisplayName(), roman)
                    .formatted(Formatting.GREEN), true);
            guard.playSound(SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.1f);
        } else {
            player.sendMessage(Text.translatable("message.basedefense.squad.full", roman, SquadState.MAX_MEMBERS)
                    .formatted(Formatting.RED), true);
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                    SoundCategory.PLAYERS, 0.6f, 1.0f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int squad = getSquad(stack);
        if (squad >= 0) {
            tooltip.add(Text.translatable("item.basedefense.summons.selected", SquadState.roman(squad))
                    .formatted(Formatting.AQUA));
        } else {
            tooltip.add(Text.translatable("item.basedefense.summons.none").formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable("item.basedefense.summons.tip1").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("item.basedefense.summons.tip2").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("item.basedefense.summons.tip3").formatted(Formatting.DARK_GRAY));
    }
}
