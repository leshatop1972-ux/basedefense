package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.screen.HouseScreenHandler;
import com.lesha_top1972.basedefense.structure.BuildManager;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Блок дома (дом фермера, скотовода и т.д.).
 * Дом строителя строится сам сразу после установки.
 * Остальные дома: блок остаётся на месте и становится стройплощадкой — дом строит
 * житель-строитель из материалов в сундуках своего дома. ПКМ по блоку — список материалов.
 * Дверь дома смотрит на игрока, дом и двор строятся «вглубь» от него.
 */
public class HouseBlock extends Block {

    private final HouseType type;

    public HouseBlock(Settings settings, HouseType type) {
        super(settings);
        this.type = type;
    }

    public HouseType getType() {
        return type;
    }

    public static String dimensionId(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Direction facing = placer != null ? placer.getHorizontalFacing() : Direction.NORTH;
        if (type.isSelfBuilt()) {
            BuildManager.start(serverWorld, type.getPlan(), pos, facing);
            return;
        }
        ConstructionState construction = ConstructionState.get(serverWorld.getServer());
        construction.addSite(pos, dimensionId(world), type, facing);
        if (placer instanceof PlayerEntity player) {
            boolean hasBuilder = construction.hasBuilders(dimensionId(world));
            player.sendMessage(Text.translatable(hasBuilder ? "message.basedefense.house.site_created"
                    : "message.basedefense.house.no_builder").formatted(hasBuilder ? Formatting.GREEN : Formatting.GOLD), false);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (type.isSelfBuilt()) {
            return ActionResult.PASS;
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
                    buf.writeBlockPos(pos);
                    buf.writeString(type.getId());
                }

                @Override
                public Text getDisplayName() {
                    return state.getBlock().getName();
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                    return new HouseScreenHandler(syncId, inventory, (ServerWorld) world, pos, type);
                }
            });
        }
        return ActionResult.success(world.isClient);
    }

    /** Блок сломали — стройплощадка отменяется (уже построенное остаётся). */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            ConstructionState.get(serverWorld.getServer()).removeSite(pos, dimensionId(world));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
