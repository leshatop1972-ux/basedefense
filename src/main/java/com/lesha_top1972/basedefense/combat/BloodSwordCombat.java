package com.lesha_top1972.basedefense.combat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.item.ModItems;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Кровавый разруб» меча кровожада — серверная часть.
 * <p>
 * Клиент держит ЛКМ → шлёт CHARGE (сервер запоминает начало замаха, замедляет игрока, рассылает анимацию
 * остальным). Отпустил после полного замаха (1.5 с) → SLAM: через 3 тика (момент удара в анимации) урон по
 * прямоугольнику 4 блока вперёд × 4 в ширину (по 1.5 блока в стороны), «Замедление I» на 2 с, +2 HP владельцу
 * за каждого задетого моба (не больше 3). Отпустил раньше → CANCEL.
 */
public final class BloodSwordCombat {

    public static final Identifier C2S = new Identifier(Basedefense.MOD_ID, "blood_sword");
    public static final Identifier S2C = new Identifier(Basedefense.MOD_ID, "blood_sword_anim");

    public static final int ACT_CANCEL = 0;
    public static final int ACT_CHARGE = 1;
    public static final int ACT_SLAM = 2;

    /** Сколько тиков держать ЛКМ, прежде чем начнётся замах (короткое нажатие — обычный удар). */
    public static final int CHARGE_DELAY = 5;
    /** Замах — 1.5 с. */
    public static final int CHARGE_TICKS = 30;
    /** Попадание — на 3‑м тике удара (как в анимации). */
    public static final int IMPACT_TICK = 3;
    /** Удар целиком (с возвратом в обычную стойку). */
    public static final int SLAM_TICKS = 14;
    /** Перезарядка после удара. */
    public static final int COOLDOWN = 20;

    public static final float SLAM_DAMAGE = 15.0f;
    public static final double REACH = 4.0;
    public static final double HALF_WIDTH = 2.0;
    private static final int SLOW_TICKS = 40;
    private static final float HEAL_PER_MOB = 2.0f;
    private static final int HEAL_MAX_MOBS = 3;
    private static final UUID SLOW_ID = UUID.fromString("6f1c2a6e-3b8e-4f41-9a77-b7d1e05c1a90");
    private static final double CHARGE_SLOWDOWN = -0.3;

    /** Игрок → тик сервера, когда начался замах. */
    private static final Map<UUID, Integer> CHARGING = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(UUID player, int at) {
    }

