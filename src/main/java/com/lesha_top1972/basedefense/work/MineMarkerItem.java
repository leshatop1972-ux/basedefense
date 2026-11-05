package com.lesha_top1972.basedefense.work;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Разметчик шахты: ПКМ по блоку — первый угол, ПКМ по второму — шахта готова (до 32 блоков по стороне).
 * Shift+ПКМ по блоку внутри шахты — убрать шахту. Shift+ПКМ, пока выбран только первый угол, — сбросить его.
 * Рамки шахт видно, когда разметчик в руке.
 */
public class MineMarkerItem extends Item {

    public MineMarkerItem(Settings settings) {
        super(settings);
    }

    @Nullable
    public static BlockPos firstCorner(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n != null && n.contains("Corner") ? NbtHelper.toBlockPos(n.getCompound("Corner")) : null;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (!(ctx.getWorld() instanceof ServerWorld w) || ctx.getPlayer() == null) {
            return ActionResult.SUCCESS;
        }
        ItemStack stack = ctx.getStack();
        BlockPos pos = ctx.getBlockPos();
        MiningZones zones = MiningZones.get(w);
        BlockPos first = firstCorner(stack);
        if (ctx.getPlayer().isSneaking()) {
            if (first != null) {
                stack.removeSubNbt("Corner");
                ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.reset"), true);
                return ActionResult.SUCCESS;
            }
            MiningZones.Zone z = zones.at(pos);
            if (z != null) {
                zones.remove(w, z);
                ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.removed"), true);
                w.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 0.8f, 0.7f);
            }
            return ActionResult.SUCCESS;
        }
        if (first == null) {
            stack.getOrCreateNbt().put("Corner", NbtHelper.fromBlockPos(pos));
            ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.first", pos.getX(), pos.getY(), pos.getZ()), true);
            w.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 0.8f, 1.2f);
            return ActionResult.SUCCESS;
        }
        int dx = Math.abs(first.getX() - pos.getX()) + 1;
        int dy = Math.abs(first.getY() - pos.getY()) + 1;
        int dz = Math.abs(first.getZ() - pos.getZ()) + 1;
        if (dx > MiningZones.MAX_SIDE || dy > MiningZones.MAX_SIDE || dz > MiningZones.MAX_SIDE) {
            ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.too_big", MiningZones.MAX_SIDE)
                    .formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        stack.removeSubNbt("Corner");
        if (zones.add(w, first, pos) == null) {
            ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.too_many", MiningZones.MAX_ZONES)
                    .formatted(Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        ctx.getPlayer().sendMessage(Text.translatable("message.basedefense.mine.created", dx, dy, dz)
                .formatted(Formatting.GREEN), true);
        w.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.basedefense.mine_marker.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.basedefense.mine_marker.2").formatted(Formatting.GRAY));
        BlockPos first = firstCorner(stack);
        if (first != null) {
            tooltip.add(Text.translatable("message.basedefense.mine.first", first.getX(), first.getY(), first.getZ())
                    .formatted(Formatting.YELLOW));
        }
    }
}
