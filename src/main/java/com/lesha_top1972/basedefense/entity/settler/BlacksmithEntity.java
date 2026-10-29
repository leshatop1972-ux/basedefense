package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.block.BaseStorage;
import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Житель-кузнец. Живёт в кузнице и куёт пустые кулоны для стола амулетов:
 * берёт 3 железных слитка, 1 золотой слиток и 5 редстоуна (сначала из сундука кузницы, потом со склада Базы),
 * работает у горна 3 минуты (2.5 на 2-м уровне, 2 на 3-м), кладёт кулон в сундук кузницы —
 * оттуда курьер относит его на склад Базы. Ночью спит (работа на паузе).
 * Если на складе и в кузнице уже {@link #PENDANT_LIMIT} кулонов — отдыхает, чтобы не тратить всё железо.
 * Кнопка в меню «Ковка кулонов» включает и выключает работу.
 */
public class BlacksmithEntity extends SettlerEntity {

    public static final int STATUS_NO_MATERIALS = 1;
    public static final int STATUS_FORGING = 2;
    public static final int STATUS_ENOUGH = 3;
    public static final int STATUS_OFF = 4;
    public static final int STATUS_RESTING = 5;

    /** Больше стольких кулонов (склад + кузница) не куёт. */
    public static final int PENDANT_LIMIT = 8;
    /** Время ковки одного кулона по уровням (тики). */
    private static final int[] FORGE_TICKS = {0, 3 * 60 * 20, 150 * 20, 2 * 60 * 20};

    public static final int IRON = 3;
    public static final int GOLD = 1;
    public static final int REDSTONE = 5;

    private boolean forgeOn = true;
    /** Сколько тиков уже куёт, -1 — не куёт. */
    private int forgeTicks = -1;
    private int status = STATUS_NO_MATERIALS;

    public BlacksmithEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.STONE_PICKAXE);
    }

    // ---------- Профессия ----------

    @Override
    public boolean hasJob() {
        return true;
    }

    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 8;
            case 3 -> 10;
            default -> 6;
        };
    }

    /** К горну ходит только пока куёт. */
    @Override
    public boolean isWorking() {
        return forgeTicks >= 0;
    }

    /** Горн (магма), точило и наковальня-плита. */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.GRINDSTONE) || state.isOf(Blocks.POLISHED_ANDESITE);
    }

    public int getSmithStatus() {
        return status;
    }

    /** Готовность кулона, %. */
    public int getForgeProgress() {
        return forgeTicks < 0 ? 0 : Math.min(100, forgeTicks * 100 / forgeTime());
    }

    private int forgeTime() {
        return FORGE_TICKS[Math.max(1, Math.min(MAX_LEVEL, getLevel()))];
    }

    // ---------- Переключатель «Ковка кулонов» ----------

    @Override
    public boolean hasWorkToggle() {
        return true;
    }

    @Override
    public boolean isWorkToggleOn() {
        return forgeOn;
    }

    @Override
    public void flipWorkToggle(PlayerEntity player) {
        forgeOn = !forgeOn;
        player.sendMessage(Text.translatable(forgeOn ? "message.basedefense.blacksmith.forge_on"
                : "message.basedefense.blacksmith.forge_off", this.getDisplayName())
                .formatted(forgeOn ? Formatting.GREEN : Formatting.GRAY), true);
    }

    // ---------- Работа ----------

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world) || !this.isAlive() || this.age % 20 != 0) {
            return;
        }
        if (isResting() || isSettlementFrozen()) {
            status = STATUS_RESTING;
            return;
        }
        if (forgeTicks >= 0) {
            status = STATUS_FORGING;
            forgeTicks += 20;
            if (this.age % 60 == 0) {
                this.swingHand(Hand.MAIN_HAND);
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.25f,
                        1.2f + this.random.nextFloat() * 0.3f);
                world.spawnParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
            }
            if (forgeTicks >= forgeTime()) {
                forgeTicks = -1;
                storeItem(new ItemStack(ModItems.EMPTY_PENDANT));
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.NEUTRAL, 0.8f, 1.1f);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.6, this.getZ(),
                        6, 0.3, 0.3, 0.3, 0.0);
            }
            return;
        }
        if (!forgeOn) {
            status = STATUS_OFF;
            return;
        }
        // Раз в 5 секунд смотрим, есть ли материалы и не хватит ли кулонов
        if (this.age % 100 != 0 && status != STATUS_FORGING) {
            return;
        }
        List<Inventory> sources = sources(world);
        if (count(sources, ModItems.EMPTY_PENDANT) >= PENDANT_LIMIT) {
            status = STATUS_ENOUGH;
            return;
        }
        if (count(sources, Items.IRON_INGOT) < IRON || count(sources, Items.GOLD_INGOT) < GOLD
                || count(sources, Items.REDSTONE) < REDSTONE) {
            status = STATUS_NO_MATERIALS;
            return;
        }
        remove(sources, Items.IRON_INGOT, IRON);
        remove(sources, Items.GOLD_INGOT, GOLD);
        remove(sources, Items.REDSTONE, REDSTONE);
        forgeTicks = 0;
        status = STATUS_FORGING;
        world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.NEUTRAL, 0.4f, 1.1f);
    }

    /** Сундук кузницы, потом сундуки склада Базы. */
    private List<Inventory> sources(ServerWorld world) {
        List<Inventory> list = new ArrayList<>();
        Inventory own = getHomeChest(world);
        if (own != null) {
            list.add(own);
        }
        for (BlockPos pos : BaseStorage.chests(world)) {
            if (world.isChunkLoaded(pos)) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof Inventory inv && inv != own) {
                    list.add(inv);
                }
            }
        }
        return list;
    }

    private static int count(List<Inventory> sources, Item item) {
        int n = 0;
        for (Inventory inv : sources) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isOf(item)) {
                    n += stack.getCount();
                }
            }
        }
        return n;
    }

    private static void remove(List<Inventory> sources, Item item, int amount) {
        for (Inventory inv : sources) {
            boolean changed = false;
            for (int i = 0; i < inv.size() && amount > 0; i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isOf(item)) {
                    int take = Math.min(amount, stack.getCount());
                    stack.decrement(take);
                    amount -= take;
                    changed = true;
                }
            }
            if (changed) {
                inv.markDirty();
            }
            if (amount <= 0) {
                return;
            }
        }
    }

    // ---------- Сохранение ----------

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("ForgeOn", forgeOn);
        nbt.putInt("ForgeTicks", forgeTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        forgeOn = !nbt.contains("ForgeOn") || nbt.getBoolean("ForgeOn");
        forgeTicks = nbt.contains("ForgeTicks") ? nbt.getInt("ForgeTicks") : -1;
    }
}
