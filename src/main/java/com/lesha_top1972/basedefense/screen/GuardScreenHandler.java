package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Меню стражника: оружие, 4 слота брони, 3 слота еды, 2 слота стрел, 2 слота артефактов (закрыты),
 * внизу — инвентарь игрока. Оружие и броня — прямо снаряжение стражника (видно на нём),
 * еда, стрелы и артефакты — его сумка ({@link GuardEntity#getBag()}).
 * Клиенту при открытии передаётся номер сущности (для портрета), остальное — через props.
 */
public class GuardScreenHandler extends ScreenHandler {

    // ---------- Размеры окна и места слотов (верхний левый угол предмета) ----------
    public static final int W = 256;
    public static final int H = 206;
    public static final int ARMOR_X = 82;
    public static final int ARMOR_Y = 44;
    public static final int WEAPON_X = 108;
    public static final int WEAPON_Y = 54;
    public static final int ARROW_X = 134;
    public static final int ARROW_Y = 54;
    public static final int ARTIFACT_X = 184;
    public static final int ARTIFACT_Y = 54;
    public static final int FOOD_X = 108;
    public static final int FOOD_Y = 90;
    public static final int INV_X = 48;
    public static final int INV_Y = 122;

    // ---------- Значения для клиента ----------
    public static final int PROP_ENTITY_LO = 0;
    public static final int PROP_ENTITY_HI = 1;
    public static final int PROP_HP = 2;        // ×10
    public static final int PROP_MAX_HP = 3;    // ×10
    public static final int PROP_ARMOR = 4;
    public static final int PROP_DAMAGE = 5;    // ×10
    public static final int PROP_SQUAD = 6;     // 0 — нет, 1..3
    public static final int PROP_MODE = 7;
    public static final int PROP_WEAPON = 8;    // GuardEntity.Weapon (с учётом стрел)
    public static final int PROP_COUNT = 9;

    /** Порядок слотов снаряжения в меню. */
    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_ICONS = {PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE};
    private static final Identifier SWORD_ICON = new Identifier("item/empty_slot_sword");

    /** Номера слотов в меню: 0 — оружие, 1–4 броня, 5–11 сумка, дальше — игрок. */
    private static final int GUARD_SLOTS = 12;

    @Nullable
    private final GuardEntity guard;
    private final int entityId;
    private final PropertyDelegate props;

    /** Клиент: номер сущности приходит из пакета открытия. */
    public GuardScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, inventory, null, buf.readVarInt(), new SimpleInventory(5), new SimpleInventory(GuardEntity.BAG_SIZE),
                new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Сервер. */
    public GuardScreenHandler(int syncId, PlayerInventory inventory, GuardEntity guard) {
        this(syncId, inventory, guard, guard.getId(), new EquipmentInventory(guard), guard.getBag(), serverProps(guard));
    }

    private GuardScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable GuardEntity guard, int entityId,
                               Inventory equipment, Inventory bag, PropertyDelegate props) {
        super(ModScreenHandlers.GUARD, syncId);
        this.guard = guard;
        this.entityId = entityId;
        this.props = props;
        this.addProperties(props);

        // 0 — оружие (меч, топор, лук)
        this.addSlot(new Slot(equipment, 0, WEAPON_X, WEAPON_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return GuardEntity.weaponOf(stack) != GuardEntity.Weapon.NONE;
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }

            @Override
            public Pair<Identifier, Identifier> getBackgroundSprite() {
                return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, SWORD_ICON);
            }
        });
        // 1–4 — броня
        for (int i = 0; i < 4; i++) {
            EquipmentSlot type = ARMOR[i];
            Identifier icon = ARMOR_ICONS[i];
            this.addSlot(new Slot(equipment, 1 + i, ARMOR_X, ARMOR_Y + i * 18) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return MobEntity.getPreferredEquipmentSlot(stack) == type;
                }

                @Override
                public int getMaxItemCount() {
                    return 1;
                }

                @Override
                public Pair<Identifier, Identifier> getBackgroundSprite() {
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, icon);
                }
            });
        }
        // 5–7 — еда
        for (int i = 0; i < GuardEntity.FOOD_SLOTS; i++) {
            this.addSlot(new Slot(bag, GuardEntity.FOOD_START + i, FOOD_X + i * 18, FOOD_Y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.isFood();
                }
            });
        }
        // 8–9 — стрелы
        for (int i = 0; i < GuardEntity.ARROW_SLOTS; i++) {
            this.addSlot(new Slot(bag, GuardEntity.ARROW_START + i, ARROW_X + i * 18, ARROW_Y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.getItem() instanceof ArrowItem;
                }
            });
        }
        // 10–11 — артефакты: пока закрыты (особые артефакты для стражников будут позже)
        for (int i = 0; i < GuardEntity.ARTIFACT_SLOTS; i++) {
            this.addSlot(new Slot(bag, GuardEntity.ARTIFACT_START + i, ARTIFACT_X + i * 18, ARTIFACT_Y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            });
        }
        // Игрок
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    private static PropertyDelegate serverProps(GuardEntity guard) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROP_ENTITY_LO -> guard.getId() & 0xFFFF;
                    case PROP_ENTITY_HI -> (guard.getId() >>> 16) & 0xFFFF;
                    case PROP_HP -> Math.round(guard.getHealth() * 10);
                    case PROP_MAX_HP -> Math.round(guard.getMaxHealth() * 10);
                    case PROP_ARMOR -> guard.getArmor();
                    case PROP_DAMAGE -> (int) Math.round(guard.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 10);
                    case PROP_SQUAD -> guard.getSquad() + 1;
                    case PROP_MODE -> guard.getMode();
                    case PROP_WEAPON -> guard.getUsableWeapon() == GuardEntity.Weapon.NONE
                            && guard.getWeapon() == GuardEntity.Weapon.BOW ? 4 : guard.getWeapon().ordinal();
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

    public int getProp(int index) {
        return props.get(index);
    }

    public int getEntityId() {
        return entityId;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        int end = this.slots.size();
        if (index < GUARD_SLOTS) {
            if (!this.insertItem(stack, GUARD_SLOTS, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Из инвентаря игрока — в подходящий слот стражника
            boolean moved = false;
            for (int i = 0; i < GUARD_SLOTS && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(i);
                if (target.canInsert(stack)) {
                    int before = stack.getCount();
                    if (target.getMaxItemCount() == 1) {
                        if (!target.hasStack()) {
                            target.setStack(stack.split(1));
                        }
                    } else {
                        this.insertItem(stack, i, i + 1, false);
                    }
                    moved |= stack.getCount() != before;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (guard == null) {
            return true;
        }
        return guard.isAlive() && player.squaredDistanceTo(guard) < 8.0 * 8.0;
    }

    /** Снаряжение стражника как «инвентарь» из 5 ячеек: оружие, шлем, нагрудник, поножи, ботинки. */
    private static final class EquipmentInventory implements Inventory {
        private static final EquipmentSlot[] ORDER = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private final GuardEntity guard;

        EquipmentInventory(GuardEntity guard) {
            this.guard = guard;
        }

        @Override
        public int size() {
            return ORDER.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot s : ORDER) {
                if (!guard.getEquippedStack(s).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getStack(int slot) {
            return guard.getEquippedStack(ORDER[slot]);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack stack = guard.getEquippedStack(ORDER[slot]);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(amount);
            guard.equipStack(ORDER[slot], stack.isEmpty() ? ItemStack.EMPTY : stack);
            return taken;
        }

        @Override
        public ItemStack removeStack(int slot) {
            ItemStack stack = guard.getEquippedStack(ORDER[slot]);
            guard.equipStack(ORDER[slot], ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            guard.equipStack(ORDER[slot], stack);
            guard.setEquipmentDropChance(ORDER[slot], 2.0f);
        }

        @Override
        public void markDirty() {
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return guard.isAlive();
        }

        @Override
        public void clear() {
            for (EquipmentSlot s : ORDER) {
                guard.equipStack(s, ItemStack.EMPTY);
            }
        }
    }
}
