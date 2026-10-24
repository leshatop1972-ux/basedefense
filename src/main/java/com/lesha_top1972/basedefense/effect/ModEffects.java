package com.lesha_top1972.basedefense.effect;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Эффекты мода. */
public final class ModEffects {

    /** Заморозка (стрела морозной лучницы): 2 секунды нельзя двигаться и атаковать. */
    public static final StatusEffect FREEZE = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "freeze"), new FreezeEffect());

    /** Дуэлянт (метка улана): улан лечится от ударов по нему, а дуэлянт бьёт улана на 20% сильнее. */
    public static final StatusEffect DUELIST = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "duelist"), new DuelistEffect());

    /** Подлая крыса: −20% урона, −0.5 HP каждые 2 с (вешает улан с шансом 8% на атакующих). */
    public static final StatusEffect SNEAKY_RAT = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "sneaky_rat"), new SneakyRatEffect());

    /** Кровотёк (кровожад и его выводок): 4 уровня, бьёшь кого‑то — сам теряешь 0.5/1/2/4 чистого урона. */
    public static final StatusEffect BLEED = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "bleed"), new BleedEffect());

    /** Оглушение (Бордюр, «Пике»): I — замедление 60%, II — 80%, нельзя атаковать никаким оружием. */
    public static final StatusEffect STUN = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "stun"), new StunEffect());

    /** Шипы (Бордюр, защитная стойка): ударивший получает 30% нанесённого урона чистым уроном. */
    public static final StatusEffect SPIKES = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "spikes"), new SpikesEffect());

    public static final float SPIKES_FACTOR = 0.3f;
    /** Тип урона «шипы» (data/basedefense/damage_type/spikes.json, чистый урон). */
    public static final RegistryKey<DamageType> SPIKES_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Basedefense.MOD_ID, "spikes"));
    /** Пакет клиенту: вспышка «Шипов» на экране игрока, которого ранили шипы. */
    public static final Identifier SPIKES_FLASH = new Identifier(Basedefense.MOD_ID, "spikes_flash");

    public static final int BLEED_TICKS = 200;
    public static final int BLEED_MAX = 3;
    private static final float[] BLEED_DAMAGE = {0.5f, 1f, 2f, 4f};
    /** Тип урона «кровотёк» (data/basedefense/damage_type/bleed.json, теги bypasses_* — чистый урон). */
    public static final RegistryKey<DamageType> BLEED_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Basedefense.MOD_ID, "bleed"));

    private ModEffects() {
    }

    /** Повесить кровотёк или поднять его уровень на 1 (максимум IV); длительность — снова 10 с. */
    public static void addBleed(LivingEntity target, LivingEntity source) {
        StatusEffectInstance cur = target.getStatusEffect(BLEED);
        int amp = cur == null ? 0 : Math.min(BLEED_MAX, cur.getAmplifier() + 1);
        if (cur != null) {
            target.removeStatusEffect(BLEED);
        }
        target.addStatusEffect(new StatusEffectInstance(BLEED, BLEED_TICKS, amp, false, true, true), source);
    }

    /** Носитель кровотёка ударил кого‑то: сам получает чистый урон, эффект снимается. */
    private static void triggerBleed(PlayerEntity player) {
        StatusEffectInstance cur = player.getStatusEffect(BLEED);
        if (cur == null) {
            return;
        }
        float dmg = BLEED_DAMAGE[Math.min(BLEED_MAX, cur.getAmplifier())];
        player.removeStatusEffect(BLEED);
        DamageSource src = new DamageSource(player.getWorld().getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEED_DAMAGE_TYPE));
        player.damage(src, dmg);
        if (player.getWorld() instanceof ServerWorld s) {
            s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                    player.getX(), player.getBodyY(0.6), player.getZ(), 10 + (int) (dmg * 4), 0.3, 0.4, 0.3, 0.15);
        }
    }

    /**
     * Носитель «Шипов» получил урон dealt: тот, кто бил (в т.ч. стрелой), получает 30% чистым уроном.
     * Вызывается из damage() носителя (сейчас — Бордюр и прислужники, AbstractCurbEntity).
     */
    public static void reflectSpikes(LivingEntity victim, DamageSource source, float dealt) {
        if (dealt <= 0 || !victim.hasStatusEffect(SPIKES) || source.isOf(SPIKES_DAMAGE_TYPE)
                || !(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) {
            return;
        }
        DamageSource src = new DamageSource(victim.getWorld().getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE).entryOf(SPIKES_DAMAGE_TYPE), victim);
        attacker.timeUntilRegen = 0;
        attacker.damage(src, dealt * SPIKES_FACTOR);
        if (victim.getWorld() instanceof ServerWorld s) {
            s.spawnParticles(ParticleTypes.END_ROD, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(),
                    6, 0.3, 0.4, 0.3, 0.06);
            s.spawnParticles(ParticleTypes.ENCHANTED_HIT, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(),
                    8, 0.3, 0.4, 0.3, 0.2);
        }
        if (attacker instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, SPIKES_FLASH, PacketByteBufs.empty());
        }
    }

    public static boolean isStunned(LivingEntity entity) {
        return entity != null && entity.hasStatusEffect(STUN);
    }

    public static boolean isFrozen(LivingEntity entity) {
        return entity != null && entity.hasStatusEffect(FREEZE);
    }

    public static void init() {
        // Замороженный игрок не может бить, ломать блоки и пользоваться предметами (лук, еда и т.п.)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                isFrozen(player) || isStunned(player) ? ActionResult.FAIL : ActionResult.PASS);
        // Оглушённый не стреляет из лука/арбалета и не бросает трезубец
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isStunned(player) && (stack.getItem() instanceof RangedWeaponItem || stack.getItem() instanceof TridentItem)) {
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
        // Кровотёк срабатывает, когда носитель бьёт кого‑либо (только на сервере)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && !player.isSpectator() && entity instanceof LivingEntity && player.hasStatusEffect(BLEED)) {
                triggerBleed(player);
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) ->
                isFrozen(player) ? TypedActionResult.fail(player.getStackInHand(hand))
                        : TypedActionResult.pass(player.getStackInHand(hand)));
    }
}
