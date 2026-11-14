package com.lesha_top1972.basedefense.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.fx.Shake;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lesha_top1972.basedefense.weapon.CleaverTimings.*;

/**
 * Тесак Громового тигра — сервер: урон, патроны, «Внутренняя сила», дрожь, эффекты и звуки для остальных, рассылка
 * анимаций. Движение своего игрока (шаги, рывок, подскок) считает клиент. Тайминги — {@link CleaverTimings}
 * (пишет tools/cleaver_gen.py вместе с анимациями).
 * <p>
 * <b>Серия</b> — 3 шага; каждый шаг можно сделать обычным (ЛКМ) или взрывным (ПКМ, −1 патрон):
 * <ol>
 *   <li>наискось справа‑сверху / взрывной широкий мах (дальше и шире, поджог);</li>
 *   <li>наотмашь слева направо / взрывной восходящий разрез (подбрасывает);</li>
 *   <li>двумя руками сверху / то же с выстрелом в землю — взрыв по площади.</li>
 * </ol>
 * Обычные удары вешают на цель «Дрожь» (до 10); взрывной удар её подрывает: урон за каждый стак и замедление.
 * Попадания копят «Внутреннюю силу» (до 25, +1% урона за единицу, гаснет без боя).
 * <p>
 * <b>Зажать ЛКМ 1 с — «Тигриный выпад»</b> (2 патрона): рывок с уколом сквозь врагов и взрыв на острие.
 * <b>Зажать ПКМ 1.2 с — «Тесак в узел»</b> (вся сила, от 10, и все патроны): три взрывных разреза вокруг,
 * удар в землю и огненная волна; урон растёт от потраченной силы.
 * <b>Перезарядка</b> (клавиша) — 1.8 с, патроны бесплатные.
 */
public final class CleaverCombat {

