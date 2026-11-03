package com.lesha_top1972.basedefense.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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

/**
 * Катана — сервер (урон, звуки для остальных, рассылка анимаций). Движение своего игрока (рывки) считает клиент.
 * <p>
 * ЛКМ — серия из 3 ударов (у каждого небольшой рывок вперёд): 1 — справа сверху вниз‑влево, 2 — слева вниз‑вправо,
 * 3 — горизонтальный справа налево и восходящий разрез (2 попадания). Тайминги — как в клипах 1‑го лица
 * (tools/katana_gen.py).
 * <p>
 * ЛКМ зажать 1.5 с — «Иссэн»: катана в ножны, зарядка; отпустить (или само через 1 с после готовности) —
 * выхват на сильном рывке (8 блоков, неуязвим на время рывка) режет всех на пути, а когда стряхивает кровь —
 * по всем задетым проходит второй, отложенный разрез. У древней катаны — разлом пространства (KatanaVariant).
 * <p>
 * ПКМ держать — блок: удар спереди ослабляется на 65% (щит мода — на 85%), без отброса; древняя катана копит
 * «Древнюю силу» (KatanaPower). Вызывает LivingEntityCombatMixin.
 */
public final class KatanaCombat {

    public static final Identifier C2S = new Identifier(Basedefense.MOD_ID, "katana_swing");
    public static final Identifier S2C = new Identifier(Basedefense.MOD_ID, "katana_anim");
    public static final Identifier FX = new Identifier(Basedefense.MOD_ID, "katana_fx");
    /** Клиент → сервер: зарядка ЛКМ (1 — начал, 0 — отпустил раньше, 2 — отпустил заряженной). */
    public static final Identifier C2S_CHARGE = new Identifier(Basedefense.MOD_ID, "katana_charge");
    /** Сервер → игроку: удар отбит блоком (отдача в руки). */
    public static final Identifier S2C_BLOCKED = new Identifier(Basedefense.MOD_ID, "katana_blocked");

    // ---- серия ----
    /** Длина ударов (тики). */
    public static final int[] LEN = {12, 12, 26};
    /** Когда бьёт (тики от начала удара). */
    public static final int[][] HITS = {{4}, {3}, {3, 11}};
    /** Урон каждого попадания (обычная катана; виды — + KatanaVariant.comboBonus). */
    public static final float[][] DAMAGE = {{4f}, {4f}, {3f, 5f}};
    /** Следующий удар — не раньше (тики). */
    public static final int NEXT_MIN = 6;
    /** Серия продолжается, если следующий клик — не позже (тики от начала прошлого удара). */
    public static final int COMBO_WINDOW = 20;
    /** После третьего удара — отдых (тики от его начала). */
    public static final int FINISH_REST = 30;
    /** Рывок вперёд у ударов (скорость, ≈ 0.6–1 блок). */
    public static final float[] DASH = {0.32f, 0.32f, 0.42f};
    /** Второй рывок третьего удара (на восходящем разрезе), тик. */
    public static final int DASH2_TICK = 8;
    public static final float DASH2 = 0.28f;
    /** Дальность и ширина сектора удара. */
    static final double REACH = 3.2;
    static final double HALF_ANGLE = 70;

    // ---- особая атака (зажать ЛКМ) ----
    /** Сколько держать ЛКМ, чтобы это была зарядка, а не удар (тики). */
    public static final int HOLD_START = 5;
    public static final int CHARGE_TICKS = 30;
    public static final int AUTO_RELEASE = 50;
    public static final int DASH_TICKS = 4;            // у древней — KatanaVariant.dashTicks
    public static final double DASH_SPEED = 2.0;
    public static final float PATH_DAMAGE = 8f;
    public static final int BURST_TICK = 14;
    public static final float BURST_DAMAGE = 12f;
    public static final int SPECIAL_COOLDOWN = 160;
    /** Древняя: +урона за каждую единицу силы. */
    public static final float POWER_DAMAGE = 1f;

    // ---- блок ----
    /** Доля урона, проходящая сквозь блок катаной. */
    public static final float BLOCK_PASS = 0.35f;
    /** Блок начинает работать через столько тиков после нажатия ПКМ. */
    public static final int BLOCK_DELAY = 3;

    // ---- виды анимаций (S2C) ----
    public static final int ANIM_SPECIAL = 3;
    public static final int ANIM_CHARGE = 4;
    public static final int ANIM_CHARGE_END = 5;

