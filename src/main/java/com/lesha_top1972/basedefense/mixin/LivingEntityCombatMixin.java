package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Бой:
 * <ul>
 *   <li>«Подлая крыса»: у атакующего с этим эффектом урон ×0.8;</li>
 *   <li>щиты: удар, принятый щитом, не обнуляется, а ослабляется на 85%; отбрасывания нет
 *       (в этот тик takeKnockback отменяется). Без повторного вызова damage — сложность
 *       мира не применяется дважды.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCombatMixin implements ShieldBlocker {

    /** Доля урона, проходящая сквозь щит. */
    @Unique
    private static final float BASEDEFENSE$SHIELD_PASS = 0.15f;

    @Unique
    private long basedefense$shieldTick = -1;
    /** Пока true — текущий удар уже обработан нашим щитом, ванильная блокировка отключена. */
    @Unique
    private boolean basedefense$partial = false;

    @Shadow
    protected boolean dead;

    @Shadow
    public abstract boolean blockedByShield(DamageSource source);

    @Shadow
    protected abstract void damageShield(float amount);

    @Shadow
    protected abstract void takeShieldHit(LivingEntity attacker);

    @Override
    public boolean basedefense$blockedThisTick() {
        LivingEntity self = (LivingEntity) (Object) this;
        return this.basedefense$shieldTick == self.getWorld().getTime();
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float basedefense$modifyDamage(float amount, DamageSource source, float original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (source.getAttacker() instanceof LivingEntity attacker && attacker.hasStatusEffect(ModEffects.SNEAKY_RAT)) {
            amount *= 0.8f;                                      // «Подлая крыса»: −20% урона
        }
        this.basedefense$partial = false;
        if (self.getWorld().isClient || amount <= 0 || !this.blockedByShield(source)) {
            return amount;
        }
        // Щит: не обнуляет удар, а ослабляет на 85%.
        this.damageShield(amount);
        if (!source.isIn(DamageTypeTags.IS_PROJECTILE) && source.getSource() instanceof LivingEntity attacker) {
            this.takeShieldHit(attacker);
        }
        self.getWorld().sendEntityStatus(self, (byte) 29);       // звук и вид блока щитом
        this.basedefense$shieldTick = self.getWorld().getTime();
        this.basedefense$partial = true;
        return amount * BASEDEFENSE$SHIELD_PASS;
    }

    /** Здоровье до удара — чтобы знать, сколько урона прошло на самом деле (для «Шума» Набата). */
    @Unique
    private float basedefense$hpBefore;

    @Inject(method = "damage", at = @At("HEAD"))
    private void basedefense$rememberHp(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.basedefense$hpBefore = self.getHealth() + self.getAbsorptionAmount();
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void basedefense$endDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.basedefense$partial = false;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && cir.getReturnValueZ()) {
            float dealt = this.basedefense$hpBefore - (self.getHealth() + self.getAbsorptionAmount());
            NabatHooks.onDamaged(self, source, dealt);
            com.lesha_top1972.basedefense.artifact.Artifacts.onDamaged(self, source, dealt);
            com.lesha_top1972.basedefense.entity.settler.SettlerThreats.onDamaged(self, source);
        }
    }

    /** Смерть слуги Набата выпускает душу «Реквиема». */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void basedefense$onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && !self.isRemoved() && !this.dead) {
            NabatHooks.onDeath(self, source);
        }
    }

    @Inject(method = "blockedByShield", at = @At("HEAD"), cancellable = true)
    private void basedefense$noDoubleBlock(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (this.basedefense$partial) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void basedefense$noShieldKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (this.basedefense$partial || this.basedefense$blockedThisTick()) {
            ci.cancel();
        }
    }
}
