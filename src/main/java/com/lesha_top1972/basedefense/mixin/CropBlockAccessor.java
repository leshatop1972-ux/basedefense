package com.lesha_top1972.basedefense.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Стадия культуры (в некоторых версиях метод не публичный). */
@Mixin(CropBlock.class)
public interface CropBlockAccessor {

    @Invoker("getAge")
    int basedefense$getAge(BlockState state);
}
