package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.needs.Needs;
import com.lesha_top1972.basedefense.needs.NeedsHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Потребности игрока: комфорт (NBT «basedefense:comfort») и ощущаемая температура. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityNeedsMixin implements NeedsHolder {

    @Unique
    private float basedefense$comfortValue = Needs.COMFORT_START;
    @Unique
    private float basedefense$feltValue = Float.NaN;

    @Override
    public float basedefense$comfort() {
        return this.basedefense$comfortValue;
    }

    @Override
    public void basedefense$setComfort(float value) {
        this.basedefense$comfortValue = value;
    }

    @Override
    public float basedefense$felt() {
        return this.basedefense$feltValue;
    }

    @Override
    public void basedefense$setFelt(float value) {
        this.basedefense$feltValue = value;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void basedefense$saveNeeds(NbtCompound nbt, CallbackInfo ci) {
        nbt.putFloat("basedefense:comfort", this.basedefense$comfortValue);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void basedefense$loadNeeds(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("basedefense:comfort")) {
            this.basedefense$comfortValue = nbt.getFloat("basedefense:comfort");
        }
    }
}
