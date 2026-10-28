package com.lesha_top1972.basedefense.artifact;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Артефакты: 5 слотов у игрока, проверка «надет ли артефакт», способности.
 * <ul>
 *   <li>Амулет рывка — клавиша R: быстрый плавный рывок на 4 блока в сторону движения (или вперёд),
 *       перезарядка 2 с. Двигает игрока клиент (движение игрока — клиентское), сервер проверяет амулет
 *       и перезарядку, ставит перезарядку на предмет, играет звук и рассылает анимацию остальным.</li>
 * </ul>
 *   <li>Амулет взлёта — клавиша Y: подброс вверх на 3.5 блока, до 3 раз подряд (и в воздухе), потом
 *       перезарядка 6 с; после взлёта до приземления урона от падения нет.</li>
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

    public static final Identifier LEAP_C2S = new Identifier(Basedefense.MOD_ID, "artifact_leap");
    public static final Identifier LEAP_S2C = new Identifier(Basedefense.MOD_ID, "artifact_leap_anim");
    /** Взлёт: скорость вверх 0.745 — подъём ≈ 3.5 блока; 3 взлёта подряд, потом 6 с перезарядки. */
    public static final float LEAP_SPEED = 0.745f;
    public static final int LEAP_CHARGES = 3;
    public static final int LEAP_COOLDOWN = 120;

    /** Игрок → {сколько взлётов подряд, тик последнего}. */
    private static final Map<UUID, int[]> LEAP_USED = new HashMap<>();
    /** После взлёта до приземления урона от падения нет. */
    private static final Set<UUID> AIRBORNE = new HashSet<>();

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
        ServerPlayNetworking.registerGlobalReceiver(LEAP_C2S, (server, player, handler, buf, sender) ->
                server.execute(() -> leap(player, server.getTicks())));
        ServerTickEvents.END_SERVER_TICK.register(server -> AIRBORNE.removeIf(id -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || p.isOnGround() || p.isTouchingWater() || p.isClimbing()) {
                return true;
            }
            p.fallDistance = 0f;
            return false;
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LEAP_USED.remove(handler.player.getUuid());
            AIRBORNE.remove(handler.player.getUuid());
        });
    }

    private static void leap(ServerPlayerEntity player, int now) {
        if (!player.isAlive() || player.isSpectator() || !has(player, ModItems.LEAP_AMULET)
                || player.getItemCooldownManager().isCoolingDown(ModItems.LEAP_AMULET)) {
            return;
        }
        int[] u = LEAP_USED.computeIfAbsent(player.getUuid(), k -> new int[]{0, -1000});
        if (now - u[1] > LEAP_COOLDOWN) {
            u[0] = 0;
        }
        u[0]++;
        u[1] = now;
        if (u[0] >= LEAP_CHARGES) {
            u[0] = 0;
            player.getItemCooldownManager().set(ModItems.LEAP_AMULET, LEAP_COOLDOWN);
        }
        player.fallDistance = 0;
        AIRBORNE.add(player.getUuid());
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                SoundCategory.PLAYERS, 0.5f, 1.5f);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                ServerPlayNetworking.send(other, LEAP_S2C, buf);
            }
        }
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
