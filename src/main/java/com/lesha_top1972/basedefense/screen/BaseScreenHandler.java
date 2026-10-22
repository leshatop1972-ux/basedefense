package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;

/**
 * Серверная часть меню базы. Слотов пока нет — позже здесь будет
 * синхронизация исследований между сервером и клиентом.
 */
public class BaseScreenHandler extends ScreenHandler {

    private final ScreenHandlerContext context;

    /** Конструктор для клиента (вызывается, когда сервер просит открыть меню). */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    /** Конструктор для сервера. */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(ModScreenHandlers.BASE, syncId);
        this.context = context;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Меню закроется, если игрок отойдёт или блок сломают.
        return canUse(context, player, ModBlocks.BASE);
    }
}
