package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.EnumSet;

import static com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.TigerTimings.*;

/**
 * «Мозг» тигра: что делать между атаками (бой не стоит на месте — обход по дуге, сближение вразвалку, отступ,
 * уклонения от ударов и стрел, подначки) и какую атаку выбрать. Ротация как у оригинала: 2 обычные атаки →
 * одна по площади («Тесак в узел», в финале — «Шквал»). Патроны кончились — перезарядка или контрстойка.
 */
final class TigerBrain {

    // паузы между действиями (тики): фаза 1 — вальяжно, фаза 2 — быстрее, финал — почти без пауз
    private static final int[][] PAUSE = {{18, 34}, {10, 22}, {6, 14}};
    /** Шанс уклониться от удара: фаза 1 вразвалку / фаза 1 в бою / фаза 2 / финал; от стрел — +15%. */
    private static final float[] DODGE = {0.30f, 0.25f, 0.22f, 0.18f};
    private static final int DODGE_CD = 50;

    private final ThunderTigerEntity t;
    int think = 30;
    private int strafeDir = 1;
    private int strafeTimer;
    private int regularCount;
    private int dodgeCd;
    private int oneCd = 300;
    private int tauntCd = 120;
    private int kickCd;
    private int dashCd;
    private int lastAttack = -1;
    private int pending = -1;
    private int pendingTicks;
    private int forced = -1;
    private int pathDelay;

    TigerBrain(ThunderTigerEntity t) {
        this.t = t;
    }

    private Random rnd() {
        return this.t.getRandom();
    }

    private int pause() {
        int[] p = PAUSE[Math.min(2, this.t.getPhase() - 1)];
        return p[0] + this.rnd().nextInt(p[1] - p[0] + 1);
    }

    void afterAction(int a) {
        this.think = this.pause();
        if (TigerActions.isDodge(a)) {
            this.think = this.forced >= 0 ? 2 : 6;
        }
        if (a == STAGGER || a == PHASE2 || a == FINAL_RISE) {
            this.think = 10;
        }
    }

    // ================================================================= уклонения
    boolean tryDodge(LivingEntity attacker, boolean ranged) {
        int a = this.t.action;
        if (this.dodgeCd > 0 || !(a == ThunderTigerEntity.NONE || a == TAUNT || a == TWIRL)
                || ModEffects.isStunned(this.t) || !this.t.isOnGround()) {
            return false;
        }
        double d = Math.sqrt(this.t.squaredDistanceTo(attacker));
        if (!ranged && d > 5.5) {
            return false;
        }
        int idx = this.t.getPhase() == 1 ? (this.t.isLazy() ? 0 : 1) : (this.t.isFinal() ? 3 : 2);
        float chance = DODGE[idx] + (ranged ? 0.15f : 0f);
        if (this.rnd().nextFloat() >= chance) {
            return false;
        }
        this.dodge(attacker);
        return true;
    }

    private void dodge(LivingEntity from) {
        double d = Math.sqrt(this.t.squaredDistanceTo(from));
        this.t.faceTarget(from, 180);
        int a;
        if (d < 2.2 && this.rnd().nextFloat() < 0.5f) {
            a = DODGE_BACK;
        } else {
            a = this.rnd().nextBoolean() ? DODGE_L : DODGE_R;
        }
        this.t.dashStart = this.t.getPos();
        TigerActions.start(this.t, a, from);
        Vec3d dir = a == DODGE_BACK ? this.t.forward().multiply(-1)
                : (a == DODGE_L ? this.t.right().multiply(-1) : this.t.right());
        TigerActions.impulse(this.t, dir, a == DODGE_BACK ? 0.95 : 1.05, 0.22);
        this.t.sound(ModSounds.TIGER_DASH, 0.9f, 1.25f);
        this.dodgeCd = DODGE_CD;
        // иногда сразу контратака
        if (this.rnd().nextFloat() < 0.35f) {
            this.forced = d < 4 ? DOUBLE : (this.t.getAmmo() > 0 ? TRIPLE_BLAST : DASH);
        }
    }

    // ================================================================= тик без цели
    void idle() {
        this.t.setLazy(true);
        if (this.dodgeCd > 0) {
            this.dodgeCd--;
        }
    }

