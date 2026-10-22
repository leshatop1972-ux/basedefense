package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.structure.BuildCost;
import com.lesha_top1972.basedefense.structure.BuildTask;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseType;
import com.lesha_top1972.basedefense.structure.StructurePlan;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Житель-строитель. Появляется в доме строителя.
 * Берёт заказ (поставленный блок дома), идёт на стройплощадку и строит дом по чертежу,
 * забирая блоки из сундуков своего дома. Нет нужного блока — ждёт, пока его положат.
 * Скорость: дом фермера строится за 4 мин (ур. 1), 3 мин (ур. 2), 1,5 мин (ур. 3);
 * дома побольше — дольше, поменьше — быстрее.
 */
public class BuilderEntity extends SettlerEntity {

    /** Сколько секунд строится дом фермера на уровнях 1–3. */
    private static final int[] SECONDS_PER_REFERENCE_HOUSE = {0, 240, 180, 90};
    /** В каком радиусе от дома строителя искать сундуки с материалами. */
    public static final int CHEST_RADIUS = 8;
    /** Строитель работает, только если стоит не дальше этого от блока дома. */
    private static final double WORK_DISTANCE = 20.0;

    // ---------- Состояние для меню ----------
    public static final int STATUS_IDLE = 1;       // заказов нет
    public static final int STATUS_WALKING = 2;    // идёт на стройку
    public static final int STATUS_BUILDING = 3;   // строит
    public static final int STATUS_MISSING = 4;    // не хватает материала

    private int status = STATUS_IDLE;
    private int progress;
    @Nullable
    private Item missingItem;
    @Nullable
    private HouseType currentHouse;

    // ---------- Стройка ----------
    @Nullable
    private ConstructionState.Site site;
    @Nullable
    private BuildTask task;
    private double budget;
    private List<BlockPos> chestCache = List.of();
    private int chestCacheAge = 1000;

