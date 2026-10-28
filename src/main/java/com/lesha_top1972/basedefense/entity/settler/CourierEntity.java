package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.block.BaseStorage;
import com.lesha_top1972.basedefense.settlement.SettlementItems;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.settlement.SettlementState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Курьер — живёт в здании Базы. Ходит по поселению:
 *  1) забирает добычу из сундуков домов (фермер, скотовод, повар, шахтёр, кузнец, лесоруб) и относит
 *     на склад Базы (сундуки вокруг блока «База»);
 *  2) когда запас еды поселения меньше чем на 3 дня — сдаёт еду прямо в блок «База»;
 *  3) носит повару продукты со склада, если у повара их меньше чем на 2 дня.
 * Строитель сам берёт материалы со склада. Сундуки строителя курьер не трогает.
 * Чем выше уровень — тем быстрее ходит и тем больше уносит за раз (4 / 6 / 9 стопок).
 * Ночью спит.
 */
public class CourierEntity extends SettlerEntity {

    // ---------- Состояние для меню ----------
    public static final int STATUS_IDLE = 1;      // всё разнесено, ждёт
    public static final int STATUS_COLLECT = 2;   // идёт за добычей к дому
    public static final int STATUS_DELIVER = 3;   // несёт на склад Базы
    public static final int STATUS_FEED = 4;      // сдаёт еду в Базу
    public static final int STATUS_COOK = 5;      // несёт продукты повару
    public static final int STATUS_NO_BASE = 6;   // нет блока «База»

    private static final int[] CARRY_STACKS = {0, 4, 6, 9};
    private static final double[] SPEED = {0, 0.75, 0.95, 1.2};
    /** Насколько далеко от Базы курьер ищет дома. */
    private static final double RANGE = 160.0;

    private final SimpleInventory carried = new SimpleInventory(9);
    private int status = STATUS_IDLE;
    /** Куда идёт. */
    @Nullable
    private BlockPos target;
    /** Что делать, когда дойдёт: */
    private int job;
    @Nullable
    private UUID cookId;
    private int thinkCooldown;
    private int walkTicks;

    private static final int JOB_NONE = 0;
    private static final int JOB_TAKE = 1;      // забрать из сундука target
    private static final int JOB_UNLOAD = 2;    // выложить всё на склад / в Базу
    private static final int JOB_COOK = 3;      // отдать продукты повару

