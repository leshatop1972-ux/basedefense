package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.artifact.ArtifactSlot;
import com.lesha_top1972.basedefense.artifact.Artifacts;
import com.lesha_top1972.basedefense.clothing.BackpackItem;
import com.lesha_top1972.basedefense.clothing.BackpackSlot;
import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.clothing.ClothingItem;
import com.lesha_top1972.basedefense.clothing.ClothingSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Доп. слоты в инвентаре игрока — в конце, чтобы не сдвигать ванильные: артефакты (46–50), одежда (51–54)
 * и рюкзак (55). Одежда — столбик справа от брони, рюкзак — над второй рукой (видны, когда открыты кнопкой).
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void basedefense$extraSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        SimpleInventory inv = Artifacts.of(owner);
        for (int i = 0; i < Artifacts.SLOTS; i++) {
            ((ScreenHandlerAccessor) this).basedefense$addSlot(new ArtifactSlot(inv, i));
        }
        SimpleInventory clothes = Clothing.of(owner);
        for (int i = 0; i < Clothing.CLOTHES; i++) {
            ((ScreenHandlerAccessor) this).basedefense$addSlot(
                    new ClothingSlot(clothes, i, Clothing.PLAYER_X, Clothing.PLAYER_Y + i * 18, Clothing::playerViewOn));
        }
        ((ScreenHandlerAccessor) this).basedefense$addSlot(
                new BackpackSlot(clothes, Clothing.PLAYER_BACKPACK_X, Clothing.PLAYER_BACKPACK_Y, Clothing::playerViewOn));
    }

    /** Shift‑клик по одежде в инвентаре — сразу в её свободный слот одежды. */
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void basedefense$quickMoveClothing(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
        PlayerScreenHandler self = (PlayerScreenHandler) (Object) this;
        if (index < 9 || index > 44) {
            return;
        }
        Slot from = self.slots.get(index);
        if (!(from.getStack().getItem() instanceof ClothingItem) && !(from.getStack().getItem() instanceof BackpackItem)) {
            return;
        }
        for (Slot s : self.slots) {
            if ((s instanceof ClothingSlot || s instanceof BackpackSlot) && !s.hasStack() && s.canInsert(from.getStack())) {
                s.setStack(from.getStack().split(1));
                from.markDirty();
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }
    }
}