    // ================================================================= бой
    void tick(LivingEntity target) {
        ThunderTigerEntity t = this.t;
        if (this.dodgeCd > 0) {
            this.dodgeCd--;
        }
        this.oneCd = Math.max(0, this.oneCd - 1);
        this.tauntCd = Math.max(0, this.tauntCd - 1);
        this.kickCd = Math.max(0, this.kickCd - 1);
        this.dashCd = Math.max(0, this.dashCd - 1);
        double dist = Math.sqrt(t.squaredDistanceTo(target.getX(), t.getY(), target.getZ()));
        // переход в фазу 2 по здоровью
        if (t.getPhase() == 1 && t.getHealth() <= t.getMaxHealth() * 0.5f) {
            TigerActions.start(t, PHASE2, target);
            return;
        }
        // шквал: паузы между рывками — стоит и смотрит (здесь можно уйти в сторону)
        if (t.flurryLeft > 0) {
            t.getNavigation().stop();
            t.faceTarget(target, 30);
            if (--t.flurryPause <= 0) {
                t.flurryLeft--;
                TigerActions.start(t, (t.flurryLeft % 2 == 0) ? FLURRY_A : FLURRY_B, target);
            }
            return;
        }
        // упреждающее уклонение: игрок замахнулся вплотную
        if (this.dodgeCd == 0 && target instanceof PlayerEntity p && p.handSwinging && dist < 3.6
                && this.rnd().nextFloat() < (t.getPhase() == 1 ? 0.10f : 0.14f)) {
            this.dodge(p);
            return;
        }
        boolean lazy = t.getPhase() == 1 && dist > 8.0;
        t.setLazy(lazy);
        if (--this.think <= 0 && this.choose(target, dist)) {
            return;
        }
        this.move(target, dist, lazy);
    }

    private void move(LivingEntity target, double dist, boolean lazy) {
        ThunderTigerEntity t = this.t;
        t.getLookControl().lookAt(target, 30f, 30f);
        if (this.pending >= 0) {
            // идём на дистанцию выбранной атаки
            this.pendingTicks++;
            this.runTo(target, t.getPhase() == 1 ? 1.0 : 1.3);
            return;
        }
        if (--this.strafeTimer <= 0) {
            this.strafeTimer = 30 + this.rnd().nextInt(40);
            if (this.rnd().nextFloat() < 0.6f) {
                this.strafeDir = -this.strafeDir;
            }
        }
        if (dist > 12.0 || (lazy && dist > 6.0)) {
            this.runTo(target, lazy ? 0.8 : 1.3);
        } else if (dist > 5.5) {
            t.getNavigation().stop();
            t.lookAtEntity(target, 30f, 30f);
            t.getMoveControl().strafeTo(0.35f, this.strafeDir * 0.6f);
        } else if (dist > 2.8) {
            t.getNavigation().stop();
            t.lookAtEntity(target, 30f, 30f);
            t.getMoveControl().strafeTo(0.0f, this.strafeDir * 0.65f);
        } else {
            t.getNavigation().stop();
            t.lookAtEntity(target, 30f, 30f);
            t.getMoveControl().strafeTo(-0.6f, this.strafeDir * 0.25f);
        }
    }

    private void runTo(LivingEntity target, double speed) {
        if (--this.pathDelay <= 0) {
            this.pathDelay = 8;
            this.t.getNavigation().startMovingTo(target, speed);
        }
    }

    /** Подходит ли дистанция для атаки a. */
    private static boolean inRange(int a, double d) {
        return switch (a) {
            case DOUBLE, TRIPLE -> d <= 3.8;
            case DOUBLE_BLAST -> d <= 4.5;
            case SHATTER -> d <= 4.2;
            case TRIPLE_BLAST -> d >= 2.0 && d <= 9.5;
            case TANGLE -> d <= 12.0;
            case ONE_WAIT -> d >= 3.0 && d <= 12.0;
            case STANCE -> d <= 7.0;
            case KICK -> d <= 2.4;
            case BACKSHOT -> d <= 3.5;
            case DASH -> d >= 4.5;
            default -> true;
        };
    }

    /** Выбрать и начать действие. true — началось. */
    private boolean choose(LivingEntity target, double d) {
        ThunderTigerEntity t = this.t;
        int a = -1;
        if (this.forced >= 0) {
            a = this.forced;
            this.forced = -1;
            if (!inRange(a, d)) {
                a = d > 4.5 ? DASH : DOUBLE;
            }
        } else if (this.pending >= 0) {
            if (inRange(this.pending, d)) {
                a = this.pending;
                this.pending = -1;
            } else if (this.pendingTicks > 50) {
                this.pending = -1;
                a = d >= 4.5 && this.dashCd == 0 ? DASH : -1;
            } else {
                this.think = 1;
                return false;
            }
        } else {
            a = this.pick(d);
            if (a >= 0 && !inRange(a, d)) {
                this.pending = a;
                this.pendingTicks = 0;
                this.think = 1;
                return false;
            }
        }
        if (a < 0) {
            this.think = 6;
            return false;
        }
        this.begin(a, target);
        return true;
    }

    private void begin(int a, LivingEntity target) {
        ThunderTigerEntity t = this.t;
        if (a == DASH) {
            this.dashCd = 60;
        }
        if (a == KICK) {
            this.kickCd = 120;
        }
        if (a == ONE_WAIT) {
            this.oneCd = 700;
        }
        if (a == TAUNT || a == TWIRL) {
            this.tauntCd = 320;
        }
        if (TigerActions.regular(a) || a == ONE_WAIT) {
            this.regularCount++;
            this.lastAttack = a;
        }
        if (a == TANGLE || a == -2) {
            this.regularCount = 0;
        }
        t.faceTarget(target, 60);
        if (a == FLURRY_A) {
            // финальная ульта: серия рывков насквозь
            this.regularCount = 0;
            t.flurryLeft = ThunderTigerEntity.FLURRY_DASHES - 1;
            t.speak("flurry");
            t.sound(ModSounds.TIGER_IGNITE, 1.6f, 1.2f);
        }
        TigerActions.start(t, a, target);
    }

