package com.lesha_top1972.basedefense.military;

import com.lesha_top1972.basedefense.item.SummonsItem;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ЛКМ по блоку с Повесткой (выбран отряд) — точка обороны отряда. Блок при этом не ломается.
 * Работает на сервере; на клиенте только отменяет ломание и пересылает удар на сервер.
 */
public final class GuardEvents {

    /** Защита от повторов, пока кнопка зажата (тики мира последнего приказа). */
    private static final Map<UUID, Long> LAST_ORDER = new WeakHashMap<>();

    private GuardEvents() {
    }

    public static void init() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof SummonsItem)) {
                return ActionResult.PASS;
            }
            int squad = SummonsItem.getSquad(stack);
            if (world.isClient) {
                if (squad < 0) {
                    player.sendMessage(Text.translatable("message.basedefense.squad.pick_first")
                            .formatted(Formatting.GOLD), true);
                    return ActionResult.FAIL;
                }
                return ActionResult.SUCCESS;   // сервер получит удар и отдаст приказ
            }
            if (squad >= 0 && player instanceof ServerPlayerEntity sp) {
                long now = world.getTime();
                Long last = LAST_ORDER.get(sp.getUuid());
                if (last == null || now - last > 8 || now < last) {
                    LAST_ORDER.put(sp.getUuid(), now);
                    order(sp, (ServerWorld) world, squad, pos, direction);
                }
            }
            return ActionResult.FAIL;
        });
    }

    private static void order(ServerPlayerEntity player, ServerWorld world, int squad, BlockPos clicked, Direction side) {
        SquadState state = SquadState.get(player.getServer());
        String roman = SquadState.roman(squad);
        if (state.squad(squad).members().isEmpty()) {
            player.sendMessage(Text.translatable("message.basedefense.squad.empty", roman).formatted(Formatting.RED), true);
            return;
        }
        BlockPos point = groundAt(world, side.getAxis().isHorizontal() ? clicked.offset(side) : clicked.up());
        state.setPoint(squad, point, world.getRegistryKey().getValue().toString());
        player.sendMessage(Text.translatable("message.basedefense.squad.order", roman,
                point.getX(), point.getY(), point.getZ()).formatted(Formatting.GREEN), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS,
                0.7f, 0.8f);
        // Кольцо-метка на месте сбора (радиус «поводка»)
        double cx = point.getX() + 0.5;
        double cz = point.getZ() + 0.5;
        for (int i = 0; i < 40; i++) {
            float a = i / 40.0f * MathHelper.TAU;
            world.spawnParticles(player, ParticleTypes.END_ROD, true, cx + MathHelper.cos(a) * 8.0,
                    point.getY() + 0.2, cz + MathHelper.sin(a) * 8.0, 1, 0, 0.05, 0, 0.0);
        }
        world.spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, true, cx, point.getY() + 0.5, cz,
                12, 0.4, 0.4, 0.4, 0.0);
    }

    /** Ищем, где стоять: опускаемся до земли (не больше 4 блоков). */
    private static BlockPos groundAt(World world, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 4; i++) {
            if (!world.getBlockState(p.down()).getCollisionShape(world, p.down()).isEmpty()) {
                return p;
            }
            p = p.down();
        }
        return pos;
    }
}
