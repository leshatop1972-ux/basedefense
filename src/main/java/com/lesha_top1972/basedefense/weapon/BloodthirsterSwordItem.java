package com.lesha_top1972.basedefense.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Меч кровожада: обычный удар как у меча (урон 8, скорость 1.4). Если держать ЛКМ — берёт меч в обе руки
 * и замахивается (1.5 с); отпустил — удар сверху вниз на 3 блока вперёд и по блоку в стороны
 * (логика — {@link BloodSwordCombat}, анимации и модель в руке — client/weapon).
 */
public class BloodthirsterSwordItem extends SwordItem {

    /** Материал: прочность как у незерита, чинится красной пылью. Урон материала 0 — весь урон в мече. */
    public static final ToolMaterial MATERIAL = new ToolMaterial() {
        @Override
        public int getDurability() {
            return 2031;
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return 8.0f;
        }

        @Override
        public float getAttackDamage() {
            return 0.0f;
        }

        @Override
        public int getMiningLevel() {
            return 4;
        }

        @Override
        public int getEnchantability() {
            return 15;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.ofItems(Items.REDSTONE);
        }
    };

    public BloodthirsterSwordItem(Settings settings) {
        // урон: 1 (рука) + 7 = 8; скорость: 4 − 2.6 = 1.4
        super(MATERIAL, 7, -2.6f, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.basedefense.bloodthirster_sword.tip1").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.basedefense.bloodthirster_sword.tip2",
                (int) BloodSwordCombat.SLAM_DAMAGE).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.basedefense.bloodthirster_sword.tip3").formatted(Formatting.GRAY));
    }
}
