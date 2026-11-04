package com.lesha_top1972.basedefense.clothing;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Одежда: кладётся только в свой слот одежды ({@link ClothingType}). Сдвигает комфортную температуру персонажа:
 * cold — на сколько градусов ниже опускается нижняя граница комфорта (греет), heat — на сколько ниже верхняя
 * (в одежде раньше становится жарко; отрицательное — наоборот, спасает от жары: соломенная шляпа, сандалии).
 * Без одежды комфорт 15…27 °C, вещи складываются (см. Temperature#comfort).
 */
public class ClothingItem extends Item {

    private final ClothingType type;
    private final Warmth warmth;
    private final float cold;
    private final float heat;

    public ClothingItem(ClothingType type, Warmth warmth, float cold, float heat, Settings settings) {
        super(settings);
        this.type = type;
        this.warmth = warmth;
        this.cold = cold;
        this.heat = heat;
    }

    public ClothingType getType() {
        return type;
    }

    public Warmth getWarmth() {
        return warmth;
    }

    public float getCold() {
        return cold;
    }

    public float getHeat() {
        return heat;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.basedefense.clothing." + type.id).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.basedefense.warmth." + warmth.name().toLowerCase(Locale.ROOT))
                .formatted(warmth == Warmth.WARM ? Formatting.GOLD : Formatting.GRAY));
        if (cold != 0) {
            tooltip.add(Text.translatable("tooltip.basedefense.clothing.cold", fmt(cold)).formatted(Formatting.AQUA));
        }
        if (heat != 0) {
            tooltip.add(Text.translatable(heat > 0 ? "tooltip.basedefense.clothing.heat" : "tooltip.basedefense.clothing.shade",
                    fmt(Math.abs(heat))).formatted(heat > 0 ? Formatting.RED : Formatting.YELLOW));
        }
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