    private int pick(double d) {
        ThunderTigerEntity t = this.t;
        Random r = this.rnd();
        int ammo = t.getAmmo();
        int phase = t.getPhase();
        // патроны: пусто — перезарядка (или контрстойка, если враг рядом)
        if (ammo == 0) {
            if (d > 7.0) {
                return RELOAD;
            }
            return r.nextFloat() < 0.65f ? STANCE : (d > 4 ? RELOAD : STANCE);
        }
        if (ammo <= 2 && d > 9.0 && r.nextFloat() < 0.5f) {
            return RELOAD_TAC;
        }
        // вальяжность: подначка или вращение тесаком (только фаза 1, издалека)
        if (phase == 1 && d > 6.0 && this.tauntCd == 0 && r.nextFloat() < 0.3f) {
            return r.nextBoolean() ? TAUNT : TWIRL;
        }
        // ротация: после двух обычных — по площади
        if (this.regularCount >= 2) {
            this.regularCount = 0;
            return t.isFinal() ? FLURRY_A : TANGLE;
        }
        if (this.oneCd == 0 && d >= 3 && d <= 12 && r.nextFloat() < 0.18f) {
            return ONE_WAIT;
        }
        if (d < 2.4 && this.kickCd == 0 && r.nextFloat() < 0.35f) {
            return KICK;
        }
        if (d < 2.6 && ammo > 0 && r.nextFloat() < 0.18f) {
            return BACKSHOT;
        }
        if (d > 9.5) {
            return this.dashCd == 0 ? DASH : TRIPLE_BLAST;
        }
        // обычные: по дистанции, без повтора подряд
        int[] cand;
        int[] w;
        if (d <= 4.2) {
            cand = new int[]{DOUBLE, TRIPLE, DOUBLE_BLAST, SHATTER};
            w = new int[]{3, 3, 2, phase >= 2 ? 3 : 1};
        } else {
            cand = new int[]{TRIPLE_BLAST, DOUBLE, TRIPLE, SHATTER};
            w = new int[]{4, 1, 1, phase >= 2 ? 1 : 0};
        }
        int sum = 0;
        for (int i = 0; i < cand.length; i++) {
            if (cand[i] == this.lastAttack) {
                w[i] = w[i] / 2;
            }
            sum += w[i];
        }
        if (sum <= 0) {
            return DOUBLE;
        }
        int x = r.nextInt(sum);
        for (int i = 0; i < cand.length; i++) {
            x -= w[i];
            if (x < 0) {
                return cand[i];
            }
        }
        return DOUBLE;
    }

    // ================================================================= цели
    private static boolean valid(LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    /** Бой с целью: движение, выбор атак; во время действия ничего не делает (действие ведёт само себя). */
    static final class CombatGoal extends Goal {
        private final ThunderTigerEntity t;

        CombatGoal(ThunderTigerEntity t) {
            this.t = t;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            return this.t.action != ThunderTigerEntity.NONE || valid(this.t.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.t.getNavigation().stop();
            this.t.brain.idle();
        }

        @Override
        public void tick() {
            if (this.t.action != ThunderTigerEntity.NONE) {
                this.t.getNavigation().stop();
                return;
            }
            LivingEntity target = this.t.getTarget();
            if (valid(target)) {
                this.t.brain.tick(target);
            } else {
                this.t.brain.idle();
            }
        }
    }

    /** Цель: «Добыча» (если рядом), иначе ближайший игрок; ответ тому, кто бьёт. */
    static final class PreyTargetGoal extends Goal {
        private final ThunderTigerEntity t;
        private int timer;

        PreyTargetGoal(ThunderTigerEntity t) {
            this.t = t;
            this.setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public void tick() {
            if (--this.timer > 0) {
                return;
            }
            this.timer = 20;
            ThunderTigerEntity t = this.t;
            if (t.action != ThunderTigerEntity.NONE && valid(t.getTarget())) {
                return;
            }
            LivingEntity cur = t.getTarget();
            if (t.prey != null && t.getWorld().getPlayerByUuid(t.prey) instanceof PlayerEntity p && valid(p)
                    && p.squaredDistanceTo(t) < 36 * 36) {
                if (cur != p) {
                    t.setTarget(p);
                }
                return;
            }
            if (valid(cur) && cur.squaredDistanceTo(t) < 44 * 44) {
                return;
            }
            PlayerEntity best = null;
            double bd = 40 * 40;
            for (PlayerEntity p : t.playersNear(40)) {
                double dd = p.squaredDistanceTo(t);
                if (dd < bd) {
                    bd = dd;
                    best = p;
                }
            }
            LivingEntity attacker = t.getAttacker();
            if (best == null && valid(attacker) && attacker.squaredDistanceTo(t) < 32 * 32) {
                t.setTarget(attacker);
                return;
            }
            t.setTarget(best);
        }
    }
}