    public BuilderEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(3, new BuildGoal(this));
    }

    // ---------- Профессия ----------

    @Override
    public boolean hasJob() {
        return true;
    }

    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 16;
            case 3 -> 23;
            default -> 12;
        };
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.IRON_SHOVEL);
    }

    @Override
    protected boolean isAwayOnDuty() {
        return site != null;
    }

    public int getBuildStatus() {
        return status;
    }

    public int getBuildProgress() {
        return progress;
    }

    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    @Nullable
    public HouseType getCurrentHouse() {
        return currentHouse;
    }

    /** Примерно сколько секунд строителю этого уровня осталось строить (если материалы есть). */
    public static int estimateSeconds(int remainingBlocks, int level) {
        int lv = Math.max(1, Math.min(MAX_LEVEL, level));
        int reference = Math.max(1, HouseType.FARMER.getCostlyBlocks());
        return (int) Math.ceil(remainingBlocks * (double) SECONDS_PER_REFERENCE_HOUSE[lv] / reference);
    }

    /** Сколько блоков-материалов строитель ставит за тик. */
    private double blocksPerTick() {
        int level = Math.max(1, Math.min(MAX_LEVEL, getLevel()));
        int reference = HouseType.FARMER.getCostlyBlocks();
        return reference / (SECONDS_PER_REFERENCE_HOUSE[level] * 20.0);
    }

    // =====================================================================
    // Работа
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld world && this.isAlive() && getHome() != null) {
            builderTick(world);
        }
    }

    private void builderTick(ServerWorld world) {
        ConstructionState construction = ConstructionState.get(world.getServer());
        String dim = HouseBlock.dimensionId(world);
        long now = world.getTime();

        if (this.age % 20 == 0) {
            construction.registerBuilder(this.getUuid(), getHome(), dim, getLevel());
            site = construction.claim(this.getUuid(), dim, now);
        }
        if (site == null) {
            clearWork();
            return;
        }
        // Блок дома сломали — заказ отменён
        if (world.isChunkLoaded(site.pos) && !(world.getBlockState(site.pos).getBlock() instanceof HouseBlock)) {
            construction.removeSite(site.pos, dim);
            clearWork();
            return;
        }
        if (task == null || !task.getPlan().equals(site.type.getPlan()) || !samePlace(task, site)) {
            task = new BuildTask(world, site.type.getPlan(), site.type.getSteps(), site.pos, site.facing, true);
            task.setIndex(site.step);
            budget = 0;
        }
        currentHouse = site.type;
        if (this.age % 20 == 0) {
            progress = progressOf(site.type, task.getIndex());
        }
        if (isSettlementFrozen()) {
            return;
        }
        if (this.squaredDistanceTo(Vec3d.ofCenter(site.pos)) > WORK_DISTANCE * WORK_DISTANCE) {
            status = STATUS_WALKING;
            return;
        }
        construction.touch(site, now);
        buildSomeBlocks(world);
        if (task.getIndex() != site.step) {
            construction.setStep(site, task.getIndex(), now);
        }
        if (task.isDone()) {
            finish(world, construction, dim);
        }
    }

    private static boolean samePlace(BuildTask task, ConstructionState.Site site) {
        BlockPos origin = task.getPlan().getOrigin();
        return task.toWorld(origin.getX(), origin.getY(), origin.getZ()).equals(site.pos)
                && task.getFacing() == site.facing;
    }

    /** Ставит блоки, сколько успевает за этот тик. */
    private void buildSomeBlocks(ServerWorld world) {
        BuildTask t = task;
        if (t == null) {
            return;
        }
        status = STATUS_BUILDING;
        missingItem = null;
        budget = Math.min(2.0, budget + blocksPerTick());
        int freeChanges = 0;
        int skipped = 0;
        while (!t.isDone()) {
            List<StructurePlan.Placement> step = t.getSteps().get(t.getIndex());
            boolean anyWork = false;
            List<Item> need = new ArrayList<>();
            for (StructurePlan.Placement p : step) {
                if (!world.isChunkLoaded(t.worldPos(p))) {
                    return;   // часть дома в незагруженной области — подождём
                }
                if (t.needsWork(p)) {
                    anyWork = true;
                    Item item = BuildCost.itemFor(p);
                    if (item != null) {
                        need.add(item);
                    }
                }
            }
            if (!anyWork) {
                // Тут уже всё как надо — дальше, без затрат времени
                t.setIndex(t.getIndex() + 1);
                if (++skipped >= 256) {
                    return;
                }
                continue;
            }
            if (need.isEmpty()) {
                // Расчистка и засыпка ям — быстро, но не всё сразу
                if (freeChanges >= 4) {
                    return;
                }
                freeChanges++;
                stepAsideIfTrapped(t, step);
                t.placeStep();
                continue;
            }
            if (budget < 1.0) {
                return;
            }
            Item lacking = takeItems(world, need);
            if (lacking != null) {
                status = STATUS_MISSING;
                missingItem = lacking;
                budget = 0;
                return;
            }
            budget -= 1.0;
            stepAsideIfTrapped(t, step);
            t.placeStep();
            this.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Если блок ставится туда, где стоит сам строитель, — он отходит к крыльцу, чтобы не застрять. */
    private void stepAsideIfTrapped(BuildTask t, List<StructurePlan.Placement> step) {
        BlockPos feet = this.getBlockPos();
        for (StructurePlan.Placement p : step) {
            BlockPos pos = t.worldPos(p);
            if ((pos.equals(feet) || pos.equals(feet.up())) && !p.state().isAir() && site != null) {
                BlockPos front = site.pos.offset(site.facing.getOpposite());
                this.getNavigation().stop();
                this.requestTeleport(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
                return;
            }
        }
    }

    private void finish(ServerWorld world, ConstructionState construction, String dim) {
        ConstructionState.Site done = site;
        BuildTask t = task;
        clearWork();
        if (done == null || t == null) {
            return;
        }
        construction.removeSite(done.pos, dim);
        if (world.getBlockState(done.pos).getBlock() instanceof HouseBlock) {
            world.setBlockState(done.pos, Blocks.AIR.getDefaultState());
        }
        t.getPlan().getFinishAction().run(t);   // заселяем жителя
        world.playSound(null, done.pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        Text houseName = Text.translatable("block.basedefense." + done.type.getId());
        world.getServer().getPlayerManager().broadcast(
                Text.translatable("message.basedefense.house.built", houseName).formatted(Formatting.GREEN), false);
    }

    private void clearWork() {
        site = null;
        task = null;
        status = STATUS_IDLE;
        missingItem = null;
        currentHouse = null;
        progress = 0;
    }

    /** Сколько процентов дома готово, если строительство дошло до шага step. */
    public static int progressOf(HouseType type, int step) {
        int total = type.getCostlyBlocks();
        if (total <= 0) {
            return 100;
        }
        int left = 0;
        for (int n : BuildCost.count(type.getSteps(), step, type.getPlan().getOrigin()).values()) {
            left += n;
        }
        return Math.max(0, Math.min(100, (total - left) * 100 / total));
    }

    // =====================================================================
    // Сундуки с материалами
    // =====================================================================

    /** Сундуки возле дома строителя (каждый блок сундука отдельно, двойной — это два блока). */
    public static List<BlockPos> findChests(World world, BlockPos home) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(home.add(-CHEST_RADIUS, -2, -CHEST_RADIUS),
                home.add(CHEST_RADIUS, 3, CHEST_RADIUS))) {
            if (world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                result.add(pos.toImmutable());
            }
        }
        return result;
    }

    /** Сколько каких предметов лежит в сундуках (только загруженные сундуки). */
    public static Map<Item, Integer> countItems(World world, List<BlockPos> chests) {
        Map<Item, Integer> result = new HashMap<>();
        for (BlockPos pos : chests) {
            Inventory inv = inventoryAt(world, pos);
            if (inv == null) {
                continue;
            }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    result.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        return result;
    }

    @Nullable
    private static Inventory inventoryAt(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) {
            return null;
        }
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof Inventory inv ? inv : null;
    }

    private List<BlockPos> chests(ServerWorld world) {
        BlockPos home = getHome();
        if (home == null) {
            return List.of();
        }
        if (++chestCacheAge > 100) {
            chestCache = findChests(world, home);
            chestCacheAge = 0;
        }
        return chestCache;
    }

    /**
     * Забирает из сундуков по одному предмету каждого вида из списка.
     * @return null — всё забрано; иначе предмет, которого не хватает (тогда ничего не забирается)
     */
    @Nullable
    private Item takeItems(ServerWorld world, List<Item> items) {
        List<BlockPos> chests = chests(world);
        Map<Item, Integer> needed = new HashMap<>();
        for (Item item : items) {
            needed.merge(item, 1, Integer::sum);
        }
        Map<Item, Integer> have = countItems(world, chests);
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return e.getKey();
            }
        }
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            int left = e.getValue();
            for (BlockPos pos : chests) {
                Inventory inv = inventoryAt(world, pos);
                if (inv == null) {
                    continue;
                }
                for (int i = 0; i < inv.size() && left > 0; i++) {
                    ItemStack stack = inv.getStack(i);
                    if (stack.isOf(e.getKey())) {
                        int take = Math.min(left, stack.getCount());
                        stack.decrement(take);
                        left -= take;
                        inv.markDirty();
                    }
                }
                if (left <= 0) {
                    break;
                }
            }
        }
        return null;
    }

    // =====================================================================
    // Смерть / удаление
    // =====================================================================

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld world && reason.shouldDestroy()) {
            ConstructionState.get(world.getServer()).removeBuilder(this.getUuid());
        }
        super.remove(reason);
    }

    // =====================================================================
    // Поведение: идти на стройку и «работать» там
    // =====================================================================

    /** Строитель идёт к стройплощадке и ходит по ней, взмахивая лопатой. */
    static class BuildGoal extends Goal {

        private final BuilderEntity builder;
        @Nullable
        private BlockPos spot;
        private int retargetTicks;
        private int stuckTicks;

        BuildGoal(BuilderEntity builder) {
            this.builder = builder;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return builder.site != null && !builder.isSettlementFrozen();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            retargetTicks = 0;
            stuckTicks = 0;
        }

        @Override
        public void stop() {
            builder.getNavigation().stop();
            spot = null;
        }

        @Override
        public void tick() {
            ConstructionState.Site site = builder.site;
            if (site == null) {
                return;
            }
            // Центр будущего дома — в нескольких блоках «вглубь» от блока дома
            BlockPos center = site.pos.offset(site.facing, 5);
            double distSq = builder.squaredDistanceTo(Vec3d.ofBottomCenter(site.pos));

            if (distSq > 12 * 12) {
                // Идём на стройку; если дороги нет или очень далеко — переносимся
                BlockPos front = site.pos.offset(site.facing.getOpposite());
                if (builder.getNavigation().isIdle() || builder.age % 40 == 0) {
                    boolean ok = builder.getNavigation().startMovingTo(front.getX() + 0.5, front.getY(),
                            front.getZ() + 0.5, 0.9);
                    stuckTicks = ok ? stuckTicks + 1 : stuckTicks + 40;
                }
                if (distSq > 96 * 96 || stuckTicks > 400) {
                    builder.getNavigation().stop();
                    builder.requestTeleport(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
                    stuckTicks = 0;
                }
                return;
            }
            stuckTicks = 0;
            // На площадке: время от времени переходим на новое место
            if (--retargetTicks <= 0 || spot == null) {
                retargetTicks = 80 + builder.getRandom().nextInt(80);
                spot = center.add(builder.getRandom().nextInt(7) - 3, 0, builder.getRandom().nextInt(7) - 3);
                builder.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.6);
            }
            builder.getLookControl().lookAt(center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5);
            if (builder.status == STATUS_BUILDING && builder.age % 15 == 0) {
                builder.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
