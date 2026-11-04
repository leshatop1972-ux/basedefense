package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.clothing.Clothing;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Режим «одежда» в инвентаре игрока: ванильные слоты брони (ячейки 36–39 инвентаря) скрыты — на их месте одежда. */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    @Final
    public Inventory inventory;

    @Shadow
    public abstract int getIndex();

    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void basedefense$hideArmor(CallbackInfoReturnable<Boolean> cir) {
        if (this.inventory instanceof PlayerInventory && this.getIndex() >= 36 && this.getIndex() <= 39
                && Clothing.playerViewOn()) {
            cir.setReturnValue(false);
        }
    }
}