    private BloodSwordCombat() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int act = buf.readByte();
            server.execute(() -> handle(server, player, act));
        });
        ServerTickEvents.END_SERVER_TICK.register(BloodSwordCombat::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CHARGING.remove(handler.player.getUuid());
            PENDING.removeIf(p -> p.player.equals(handler.player.getUuid()));
        });
    }

    public static boolean holding(PlayerEntity p) {
        return p.getMainHandStack().isOf(ModItems.BLOODTHIRSTER_SWORD);
    }

    private static void handle(MinecraftServer server, ServerPlayerEntity player, int act) {
        UUID id = player.getUuid();
        switch (act) {
            case ACT_CHARGE -> {
                if (!holding(player) || !player.isAlive() || player.isSpectator()
                        || player.getItemCooldownManager().isCoolingDown(ModItems.BLOODTHIRSTER_SWORD)) {
                    send(player, player, ACT_CANCEL);           // клиент начал зря — пусть опустит меч
                    return;
                }
                CHARGING.put(id, server.getTicks());
                slow(player, true);
                player.setSprinting(false);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SWORD_CHARGE,
                        SoundCategory.PLAYERS, 0.9f, 1.0f);
                broadcast(player, ACT_CHARGE);
            }
            case ACT_SLAM -> {
                Integer start = CHARGING.remove(id);
                slow(player, false);
                // небольшой запас на задержку сети: клиент считает 30 тиков, сервер принимает от 26
                if (start == null || !holding(player) || server.getTicks() - start < CHARGE_TICKS - 4) {
                    broadcast(player, ACT_CANCEL);
                    send(player, player, ACT_CANCEL);
                    return;
                }
                PENDING.add(new Pending(id, server.getTicks() + IMPACT_TICK));
                player.getItemCooldownManager().set(ModItems.BLOODTHIRSTER_SWORD, COOLDOWN);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SWORD_CLEAVE,
                        SoundCategory.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
                broadcast(player, ACT_SLAM);
            }
            default -> {
                if (CHARGING.remove(id) != null) {
                    slow(player, false);
                    broadcast(player, ACT_CANCEL);
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTicks();
        if (!CHARGING.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = CHARGING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p == null) {
                    it.remove();
                    continue;
                }
                if (!holding(p) || !p.isAlive() || p.isSpectator()) {
                    it.remove();
                    slow(p, false);
                    broadcast(p, ACT_CANCEL);
                    send(p, p, ACT_CANCEL);
                    continue;
                }
                if (now - e.getValue() == CHARGE_TICKS) {             // замах готов — удар сердца
                    p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SWORD_READY,
                            SoundCategory.PLAYERS, 0.9f, 1.0f);
                }
            }
        }
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
                if (pl != null && pl.isAlive()) {
                    impact(pl);
                }
            }
        }
    }

    /** Удар: прямоугольник перед игроком 4 × 4 блока. */
    private static void impact(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        double rad = Math.toRadians(player.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d side = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d o = player.getPos();
        ItemStack stack = player.getMainHandStack();
        Box box = player.getBoundingBox().expand(REACH + 1.5, 2.0, REACH + 1.5);
        int healed = 0;
        int hits = 0;
        Vec3d chest = player.getPos().add(0, player.getHeight() * 0.6, 0);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (excluded(player, e)) {
                continue;
            }
            Vec3d rel = e.getPos().subtract(o);
            double f = rel.dotProduct(fwd);
            double s = Math.abs(rel.dotProduct(side));
            double hw = e.getWidth() / 2.0;
            double dy = e.getY() - o.y;
            if (f + hw < 0.2 || f - hw > REACH || s - hw > HALF_WIDTH || dy < -1.6 || dy > 2.4) {
                continue;
            }
            float dmg = SLAM_DAMAGE + EnchantmentHelper.getAttackDamage(stack, e.getGroup());
            if (!e.damage(w.getDamageSources().playerAttack(player), dmg)) {
                continue;
            }
            hits++;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOW_TICKS, 0), player);
            e.takeKnockback(0.3, -fwd.x, -fwd.z);
            int fire = EnchantmentHelper.getFireAspect(player);
            if (fire > 0) {
                e.setOnFireFor(fire * 4);
            }
            EnchantmentHelper.onTargetDamaged(player, e);
            Fx.blood(w, e.getPos().add(0, e.getHeight() * 0.6, 0), 10, 0.3);
            if (e instanceof MobEntity && healed < HEAL_MAX_MOBS) {
                healed++;
                // кровь тянется от моба к владельцу меча
                Fx.line(w, e.getPos().add(0, e.getHeight() * 0.6, 0), chest, 14,
                        Fx.dust(0xC8101E, 1.1f), 0, Fx.F_DUST);
            }
        }
        if (healed > 0) {
            player.heal(HEAL_PER_MOB * healed);
        }
        if (hits > 0 && !player.isCreative()) {
            stack.damage(2, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        }
        player.addExhaustion(0.3f);
        Vec3d c = o.add(fwd.multiply(1.6));
        Fx.cleave(w, c, fwd, REACH, hits);
    }

    /** Кого удар не задевает: игроков (игра с друзьями), жителей, своих питомцев, стойки для брони. */
    static boolean excluded(PlayerEntity player, LivingEntity e) {
        if (e instanceof PlayerEntity || e instanceof SettlerEntity || e instanceof ArmorStandEntity) {
            return true;
        }
        if (e instanceof TameableEntity t && t.isOwner(player)) {
            return true;
        }
        return e.isTeammate(player);
    }

    private static void slow(PlayerEntity p, boolean on) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        if (on) {
            if (inst.getModifier(SLOW_ID) == null) {
                inst.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID, "basedefense:blood_sword_charge",
                        CHARGE_SLOWDOWN, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            inst.removeModifier(SLOW_ID);
        }
    }

    /** Анимация — всем, кто видит игрока (сам игрок рисует её у себя без ожидания сервера). */
    private static void broadcast(ServerPlayerEntity player, int act) {
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                send(other, player, act);
            }
        }
    }

    private static void send(ServerPlayerEntity to, PlayerEntity actor, int act) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(actor.getId());
        buf.writeByte(act);
        ServerPlayNetworking.send(to, S2C, buf);
    }
}
