package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.screen.BaseScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Блок «База». По правому клику открывает меню базы.
 * Меню открывается на сервере, клиенту приходит только команда показать экран.
 */
public class BaseBlock extends Block {

    private static final Text TITLE = Text.translatable("container.basedefense.base");

    public BaseBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
        return ActionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) ->
                        new BaseScreenHandler(syncId, playerInventory, ScreenHandlerContext.create(world, pos)),
                TITLE);
    }
}