    public static final Identifier C2S = new Identifier(Basedefense.MOD_ID, "cleaver_act");
    public static final Identifier S2C = new Identifier(Basedefense.MOD_ID, "cleaver_anim");
    /** Сервер → игроку: «Внутренняя сила». */
    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "cleaver_inner");

    // ---- действия клиента (C2S: байт действия, байт параметра) ----
    public static final int ACT_SWING = 0;          // параметр: шаг 0..2, +4 — взрывной
    public static final int ACT_CHARGE = 1;         // параметр: 1 — выпад, 2 — узел
    public static final int ACT_CANCEL = 2;
    public static final int ACT_RELEASE = 3;
    public static final int ACT_RELOAD = 4;

    // ---- анимации для остальных игроков (S2C): 0..8 — клипы CleaverKeys.TP_CLIPS ----
    public static final int ANIM_N = 0;
    public static final int ANIM_B = 3;
    public static final int ANIM_S1 = 6;
    public static final int ANIM_S2 = 7;
    public static final int ANIM_RELOAD = 8;
    public static final int ANIM_CHARGE1 = 10;
    public static final int ANIM_CHARGE2 = 11;
    public static final int ANIM_CHARGE_END = 12;

    // ---- серия ----
    /** Урон шагов: обычный и взрывной. */
    public static final float[] N_DAMAGE = {6f, 6f, 9f};
    public static final float[] B_DAMAGE = {9f, 9f, 12f};
    /** Следующий удар — не раньше (тики от начала прошлого): после обычного / после взрывного. */
    public static final int NEXT_MIN_N = 9;
    public static final int NEXT_MIN_B = 11;
    /** Серия продолжается, если следующий клик — не позже (тики от начала прошлого удара). */
    public static final int COMBO_WINDOW = 18;
    /** Отдых после третьего шага (тики от его начала). */
    public static final int REST_N = 24;
    public static final int REST_B = 29;
    /** Шаг вперёд у ударов (скорость). */
    public static final float[] DASH_N = {0.30f, 0.30f, 0.36f};
    public static final float[] DASH_B = {0.46f, 0.40f, 0.30f};
    static final double[] REACH_N = {3.6, 3.6, 3.9};
    static final double[] HALF_N = {70, 75, 42};
    static final double[] REACH_B = {4.4, 3.9, 0};
    static final double[] HALF_B = {88, 62, 0};
    /** Взрыв третьего шага: где (блоков впереди) и радиус. */
    static final double SMASH_AHEAD = 2.3;
    static final double SMASH_RADIUS = 3.2;
    static final int BLAST_FIRE = 3;

    // ---- «Тигриный выпад» ----
    public static final int HOLD_START = 5;
    public static final int AUTO_RELEASE = 24;                 // держат дольше зарядки на столько тиков — бьёт сам
    public static final int S1_ROUNDS = 2;
    public static final int S1_DASH_TICKS = 5;
    public static final double S1_DASH_SPEED = 1.3;
    public static final float S1_PATH = 8f;
    public static final float S1_BLAST_DAMAGE = 12f;
    static final double S1_RADIUS = 3.5;
    public static final int S1_COOLDOWN = 200;

    // ---- «Тесак в узел» ----
    public static final int S2_INNER_MIN = 10;
    public static final float S2_CUT = 7f;
    public static final float S2_SMASH_DAMAGE = 12f;
    public static final float S2_WAVE = 6f;
    /** +урона за каждую потраченную единицу силы. */
    public static final float S2_PER_INNER = 0.03f;
    static final double S2_REACH = 4.6;
    static final double S2_SMASH_RADIUS = 4.0;
    static final double WAVE_SPEED = 0.5;
    static final double WAVE_MAX = 8.0;
    public static final int S2_COOLDOWN = 500;
    public static final float[] S2_STEP = {0.38f, 0.34f, 0.34f};

    // ---- внутренняя сила и дрожь ----
    public static final int INNER_MAX = 25;
    public static final float INNER_BONUS = 0.01f;
    static final int INNER_HOLD = 160;                         // столько тиков после пополнения не гаснет
    static final int INNER_DECAY = 60;                         // потом −1 раз в столько тиков
    static final int TREMOR_MAX = 10;
    static final int TREMOR_TICKS = 160;
    static final float BURST_PER = 1.0f;

    private static final UUID SLOW_ID = UUID.fromString("3b7e2c1a-9d4f-4e6b-8a5c-7f1e2d3c4b5a");

    private static final Map<UUID, int[]> LAST = new HashMap<>();          // {тик, шаг, взрывной 0/1}
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Special> SPECIALS = new HashMap<>();
    private static final Map<UUID, int[]> CHARGES = new HashMap<>();       // {тик начала, вид}
    private static final Map<UUID, Integer> RELOADS = new HashMap<>();     // тик начала
    private static final Map<UUID, Integer> S1_READY = new HashMap<>();
    private static final Map<UUID, Integer> S2_READY = new HashMap<>();
    private static final Map<UUID, int[]> INNER = new HashMap<>();         // {сила, тик следующего распада}

    private record Pending(UUID player, int at, int step, boolean blast) {
    }

    private static final class Special {
        final int kind;
        final int start;
        float power = 1f;
        Vec3d last;
        final Set<Integer> hit = new HashSet<>();
        Vec3d waveCenter;
        double waveRadius;
        final Set<Integer> waveHit = new HashSet<>();

        Special(int kind, int start, Vec3d pos) {
            this.kind = kind;
            this.start = start;
            this.last = pos;
        }
    }

    private CleaverCombat() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int act = buf.readByte();
            int arg = buf.readByte();
            server.execute(() -> act(server, player, act, arg));
        });
        ServerTickEvents.END_SERVER_TICK.register(CleaverCombat::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            LAST.remove(id);
            SPECIALS.remove(id);
            CHARGES.remove(id);
            RELOADS.remove(id);
            S1_READY.remove(id);
            S2_READY.remove(id);
            INNER.remove(id);
        });
    }

    public static boolean holding(PlayerEntity p) {
        return p.getMainHandStack().getItem() instanceof CleaverItem;
    }

    /** Занят особой атакой, зарядкой или перезарядкой (сервер). */
    public static boolean busy(PlayerEntity p) {
        UUID id = p.getUuid();
        return SPECIALS.containsKey(id) || CHARGES.containsKey(id) || RELOADS.containsKey(id);
    }

    private static void act(MinecraftServer server, ServerPlayerEntity player, int act, int arg) {
        if (!holding(player) || !player.isAlive() || player.isSpectator()) {
            cancelCharge(player, false);
            return;
        }
        switch (act) {
            case ACT_SWING -> swing(server, player, arg & 3, (arg & 4) != 0);
            case ACT_CHARGE -> charge(server, player, arg);
            case ACT_CANCEL -> cancelCharge(player, true);
            case ACT_RELEASE -> release(server, player);
            case ACT_RELOAD -> reload(server, player);
            default -> {
            }
        }
    }

    // =====================================================================
    // Серия
    // =====================================================================
    private static void swing(MinecraftServer server, ServerPlayerEntity player, int clientStep, boolean blast) {
        if (busy(player)) {
            return;
        }
        int now = server.getTicks();
        int[] last = LAST.get(player.getUuid());
        if (last != null) {
            int age = now - last[0];
            int need = last[1] == 2 ? (last[2] == 1 ? REST_B : REST_N) : (last[2] == 1 ? NEXT_MIN_B : NEXT_MIN_N);
            if (age < need - 2) {
                return;                                  // слишком часто
            }
        }
        ItemStack stack = player.getMainHandStack();
        if (blast) {
            int r = CleaverItem.rounds(stack);
            if (r <= 0) {
                return;
            }
            CleaverItem.setRounds(stack, r - 1);
        }
        boolean chain = last != null && last[1] < 2 && now - last[0] <= COMBO_WINDOW + 4 && clientStep == last[1] + 1;
        int step = clientStep >= 1 && clientStep <= 2 && chain ? clientStep : 0;
        LAST.put(player.getUuid(), new int[]{now, step, blast ? 1 : 0});
        PENDING.add(new Pending(player.getUuid(), now + (blast ? B_HIT[step] : N_HIT[step]), step, blast));
        sound(player, true, step == 2 || blast ? ModSounds.TIGER_SWING_HEAVY : ModSounds.TIGER_SWING, 0.9f, 0.95f + step * 0.04f);
        sendAnim(player, (blast ? ANIM_B : ANIM_N) + step);
    }

    static void sendAnim(ServerPlayerEntity player, int code) {
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                buf.writeByte(code);
                ServerPlayNetworking.send(other, S2C, buf);
            }
        }
    }

    /** Звук: self = true — всем, кроме самого игрока (он слышит свой из клипа анимации). */
    private static void sound(ServerPlayerEntity player, boolean exceptSelf, SoundEvent ev, float vol, float pitch) {
        player.getServerWorld().playSound(exceptSelf ? player : null, player.getX(), player.getY(), player.getZ(), ev,
                SoundCategory.PLAYERS, vol, pitch);
    }

    private static Vec3d forward(PlayerEntity p) {
        double rad = Math.toRadians(p.getYaw());
        return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
    }

    /** Цели в секторе перед игроком (то же считает клиент для «стоп‑кадра»). */
    public static List<LivingEntity> targets(PlayerEntity player, World w, double reach, double halfAngle) {
        Vec3d fwd = forward(player);
        Vec3d o = player.getPos();
        double minCos = Math.cos(Math.toRadians(halfAngle));
        Box box = player.getBoundingBox().expand(reach + 1, 1.5, reach + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (BloodSwordCombat.excluded(player, e)) {
                continue;
            }
            Vec3d rel = e.getPos().subtract(o);
            Vec3d flat = new Vec3d(rel.x, 0, rel.z);
            double dist = flat.length() - e.getWidth() / 2.0;
            if (dist > reach || rel.y < -1.8 || rel.y > 2.8) {
                continue;
            }
            if (flat.lengthSquared() > 0.25 && flat.normalize().dotProduct(fwd) < minCos) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** Цели удара шага серии (для клиента). */
    public static List<LivingEntity> stepTargets(PlayerEntity player, World w, int step, boolean blast) {
        if (blast && step == 2) {
            return around(player, w, smashPoint(player), SMASH_RADIUS);
        }
        return targets(player, w, blast ? REACH_B[step] : REACH_N[step], blast ? HALF_B[step] : HALF_N[step]);
    }

    static List<LivingEntity> around(PlayerEntity player, World w, Vec3d c, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Box box = new Box(c, c).expand(radius + 1, 2.5, radius + 1);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (BloodSwordCombat.excluded(player, e)) {
                continue;
            }
            double d = Math.sqrt(e.squaredDistanceTo(c.x, e.getY(), c.z)) - e.getWidth() / 2.0;
            if (d <= radius && Math.abs(e.getY() - c.y) < 3.0) {
                out.add(e);
            }
        }
        return out;
    }

    /** Точка удара в землю: впереди игрока, на поверхности под ней. */
    static Vec3d smashPoint(PlayerEntity player) {
        Vec3d p = player.getPos().add(forward(player).multiply(SMASH_AHEAD));
        World w = player.getWorld();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 1; dy >= -4; dy--) {
            m.set(Math.floor(p.x), Math.floor(p.y) + dy, Math.floor(p.z));
            if (!w.getBlockState(m).getCollisionShape(w, m).isEmpty()) {
                return new Vec3d(p.x, m.getY() + 1.0, p.z);
            }
        }
        return p;
    }

    private static float bonus(ServerPlayerEntity player) {
        return 1f + inner(player) * INNER_BONUS;
    }

    /** Удар по одной цели: урон (с чарами и силой), отброс. @return попал. */
    private static boolean strike(ServerPlayerEntity player, LivingEntity e, float base, double knock, Vec3d from, int fire) {
        ServerWorld w = player.getServerWorld();
        ItemStack stack = player.getMainHandStack();
        float dmg = (base + EnchantmentHelper.getAttackDamage(stack, e.getGroup())) * bonus(player);
        e.timeUntilRegen = 0;                             // удары серии чаще ванильной «неуязвимости»
        if (!e.damage(w.getDamageSources().playerAttack(player), dmg)) {
            return false;
        }
        if (knock > 0) {
            e.takeKnockback(knock, from.x - e.getX(), from.z - e.getZ());
        }
        int aspect = EnchantmentHelper.getFireAspect(player);
        if (fire > 0 || aspect > 0) {
            e.setOnFireFor(Math.max(fire, aspect * 4));
        }
        EnchantmentHelper.onTargetDamaged(player, e);
        return true;
    }

    private static void wear(ServerPlayerEntity player, int n) {
        if (!player.isCreative()) {
            player.getMainHandStack().damage(n, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        }
    }

    private static void hit(ServerPlayerEntity player, int step, boolean blast) {
        ServerWorld w = player.getServerWorld();
        Vec3d fwd = forward(player);
        Vec3d chest = player.getPos().add(0, player.getHeight() * 0.62, 0);
        int hits = 0;
        if (!blast) {
            for (LivingEntity e : targets(player, w, REACH_N[step], HALF_N[step])) {
                if (strike(player, e, N_DAMAGE[step], step == 2 ? 0.5 : 0.25, player.getPos(), 0)) {
                    hits++;
                    addTremor(e, step == 2 ? 2 : 1, player);
                }
            }
            Fx.tigerSlash(w, chest, player.getYaw(), REACH_N[step], step == 2 ? 1.3 : (step == 0 ? 0.5 : 0.0), step == 1 ? -1 : 1, false);
            if (step == 2) {
                Vec3d g = smashPoint(player);
                Fx.impact(w, g.add(0, 0.1, 0), 0.8f, false);
                w.playSound(null, g.x, g.y, g.z, ModSounds.TIGER_PLANT, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            if (hits > 0) {
                addInner(player, 1);
            }
        } else if (step < 2) {
            // выстрел из сопел разгоняет клинок: пламя назад, огненная дуга впереди
            Fx.tigerShot(w, chest.add(fwd.multiply(0.6)), fwd.multiply(-1).add(0, 0.3, 0), false);
            Fx.tigerSlash(w, chest, player.getYaw(), REACH_B[step], step == 1 ? -0.9 : 0.0, step == 1 ? -1 : 1, true);
            sound(player, true, ModSounds.TIGER_SHOT, 1.0f, 1.0f);
            for (LivingEntity e : targets(player, w, REACH_B[step], HALF_B[step])) {
                if (strike(player, e, B_DAMAGE[step], step == 0 ? 0.7 : 0.3, player.getPos(), BLAST_FIRE)) {
                    hits++;
                    if (step == 1) {                      // восходящий — подбрасывает
                        e.addVelocity(0, 0.55, 0);
                        e.velocityModified = true;
                    }
                    burst(e, player);
                }
            }
            if (hits > 0) {
                addInner(player, 2);
            }
        } else {
            // удар в землю с выстрелом: взрыв по площади
            Vec3d g = smashPoint(player);
            hits = explode(player, g, SMASH_RADIUS, B_DAMAGE[2], BLAST_FIRE, 0.9f, false);
            if (hits > 0) {
                addInner(player, 2);
            }
        }
        if (hits > 0) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TIGER_HIT, SoundCategory.PLAYERS,
                    0.9f, 0.95f + w.random.nextFloat() * 0.15f);
            wear(player, 1);
        }
        player.addExhaustion(blast ? 0.2f : 0.1f);
    }

    /** Взрыв патрона в точке c (без разрушения блоков): урон падает к краю до половины, поджог, подрыв дрожи. */
    private static int explode(ServerPlayerEntity player, Vec3d c, double radius, float damage, int fire, float shake, boolean savage) {
        ServerWorld w = player.getServerWorld();
        Fx.tigerBlast(w, c.add(0, 0.2, 0), radius, savage);
        Fx.tigerFire(w, c, radius * 0.6);
        Fx.impact(w, c.add(0, 0.1, 0), 1.2f, true);
        Shake.send(w, c, 14.0, shake, 8);
        w.playSound(null, c.x, c.y, c.z, ModSounds.TIGER_SMASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        w.playSound(player, c.x, c.y, c.z, ModSounds.TIGER_SHOT, SoundCategory.PLAYERS, 1.0f, 0.9f);
        int hits = 0;
        for (LivingEntity e : around(player, w, c, radius)) {
            double d = Math.sqrt(e.squaredDistanceTo(c.x, e.getY(), c.z));
            float k = (float) (1.0 - 0.5 * Math.min(1.0, d / radius));
            if (strike(player, e, damage * k, 0.8, c, fire)) {
                hits++;
                e.addVelocity(0, 0.25, 0);
                e.velocityModified = true;
                burst(e, player);
            }
        }
        return hits;
    }

    // =====================================================================
    // Дрожь (на мобах — тот же эффект, что тигр вешает на игроков)
    // =====================================================================
    static int tremorOf(LivingEntity e) {
        StatusEffectInstance cur = e.getStatusEffect(ModEffects.TREMOR);
        return cur == null ? 0 : cur.getAmplifier() + 1;
    }

    static void addTremor(LivingEntity e, int n, LivingEntity source) {
        if (!e.isAlive()) {
            return;
        }
        int s = Math.min(TREMOR_MAX, tremorOf(e) + n);
        e.removeStatusEffect(ModEffects.TREMOR);
        e.addStatusEffect(new StatusEffectInstance(ModEffects.TREMOR, TREMOR_TICKS, s - 1, false, true, true), source);
    }

    /** Подрыв дрожи: урон за каждый стак, короткое замедление, стаки сгорают. */
    static void burst(LivingEntity e, ServerPlayerEntity player) {
        int s = tremorOf(e);
        if (s <= 0 || !e.isAlive()) {
            return;
        }
        ServerWorld w = player.getServerWorld();
        e.removeStatusEffect(ModEffects.TREMOR);
        e.timeUntilRegen = 0;
        e.damage(w.getDamageSources().indirectMagic(player, player), BURST_PER * s * bonus(player));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 3, false, false, true), player);
        Fx.tigerBurst(w, e.getPos().add(0, e.getHeight() * 0.5, 0), s, true);
        w.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.TIGER_BURST, SoundCategory.PLAYERS, 0.9f, 0.9f + s * 0.03f);
    }

    // =====================================================================
    // Внутренняя сила
    // =====================================================================
    public static int inner(PlayerEntity p) {
        int[] v = INNER.get(p.getUuid());
        return v == null ? 0 : v[0];
    }

    static void addInner(ServerPlayerEntity p, int n) {
        int now = p.getServer() == null ? 0 : p.getServer().getTicks();
        int[] v = INNER.computeIfAbsent(p.getUuid(), k -> new int[]{0, 0});
        int old = v[0];
        v[0] = Math.max(0, Math.min(INNER_MAX, v[0] + n));
        if (n > 0) {
            v[1] = now + INNER_HOLD;
        }
        if (v[0] != old) {
            syncInner(p, v[0]);
        }
    }

    private static void syncInner(ServerPlayerEntity p, int value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(value);
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    // =====================================================================
    // Зарядка особых атак (зажатые ЛКМ / ПКМ)
    // =====================================================================
    private static void charge(MinecraftServer server, ServerPlayerEntity player, int kind) {
        int now = server.getTicks();
        if (busy(player) || (kind != 1 && kind != 2)) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (kind == 1 && (CleaverItem.rounds(stack) < S1_ROUNDS || now < S1_READY.getOrDefault(player.getUuid(), 0))) {
            return;
        }
        if (kind == 2 && (CleaverItem.rounds(stack) < 1 || inner(player) < S2_INNER_MIN
                || now < S2_READY.getOrDefault(player.getUuid(), 0))) {
            return;
        }
        CHARGES.put(player.getUuid(), new int[]{now, kind});
        slow(player, true);
        sendAnim(player, kind == 1 ? ANIM_CHARGE1 : ANIM_CHARGE2);
        sound(player, true, ModSounds.TIGER_IGNITE, 0.8f, kind == 1 ? 1.1f : 0.9f);
    }

    private static void cancelCharge(ServerPlayerEntity player, boolean tell) {
        if (CHARGES.remove(player.getUuid()) != null) {
            slow(player, false);
            if (tell) {
                sendAnim(player, ANIM_CHARGE_END);
            }
        }
    }

    /** Пока заряжает — шагом (−50% скорости; клиент видит атрибут и сам замедляется). */
    private static void slow(ServerPlayerEntity player, boolean on) {
        EntityAttributeInstance a = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (a == null) {
            return;
        }
        a.removeModifier(SLOW_ID);
        if (on) {
            a.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID, "cleaver_charge", -0.5,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void release(MinecraftServer server, ServerPlayerEntity player) {
        int[] ch = CHARGES.remove(player.getUuid());
        slow(player, false);
        if (ch == null) {
            return;
        }
        int now = server.getTicks();
        int kind = ch[1];
        ItemStack stack = player.getMainHandStack();
        int rounds = CleaverItem.rounds(stack);
        boolean ready = now - ch[0] >= (kind == 1 ? S1_CHARGE : S2_CHARGE) - 3;
        if (!ready || (kind == 1 && rounds < S1_ROUNDS) || (kind == 2 && (rounds < 1 || inner(player) < S2_INNER_MIN))) {
            sendAnim(player, ANIM_CHARGE_END);
            return;
        }
        Special sp = new Special(kind, now, player.getPos());
        if (kind == 1) {
            CleaverItem.setRounds(stack, rounds - S1_ROUNDS);
            S1_READY.put(player.getUuid(), now + S1_COOLDOWN);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, S1_DASH_TICKS + 6, 4, false, false, false));
            Vec3d chest = player.getPos().add(0, player.getHeight() * 0.62, 0);
            Fx.tigerShot(player.getServerWorld(), chest, forward(player).multiply(-1), false);
            sound(player, true, ModSounds.TIGER_SHOT, 1.0f, 1.05f);
            sound(player, true, ModSounds.TIGER_DASH, 1.0f, 1.0f);
            sendAnim(player, ANIM_S1);
        } else {
            int in = inner(player);
            sp.power = 1f + in * S2_PER_INNER;
            addInner(player, -in);
            CleaverItem.setRounds(stack, 0);
            S2_READY.put(player.getUuid(), now + S2_COOLDOWN);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, S2_SMASH + 8, 2, false, false, false));
            Fx.tigerRings(player.getServerWorld(), player.getPos());
            sendAnim(player, ANIM_S2);
        }
        SPECIALS.put(player.getUuid(), sp);
    }

    /** @return true — атака закончилась. */
    private static boolean tickSpecial(ServerPlayerEntity player, Special s, int now) {
        int age = now - s.start;
        ServerWorld w = player.getServerWorld();
        player.fallDistance = 0f;
        if (s.kind == 1) {
            if (age <= S1_DASH_TICKS + 1) {              // укол на рывке: все на пути
                Vec3d cur = player.getPos();
                Vec3d a = s.last.add(0, 1.0, 0);
                Vec3d b = cur.add(0, 1.0, 0).add(forward(player).multiply(1.2));
                Box sweep = new Box(a, b).expand(1.6);
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, sweep, e -> e != player && e.isAlive())) {
                    if (s.hit.contains(e.getId()) || BloodSwordCombat.excluded(player, e)) {
                        continue;
                    }
                    Box bb = e.getBoundingBox().expand(1.0);
                    if (!bb.contains(a) && !bb.contains(b) && bb.raycast(a, b).isEmpty()) {
                        continue;
                    }
                    s.hit.add(e.getId());
                    if (strike(player, e, S1_PATH, 0.2, cur, 0)) {
                        addTremor(e, 3, player);
                    }
                }
                if (age == S1_DASH_TICKS + 1) {
                    Fx.tigerTrail(w, s.last.add(0, 1.0, 0), cur.add(0, 1.0, 0));
                }
                if (age > 0 && age % 2 == 0) {
                    s.last = cur;
                }
            }
            if (age >= S1_BLAST) {                       // второй патрон — взрыв на острие
                Vec3d tip = player.getPos().add(forward(player).multiply(2.6));
                int hits = explode(player, new Vec3d(tip.x, player.getY(), tip.z), S1_RADIUS, S1_BLAST_DAMAGE, 4, 1.0f, false);
                if (hits > 0 || !s.hit.isEmpty()) {
                    addInner(player, 3);
                    wear(player, 2);
                }
                return true;
            }
            return false;
        }
        // --- «Тесак в узел»
        for (int i = 0; i < S2_CUTS.length; i++) {
            if (age == S2_CUTS[i]) {
                Vec3d chest = player.getPos().add(0, player.getHeight() * 0.62, 0);
                Vec3d fwd = forward(player);
                Fx.tigerShot(w, chest.add(fwd.multiply(0.6)), fwd.multiply(-1).add(0, 0.3, 0), true);
                Fx.tigerSlash(w, chest, player.getYaw(), S2_REACH, i == 1 ? -0.9 : (i == 2 ? 0.6 : 0.0), i == 1 ? -1 : 1, true);
                w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TIGER_SHOT_SAVAGE, SoundCategory.PLAYERS, 1.0f, 1.0f + i * 0.05f);
                for (LivingEntity e : targets(player, w, S2_REACH, 110)) {
                    if (strike(player, e, S2_CUT * s.power, 0.2, player.getPos(), 4)) {
                        burst(e, player);
                        addTremor(e, 2, player);
                    }
                }
            }
        }
        if (age == S2_SMASH) {
            Vec3d g = smashPoint(player);
            explode(player, g, S2_SMASH_RADIUS, S2_SMASH_DAMAGE * s.power, 5, 1.0f, true);
            w.playSound(null, g.x, g.y, g.z, ModSounds.TIGER_WAVE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            s.waveCenter = g;
            s.waveRadius = 1.2;
            wear(player, 3);
        }
        if (s.waveCenter != null) {                      // огненная волна расходится кольцом
            s.waveRadius += WAVE_SPEED;
            Fx.tigerWave(w, s.waveCenter, s.waveRadius, true);
            for (LivingEntity e : around(player, w, s.waveCenter, WAVE_MAX + 1)) {
                if (s.waveHit.contains(e.getId())) {
                    continue;
                }
                double d = Math.sqrt(e.squaredDistanceTo(s.waveCenter.x, e.getY(), s.waveCenter.z));
                if (Math.abs(d - s.waveRadius) < 0.9) {
                    s.waveHit.add(e.getId());
                    if (strike(player, e, S2_WAVE * s.power, 0.8, s.waveCenter, 5)) {
                        e.addVelocity(0, 0.3, 0);
                        e.velocityModified = true;
                    }
                }
            }
            return s.waveRadius >= WAVE_MAX;
        }
        return age > S2_LEN;
    }

    // =====================================================================
    // Перезарядка
    // =====================================================================
    private static void reload(MinecraftServer server, ServerPlayerEntity player) {
        if (busy(player) || CleaverItem.rounds(player.getMainHandStack()) >= CleaverItem.MAX_ROUNDS) {
            return;
        }
        RELOADS.put(player.getUuid(), server.getTicks());
        sendAnim(player, ANIM_RELOAD);
    }

    /** @return true — закончена (или сорвана). */
    private static boolean tickReload(ServerPlayerEntity player, int start, int now) {
        if (!holding(player)) {
            return true;
        }
        int age = now - start;
        if (age == 5) {
            sound(player, true, ModSounds.TIGER_DRUM_OPEN, 0.8f, 1.0f);
        } else if (age == 9) {
            int empty = CleaverItem.MAX_ROUNDS - CleaverItem.rounds(player.getMainHandStack());
            Fx.tigerShells(player.getServerWorld(), player.getPos().add(forward(player).multiply(0.5)).add(0, 1.0, 0), Math.max(1, empty));
            sound(player, true, ModSounds.TIGER_SHELL, 0.8f, 1.0f);
        } else if (age == RELOAD_LOAD) {
            CleaverItem.setRounds(player.getMainHandStack(), CleaverItem.MAX_ROUNDS);
            sound(player, true, ModSounds.TIGER_DRUM_LOAD, 0.8f, 1.0f);
        } else if (age == RELOAD_LOAD + 7) {
            sound(player, true, ModSounds.TIGER_DRUM_CLOSE, 0.8f, 1.0f);
        }
        return age >= RELOAD_LEN - 6;                    // последние тики клипа — уже можно бить
    }

    // =====================================================================
    private static void tick(MinecraftServer server) {
        int now = server.getTicks();
        if (!PENDING.isEmpty()) {
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
                    hit(pl, p.step, p.blast);
                }
            }
        }
        if (!SPECIALS.isEmpty()) {
            Iterator<Map.Entry<UUID, Special>> it = SPECIALS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Special> e = it.next();
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.getKey());
                if (pl == null || !pl.isAlive() || !holding(pl) || tickSpecial(pl, e.getValue(), now)) {
                    it.remove();
                }
            }
        }
        if (!RELOADS.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = RELOADS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.getKey());
                if (pl == null || !pl.isAlive() || tickReload(pl, e.getValue(), now)) {
                    it.remove();
                }
            }
        }
        if (!CHARGES.isEmpty()) {                        // убрал тесак из руки во время зарядки
            Iterator<Map.Entry<UUID, int[]>> it = CHARGES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, int[]> e = it.next();
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.getKey());
                if (pl == null || !pl.isAlive() || !holding(pl) || now - e.getValue()[0] > 200) {
                    it.remove();
                    if (pl != null) {
                        slow(pl, false);
                        sendAnim(pl, ANIM_CHARGE_END);
                    }
                }
            }
        }
        if (!INNER.isEmpty()) {
            for (Map.Entry<UUID, int[]> e : INNER.entrySet()) {
                int[] v = e.getValue();
                if (v[0] > 0 && now >= v[1]) {
                    v[0]--;
                    v[1] = now + INNER_DECAY;
                    ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.getKey());
                    if (pl != null) {
                        syncInner(pl, v[0]);
                    }
                }
            }
        }
    }
}
