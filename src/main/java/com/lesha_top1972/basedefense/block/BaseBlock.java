package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.screen.BaseScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Блок «База». Один на весь сервер и общий для всех игроков.
 * - Сломать нельзя (как бедрок; только в творческом режиме).
 * - Если База уже стоит, новая сразу ломается и выпадает предметом.
 * - По правому клику открывает меню базы.
 */
public class BaseBlock extends Block {

    private static final Text TITLE = Text.translatable("container.basedefense.base");

    public BaseBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!(world instanceof ServerWorld serverWorld) || oldState.isOf(this)) {
            return;
        }
        BaseLocationState base = BaseLocationState.get(serverWorld.getServer());
        if (!base.hasBase() || !isBaseStillThere(serverWorld, base)) {
            base.set(world, pos);                       // это первая База — запоминаем
        } else if (!base.isAt(world, pos)) {
            world.scheduleBlockTick(pos, this, 1);      // База уже есть — сломаем эту в следующем тике
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world instanceof ServerWorld serverWorld && placer instanceof PlayerEntity player) {
            BaseLocationState base = BaseLocationState.get(serverWorld.getServer());
            if (base.hasBase() && !base.isAt(world, pos)) {
                BlockPos at = base.getPos();
                player.sendMessage(Text.translatable("message.basedefense.base.already_exists",
                        at.getX(), at.getY(), at.getZ()).formatted(Formatting.RED), false);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BaseLocationState base = BaseLocationState.get(world.getServer());
        if (base.hasBase() && !base.isAt(world, pos)) {
            world.breakBlock(pos, true);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            BaseLocationState base = BaseLocationState.get(serverWorld.getServer());
            if (base.isAt(world, pos)) {
                base.clear();                           // Базу убрали (например, в творческом) — можно ставить новую
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** На всякий случай проверяем, что сохранённая База действительно стоит на месте. */
    private static boolean isBaseStillThere(ServerWorld anyWorld, BaseLocationState base) {
        ServerWorld world = anyWorld.getServer().getWorld(base.getDimension());
        if (world == null) {
            return false;
        }
        BlockPos pos = base.getPos();
        if (!world.isChunkLoaded(pos)) {
            return true; // чанк не загружен — считаем, что База на месте
        }
        return world.getBlockState(pos).isOf(ModBlocks.BASE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        // База, поставленная до этого обновления, запоминается при первом открытии
        if (world instanceof ServerWorld serverWorld) {
            BaseLocationState base = BaseLocationState.get(serverWorld.getServer());
            if (!base.hasBase()) {
                base.set(world, pos);
            }
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