    // ---- эффекты (FX) ----
    public static final int FX_DASH = 1;
    public static final int FX_BURST = 2;
    public static final int FX_HIT = 3;
    /** Разлом пространства по пути рывка (древняя). */
    public static final int FX_RIFT = 4;
    /** Отложенный разрез древней: пространство трескается на цели. */
    public static final int FX_RIFT_BURST = 5;
    /** Блок: искры и звон в точке p. */
    public static final int FX_BLOCK = 6;

    private static final java.util.UUID SLOW_ID = java.util.UUID.fromString("6f1c2b7e-3d4a-4f5b-9c8d-1e2f3a4b5c6d");

    private static final Map<UUID, int[]> LAST = new HashMap<>();     // {тик, номер удара}
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Special> SPECIALS = new HashMap<>();
    private static final Map<UUID, Integer> CHARGES = new HashMap<>();

    private record Pending(UUID player, int at, int idx, int hit) {
    }

    private static final class Special {
        final int start;
        final KatanaVariant variant;
        float pathDamage;
        float burstDamage;
        Vec3d last;
        final Vec3d from;
        final Set<Integer> hit = new HashSet<>();
        final List<LivingEntity> targets = new ArrayList<>();

        Special(int start, Vec3d pos, KatanaVariant variant) {
            this.start = start;
            this.variant = variant;
            this.last = pos;
            this.from = pos;
        }
    }

