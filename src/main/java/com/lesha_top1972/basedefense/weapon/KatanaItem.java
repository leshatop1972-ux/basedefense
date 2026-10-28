package com.lesha_top1972.basedefense.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Катана: серия из трёх быстрых ударов (сбоку, с другой стороны, сверху), каждый — с рывком вперёд;
 * урон каждого удара 5, после серии — перезарядка 1 с. Удары считает {@link KatanaCombat}
 * (ванильный удар ЛКМ для катаны заменён), в покое катана в ножнах у бедра, рука на рукояти.
 */
public class KatanaItem extends SwordItem {

    public KatanaItem(Settings settings) {
        // в подсказке: урон 1 + 4 = 5 (железный материал даёт +2, поэтому 2), скорость 1.6
        super(ToolMaterials.IRON, 2, -2.4f, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.basedefense.katana.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.basedefense.katana.tip2").formatted(Formatting.GRAY));
    }
}
