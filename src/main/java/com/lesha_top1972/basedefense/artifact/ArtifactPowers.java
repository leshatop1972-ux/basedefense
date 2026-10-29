package com.lesha_top1972.basedefense.artifact;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Амулеты и кольца второй партии (29.10.2026):
 * <ul>
 *   <li>Амулет памяти — удар в ближнем бою по мобу тратит 1/4 уровня опыта и оглушает его на 3 с
 *       (не двигается и не атакует), перезарядка 10 с;</li>
 *   <li>Амулет вечности — как тотем бессмертия, но не тратится: перезарядка 5 минут;</li>
 *   <li>Амулет жадности — голод быстрее и выгоднее превращается в здоровье: +1 HP в секунду (вчетверо чаще
 *       обычного) за половину обычного расхода сытости, пока сытость больше 6;</li>
 *   <li>Амулет ярости — +4 HP; каждый удар в ближнем бою тратит 1/8 уровня опыта: урон ×1.2;</li>
 *   <li>Амулет чистоты I — снимает все негативные эффекты раз в 10 с, +2 HP;</li>
 *   <li>Амулет чистоты II — раз в 8 с, +4 HP; клавиша слота — 10 с невосприимчивости к негативным
 *       эффектам, перезарядка 30 с;</li>
 *   <li>Кольцо жадности I — каждый удар в ближнем бою стоит 1 HP: урон ×1.25;</li>
 *   <li>Кольцо жадности II — 2 HP за удар: урон ×1.5; +6 HP.</li>
 * </ul>
 * Чистота I/II и кольца I/II не складываются (действует сильнейшее надетое).
 */
public final class ArtifactPowers {

    /** Клавиша слота артефакта: клиент присылает номер слота (0–4). */
    public static final Identifier ABILITY_C2S = new Identifier(Basedefense.MOD_ID, "artifact_ability");

    public static final int MEMORY_STUN = 60;
    public static final int MEMORY_COOLDOWN = 200;
    public static final int ETERNITY_COOLDOWN = 5 * 60 * 20;
    public static final int PURITY_IMMUNE = 200;
    public static final int PURITY_COOLDOWN = 600;
    private static final int PURITY1_PERIOD = 200;
    private static final int PURITY2_PERIOD = 160;
    private static final int GREED_PERIOD = 20;
    private static final float GREED_EXHAUSTION = 3.0f;

    /** Оглушённые амулетом памяти мобы → тик сервера, когда оглушение кончится. */
    private static final Map<MobEntity, Long> STUNNED = new HashMap<>();
    /** Мобы, которым мы сами выключили ИИ (вернуть потом). */
    private static final Map<MobEntity, Boolean> AI_WAS_ON = new HashMap<>();
    /** Невосприимчивость к негативным эффектам (чистота II): игрок → тик окончания. */
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    /** Последнее очищение (чистота I/II): игрок → тик. */
    private static final Map<UUID, Long> LAST_CLEANSE = new HashMap<>();
    /** В этом тике за удар уже заплачено (размашистый удар задевает нескольких). */
    private static final Map<UUID, Long> PAID_FURY = new HashMap<>();
    private static final Map<UUID, Long> PAID_RING = new HashMap<>();

    private static MinecraftServer server;

    private ArtifactPowers() {
    }

