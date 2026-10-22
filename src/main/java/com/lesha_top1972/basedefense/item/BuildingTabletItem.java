package com.lesha_top1972.basedefense.item;

import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.TabletScreenHandler;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Строительный планшет. Им ставятся все постройки поселения.
 * - ПКМ в воздух (или по блоку, когда ничего не выбрано) — меню выбора постройки.
 *   В меню открыты дом строителя и всё, что исследовано в Базе.
 * - Выбрал постройку — наводишь на место (видна рамка) и жмёшь ПКМ по блоку:
 *   там появляется блок постройки (стройплощадка), дальше всё как раньше — строит строитель.
 * Выбранная постройка хранится в NBT планшета ("House").
 */
public class BuildingTabletItem extends Item {

    private static final String NBT_HOUSE = "House";

    public BuildingTabletItem(Settings settings) {
        super(settings);
    }

    // ---------- Выбранная постройка ----------

    @Nullable
    public static HouseType getSelected(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_HOUSE)) {
            return null;
        }
        return HouseType.byId(nbt.getString(NBT_HOUSE));
    }

    public static void setSelected(ItemStack stack, @Nullable HouseType type) {
        if (type == null) {
            stack.removeSubNbt(NBT_HOUSE);
        } else {
            stack.getOrCreateNbt().putString(NBT_HOUSE, type.getId());
        }
    }

    /** Планшет в руке игрока (сначала основная рука, потом вторая) или пустой стек. */
    public static ItemStack findInHands(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BuildingTabletItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------- Использование ----------

    /** ПКМ в воздух — меню выбора постройки. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            openMenu(serverPlayer, hand);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    /** ПКМ по блоку: ничего не выбрано — меню; выбрано — ставим стройплощадку. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();
        HouseType type = getSelected(stack);
        if (player == null) {
            return ActionResult.PASS;
        }
        if (type == null) {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                openMenu(serverPlayer, context.getHand());
            }
            return ActionResult.success(world.isClient);
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        ServerWorld serverWorld = (ServerWorld) world;

        // Исследование могли «потерять» (например, другой мир) — проверяем ещё раз
        if (!type.isUnlocked(ResearchManager.researchMask(serverWorld.getServer()))) {
            setSelected(stack, null);
            player.sendMessage(Text.translatable("message.basedefense.tablet.locked", type.getName())
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        // Куда ставить: в саму клетку, если она «заменяемая» (трава, снег), иначе — рядом, как обычный блок
        BlockPos clicked = context.getBlockPos();
        BlockPos pos = world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(context.getSide());
        BlockState current = world.getBlockState(pos);
        if (!current.isReplaceable() || !world.canPlayerModifyAt(player, pos)
                || !player.canPlaceOn(pos, context.getSide(), stack)) {
            player.sendMessage(Text.translatable("message.basedefense.tablet.cant_place")
                    .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        Block block = ModBlocks.houseBlock(type);
        world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.9f);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        HouseBlock.startConstruction(serverWorld, pos, type, player.getHorizontalFacing(), player);

        // Одна постройка за раз: после установки выбор сбрасывается
        setSelected(stack, null);
        return ActionResult.CONSUME;
    }

    private static void openMenu(ServerPlayerEntity player, Hand hand) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, p) -> new TabletScreenHandler(syncId, inventory, hand),
                Text.translatable("screen.basedefense.tablet.title")));
    }

    // ---------- Вид ----------

    /** Когда постройка выбрана, планшет «светится». */
    @Override
    public boolean hasGlint(ItemStack stack) {
        return getSelected(stack) != null;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        HouseType type = getSelected(stack);
        if (type != null) {
            tooltip.add(Text.translatable("item.basedefense.building_tablet.selected", type.getName())
                    .formatted(Formatting.GOLD));
            tooltip.add(Text.translatable("item.basedefense.building_tablet.hint_place").formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("item.basedefense.building_tablet.hint_menu").formatted(Formatting.GRAY));
        }
    }
}
