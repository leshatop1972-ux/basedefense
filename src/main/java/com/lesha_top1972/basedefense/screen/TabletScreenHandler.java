package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.item.BuildingTabletItem;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseDesign;
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
 * Клиенту передаётся, есть ли строитель. Кнопки (номер = id):
 *   id &lt; {@link #REMEMBER_BASE} — выбрать постройку для установки: id = номер постройки × 4 + номер дизайна;
 *   id ≥ {@link #REMEMBER_BASE} — только запомнить дизайн здания (из окна дизайнов), так же закодировано.
 */
public class TabletScreenHandler extends ScreenHandler {

    public static final int PROP_HAS_BUILDER = 0;
    public static final int PROP_COUNT = 1;
    public static final int REMEMBER_BASE = 128;

    /** Номер кнопки для пары «здание + дизайн». */
    public static int encode(HouseType type, HouseDesign design) {
        return type.ordinal() * HouseDesign.values().length + design.ordinal();
    }

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
                return index == PROP_HAS_BUILDER && ConstructionState.get(server)
                        .hasBuilders(HouseBlock.dimensionId(player.getWorld())) ? 1 : 0;
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

    public boolean hasBuilder() {
        return props.get(PROP_HAS_BUILDER) != 0;
    }

    // ---------- Логика ----------

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        boolean remember = id >= REMEMBER_BASE;
        int code = remember ? id - REMEMBER_BASE : id;
        int n = HouseDesign.values().length;
        HouseType type = HouseType.byIndex(code / n);
        HouseDesign design = HouseDesign.byIndex(code % n);
        ItemStack stack = player.getStackInHand(hand);
        if (code < 0 || type == null || !(stack.getItem() instanceof BuildingTabletItem)) {
            return false;
        }
        if (remember) {
            BuildingTabletItem.rememberDesign(stack, type, design);
            return true;
        }
        BuildingTabletItem.setSelected(stack, type, design);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.getWorld().playSound(null, serverPlayer.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN,
                    SoundCategory.PLAYERS, 1.0f, 1.2f);
            serverPlayer.sendMessage(Text.translatable("message.basedefense.tablet.selected", type.getName(),
                    design.getName()).formatted(Formatting.GREEN), true);
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
