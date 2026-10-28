package com.lesha_top1972.basedefense.artifact;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * Артефакты: 5 слотов у игрока, проверка «надет ли артефакт», способности.
 * <ul>
 *   <li>Амулет рывка — клавиша R: быстрый плавный рывок на 4 блока в сторону движения (или вперёд),
 *       перезарядка 2 с. Двигает игрока клиент (движение игрока — клиентское), сервер проверяет амулет
 *       и перезарядку, ставит перезарядку на предмет, играет звук и рассылает анимацию остальным.</li>
 * </ul>
 * Артефакты не выпадают при смерти — остаются в слотах.
 */
public final class Artifacts {

    public static final int SLOTS = 5;
    public static final Identifier DASH_C2S = new Identifier(Basedefense.MOD_ID, "artifact_dash");
    public static final Identifier DASH_S2C = new Identifier(Basedefense.MOD_ID, "artifact_dash_anim");

    /** Рывок: 5 тиков по 0.8 блока = 4 блока. */
    public static final int DASH_TICKS = 5;
    public static final float DASH_SPEED = 0.8f;
    public static final int DASH_COOLDOWN = 40;

    private Artifacts() {
    }

    public static SimpleInventory of(PlayerEntity p) {
        return ((ArtifactHolder) p).basedefense$artifacts();
    }

    public static boolean has(PlayerEntity p, Item item) {
        SimpleInventory inv = of(p);
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) {
                return true;
            }
        }
        return false;
    }

    public static void init() {
        // после смерти и возвращения из Энда артефакты остаются
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SimpleInventory from = of(oldPlayer);
            SimpleInventory to = of(newPlayer);
            for (int i = 0; i < from.size(); i++) {
                to.setStack(i, from.getStack(i).copy());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DASH_C2S, (server, player, handler, buf, sender) -> {
            float f = buf.readFloat();
            float s = buf.readFloat();
            server.execute(() -> dash(player, f, s));
        });
    }

    private static void dash(ServerPlayerEntity player, float f, float s) {
        if (!player.isAlive() || player.isSpectator() || !has(player, ModItems.DASH_AMULET)
                || player.getItemCooldownManager().isCoolingDown(ModItems.DASH_AMULET)) {
            return;
        }
        player.getItemCooldownManager().set(ModItems.DASH_AMULET, DASH_COOLDOWN);
        player.fallDistance = 0;
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                SoundCategory.PLAYERS, 0.45f, 1.9f);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.5f, 0.6f);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                buf.writeFloat(f);
                buf.writeFloat(s);
                ServerPlayNetworking.send(other, DASH_S2C, buf);
            }
        }
    }
}
