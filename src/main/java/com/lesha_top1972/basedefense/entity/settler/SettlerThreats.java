package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.mixin.MobEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Враждебные мобы охотятся и на жителей поселения, и отвечают тому, кто их бьёт.
 * - Когда моб появляется в мире, ему добавляются цели «атаковать жителя» и «ответный удар».
 * - Ответный удар: моба ударил кто‑то другой, не его текущая цель (стражник, игрок, житель), —
 *   моб переключается на обидчика (не чаще раза в {@link #SWITCH_COOLDOWN} тиков) и держит его,
 *   пока тот продолжает бить (обидчик не бил {@link #FORGET_TICKS} тиков — моб снова выбирает сам).
 * Не трогаем: нейтральных (эндермен, зомби‑пиглин — они Angerable) и криперов (чтобы не взрывали дома).
 */
public final class SettlerThreats {

    /** Не чаще раза в 1.5 с — чтобы моб не метался между двумя бьющими. */
    private static final int SWITCH_COOLDOWN = 30;
    /** Обидчик не бил 8 с — моб снова выбирает цель сам. */
    private static final int FORGET_TICKS = 160;

    private record Revenge(LivingEntity target, long lastHit, long switchedAt) {
    }

    private static final Map<MobEntity, Revenge> REVENGE = new WeakHashMap<>();

    private SettlerThreats() {
    }

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isThreat(entity)) {
                HostileEntity mob = (HostileEntity) entity;
                MobEntityAccessor acc = (MobEntityAccessor) mob;
                acc.basedefense$getTargetSelector().add(0, new RevengeTargetGoal(mob));
                acc.basedefense$getTargetSelector().add(3, new ActiveTargetGoal<>(mob, SettlerEntity.class, true));
            }
        });
    }

    /** Этот моб нападает на жителей (и жители от него убегают). */
    public static boolean isThreat(Entity entity) {
        return entity instanceof HostileEntity
                && !entity.getCommandTags().contains(com.lesha_top1972.basedefense.director.WorldEvents.FOG_TAG)
                && !(entity instanceof Angerable)
                && !(entity instanceof CreeperEntity);
    }

    /**
     * Обидчик, которому моб сейчас отвечает (null — нет). Мобы со своим выбором цели (улан, Бордюр,
     * кровопускатель, жук, подкопщик) не переключаются на игроков, пока он есть.
     */
    @org.jetbrains.annotations.Nullable
    public static LivingEntity revengeTarget(MobEntity mob) {
        Revenge r = REVENGE.get(mob);
        if (r == null) {
            return null;
        }
        LivingEntity t = r.target();
        if (!t.isAlive() || t.isRemoved() || mob.getWorld().getTime() - r.lastHit() >= FORGET_TICKS
                || mob.squaredDistanceTo(t) > 40 * 40) {
            return null;
        }
        return t;
    }

    /** Вызывается после каждого прошедшего урона (LivingEntityCombatMixin). */
    public static void onDamaged(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof MobEntity mob) || !isThreat(mob) || !mob.isAlive()) {
            return;
        }
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == mob || !attacker.isAlive()
                || attacker instanceof HostileEntity) {
            return;   // мобы друг друга случайно задели — не ссорятся
        }
        if (attacker instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) {
            return;
        }
        long now = mob.getWorld().getTime();
        Revenge old = REVENGE.get(mob);
        if (old != null && old.target() == attacker) {
            REVENGE.put(mob, new Revenge(attacker, now, old.switchedAt()));
            return;
        }
        if (old != null && now - old.switchedAt() < SWITCH_COOLDOWN && old.target().isAlive()) {
            return;
        }
        REVENGE.put(mob, new Revenge(attacker, now, now));
        if (mob.getTarget() != attacker) {
            mob.setTarget(attacker);
        }
    }

    /** Держит цель на обидчике, пока тот бьёт (стоит выше всех прочих целей моба). */
    static class RevengeTargetGoal extends Goal {
        private final MobEntity mob;

        RevengeTargetGoal(MobEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.TARGET));
        }

        private boolean valid() {
            Revenge r = REVENGE.get(mob);
            if (r == null) {
                return false;
            }
            LivingEntity t = r.target();
            boolean ok = t.isAlive() && !t.isRemoved() && t.getWorld() == mob.getWorld()
                    && mob.squaredDistanceTo(t) < 40 * 40
                    && mob.getWorld().getTime() - r.lastHit() < FORGET_TICKS
                    && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
            if (!ok) {
                REVENGE.remove(mob);
            }
            return ok;
        }

        @Override
        public boolean canStart() {
            return valid();
        }

        @Override
        public boolean shouldContinue() {
            return valid();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Revenge r = REVENGE.get(mob);
            if (r != null && mob.getTarget() != r.target()) {
                mob.setTarget(r.target());
            }
        }
    }
}
