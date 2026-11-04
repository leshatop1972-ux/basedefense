package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.clothing.ClothingHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Слой одежды игрока (4 слота); сохраняется в NBT игрока («basedefense:clothing»), при смерти выпадает. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityClothingMixin implements ClothingHolder {

    @Unique
    private SimpleInventory basedefense$clothingInv;

    @Override
    public SimpleInventory basedefense$clothing() {
        if (this.basedefense$clothingInv == null) {
            this.basedefense$clothingInv = new SimpleInventory(Clothing.SLOTS);
        }
        return this.basedefense$clothingInv;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void basedefense$saveClothing(NbtCompound nbt, CallbackInfo ci) {
        nbt.put("basedefense:clothing", Clothing.toNbt(this.basedefense$clothing()));
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void basedefense$loadClothing(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("basedefense:clothing", NbtElement.LIST_TYPE)) {
            Clothing.fromNbt(this.basedefense$clothing(), nbt.getList("basedefense:clothing", NbtElement.COMPOUND_TYPE));
        }
    }

    @Inject(method = "dropInventory", at = @At("TAIL"))
    private void basedefense$dropClothing(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!self.getWorld().isClient && !self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            Clothing.dropOnDeath(self);
        }
    }
}
