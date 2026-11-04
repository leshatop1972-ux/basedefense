package com.lesha_top1972.basedefense.clothing;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Рюкзак: надевается в слот рюкзака (панель одежды). Даёт rows рядов по 9 ячеек — открываются вкладкой «Рюкзак»
 * справа от инвентаря. Вещи хранятся в самом рюкзаке (как в шалкере): снял — они остались внутри.
 * Большой рюкзак замедляет на 10%.
 */
public class BackpackItem extends Item {

    private final int rows;
    /** Множитель скорости (−0.1 — минус 10%). */
    private final double speed;

    public BackpackItem(int rows, double speed, Settings settings) {
        super(settings);
        this.rows = rows;
        this.speed = speed;
    }

    public int getRows() {
        return rows;
    }

    public double getSpeed() {
        return speed;
    }

    /** Сколько предметов внутри (для подсказки). */
    public static int countItems(ItemStack stack, int size) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("Items")) {
            return 0;
        }
        DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        int n = 0;
        for (ItemStack s : list) {
            n += s.getCount();
        }
        return n;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.basedefense.backpack.rows", rows, rows * 9).formatted(Formatting.GRAY));
        if (speed != 0) {
            tooltip.add(Text.translatable("tooltip.basedefense.backpack.speed", Math.round(speed * 100))
                    .formatted(Formatting.RED));
        }
        int n = countItems(stack, rows * 9);
        if (n > 0) {
            tooltip.add(Text.translatable("tooltip.basedefense.backpack.items", n).formatted(Formatting.GOLD));
        }
    }
}
