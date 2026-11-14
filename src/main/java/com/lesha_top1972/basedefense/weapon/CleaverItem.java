package com.lesha_top1972.basedefense.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Тесак Громового тигра — клинок с барабаном на 6 патронов и соплами по обуху ({@link CleaverCombat}).
 * ЛКМ — серия из 3 ударов, ПКМ — «взрывная» версия очередного удара серии (тратит патрон),
 * зажать ЛКМ — «Тигриный выпад», зажать ПКМ — «Тесак в узел», клавиша перезарядки — новая обойма.
 * Патроны лежат в самом предмете (NBT Rounds).
 */
public class CleaverItem extends SwordItem {

    public static final int MAX_ROUNDS = 6;
    private static final String ROUNDS = "Rounds";

    public CleaverItem(Settings settings) {
        // в подсказке урон = 1 + 3 (алмаз) + 2 = 6 — как первый удар серии
        super(ToolMaterials.DIAMOND, 2, -2.6f, settings.maxDamage(1500));
    }

    /** Патронов в барабане (у нового тесака барабан полон). */
    public static int rounds(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.contains(ROUNDS) ? MAX_ROUNDS : Math.max(0, Math.min(MAX_ROUNDS, nbt.getInt(ROUNDS)));
    }

    public static void setRounds(ItemStack stack, int n) {
        stack.getOrCreateNbt().putInt(ROUNDS, Math.max(0, Math.min(MAX_ROUNDS, n)));
    }

    /** ПКМ обрабатывает клиент (взрывной удар / зарядка) — здесь только «съесть» нажатие, чтобы не сработала вторая рука. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        return hand == Hand.MAIN_HAND ? TypedActionResult.consume(stack) : TypedActionResult.pass(stack);
    }

    /** Смена числа патронов и прочности не «передёргивает» предмет в руке. */
    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.basedefense.tiger_cleaver.rounds", rounds(stack), MAX_ROUNDS).formatted(Formatting.GOLD));
        for (int i = 1; i <= 6; i++) {
            tooltip.add(Text.translatable("item.basedefense.tiger_cleaver.tip" + i).formatted(Formatting.GRAY));
        }
    }
}
