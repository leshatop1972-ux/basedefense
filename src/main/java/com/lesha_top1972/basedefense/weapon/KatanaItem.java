package com.lesha_top1972.basedefense.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Катана (все виды — {@link KatanaVariant}): ЛКМ — серия из 3 ударов с рывками, зажать ЛКМ 1.5 с — «Иссэн»
 * ({@link KatanaCombat}, ванильный удар заменён); ПКМ держать — блок (−65% урона спереди).
 */
public class KatanaItem extends SwordItem {

    private final KatanaVariant variant;

    public KatanaItem(KatanaVariant variant, Settings settings) {
        // в подсказке урон = 1 + 2 (железо) + attackDamage: 4 / 5 / 6 — как первый удар серии; скорость 1.6
        super(ToolMaterials.IRON, 1 + (int) variant.comboBonus, -2.4f, settings.maxDamage(variant.durability));
        this.variant = variant;
    }

    public KatanaVariant variant() {
        return this.variant;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    /** ПКМ — блок, пока держат. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND || KatanaCombat.busy(user)) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String k = "item.basedefense." + this.variant.model.replace("katana_", "") + (this.variant == KatanaVariant.BASE ? "" : "_katana");
        if (this.variant == KatanaVariant.BASE) {
            k = "item.basedefense.katana";
        }
        tooltip.add(Text.translatable(k + ".tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(k + ".tip2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(k + ".tip3").formatted(Formatting.GRAY));
    }
}
