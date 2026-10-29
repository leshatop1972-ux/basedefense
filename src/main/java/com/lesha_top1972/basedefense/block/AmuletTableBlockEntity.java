package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.artifact.AmuletRecipes;
import com.lesha_top1972.basedefense.artifact.ArtifactItem;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.AmuletTableScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Стол амулетов: слоты 0 и 1 — амулеты, 2 — пустой кулон, 3 — результат.
 * Кнопка «Начать» забирает амулеты и кулон (они хранятся внутри, пока идёт крафт) и запускает
 * 8 минут работы. Время считается по часам мира (getTime), так что идёт, даже если рядом никого нет.
 * В конце результат выпадает по шансам рецепта и кладётся в слот 3, игроку, начавшему крафт, — сообщение.
 */
public class AmuletTableBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int PENDANT = 2;
    public static final int RESULT = 3;
    public static final int SIZE = 4;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    /** Что забрано на крафт (выпадет, если стол сломать во время работы). */
    private final DefaultedList<ItemStack> consumed = DefaultedList.ofSize(3, ItemStack.EMPTY);
    /** Номер рецепта в работе, -1 — стол свободен. */
    private int recipe = -1;
    private long endTime;
    private int totalTicks;
    @Nullable
    private UUID crafter;

    public AmuletTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMULET_TABLE, pos, state);
    }

    // ---------- Состояние ----------

    public boolean isCrafting() {
        return recipe >= 0;
    }

    public int getRecipe() {
        return recipe;
    }

    public int remainingTicks() {
        if (!isCrafting() || world == null) {
            return 0;
        }
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, endTime - world.getTime()));
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    /** Можно ли начать: стол свободен, слот результата пуст, есть кулон и подходящая пара амулетов. */
    public boolean canStart() {
        return !isCrafting() && items.get(RESULT).isEmpty() && items.get(PENDANT).isOf(ModItems.EMPTY_PENDANT)
                && AmuletRecipes.find(items.get(LEFT), items.get(RIGHT)) >= 0;
    }

    /** Кнопка «Начать крафт» (сервер). */
    public void start(ServerPlayerEntity player) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!canStart()) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.6f, 0.6f);
            return;
        }
        recipe = AmuletRecipes.find(items.get(LEFT), items.get(RIGHT));
        consumed.set(0, items.get(LEFT).split(1));
        consumed.set(1, items.get(RIGHT).split(1));
        consumed.set(2, items.get(PENDANT).split(1));
        totalTicks = player.isCreative() ? AmuletRecipes.CREATIVE_TICKS : AmuletRecipes.CRAFT_TICKS;
        endTime = serverWorld.getTime() + totalTicks;
        crafter = player.getUuid();
        markDirty();
        serverWorld.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 0.9f);
        serverWorld.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.2f);
        serverWorld.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
                30, 0.3, 0.3, 0.3, 0.6);
    }

    public static void tick(World world, BlockPos pos, BlockState state, AmuletTableBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld) || !be.isCrafting()) {
            return;
        }
        long now = world.getTime();
        if (now % 20 == 0) {
            serverWorld.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
                    3, 0.25, 0.2, 0.25, 0.4);
        }
        if (now % 100 == 0) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f, 1.4f);
        }
        if (now >= be.endTime && be.items.get(RESULT).isEmpty()) {
            be.finish(serverWorld);
        }
    }

    private void finish(ServerWorld world) {
        AmuletRecipes.Recipe r = AmuletRecipes.get(recipe);
        Item result = r != null ? r.roll(world.getRandom()) : null;
        if (result != null) {
            items.set(RESULT, new ItemStack(result));
            consumed.clear();
        } else {
            // рецепт пропал (обновление мода) — возвращаем всё как было
            for (int i = 0; i < 3; i++) {
                if (items.get(i).isEmpty()) {
                    items.set(i, consumed.get(i));
                } else {
                    ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, consumed.get(i));
                }
            }
            consumed.clear();
        }
        UUID who = crafter;
        recipe = -1;
        crafter = null;
        markDirty();

        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 1.0f, 1.3f);
        world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6f, 1.5f);
        world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
                20, 0.2, 0.3, 0.2, 0.08);
        if (who != null && result != null) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(who);
            if (player != null) {
                player.sendMessage(Text.translatable("message.basedefense.amulet_table.done",
                        new ItemStack(result).getName()).formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
    }

    /** Стол сломали: выпадает всё, в том числе то, что было в работе. */
    public void dropAll(World world, BlockPos pos) {
        ItemScatterer.spawn(world, pos, this);
        ItemScatterer.spawn(world, pos, consumed);
        consumed.clear();
    }

    // ---------- Меню ----------

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.basedefense.amulet_table");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return new AmuletTableScreenHandler(syncId, inventory, this);
    }

    // ---------- Inventory ----------

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = Inventories.splitStack(items, slot, amount);
        if (!stack.isEmpty()) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        markDirty();
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return switch (slot) {
            case LEFT, RIGHT -> stack.getItem() instanceof ArtifactItem;
            case PENDANT -> stack.isOf(ModItems.EMPTY_PENDANT);
            default -> false;
        };
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        items.clear();
    }

    // ---------- Сохранение ----------

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        NbtCompound work = new NbtCompound();
        Inventories.writeNbt(work, consumed);
        nbt.put("Work", work);
        nbt.putInt("Recipe", recipe);
        nbt.putLong("End", endTime);
        nbt.putInt("Total", totalTicks);
        if (crafter != null) {
            nbt.putUuid("Crafter", crafter);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
        consumed.clear();
        Inventories.readNbt(nbt.getCompound("Work"), consumed);
        recipe = nbt.contains("Recipe") ? nbt.getInt("Recipe") : -1;
        endTime = nbt.getLong("End");
        totalTicks = nbt.getInt("Total");
        crafter = nbt.containsUuid("Crafter") ? nbt.getUuid("Crafter") : null;
    }
}
