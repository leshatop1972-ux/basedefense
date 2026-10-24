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

    public static boolean isFrozen(LivingEntity entity) {
        return entity != null && entity.hasStatusEffect(FREEZE);
    }

    public static void init() {
        // Замороженный игрок не может бить, ломать блоки и пользоваться предметами (лук, еда и т.п.)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
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
