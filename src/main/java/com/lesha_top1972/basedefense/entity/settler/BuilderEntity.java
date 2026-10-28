package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.block.BaseStorage;
import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.structure.BuildCost;
import com.lesha_top1972.basedefense.structure.BuildTask;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseDesign;
import com.lesha_top1972.basedefense.structure.HouseType;
import com.lesha_top1972.basedefense.structure.PathPlanner;
import com.lesha_top1972.basedefense.structure.TownState;
import com.lesha_top1972.basedefense.structure.StructurePlan;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.BlockState;
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

import java.util.ArrayDeque;
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
 * Материалы берёт и из сундуков своего дома, и со склада Базы (BaseStorage).
 * Когда заказов нет — чинит повреждённые здания (ставит недостающие блоки по чертежу; чужие блоки
 * на месте здания не трогает), а если чинить нечего — прокладывает тропинки. Ночью спит.
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
    public static final int STATUS_PATHS = 5;      // заказов нет — прокладывает тропинки
    public static final int STATUS_REPAIR = 6;     // чинит повреждённое здание

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

    // ---------- Тропинки (когда нет заказов) ----------
    private final ArrayDeque<BlockPos> pathQueue = new ArrayDeque<>();
    private int pathRevision = -1;
    private int pathReplan;
    @Nullable
    private BlockPos pathTarget;
    private int pathStuck;
    private int chestCacheAge = 1000;

    // ---------- Ремонт зданий ----------
    /** Одна починка: шаг чертежа (1–2 блока) в здании. */
    private record RepairJob(BlockPos pos, BuildTask task, List<StructurePlan.Placement> step) {
    }

    private final ArrayDeque<RepairJob> repairQueue = new ArrayDeque<>();
    @Nullable
    private RepairJob repairTarget;
    private int repairScanCooldown;
    private int repairBuildingIndex;
    private int repairStuck;
    /** Где встать, чтобы дотянуться до места ремонта (клетка на земле рядом). */
    @Nullable
    private BlockPos repairStand;

    public BuilderEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(3, new BuildGoal(this));
        this.goalSelector.add(3, new PathGoal(this));
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
        return super.isAwayOnDuty() || site != null || pathTarget != null || repairTarget != null;
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
        int reference = Math.max(1, HouseType.FARMER.getCostlyBlocks(HouseDesign.OAK));
        return (int) Math.ceil(remainingBlocks * (double) SECONDS_PER_REFERENCE_HOUSE[lv] / reference);
    }

    /** Сколько блоков-материалов строитель ставит за тик. */
    private double blocksPerTick() {
        int level = Math.max(1, Math.min(MAX_LEVEL, getLevel()));
        int reference = HouseType.FARMER.getCostlyBlocks(HouseDesign.OAK);
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
        if (isResting()) {
            return;   // ночь — спит
        }
        if (site == null) {
            clearWork();
            if (!repairTick(world, dim)) {
                pathTick(world, dim);
            }
            return;
        }
        repairTarget = null;
        repairQueue.clear();
        pathTarget = null;
        // Блок дома сломали — заказ отменён
        if (world.isChunkLoaded(site.pos) && !(world.getBlockState(site.pos).getBlock() instanceof HouseBlock)) {
            construction.removeSite(site.pos, dim);
            clearWork();
            return;
        }
        if (task == null || task.getPlan() != site.plan() || !samePlace(task, site)) {
            task = new BuildTask(world, site.plan(), site.type.getSteps(site.design), site.pos, site.facing, true);
            task.setIndex(site.step);
            budget = 0;
        }
        currentHouse = site.type;
        if (this.age % 20 == 0) {
            progress = progressOf(site.type, site.design, task.getIndex());
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

    /**
     * Заказов нет — прокладываем тропинки: кольцо у площади, главные дороги и ответвления к домам
     * (см. PathPlanner). Нужна построенная центральная площадь. Материалы не нужны.
     */
    private void pathTick(ServerWorld world, String dim) {
        TownState town = TownState.get(world.getServer());
        TownState.Square square = town.getSquare(dim);
        if (square == null) {
            pathTarget = null;
            pathQueue.clear();
            return;
        }
        // План пересчитываем, когда появился новый дом/площадь или раз в минуту, если всё сделано
        if (town.getRevision() != pathRevision || (pathQueue.isEmpty() && pathTarget == null && --pathReplan <= 0)) {
            pathQueue.clear();
            pathQueue.addAll(PathPlanner.plan(square, town.getEntrances(dim)));
            pathRevision = town.getRevision();
            pathReplan = 1200;
            pathTarget = null;
        }
        if (isSettlementFrozen()) {
            return;
        }
        // Следующая клетка, где тропинки ещё нет (не больше 48 проверок за тик)
        for (int i = 0; pathTarget == null && i < 48 && !pathQueue.isEmpty(); i++) {
            BlockPos tile = pathQueue.poll();
            BlockPos ground = PathPlanner.groundAt(world, tile.getX(), tile.getZ());
            if (ground != null && PathPlanner.needsPath(world, ground)) {
                pathTarget = ground;
                pathStuck = 0;
            }
        }
        if (pathTarget == null) {
            return;
        }
        status = STATUS_PATHS;
        double dx = this.getX() - (pathTarget.getX() + 0.5);
        double dz = this.getZ() - (pathTarget.getZ() + 0.5);
        if (dx * dx + dz * dz <= 3.5 * 3.5) {
            int interval = switch (getLevel()) {
                case 3 -> 4;
                case 2 -> 7;
                default -> 10;
            };
            if (this.age % interval == 0) {
                if (PathPlanner.needsPath(world, pathTarget)) {
                    PathPlanner.makePath(world, pathTarget);
                    this.swingHand(Hand.MAIN_HAND);
                }
                pathTarget = null;
            }
        } else if (++pathStuck > 400) {
            pathTarget = null;   // не дойти — пропускаем клетку
        }
    }

    // =====================================================================
    // Ремонт зданий
    // =====================================================================

    /**
     * Заказов нет — ищем повреждённые здания (по списку построенных в TownState, по одному за раз)
     * и ставим недостающие блоки. Чинится только то, чего нет (на месте воздух, вода, трава);
     * если на месте блока здания стоит что-то другое — не трогаем.
     * @return true — строитель занят ремонтом (тропинки подождут)
     */
    private boolean repairTick(ServerWorld world, String dim) {
        if (repairTarget == null && repairQueue.isEmpty()) {
            if (--repairScanCooldown > 0) {
                return false;
            }
            repairScanCooldown = 40;
            List<TownState.Building> list = TownState.get(world.getServer()).getBuildings(dim);
            if (list.isEmpty()) {
                return false;
            }
            scanForDamage(world, list.get(Math.floorMod(repairBuildingIndex++, list.size())));
            if (repairQueue.isEmpty()) {
                return false;
            }
        }
        if (isSettlementFrozen()) {
            return true;
        }
        if (repairTarget == null) {
            repairTarget = repairQueue.poll();
            repairStuck = 0;
            if (repairTarget == null) {
                return false;
            }
            repairStand = findStandSpot(world, repairTarget.pos());
        }
        RepairJob job = repairTarget;
        if (!isDamaged(world, job)) {
            repairTarget = null;
            return true;
        }
        status = STATUS_REPAIR;
        missingItem = null;
        double dx = this.getX() - (job.pos().getX() + 0.5);
        double dz = this.getZ() - (job.pos().getZ() + 0.5);
        double dy = Math.abs(this.getY() - job.pos().getY());
        if (dx * dx + dz * dz <= 4.0 * 4.0 && dy <= 12) {
            int interval = switch (getLevel()) {
                case 3 -> 5;
                case 2 -> 8;
                default -> 12;
            };
            if (this.age % interval == 0) {
                List<Item> need = new ArrayList<>();
                for (StructurePlan.Placement p : job.step()) {
                    Item item = BuildCost.itemFor(p);
                    if (item != null) {
                        need.add(item);
                    }
                }
                Item lacking = takeItems(world, need);
                if (lacking != null) {
                    // Нечем чинить — бросаем ремонт на минуту (строитель вернётся, когда материалы появятся)
                    status = STATUS_MISSING;
                    missingItem = lacking;
                    repairQueue.clear();
                    repairTarget = null;
                    repairScanCooldown = 1200;
                    return false;
                }
                for (StructurePlan.Placement p : job.step()) {
                    job.task().placeSingle(p);
                }
                this.swingHand(Hand.MAIN_HAND);
                repairTarget = null;
            }
        } else if (++repairStuck > 300) {
            // Не дойти 15 секунд — переносимся к месту ремонта (как на стройку)
            BlockPos stand = repairStand != null ? repairStand : job.pos();
            this.getNavigation().stop();
            this.requestTeleport(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            repairStuck = 0;
        }
        return true;
    }

    /** Ближайшая к месту ремонта клетка, где можно стоять: под ногами твёрдый блок, сверху 2 блока воздуха. */
    @Nullable
    private static BlockPos findStandSpot(ServerWorld world, BlockPos target) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(target.add(-3, -6, -3), target.add(3, 2, 3))) {
            if (world.getBlockState(p.down()).isSolidBlock(world, p.down())
                    && world.getBlockState(p).getCollisionShape(world, p).isEmpty()
                    && world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()) {
                double d = p.getSquaredDistance(target);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.toImmutable();
                }
            }
        }
        return best;
    }

    /** Найти повреждённые места здания (не больше 64 за раз). */
    private void scanForDamage(ServerWorld world, TownState.Building b) {
        if (!world.isChunkLoaded(b.pos())) {
            return;
        }
        BuildTask t = new BuildTask(world, b.plan(), b.type().getSteps(b.design()), b.pos(), b.facing(), false);
        for (List<StructurePlan.Placement> step : t.getSteps()) {
            if (repairQueue.size() >= 64) {
                break;
            }
            RepairJob job = new RepairJob(t.worldPos(step.get(0)), t, step);
            if (isDamaged(world, job)) {
                repairQueue.add(job);
            }
        }
    }

    /**
     * Шаг чертежа повреждён: хотя бы один его блок пропал (там пусто), и ни на одном месте
     * не стоит чужой блок. Бесплатные блоки (земля двора, листва, вода, расчистка) не чинятся.
     */
    private static boolean isDamaged(ServerWorld world, RepairJob job) {
        boolean costly = false;
        boolean missing = false;
        for (StructurePlan.Placement p : job.step()) {
            if (p.onlyIfReplaceable() || p.state().isAir()) {
                return false;
            }
            if (BuildCost.itemFor(p) != null) {
                costly = true;
            }
            BlockPos pos = job.task().worldPos(p);
            if (!world.isChunkLoaded(pos)) {
                return false;
            }
            BlockState current = world.getBlockState(pos);
            if (current.isOf(job.task().targetState(p).getBlock())) {
                continue;
            }
            if (!current.isReplaceable()) {
                return false;
            }
            missing = true;
        }
        return costly && missing;
    }

    /** Куда идти строителю без заказа: к месту ремонта или к клетке тропинки. */
    @Nullable
    private BlockPos walkTarget() {
        if (repairTarget != null) {
            return repairStand != null ? repairStand : repairTarget.pos();
        }
        return pathTarget == null ? null : pathTarget.up();
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
        TownState.get(world.getServer()).addBuilding(done.type, done.design, done.pos, done.facing, dim);
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
    public static int progressOf(HouseType type, HouseDesign design, int step) {
        int total = type.getCostlyBlocks(design);
        if (total <= 0) {
            return 100;
        }
        int left = 0;
        for (int n : BuildCost.count(type.getSteps(design), step, type.getPlan(design).getOrigin()).values()) {
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
            List<BlockPos> all = new ArrayList<>(findChests(world, home));
            for (BlockPos pos : BaseStorage.chests(world)) {
                if (!all.contains(pos)) {
                    all.add(pos);   // склад Базы — тоже источник материалов
                }
            }
            chestCache = all;
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
            return builder.site != null && !builder.isSettlementFrozen() && !builder.isResting();
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

    /** Строитель без заказов идёт к следующей клетке будущей тропинки. */
    static class PathGoal extends Goal {

        private final BuilderEntity builder;

        PathGoal(BuilderEntity builder) {
            this.builder = builder;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return builder.site == null && builder.walkTarget() != null && !builder.isSettlementFrozen()
                    && !builder.isResting();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            builder.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos target = builder.walkTarget();
            if (target == null) {
                return;
            }
            builder.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            double dx = builder.getX() - (target.getX() + 0.5);
            double dz = builder.getZ() - (target.getZ() + 0.5);
            if (dx * dx + dz * dz > 2.5 * 2.5) {
                if (builder.getNavigation().isIdle() || builder.age % 40 == 0) {
                    // Подойти хотя бы на 2 блока (точно в клетку может не быть дороги)
                    net.minecraft.entity.ai.pathing.Path path = builder.getNavigation().findPathTo(target, 2);
                    if (path != null) {
                        builder.getNavigation().startMovingAlong(path, 0.7);
                    }
                }
            } else {
                builder.getNavigation().stop();
            }
        }
    }
}