    private KatanaCombat() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int idx = buf.readByte();
            server.execute(() -> swing(server, player, idx));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_CHARGE, (server, player, handler, buf, sender) -> {
            int what = buf.readByte();
            server.execute(() -> charge(server, player, what));
        });
        ServerTickEvents.END_SERVER_TICK.register(KatanaCombat::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST.remove(handler.player.getUuid());
            SPECIALS.remove(handler.player.getUuid());
            CHARGES.remove(handler.player.getUuid());
        });
    }

    public static boolean holding(PlayerEntity p) {
        return p.getMainHandStack().getItem() instanceof KatanaItem;
    }

    /** Вид катаны в руке (или BASE). */
    public static KatanaVariant variant(PlayerEntity p) {
        return p.getMainHandStack().getItem() instanceof KatanaItem k ? k.variant() : KatanaVariant.BASE;
    }

    /** Занят особой атакой (сервер). */
    public static boolean busy(PlayerEntity p) {
        return SPECIALS.containsKey(p.getUuid());
    }

    // =====================================================================
    // Серия
    // =====================================================================
    private static void swing(MinecraftServer server, ServerPlayerEntity player, int clientIdx) {
        if (!holding(player) || !player.isAlive() || player.isSpectator() || player.isUsingItem() || busy(player)
                || CHARGES.containsKey(player.getUuid())) {
            return;
        }
        int now = server.getTicks();
        int[] last = LAST.get(player.getUuid());
        if (last != null) {
            int age = now - last[0];
            if (last[1] == 2 ? age < FINISH_REST - 2 : age < NEXT_MIN - 2) {
                return;                                  // слишком часто
            }
        }
        boolean chain = last != null && last[1] < 2 && now - last[0] <= COMBO_WINDOW + 4 && clientIdx == last[1] + 1;
        int idx = clientIdx >= 1 && clientIdx <= 2 && chain ? clientIdx : 0;
        LAST.put(player.getUuid(), new int[]{now, idx});
        for (int h = 0; h < HITS[idx].length; h++) {
            PENDING.add(new Pending(player.getUuid(), now + HITS[idx][h], idx, h));
        }
        ServerWorld w = player.getServerWorld();
        w.playSound(player, player.getX(), player.getY(), player.getZ(), idx == 2 ? ModSounds.KATANA_SWING_HEAVY : ModSounds.KATANA_SWING,
                SoundCategory.PLAYERS, 0.9f, 0.95f + idx * 0.05f);
        sendAnim(player, idx);
    }

    static void sendAnim(ServerPlayerEntity player, int idx) {
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
                    hit(pl, p.idx, p.hit);
                }
            }
        }
        if (!SPECIALS.isEmpty()) {
            Iterator<Map.Entry<UUID, Special>> it = SPECIALS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Special> e = it.next();
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.getKey());
                if (pl == null || !pl.isAlive() || tickSpecial(pl, e.getValue(), now)) {
                    it.remove();
                }
            }
        }
    }

    /** Цели удара в секторе перед игроком (то же считает клиент для «стоп‑кадра»). */
    public static List<LivingEntity> targets(PlayerEntity player, World w) {
        double rad = Math.toRadians(player.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d o = player.getPos();
        double minCos = Math.cos(Math.toRadians(HALF_ANGLE));
        Box box = player.getBoundingBox().expand(REACH + 1, 1.5, REACH + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (BloodSwordCombat.excluded(player, e)) {
                continue;
            }
            Vec3d rel = e.getPos().subtract(o);
            Vec3d flat = new Vec3d(rel.x, 0, rel.z);
            double dist = flat.length() - e.getWidth() / 2.0;
            if (dist > REACH || rel.y < -1.5 || rel.y > 2.5) {
                continue;
            }
            if (flat.lengthSquared() > 0.25 && flat.normalize().dotProduct(fwd) < minCos) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private static void hit(ServerPlayerEntity player, int idx, int hitNo) {
        ServerWorld w = player.getServerWorld();
        double rad = Math.toRadians(player.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        ItemStack stack = player.getMainHandStack();
        int hits = 0;
        for (LivingEntity e : targets(player, w)) {
            float dmg = DAMAGE[idx][hitNo] + variant(player).comboBonus + EnchantmentHelper.getAttackDamage(stack, e.getGroup());
            e.timeUntilRegen = 0;                         // удары серии чаще ванильной «неуязвимости»
            if (!e.damage(w.getDamageSources().playerAttack(player), dmg)) {
                continue;
            }
            hits++;
            e.takeKnockback(idx == 2 && hitNo == 1 ? 0.45 : 0.2, -fwd.x, -fwd.z);
            int fire = EnchantmentHelper.getFireAspect(player);
            if (fire > 0) {
                e.setOnFireFor(fire * 4);
            }
            EnchantmentHelper.onTargetDamaged(player, e);
            fx(w, FX_HIT, new Vec3d(e.getX(), e.getBodyY(0.55), e.getZ()), fwd, idx * 2 + hitNo);
        }
        if (hits > 0) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_HIT, SoundCategory.PLAYERS,
                    0.9f, 0.95f + w.random.nextFloat() * 0.15f);
            if (!player.isCreative()) {
                stack.damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
            }
        }
        player.addExhaustion(0.1f);
    }

    // =====================================================================
    // Особая атака: зарядка — зажатая ЛКМ (пакеты от клиента)
    // =====================================================================
    private static void charge(MinecraftServer server, ServerPlayerEntity player, int what) {
        int now = server.getTicks();
        if (what == 1) {
            if (!holding(player) || busy(player) || player.isUsingItem() || player.isSpectator()
                    || player.getItemCooldownManager().isCoolingDown(player.getMainHandStack().getItem())) {
                return;
            }
            CHARGES.put(player.getUuid(), now);
            slow(player, true);
            sendAnim(player, ANIM_CHARGE);
            player.getServerWorld().playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_CHARGE,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);
            return;
        }
        Integer start = CHARGES.remove(player.getUuid());
        slow(player, false);
        if (start == null) {
            return;
        }
        if (what == 2 && now - start >= CHARGE_TICKS - 3 && holding(player)) {
            release(player);
        } else {
            sendAnim(player, ANIM_CHARGE_END);
        }
    }

    /** Пока заряжает — шагом (−50% скорости; клиент видит атрибут и сам замедляется). */
    private static void slow(ServerPlayerEntity player, boolean on) {
        net.minecraft.entity.attribute.EntityAttributeInstance a =
                player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (a == null) {
            return;
        }
        a.removeModifier(SLOW_ID);
        if (on) {
            a.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(SLOW_ID, "katana_charge", -0.5,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void release(ServerPlayerEntity player) {
        if (busy(player)) {
            return;
        }
        ServerWorld w = player.getServerWorld();
        int now = w.getServer().getTicks();
        KatanaVariant v = variant(player);
        Special sp = new Special(now, player.getPos(), v);
        sp.pathDamage = PATH_DAMAGE + v.specialBonus;
        sp.burstDamage = BURST_DAMAGE + v.specialBonus;
        if (v.ancient) {
            int power = KatanaPower.spendHalf(player);
            sp.pathDamage += power * POWER_DAMAGE;
            sp.burstDamage += power * POWER_DAMAGE;
        }
        SPECIALS.put(player.getUuid(), sp);
        player.getItemCooldownManager().set(player.getMainHandStack().getItem(), SPECIAL_COOLDOWN);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, v.dashTicks + 4, 4, false, false, false));
        w.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_SPECIAL, SoundCategory.PLAYERS, 1.0f, 1.0f);
        if (v.ancient) {
            w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_RIFT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        sendAnim(player, ANIM_SPECIAL);
    }

    // =====================================================================
    // Блок (ПКМ): вызывает LivingEntityCombatMixin.damage до вычета урона
    // =====================================================================
    /** @return урон после блока, или −1 — не заблокировано. */
    public static float block(PlayerEntity player, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (!(player instanceof ServerPlayerEntity sp) || !holding(player) || !player.isUsingItem()
                || !(player.getActiveItem().getItem() instanceof KatanaItem) || player.getItemUseTime() < BLOCK_DELAY
                || source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_SHIELD)) {
            return -1f;
        }
        Vec3d from = source.getPosition();
        if (from == null) {
            return -1f;
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d to = from.subtract(player.getPos());
        if (new Vec3d(to.x, 0, to.z).dotProduct(look) < 0.0) {
            return -1f;                                      // удар сзади — как у щита, не блокируется
        }
        ServerWorld w = sp.getServerWorld();
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_BLOCK, SoundCategory.PLAYERS,
                1.0f, 0.9f + w.random.nextFloat() * 0.2f);
        Vec3d at = player.getEyePos().add(look.multiply(0.7)).add(0, -0.35, 0);
        fx(w, FX_BLOCK, at, look, variant(player).ordinal());
        ServerPlayNetworking.send(sp, S2C_BLOCKED, PacketByteBufs.create());
        if (variant(player).ancient) {
            KatanaPower.add(sp, 1);
        }
        if (!player.isCreative()) {
            player.getMainHandStack().damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        }
        return amount * BLOCK_PASS;
    }

    /** @return true — атака закончилась. */
    private static boolean tickSpecial(ServerPlayerEntity player, Special s, int now) {
        int age = now - s.start;
        ServerWorld w = player.getServerWorld();
        KatanaVariant v = s.variant;
        if (age <= v.dashTicks + 2) {
            player.fallDistance = 0f;
            Vec3d cur = player.getPos();
            Vec3d a = s.last.add(0, 1.0, 0);
            Vec3d b = cur.add(0, 1.0, 0);
            Box sweep = new Box(a, b).expand(v.sweep);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, sweep, e -> e != player && e.isAlive())) {
                if (s.hit.contains(e.getId()) || BloodSwordCombat.excluded(player, e)) {
                    continue;
                }
                Box bb = e.getBoundingBox().expand(v.hitBox);
                if (!bb.contains(a) && !bb.contains(b) && bb.raycast(a, b).isEmpty()) {
                    continue;
                }
                s.hit.add(e.getId());
                s.targets.add(e);
                e.timeUntilRegen = 0;
                e.damage(w.getDamageSources().playerAttack(player), s.pathDamage);
                fx(w, FX_HIT, new Vec3d(e.getX(), e.getBodyY(0.55), e.getZ()), b.subtract(a), 6);
            }
            s.last = cur;
            if (age == v.dashTicks + 2) {
                fx(w, v.ancient ? FX_RIFT : FX_DASH, s.from.add(0, 1.0, 0), cur.add(0, 1.0, 0).subtract(s.from.add(0, 1.0, 0)), 0);
            }
        }
        if (age == BURST_TICK) {
            int n = 0;
            for (LivingEntity e : s.targets) {
                if (!e.isAlive()) {
                    continue;
                }
                e.timeUntilRegen = 0;
                if (e.damage(w.getDamageSources().playerAttack(player), s.burstDamage)) {
                    n++;
                }
                fx(w, v.ancient ? FX_RIFT_BURST : FX_BURST, new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ()),
                        new Vec3d(e.getWidth(), e.getHeight(), 0), e.getId());
            }
            if (n > 0) {
                w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KATANA_BURST, SoundCategory.PLAYERS, 1.0f, 1.0f);
                if (!player.isCreative()) {
                    player.getMainHandStack().damage(2, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
                }
            }
            return true;
        }
        return false;
    }

    /** Эффект для всех рядом (рисует клиент — client/weapon/KatanaWorldFx). */
    static void fx(ServerWorld w, int type, Vec3d p, Vec3d v, int data) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(type);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat((float) v.x);
        buf.writeFloat((float) v.y);
        buf.writeFloat((float) v.z);
        buf.writeVarInt(data);
        for (ServerPlayerEntity pl : PlayerLookup.around(w, p, 64)) {
            ServerPlayNetworking.send(pl, FX, PacketByteBufs.copy(buf));
        }
        if (type == FX_HIT) {
            w.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 5, 0.2, 0.25, 0.2, 0.25);
        }
    }
}
