package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.research.Technology;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Серверная часть меню базы.
 * - Передаёт клиенту, какие технологии уже исследованы (через PropertyDelegate).
 * - Принимает нажатие кнопки «Исследовать» (номер кнопки = номер технологии).
 */
public class BaseScreenHandler extends ScreenHandler {

    private final ScreenHandlerContext context;
    private final PropertyDelegate research;

    /** Конструктор для клиента (вызывается, когда сервер просит открыть меню). */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY, new ArrayPropertyDelegate(1));
    }

    /** Конструктор для сервера. */
    public BaseScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        this(syncId, playerInventory, context, serverResearch(playerInventory.player));
    }

    private BaseScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context,
                              PropertyDelegate research) {
        super(ModScreenHandlers.BASE, syncId);
        this.context = context;
        this.research = research;
        this.addProperties(research);
    }

    /** На сервере значение каждый раз берётся из сохранённых исследований игрока. */
    private static PropertyDelegate serverResearch(PlayerEntity player) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return player instanceof ServerPlayerEntity serverPlayer ? ResearchManager.researchMask(serverPlayer) : 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }

    /** Исследована ли технология (работает на клиенте). */
    public boolean isResearched(Technology tech) {
        return (research.get(0) & tech.bit()) != 0;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        Technology tech = Technology.byIndex(id);
        if (tech == null) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ResearchManager.tryResearch(serverPlayer, tech);
        }
        return true;
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
