package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.item.BuildingTabletItem;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * Серверная часть меню строительного планшета.
 * Клиенту передаются исследования и есть ли строитель; нажатие кнопки = номер постройки (HouseType.ordinal),
 * сервер проверяет исследование и записывает выбор в планшет.
 */
public class TabletScreenHandler extends ScreenHandler {

    public static final int PROP_RESEARCH = 0;
    public static final int PROP_HAS_BUILDER = 1;
    public static final int PROP_COUNT = 2;

    private final PropertyDelegate props;
    private final Hand hand;

    /** Конструктор для клиента. */
    public TabletScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, Hand.MAIN_HAND, new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Конструктор для сервера. */
    public TabletScreenHandler(int syncId, PlayerInventory playerInventory, Hand hand) {
        this(syncId, hand, serverProps(playerInventory.player));
    }

    private TabletScreenHandler(int syncId, Hand hand, PropertyDelegate props) {
        super(ModScreenHandlers.TABLET, syncId);
        this.hand = hand;
        this.props = props;
        this.addProperties(props);
    }

    private static PropertyDelegate serverProps(PlayerEntity player) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return 0;
                }
                return switch (index) {
                    case PROP_RESEARCH -> ResearchManager.researchMask(server);
                    case PROP_HAS_BUILDER -> ConstructionState.get(server)
                            .hasBuilders(HouseBlock.dimensionId(player.getWorld())) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return PROP_COUNT;
            }
        };
    }

    // ---------- Для экрана (клиент) ----------

    public boolean isUnlocked(HouseType type) {
        return type.isUnlocked(props.get(PROP_RESEARCH));
    }

    public boolean hasBuilder() {
        return props.get(PROP_HAS_BUILDER) != 0;
    }

    // ---------- Логика ----------

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        HouseType type = HouseType.byIndex(id);
        ItemStack stack = player.getStackInHand(hand);
        if (type == null || !(stack.getItem() instanceof BuildingTabletItem) || !isUnlocked(type)) {
            return false;
        }
        BuildingTabletItem.setSelected(stack, type);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.getWorld().playSound(null, serverPlayer.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN,
                    SoundCategory.PLAYERS, 1.0f, 1.2f);
            serverPlayer.sendMessage(Text.translatable("message.basedefense.tablet.selected", type.getName())
                    .formatted(Formatting.GREEN), true);
            serverPlayer.closeHandledScreen();
        }
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.getStackInHand(hand).getItem() instanceof BuildingTabletItem;
    }
}
