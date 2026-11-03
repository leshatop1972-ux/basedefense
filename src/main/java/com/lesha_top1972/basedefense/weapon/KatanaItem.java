package com.lesha_top1972.basedefense.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Катана: ЛКМ — серия из 3 ударов с рывками ({@link KatanaCombat}, ванильный удар заменён);
 * ПКМ зажать 1.5 с — особая атака «Иссэн» (как лук: пока держишь — зарядка, отпустил — удар; перезарядка 8 с).
 */
public class KatanaItem extends SwordItem {

    public KatanaItem(Settings settings) {
        // в подсказке: урон 1 + 4 = 5 (железный материал даёт +2, поэтому 2), скорость 1.6
        super(ToolMaterials.IRON, 2, -2.4f, settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND || user.getItemCooldownManager().isCoolingDown(this) || KatanaCombat.busy(user)) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            KatanaCombat.release(sp, used);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.basedefense.katana.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.basedefense.katana.tip2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.basedefense.katana.tip3").formatted(Formatting.GRAY));
    }
}