    private static long now() {
        return server == null ? 0 : server.getTicks();
    }

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(s -> server = s);
        ServerTickEvents.END_SERVER_TICK.register(ArtifactPowers::tickStuns);
        ServerPlayNetworking.registerGlobalReceiver(ABILITY_C2S, (srv, player, handler, buf, sender) -> {
            int slot = buf.readVarInt();
            srv.execute(() -> ability(player, slot));
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register(ArtifactPowers::allowDeath);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            UUID id = handler.player.getUuid();
            IMMUNE.remove(id);
            LAST_CLEANSE.remove(id);
            PAID_FURY.remove(id);
            PAID_RING.remove(id);
        });
    }

    // =====================================================================
    // Пассивы (раз в 10 тиков из Artifacts.updatePassives)
    // =====================================================================

    /** Добавка к максимальному здоровью от ярости, чистоты и кольца жадности II. */
    public static double extraHealth(PlayerEntity p) {
        double hp = 0;
        if (Artifacts.has(p, ModItems.FURY_AMULET)) {
            hp += 4;
        }
        if (Artifacts.has(p, ModItems.PURITY_AMULET_2)) {
            hp += 4;
        } else if (Artifacts.has(p, ModItems.PURITY_AMULET_1)) {
            hp += 2;
        }
        if (Artifacts.has(p, ModItems.GREED_RING_2)) {
            hp += 6;
        }
        return hp;
    }

    public static void tickPlayer(ServerPlayerEntity p) {
        long t = now();
        ServerWorld world = p.getServerWorld();
        // Чистота: раз в 10 / 8 с снимает негативные эффекты
        int period = Artifacts.has(p, ModItems.PURITY_AMULET_2) ? PURITY2_PERIOD
                : Artifacts.has(p, ModItems.PURITY_AMULET_1) ? PURITY1_PERIOD : 0;
        if (period > 0) {
            long last = LAST_CLEANSE.getOrDefault(p.getUuid(), -100000L);
            if (t - last >= period) {
                LAST_CLEANSE.put(p.getUuid(), t);
                if (cleanse(p)) {
                    world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getBodyY(0.6), p.getZ(), 8, 0.35, 0.5, 0.35, 0.02);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                            SoundCategory.PLAYERS, 0.7f, 1.6f);
                }
            }
        }
        // Невосприимчивость (чистота II): блёстки вокруг
        Long until = IMMUNE.get(p.getUuid());
        if (until != null) {
            if (t >= until || !Artifacts.has(p, ModItems.PURITY_AMULET_2)) {
                IMMUNE.remove(p.getUuid());
            } else {
                world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getBodyY(0.5), p.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
            }
        }
        // Жадность: +1 HP раз в секунду за пол-обычной цены
        if (t % GREED_PERIOD == 0 && Artifacts.has(p, ModItems.GREED_AMULET) && p.canFoodHeal()
                && p.getHungerManager().getFoodLevel() > 6
                && world.getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)) {
            p.heal(1.0f);
            p.getHungerManager().addExhaustion(GREED_EXHAUSTION);
        }
    }

    /** Снять все негативные эффекты; true — что-то было снято. */
    private static boolean cleanse(LivingEntity e) {
        List<StatusEffectInstance> bad = new ArrayList<>();
        for (StatusEffectInstance inst : e.getStatusEffects()) {
            if (inst.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) {
                bad.add(inst);
            }
        }
        for (StatusEffectInstance inst : bad) {
            e.removeStatusEffect(inst.getEffectType());
        }
        return !bad.isEmpty();
    }

    /** Из миксина (LivingEntity.canHaveStatusEffect): невосприимчивый игрок не получает негативных эффектов. */
    public static boolean blocksEffect(LivingEntity entity, StatusEffectInstance effect) {
        if (!(entity instanceof ServerPlayerEntity p) || effect.getEffectType().getCategory() != StatusEffectCategory.HARMFUL) {
            return false;
        }
        Long until = IMMUNE.get(p.getUuid());
        return until != null && now() < until;
    }

    // =====================================================================
    // Клавиши слотов
    // =====================================================================

    private static void ability(ServerPlayerEntity p, int slot) {
        if (slot < 0 || slot >= Artifacts.SLOTS || !p.isAlive() || p.isSpectator()) {
            return;
        }
        ItemStack stack = Artifacts.of(p).getStack(slot);
        if (stack.isOf(ModItems.PURITY_AMULET_2)) {
            purityShield(p);
        }
    }

    private static void purityShield(ServerPlayerEntity p) {
        Item item = ModItems.PURITY_AMULET_2;
        if (p.getItemCooldownManager().isCoolingDown(item)) {
            return;
        }
        p.getItemCooldownManager().set(item, PURITY_COOLDOWN);
        IMMUNE.put(p.getUuid(), now() + PURITY_IMMUNE);
        cleanse(p);
        ServerWorld world = p.getServerWorld();
        world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getBodyY(0.5), p.getZ(), 30, 0.5, 0.8, 0.5, 0.06);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 0.8f, 1.5f);
        p.sendMessage(Text.translatable("message.basedefense.purity_shield").formatted(Formatting.AQUA), true);
    }

    // =====================================================================
    // Удары игрока
    // =====================================================================

    /** Удар рукой/оружием игрока по мобу (не жителю). */
    private static ServerPlayerEntity meleeAttacker(LivingEntity target, DamageSource source) {
        if (!(target instanceof MobEntity) || target instanceof SettlerEntity || !source.isOf(DamageTypes.PLAYER_ATTACK)
                || !(source.getAttacker() instanceof ServerPlayerEntity p) || source.getSource() != p || !p.isAlive()) {
            return null;
        }
        return p;
    }

    /** Из LivingEntityCombatMixin (начало damage): ярость и кольца жадности усиливают удар. */
    public static float modifyOutgoing(LivingEntity target, DamageSource source, float amount) {
        ServerPlayerEntity p = meleeAttacker(target, source);
        if (p == null || amount <= 0 || target.timeUntilRegen > 10) {
            return amount;
        }
        long t = now();
        UUID id = p.getUuid();
        if (Artifacts.has(p, ModItems.FURY_AMULET)) {
            boolean paid = PAID_FURY.getOrDefault(id, -1L) == t;
            if (paid || Artifacts.spendLevel(p, 0.125f)) {
                PAID_FURY.put(id, t);
                amount *= 1.2f;
            }
        }
        int ring = Artifacts.has(p, ModItems.GREED_RING_2) ? 2 : Artifacts.has(p, ModItems.GREED_RING_1) ? 1 : 0;
        if (ring > 0) {
            float cost = ring;
            boolean paid = PAID_RING.getOrDefault(id, -1L) == t;
            if (paid || p.isCreative() || p.getHealth() > cost) {
                if (!paid && !p.isCreative()) {
                    p.setHealth(p.getHealth() - cost);
                }
                PAID_RING.put(id, t);
                amount *= ring == 2 ? 1.5f : 1.25f;
                if (!paid) {
                    p.getServerWorld().spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getBodyY(0.6), p.getZ(),
                            ring, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }
        return amount;
    }

    /** Из Artifacts.onDamaged (удар прошёл): амулет памяти оглушает. */
    public static void onHit(LivingEntity target, DamageSource source) {
        ServerPlayerEntity p = meleeAttacker(target, source);
        if (p == null || !target.isAlive() || !Artifacts.has(p, ModItems.MEMORY_AMULET)
                || p.getItemCooldownManager().isCoolingDown(ModItems.MEMORY_AMULET)) {
            return;
        }
        if (!Artifacts.spendLevel(p, 0.25f)) {
            return;
        }
        p.getItemCooldownManager().set(ModItems.MEMORY_AMULET, MEMORY_COOLDOWN);
        stun((MobEntity) target, MEMORY_STUN);
        ServerWorld world = p.getServerWorld();
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_ANVIL_LAND,
                SoundCategory.PLAYERS, 0.5f, 1.8f);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.8), target.getZ(),
                12, 0.3, 0.3, 0.3, 0.1);
    }

    // =====================================================================
    // Оглушение мобов (амулет памяти)
    // =====================================================================

    private static void stun(MobEntity mob, int ticks) {
        if (!STUNNED.containsKey(mob)) {
            AI_WAS_ON.put(mob, !mob.isAiDisabled());
        }
        STUNNED.put(mob, now() + ticks);
        mob.setAiDisabled(true);
        mob.getNavigation().stop();
        mob.setVelocity(0, Math.min(0, mob.getVelocity().y), 0);
        mob.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, ticks, 1, false, true, true));
    }

    /** Оглушённый амулетом моб не наносит урона (ни ударом, ни снарядом). */
    public static boolean isStunned(Entity attacker) {
        return attacker instanceof MobEntity mob && STUNNED.containsKey(mob);
    }

    private static void tickStuns(MinecraftServer srv) {
        if (STUNNED.isEmpty()) {
            return;
        }
        long t = srv.getTicks();
        Iterator<Map.Entry<MobEntity, Long>> it = STUNNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MobEntity, Long> e = it.next();
            MobEntity mob = e.getKey();
            if (mob.isRemoved() || !mob.isAlive() || t >= e.getValue()) {
                if (!mob.isRemoved() && Boolean.TRUE.equals(AI_WAS_ON.get(mob))) {
                    mob.setAiDisabled(false);
                }
                AI_WAS_ON.remove(mob);
                it.remove();
            } else {
                mob.setVelocity(0, Math.min(0, mob.getVelocity().y), 0);
            }
        }
    }

    // =====================================================================
    // Амулет вечности
    // =====================================================================

    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p) || !Artifacts.has(p, ModItems.ETERNITY_AMULET)
                || p.getItemCooldownManager().isCoolingDown(ModItems.ETERNITY_AMULET)
                || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // Как тотем бессмертия
        p.setHealth(1.0f);
        p.clearStatusEffects();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
        p.getWorld().sendEntityStatus(p, (byte) 35);   // анимация и звук тотема
        p.getItemCooldownManager().set(ModItems.ETERNITY_AMULET, ETERNITY_COOLDOWN);
        p.sendMessage(Text.translatable("message.basedefense.eternity").formatted(Formatting.GOLD), true);
        return false;
    }
}