    public CourierEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(3, new CarryGoal(this));
    }

    // ---------- Профессия ----------

    @Override
    public boolean hasJob() {
        return true;
    }

    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 14;
            case 3 -> 20;
            default -> 10;
        };
    }

    @Override
    protected ItemStack getDefaultTool() {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean isAwayOnDuty() {
        return super.isAwayOnDuty() || target != null;
    }

    public int getCourierStatus() {
        return status;
    }

    private int capacity() {
        return CARRY_STACKS[Math.max(1, Math.min(MAX_LEVEL, getLevel()))];
    }

    private double speed() {
        return SPEED[Math.max(1, Math.min(MAX_LEVEL, getLevel()))];
    }

    private boolean carrying() {
        return !carried.isEmpty();
    }

    // =====================================================================
    // Работа
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world) || !this.isAlive()) {
            return;
        }
        // В руках — ящик, когда что-то несёт
        boolean showBox = carrying();
        if (showBox != this.getMainHandStack().isOf(Items.CHEST) && this.age % 10 == 0) {
            this.equipStack(EquipmentSlot.MAINHAND, showBox ? new ItemStack(Items.CHEST) : ItemStack.EMPTY);
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
        if (isResting() || isSettlementFrozen()) {
            return;
        }
        BlockPos base = BaseStorage.basePos(world);
        if (base == null) {
            status = STATUS_NO_BASE;
            target = null;
            return;
        }
        if (target == null) {
            if (--thinkCooldown <= 0) {
                thinkCooldown = 40;
                plan(world, base);
            }
            return;
        }
        // Дошёл?
        double dx = this.getX() - (target.getX() + 0.5);
        double dz = this.getZ() - (target.getZ() + 0.5);
        double dy = Math.abs(this.getY() - target.getY());
        if (dx * dx + dz * dz <= 2.8 * 2.8 && dy <= 3) {
            arrive(world, base);
            target = null;
            thinkCooldown = 10;
        } else if (++walkTicks > 1200) {
            // Не дойти минуту — переносимся (как строитель)
            this.getNavigation().stop();
            this.requestTeleport(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
            walkTicks = 0;
        }
    }

    /** Что делать дальше. */
    private void plan(ServerWorld world, BlockPos base) {
        walkTicks = 0;
        // 1) Что-то несём — на склад
        if (carrying()) {
            goTo(base, JOB_UNLOAD, needsFood(world) && hasCarriedFood() ? STATUS_FEED : STATUS_DELIVER);
            return;
        }
        // 2) Мало еды — берём еду со склада и сдаём в Базу
        if (needsFood(world) && storageHasFood(world)) {
            takeFromStorage(world, true);
            if (carrying()) {
                goTo(base, JOB_UNLOAD, STATUS_FEED);
                return;
            }
        }
        // 3) Повару не хватает продуктов — несём со склада
        CookEntity cook = hungryCook(world, base);
        if (cook != null && storageHasFood(world)) {
            takeFromStorage(world, true);
            if (carrying()) {
                cookId = cook.getUuid();
                BlockPos at = cook.getBlockPos();
                goTo(at, JOB_COOK, STATUS_COOK);
                return;
            }
        }
        // 4) Обход домов: сундук, где больше всего добычи
        BlockPos chest = fullestHouseChest(world, base);
        if (chest != null) {
            goTo(chest, JOB_TAKE, STATUS_COLLECT);
            return;
        }
        status = STATUS_IDLE;
    }

    private void goTo(BlockPos pos, int job, int status) {
        this.target = pos.toImmutable();
        this.job = job;
        this.status = status;
        this.walkTicks = 0;
    }

    /** Дошёл до цели. */
    private void arrive(ServerWorld world, BlockPos base) {
        switch (job) {
            case JOB_TAKE -> {
                Inventory chest = inventoryAt(world, target);
                if (chest != null) {
                    takeFrom(chest, false);
                    this.swingHand(Hand.MAIN_HAND);
                    world.playSound(null, target, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.NEUTRAL, 0.5f, 1.1f);
                }
            }
            case JOB_UNLOAD -> unload(world);
            case JOB_COOK -> {
                if (cookId != null && world.getEntity(cookId) instanceof CookEntity cook && cook.isAlive()
                        && cook.squaredDistanceTo(this) < 6 * 6) {
                    giveToCook(cook);
                } else if (cookId != null) {
                    // Повар ушёл — догоняем ещё раз
                    for (CookEntity c : world.getEntitiesByClass(CookEntity.class, this.getBoundingBox().expand(48),
                            c2 -> c2.getUuid().equals(cookId))) {
                        goTo(c.getBlockPos(), JOB_COOK, STATUS_COOK);
                        return;
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Выложить всё: еду — в блок «База» (если запас мал), остальное — в сундуки склада. */
    private void unload(ServerWorld world) {
        boolean feed = needsFood(world);
        for (int i = 0; i < carried.size(); i++) {
            ItemStack stack = carried.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (feed && SettlementItems.isAccepted(stack)) {
                int used = SettlementManager.deposit(world.getServer(), stack);
                stack.decrement(used);
            }
            for (BlockPos pos : BaseStorage.chests(world)) {
                if (stack.isEmpty()) {
                    break;
                }
                Inventory inv = inventoryAt(world, pos);
                if (inv != null) {
                    ItemStack rest = insertInto(inv, stack.copy());
                    if (rest.getCount() != stack.getCount()) {
                        inv.markDirty();
                    }
                    stack = rest;
                }
            }
            carried.setStack(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        this.swingHand(Hand.MAIN_HAND);
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.6f, 0.9f);
        if (carrying()) {
            // Склад полон — остаток бросаем у Базы, чтобы не ходить по кругу
            for (int i = 0; i < carried.size(); i++) {
                ItemStack stack = carried.getStack(i);
                if (!stack.isEmpty()) {
                    this.dropStack(stack.copy());
                    carried.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private void giveToCook(CookEntity cook) {
        for (int i = 0; i < carried.size(); i++) {
            ItemStack stack = carried.getStack(i);
            int value = SettlementItems.foodValue(stack);
            if (value <= 0) {
                continue;
            }
            int space = INGREDIENTS_MAX - cook.getIngredients();
            int count = Math.min(stack.getCount(), space / value);
            if (count > 0) {
                stack.decrement(count);
                cook.setIngredients(cook.getIngredients() + count * value);
            }
        }
        this.swingHand(Hand.MAIN_HAND);
        cook.playAmbientSound();
    }

    // ---------- Поиск ----------

    /** Еды в поселении меньше, чем на 3 дня. */
    private static boolean needsFood(ServerWorld world) {
        SettlementState s = SettlementState.get(world.getServer());
        return s.getFood() < Math.max(30, s.getDailyFood() * 3) && s.getFood() < SettlementManager.FOOD_MAX;
    }

    private boolean hasCarriedFood() {
        for (int i = 0; i < carried.size(); i++) {
            if (SettlementItems.isAccepted(carried.getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean storageHasFood(ServerWorld world) {
        for (BlockPos pos : BaseStorage.chests(world)) {
            Inventory inv = inventoryAt(world, pos);
            if (inv == null) {
                continue;
            }
            for (int i = 0; i < inv.size(); i++) {
                if (SettlementItems.isAccepted(inv.getStack(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Взять со склада (только еду, если foodOnly). */
    private void takeFromStorage(ServerWorld world, boolean foodOnly) {
        for (BlockPos pos : BaseStorage.chests(world)) {
            Inventory inv = inventoryAt(world, pos);
            if (inv != null) {
                takeFrom(inv, foodOnly);
            }
        }
    }

    /** Переложить из сундука к себе, сколько влезет (не больше capacity() стопок). */
    private void takeFrom(Inventory chest, boolean foodOnly) {
        int used = 0;
        for (int i = 0; i < carried.size(); i++) {
            if (!carried.getStack(i).isEmpty()) {
                used++;
            }
        }
        boolean changed = false;
        for (int i = 0; i < chest.size() && used < capacity(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isEmpty() || (foodOnly && !SettlementItems.isAccepted(stack))) {
                continue;
            }
            for (int j = 0; j < carried.size(); j++) {
                if (carried.getStack(j).isEmpty()) {
                    carried.setStack(j, stack.copy());
                    chest.setStack(i, ItemStack.EMPTY);
                    used++;
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            chest.markDirty();
        }
    }

    /** Сундук дома (не строителя и не самого курьера), где больше всего предметов. */
    @Nullable
    private BlockPos fullestHouseChest(ServerWorld world, BlockPos base) {
        List<SettlerEntity> settlers = world.getEntitiesByClass(SettlerEntity.class,
                new Box(base).expand(RANGE), s -> s.isAlive() && s.hasJob()
                        && !(s instanceof BuilderEntity) && !(s instanceof CourierEntity));
        BlockPos best = null;
        int bestCount = 0;
        for (SettlerEntity s : settlers) {
            BlockPos chestPos = s.getChestPos();
            if (chestPos == null) {
                continue;
            }
            Inventory inv = s.getHomeChest(world);
            if (inv == null) {
                continue;
            }
            int count = 0;
            for (int i = 0; i < inv.size(); i++) {
                count += inv.getStack(i).getCount();
            }
            // Мелочь не носим — ждём, пока наберётся
            if (count >= 16 && count > bestCount) {
                bestCount = count;
                best = chestPos;
            }
        }
        return best;
    }

    /** Повар, у которого продуктов меньше чем на 2 дня. */
    @Nullable
    private static CookEntity hungryCook(ServerWorld world, BlockPos base) {
        for (CookEntity cook : world.getEntitiesByClass(CookEntity.class, new Box(base).expand(RANGE),
                c -> c.isAlive() && c.getIngredients() < c.getIngredientsPerDay() * 2)) {
            return cook;
        }
        return null;
    }

    @Nullable
    private static Inventory inventoryAt(World world, @Nullable BlockPos pos) {
        if (pos == null || !world.isChunkLoaded(pos)) {
            return null;
        }
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof Inventory inv ? inv : null;
    }

    // ---------- Сохранение ----------

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Carried", carried.toNbtList());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        carried.readNbtList(nbt.getList("Carried", NbtElement.COMPOUND_TYPE));
    }

    /** Погиб — то, что нёс, выпадает. */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient && reason == RemovalReason.KILLED) {
            for (int i = 0; i < carried.size(); i++) {
                ItemStack stack = carried.getStack(i);
                if (!stack.isEmpty()) {
                    this.dropStack(stack.copy());
                }
            }
            carried.clear();
        }
        super.remove(reason);
    }

    // =====================================================================
    // Ходьба к цели (скорость зависит от уровня)
    // =====================================================================

    static class CarryGoal extends Goal {

        private final CourierEntity courier;

        CarryGoal(CourierEntity courier) {
            this.courier = courier;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return courier.target != null && !courier.isResting() && !courier.isSettlementFrozen();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            courier.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos t = courier.target;
            if (t == null) {
                return;
            }
            courier.getLookControl().lookAt(Vec3d.ofCenter(t));
            if (courier.getNavigation().isIdle() || courier.age % 40 == 0) {
                courier.getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, courier.speed());
            }
        }
    }
}
