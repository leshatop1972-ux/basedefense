package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.artifact.ArtifactHolder;
import com.lesha_top1972.basedefense.artifact.Artifacts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** 5 слотов артефактов у игрока; сохраняются в NBT игрока («basedefense:artifacts»). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityArtifactsMixin implements ArtifactHolder {

    @Unique
    private SimpleInventory basedefense$artifactInv;

    @Override
    public SimpleInventory basedefense$artifacts() {
        if (this.basedefense$artifactInv == null) {
            this.basedefense$artifactInv = new SimpleInventory(Artifacts.SLOTS);
        }
        return this.basedefense$artifactInv;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void basedefense$saveArtifacts(NbtCompound nbt, CallbackInfo ci) {
        SimpleInventory inv = this.basedefense$artifacts();
        NbtList list = new NbtList();
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                NbtCompound c = inv.getStack(i).writeNbt(new NbtCompound());
                c.putByte("Slot", (byte) i);
                list.add(c);
            }
        }
        nbt.put("basedefense:artifacts", list);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void basedefense$loadArtifacts(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("basedefense:artifacts", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("basedefense:artifacts", NbtElement.COMPOUND_TYPE);
            SimpleInventory inv = this.basedefense$artifacts();
            inv.clear();
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                int slot = c.getByte("Slot");
                if (slot >= 0 && slot < inv.size()) {
                    inv.setStack(slot, ItemStack.fromNbt(c));
                }
            }
        }
    }

    /** Амулеты уворота: атака моба может не пройти (до щита, брони и отбрасывания). */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void basedefense$dodge(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity && amount > 0
                && Artifacts.tryDodge((ServerPlayerEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }
}
