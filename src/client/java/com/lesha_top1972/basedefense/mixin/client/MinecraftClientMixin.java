package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.weapon.BloodSwordClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пока ЛКМ зажата для замаха мечом кровожада — блоки не ломаются. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void basedefense$bloodSwordNoMining(boolean breaking, CallbackInfo ci) {
        if (breaking && BloodSwordClient.localBusy()) {
            if (this.interactionManager != null) {
                this.interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }
}
