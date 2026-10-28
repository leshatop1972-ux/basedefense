package com.lesha_top1972.basedefense.entity.enemy.nabat;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.enemy.iron_knight.IronKnightEntity;
import com.lesha_top1972.basedefense.entity.enemy.lancer.TatteredLancerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Связь Набата с остальным миром (вызывается из LivingEntityCombatMixin):
 * урон по Набату, его слугам и колоколам даёт ударившему «Шум»; смерть слуги выпускает душу «Реквиема».
 * Слуги помечены тегом {@link #TAG} (так помечаются и ванильные фантомы, и мелкие кровожады, и улан).
 */
public final class NabatHooks {

    /** Тег слуги Набата (команда /tag тоже видит его). */
    public static final String TAG = "basedefense.nabat";
    /** Тег «не выпускает душу» (колокола, якорь, плен — части самого Набата). */
    public static final String NO_SOUL = "basedefense.nabat_nosoul";

    private NabatHooks() {
    }

    public static boolean isKin(Entity e) {
        return e instanceof NabatEntity || e.getCommandTags().contains(TAG);
    }

    /** Ударивший игрок (в т.ч. стрелой). */
    static PlayerEntity playerOf(DamageSource source) {
        return source.getAttacker() instanceof PlayerEntity p ? p : null;
    }

    public static void onDamaged(LivingEntity victim, DamageSource source, float dealt) {
        if (dealt <= 0 || !isKin(victim)) {
            return;
        }
        PlayerEntity p = playerOf(source);
        if (p != null) {
            NabatNoise.add(p, dealt);
        }
    }

    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(victim.getWorld() instanceof ServerWorld sw) || victim instanceof NabatEntity
                || !victim.getCommandTags().contains(TAG) || victim.getCommandTags().contains(NO_SOUL)) {
            return;
        }
        NabatEntity nabat = nearest(sw, victim, 80.0);
        if (nabat == null || !nabat.isAlive()) {
            return;
        }
        int stacks = victim instanceof NabatBladeEntity || victim instanceof IronKnightEntity ? 2
                : (victim instanceof TatteredLancerEntity ? 8 : 1);
        NabatSoulEntity soul = ModEntities.NABAT_SOUL.create(sw);
        if (soul == null) {
            return;
        }
        soul.setup(nabat, stacks, victim instanceof TatteredLancerEntity);
        soul.refreshPositionAndAngles(victim.getX(), victim.getY() + 0.3, victim.getZ(), 0f, 0f);
        sw.spawnEntity(soul);
    }

    static NabatEntity nearest(ServerWorld w, Entity from, double radius) {
        List<NabatEntity> list = w.getEntitiesByClass(NabatEntity.class, from.getBoundingBox().expand(radius), Entity::isAlive);
        NabatEntity best = null;
        double bd = Double.MAX_VALUE;
        for (NabatEntity n : list) {
            double d = n.squaredDistanceTo(from);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }
}
