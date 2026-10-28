package com.lesha_top1972.basedefense.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Катана — серверная часть. Клиент на каждый клик шлёт номер удара серии (0 — сбоку от правого плеча,
 * 1 — с другой стороны, 2 — сверху); рывок вперёд делает сам клиент (движение игрока — клиентское).
 * Сервер проверяет темп и серию, через 3 тика (момент удара в анимации) бьёт всех в секторе перед игроком
 * и рассылает анимацию остальным. После третьего удара — перезарядка 1 с.
 */
public final class KatanaCombat {

    public static final Identifier C2S = new Identifier(Basedefense.MOD_ID, "katana_swing");
    public static final Identifier S2C = new Identifier(Basedefense.MOD_ID, "katana_anim");

    /** Удар длится 0.4 с. */
    public static final int SWING_TICKS = 8;
    /** Следующий удар — не раньше, чем через 5 тиков. */
    public static final int NEXT_MIN = 5;
    /** Серия продолжается, если следующий клик — не позже 20 тиков после начала прошлого удара. */
    public static final int COMBO_WINDOW = 20;
    public static final int HIT_TICK = 3;
    /** После третьего удара: 0.4 с удара + 1 с перезарядки. */
    public static final int COOLDOWN = SWING_TICKS + 20;
    public static final float DAMAGE = 5.0f;
    /** Рывок вперёд у каждого удара (скорость, ≈ 0.5–1 блока). */
    public static final float[] DASH = {0.36f, 0.26f, 0.26f};

    private static final Map<UUID, int[]> LAST = new HashMap<>();     // {тик, номер удара}
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(UUID player, int at, int idx) {
    }

    private KatanaCombat() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int idx = buf.readByte();
            server.execute(() -> swing(server, player, idx));
        });
        ServerTickEvents.END_SERVER_TICK.register(KatanaCombat::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUuid()));
    }

    public static boolean holding(PlayerEntity p) {
        return p.getMainHandStack().isOf(ModItems.KATANA);
    }

    private static void swing(MinecraftServer server, ServerPlayerEntity player, int clientIdx) {
        if (!holding(player) || !player.isAlive() || player.isSpectator()
                || player.getItemCooldownManager().isCoolingDown(ModItems.KATANA)) {
            return;
        }
        int now = server.getTicks();
        int[] last = LAST.get(player.getUuid());
        if (last != null && now - last[0] < NEXT_MIN - 1) {
            return;                                      // слишком часто
        }
        // номер удара — как у клиента, если серия действительно продолжается; иначе серия с начала
        boolean chain = last != null && now - last[0] <= COMBO_WINDOW + 4 && clientIdx == last[1] + 1;
        int idx = clientIdx >= 1 && clientIdx <= 2 && chain ? clientIdx : 0;
        LAST.put(player.getUuid(), new int[]{now, idx});
        if (idx == 2) {
            player.getItemCooldownManager().set(ModItems.KATANA, COOLDOWN);
        }
        PENDING.add(new Pending(player.getUuid(), now + HIT_TICK, idx));
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.8f, idx == 2 ? 1.15f : 1.45f + idx * 0.1f);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                buf.writeByte(idx);
                ServerPlayNetworking.send(other, S2C, buf);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTicks();
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.at <= now) {
                due.add(p);
                return true;
            }
            return false;
        });
        for (Pending p : due) {
            ServerPlayerEntity pl = server.getPlayerManager().getPlayer(p.player);
            if (pl != null && pl.isAlive() && holding(pl)) {
                hit(pl, p.idx);
            }
        }
    }

    /** Удары сбоку — широкий сектор (140°) на 3 блока, сверху — узкий (70°) на 3.2 блока. */
    private static void hit(ServerPlayerEntity player, int idx) {
        ServerWorld w = player.getServerWorld();
        double rad = Math.toRadians(player.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d o = player.getPos();
        double reach = idx == 2 ? 3.2 : 3.0;
        double minCos = Math.cos(Math.toRadians(idx == 2 ? 35 : 70));
        ItemStack stack = player.getMainHandStack();
        Box box = player.getBoundingBox().expand(reach + 1, 1.5, reach + 1);
        int hits = 0;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (BloodSwordCombat.excluded(player, e)) {
                continue;
            }
            Vec3d rel = e.getPos().subtract(o);
            double dy = rel.y;
            Vec3d flat = new Vec3d(rel.x, 0, rel.z);
            double dist = flat.length() - e.getWidth() / 2.0;
            if (dist > reach || dy < -1.5 || dy > 2.5) {
                continue;
            }
            if (flat.lengthSquared() > 0.25 && flat.normalize().dotProduct(fwd) < minCos) {
                continue;
            }
            float dmg = DAMAGE + EnchantmentHelper.getAttackDamage(stack, e.getGroup());
            e.timeUntilRegen = 0;                         // удары серии чаще ванильной «неуязвимости»
            if (!e.damage(w.getDamageSources().playerAttack(player), dmg)) {
                continue;
            }
            hits++;
            e.takeKnockback(0.2, -fwd.x, -fwd.z);
            int fire = EnchantmentHelper.getFireAspect(player);
            if (fire > 0) {
                e.setOnFireFor(fire * 4);
            }
            EnchantmentHelper.onTargetDamaged(player, e);
            w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.6), e.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
        }
        Vec3d c = o.add(fwd.multiply(1.6)).add(0, player.getHeight() * 0.55, 0);
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        if (hits > 0) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.PLAYERS, 0.9f, 1.2f);
            if (!player.isCreative()) {
                stack.damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
            }
        }
        player.addExhaustion(0.1f);
    }
}
