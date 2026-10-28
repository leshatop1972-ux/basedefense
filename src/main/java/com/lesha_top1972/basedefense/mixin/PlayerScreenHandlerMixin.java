package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.artifact.ArtifactSlot;
import com.lesha_top1972.basedefense.artifact.Artifacts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Слоты артефактов — в конце инвентаря игрока (номера 46–50), чтобы не сдвигать ванильные слоты. */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void basedefense$artifactSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        SimpleInventory inv = Artifacts.of(owner);
        for (int i = 0; i < Artifacts.SLOTS; i++) {
            ((ScreenHandlerAccessor) this).basedefense$addSlot(new ArtifactSlot(inv, i));
        }
    }
}
