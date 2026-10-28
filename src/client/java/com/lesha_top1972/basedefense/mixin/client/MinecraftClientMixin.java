package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.weapon.BloodSwordClient;
import com.lesha_top1972.basedefense.client.weapon.KatanaClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Меч кровожада (замах) и катана: блоки не ломаются; у катаны ЛКМ — своя серия ударов вместо ванильного. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void basedefense$katanaAttack(CallbackInfoReturnable<Boolean> cir) {
        if (KatanaClient.onAttack()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void basedefense$bloodSwordNoMining(boolean breaking, CallbackInfo ci) {
        if (breaking && (BloodSwordClient.localBusy() || KatanaClient.holdingLocal())) {
            if (this.interactionManager != null) {
                this.interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }
}
