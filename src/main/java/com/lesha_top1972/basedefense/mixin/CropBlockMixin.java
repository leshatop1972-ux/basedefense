package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.farming.Crops;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Культуры: вдвое медленнее, нужен свет, зависят от температуры (см. Crops). */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void basedefense$growth(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (Crops.blockGrowth((CropBlock) (Object) this, state, world, pos, random)) {
            ci.cancel();
        }
    }
}
