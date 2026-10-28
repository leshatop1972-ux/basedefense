package com.lesha_top1972.basedefense.artifact;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Артефакт: кладётся в один из 5 слотов артефактов (кнопка справа от книги рецептов в инвентаре).
 * Пока лежит в слоте — даёт эффект или способность (см. {@link Artifacts}).
 */
public class ArtifactItem extends Item {

    private final String tipKey;

    public ArtifactItem(Settings settings, String tipKey) {
        super(settings.maxCount(1));
        this.tipKey = tipKey;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.basedefense.artifact").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable(this.tipKey).formatted(Formatting.GRAY));
    }
}
