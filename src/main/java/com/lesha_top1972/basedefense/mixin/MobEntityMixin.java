package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Заморозка: замороженный моб (житель, голем…) не может бить в ближнем бою. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {

    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void basedefense$frozenAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (ModEffects.isFrozen((MobEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
