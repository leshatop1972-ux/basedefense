package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.SettlerScreenHandler;
import com.lesha_top1972.basedefense.settlement.SettlementItems;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.settlement.SettlementState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Общий класс для жителей поселения (фермер, скотовод и т.д.).
 * Дружелюбный, гуляет возле своего дома и не уходит от него
 * дальше чем на {@link #HOME_RADIUS} блоков.
 * Каждый житель привязан к Базе: он входит в поселение и потребляет его запасы.
 * Если запасы поселения кончились (счётчик дошёл до -20) — житель замирает.
 *
 * Работа: по клику открывается меню (начать/закончить работу, забрать добычу, улучшить).
 * Какую работу умеет житель, задаёт подкласс через {@link #getWorkLoot} и {@link #getWorkFood}.
 */
public abstract class SettlerEntity extends PathAwareEntity {

    /** Максимальное расстояние от дома. */
    public static final int HOME_RADIUS = 50;
    public static final int MAX_LEVEL = 3;
    /** Цена улучшения в монетах: [уровень сейчас] → цена перехода на следующий. */
    private static final int[] UPGRADE_COST = {0, 20, 60};
    /** Сколько раз в день житель «тянет» добычу из списка (по умолчанию). */
    public static final int LOOT_ROLLS_PER_DAY = 3;
    /** Сколько очков продуктов житель (повар) может хранить. */
    public static final int INGREDIENTS_MAX = 300;
    private static final int STORAGE_SIZE = 27;

    private static final TrackedData<Integer> LEVEL =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Nullable
    private BlockPos homePos;
    private boolean working;
    /** Номер игрового дня, за который уже выдана добыча. */
    private long lastWorkDay = -1;
    /** Что житель добыл и ещё не отдал игроку. */
    private final SimpleInventory storage = new SimpleInventory(STORAGE_SIZE);
    /** Очки продуктов, которые игрок отдал жителю (для повара). */
    private int ingredients;
    /** Игрок, у которого сейчас открыто меню этого жителя (житель стоит и смотрит на него). */
    @Nullable
    private PlayerEntity interactingPlayer;

    protected SettlerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        // Умеет открывать двери (иначе не выйдет из дома на огород)
        if (this.getNavigation() instanceof MobNavigation navigation) {
            navigation.setCanPathThroughDoors(true);
            navigation.setCanEnterOpenDoors(true);
        }
    }

    public static DefaultAttributeContainer.Builder createSettlerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LEVEL, 1);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FleeHomeGoal(this));     // рядом враг — бежит домой и закрывает дверь
        this.goalSelector.add(1, new StandStillGoal(this));   // нет запасов или открыто меню — стоит
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(1, new LongDoorInteractGoal(this, true));   // открывает и закрывает двери
        this.goalSelector.add(2, new ReturnHomeGoal(this));
        this.goalSelector.add(3, new WorkGoal(this));         // работает (грядки, загон…)
        this.goalSelector.add(4, new GoToWalkTargetGoal(this, 0.8));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.6, 0.001f));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    // =====================================================================
    // Работа (подклассы переопределяют)
    // =====================================================================

    /** Что житель добывает на этом уровне. null — у жителя пока нет работы. */
    @Nullable
    public List<WorkLoot> getWorkLoot(int level) {
        return null;
    }

    /** Сколько еды тратит житель в день, когда работает. */
    public int getWorkFood(int level) {
        return SettlementManager.FOOD_PER_SETTLER;
    }

    /** Сколько раз в день житель выбирает добычу из списка. */
    public int getLootRolls() {
        return LOOT_ROLLS_PER_DAY;
    }

    /** Принимает ли житель продукты от игрока (повар). */
    public boolean acceptsIngredients() {
        return false;
    }

    /** Сколько очков продуктов нужно на один рабочий день (для повара). */
    public int getIngredientsPerDay() {
        return 0;
    }

    public int getIngredients() {
        return ingredients;
    }

    protected void setIngredients(int value) {
        this.ingredients = Math.max(0, Math.min(INGREDIENTS_MAX, value));
    }

    /** Возле каких блоков житель работает (фермер — пашня, скотовод — сено и т.д.). */
    public boolean isWorkSpot(BlockState state) {
        return false;
    }

    public boolean hasJob() {
        return getWorkLoot(1) != null;
    }

    /** Сколько еды житель тратит в день прямо сейчас. */
    public int getFoodPerDay() {
        return working ? getWorkFood(getLevel()) : SettlementManager.FOOD_PER_SETTLER;
    }

    public static int getUpgradeCost(int level) {
        return level >= 1 && level < UPGRADE_COST.length ? UPGRADE_COST[level] : 0;
    }

    public int getLevel() {
        return this.dataTracker.get(LEVEL);
    }

    private void setLevel(int level) {
        this.dataTracker.set(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
    }

    public boolean isWorking() {
        return working;
    }

    /** Сколько предметов житель добыл и хранит. */
    public int getStoredCount() {
        int count = 0;
        for (int i = 0; i < storage.size(); i++) {
            count += storage.getStack(i).getCount();
        }
        return count;
    }

    public void setInteractingPlayer(@Nullable PlayerEntity player) {
        this.interactingPlayer = player;
    }

    @Nullable
    public PlayerEntity getInteractingPlayer() {
        return interactingPlayer;
    }

    private boolean isSettlementFrozen() {
        return this.getWorld() instanceof ServerWorld serverWorld && SettlementManager.isFrozen(serverWorld.getServer());
    }

    private static long currentDay(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / 24000L;
    }

    /** Кнопка 1: начать / закончить работу. */
    public void toggleWork(PlayerEntity player) {
        if (!hasJob() || !(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        working = !working;
        if (working) {
            lastWorkDay = currentDay(serverWorld.getServer()); // первая добыча — на следующем рассвете
        }
        updateSettlement(serverWorld);
        player.sendMessage(Text.translatable(working ? "message.basedefense.settler.work_started"
                        : "message.basedefense.settler.work_stopped", this.getDisplayName(), getFoodPerDay())
                .formatted(working ? Formatting.GREEN : Formatting.YELLOW), true);
        this.playSound(working ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f);
    }

    /** Кнопка 2: отдать игроку всё, что добыто. */
    public void giveLoot(PlayerEntity player) {
        int given = 0;
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            if (!stack.isEmpty()) {
                given += stack.getCount();
                player.getInventory().offerOrDrop(stack.copy());
                storage.setStack(i, ItemStack.EMPTY);
            }
        }
        if (given > 0) {
            this.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS, 0.6f, 1.0f);
        } else {
            player.sendMessage(Text.translatable("message.basedefense.settler.nothing")
                    .formatted(Formatting.GRAY), true);
        }
    }

    /** Кнопка 3: улучшить жителя за монеты исследований. */
    public void tryUpgrade(PlayerEntity player) {
        if (!hasJob() || !(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        int level = getLevel();
        if (level >= MAX_LEVEL) {
            return;
        }
        int cost = getUpgradeCost(level);
        if (ResearchManager.countCoins(player) < cost) {
            player.sendMessage(Text.translatable("message.basedefense.research.not_enough", cost)
                    .formatted(Formatting.RED), true);
            return;
        }
        ResearchManager.removeCoins(player, cost);
        setLevel(level + 1);
        updateSettlement(serverWorld);
        serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.2, this.getZ(),
                15, 0.4, 0.6, 0.4, 0.0);
        serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.NEUTRAL, 0.8f, 1.3f);
        player.sendMessage(Text.translatable("message.basedefense.settler.upgraded", this.getDisplayName(), level + 1)
                .formatted(Formatting.GREEN), true);
    }

    /** Кнопка 4 (повар): отдать жителю еду из руки — она превращается в очки продуктов. */
    public void takeIngredientsFromHand(PlayerEntity player) {
        if (!acceptsIngredients()) {
            return;
        }
        ItemStack hand = player.getMainHandStack();
        int value = SettlementItems.foodValue(hand);
        if (value <= 0) {
            player.sendMessage(Text.translatable("message.basedefense.settler.need_food_in_hand")
                    .formatted(Formatting.RED), true);
            return;
        }
        int space = INGREDIENTS_MAX - ingredients;
        int count = Math.min(hand.getCount(), space / value);
        if (count <= 0) {
            player.sendMessage(Text.translatable("message.basedefense.settler.ingredients_full")
                    .formatted(Formatting.YELLOW), true);
            return;
        }
        hand.decrement(count);
        setIngredients(ingredients + count * value);
        this.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.basedefense.settler.ingredients_added",
                count * value, ingredients).formatted(Formatting.GREEN), true);
    }

    /** Добыча за один рабочий день: getLootRolls() раз берётся случайный вариант из списка. */
    protected void produceOneDay() {
        List<WorkLoot> loot = getWorkLoot(getLevel());
        if (loot == null || loot.isEmpty()) {
            return;
        }
        for (int i = 0; i < getLootRolls(); i++) {
            addLoot(loot.get(this.random.nextInt(loot.size())));
        }
    }

    /** Положить добычу в хранилище жителя (если оно полно — лишнее пропадает). */
    protected void addLoot(WorkLoot pick) {
        int count = pick.rollCount(this.random);
        while (count > 0) {
            int part = Math.min(count, pick.item().getMaxCount());
            storage.addStack(new ItemStack(pick.item(), part));
            count -= part;
        }
    }

    private void updateSettlement(ServerWorld world) {
        SettlementState.get(world.getServer()).addSettler(this.getUuid(), getFoodPerDay());
    }

    // =====================================================================
    // Клик по жителю
    // =====================================================================

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return super.interactMob(player, hand);
        }
        if (!hasJob()) {
            if (!this.getWorld().isClient) {
                player.sendMessage(Text.translatable("message.basedefense.settler.no_job", this.getDisplayName())
                        .formatted(Formatting.GRAY), true);
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        if (!this.getWorld().isClient) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, p) -> new SettlerScreenHandler(syncId, inventory, this),
                    this.getDisplayName()));
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    // =====================================================================
    // Дом
    // =====================================================================

    /** Назначить дом. Гуляет житель только в пределах радиуса от него. */
    public void setHome(BlockPos pos) {
        this.homePos = pos.toImmutable();
        // Бродит чуть ближе границы, чтобы не упираться в неё
        this.setPositionTarget(this.homePos, HOME_RADIUS - 8);
    }

    @Nullable
    public BlockPos getHome() {
        return homePos;
    }

    @Override
    public void tick() {
        super.tick();
        // Если житель появился из яйца — его дом там, где он появился
        if (!this.getWorld().isClient && this.homePos == null) {
            setHome(this.getBlockPos());
        }
        if (this.getWorld() instanceof ServerWorld serverWorld && this.age % 20 == 1 && this.isAlive()) {
            // Привязка к Базе: житель записан в поселение вместе со своим расходом еды
            updateSettlement(serverWorld);

            boolean frozen = SettlementManager.isFrozen(serverWorld.getServer());
            // Добыча: на каждом рассвете, пока житель работает (дни, пока чанк не был загружен, тоже считаются)
            if (working) {
                long day = currentDay(serverWorld.getServer());
                if (lastWorkDay < 0 || day < lastWorkDay) {
                    lastWorkDay = day;
                } else if (day > lastWorkDay) {
                    long days = Math.min(day - lastWorkDay, 7);
                    lastWorkDay = day;
                    if (!frozen) {
                        for (int i = 0; i < days; i++) {
                            produceOneDay();
                        }
                    }
                }
            }
            // Замерший житель — сердитые облачка над головой, чтобы было видно
            if (frozen && this.age % 60 == 1) {
                serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.2, this.getZ(),
                        1, 0.2, 0.1, 0.2, 0.0);
            }
        }
        // Меню закрыли или игрок ушёл
        if (interactingPlayer != null && (!interactingPlayer.isAlive()
                || !(interactingPlayer.currentScreenHandler instanceof SettlerScreenHandler))) {
            interactingPlayer = null;
        }
    }

    /** Житель погиб или удалён — убираем его из поселения, добыча выпадает. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld serverWorld && reason.shouldDestroy()) {
            SettlementState.get(serverWorld.getServer()).removeSettler(this.getUuid());
            if (reason == Entity.RemovalReason.KILLED) {
                for (int i = 0; i < storage.size(); i++) {
                    ItemStack stack = storage.getStack(i);
                    if (!stack.isEmpty()) {
                        this.dropStack(stack.copy());
                    }
                }
                storage.clear();
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (homePos != null) {
            nbt.put("Home", NbtHelper.fromBlockPos(homePos));
        }
        nbt.putInt("Level", getLevel());
        nbt.putBoolean("Working", working);
        nbt.putLong("LastWorkDay", lastWorkDay);
        nbt.put("Storage", storage.toNbtList());
        nbt.putInt("Ingredients", ingredients);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) {
            setHome(NbtHelper.toBlockPos(nbt.getCompound("Home")));
        }
        setLevel(nbt.contains("Level") ? nbt.getInt("Level") : 1);
        working = nbt.getBoolean("Working");
        lastWorkDay = nbt.contains("LastWorkDay") ? nbt.getLong("LastWorkDay") : -1;
        storage.readNbtList(nbt.getList("Storage", NbtElement.COMPOUND_TYPE));
        ingredients = nbt.getInt("Ingredients");
    }

    // ---------- Звуки (пока как у обычного жителя) ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    // =====================================================================
    // Поведение (цели ИИ)
    // =====================================================================

    /** Рядом враждебный моб или житель только что получил удар от него. */
    boolean isInDanger() {
        if (SettlerThreats.isThreat(this.getAttacker())) {
            return true;
        }
        return !this.getWorld().getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,
                this.getBoundingBox().expand(10.0, 4.0, 10.0),
                mob -> mob.isAlive() && SettlerThreats.isThreat(mob)).isEmpty();
    }

    /**
     * Опасность: житель бросает всё и бежит домой (двери за собой закрывает
     * LongDoorInteractGoal). Дома ждёт, пока 10 секунд подряд будет спокойно.
     */
    static class FleeHomeGoal extends Goal {

        private static final int CALM_TICKS_TO_STOP = 200;

        private final SettlerEntity settler;
        private int checkCooldown;
        private int calmTicks;

        FleeHomeGoal(SettlerEntity settler) {
            this.settler = settler;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (settler.getHome() == null) {
                return false;
            }
            // Удар — бежим сразу; иначе осматриваемся раз в полсекунды
            if (settler.getAttacker() == null && --checkCooldown > 0) {
                return false;
            }
            checkCooldown = 10;
            return settler.isInDanger();
        }

        @Override
        public boolean shouldContinue() {
            return settler.getHome() != null && calmTicks < CALM_TICKS_TO_STOP;
        }

        @Override
        public void start() {
            calmTicks = 0;
            runHome();
        }

        @Override
        public void stop() {
            settler.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (settler.age % 10 == 0) {
                calmTicks = settler.isInDanger() ? 0 : calmTicks + 10;
            }
            BlockPos home = settler.getHome();
            if (home == null) {
                return;
            }
            if (settler.squaredDistanceTo(Vec3d.ofBottomCenter(home)) > 1.5 * 1.5) {
                if (settler.getNavigation().isIdle() || settler.age % 20 == 0) {
                    runHome();
                }
            } else {
                settler.getNavigation().stop();
            }
        }

        private void runHome() {
            BlockPos home = settler.getHome();
            if (home != null) {
                settler.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.35);
            }
        }
    }

    /**
     * Житель стоит на месте:
     * - у поселения нет запасов (счётчик дошёл до -20);
     * - игрок открыл его меню (тогда житель смотрит на игрока).
     */
    static class StandStillGoal extends Goal {

        private final SettlerEntity settler;

        StandStillGoal(SettlerEntity settler) {
            this.settler = settler;
            // Плавать (прыгать) не мешаем, чтобы житель не утонул
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private boolean active() {
            return settler.getInteractingPlayer() != null || settler.isSettlementFrozen();
        }

        @Override
        public boolean canStart() {
            return active();
        }

        @Override
        public boolean shouldContinue() {
            return active();
        }

        @Override
        public void start() {
            settler.getNavigation().stop();
        }

        @Override
        public void tick() {
            settler.getNavigation().stop();
            PlayerEntity player = settler.getInteractingPlayer();
            if (player != null) {
                settler.getLookControl().lookAt(player, 30.0f, 30.0f);
            }
        }
    }

    /** Работающий житель ходит к рабочим местам возле дома (грядки, сено…) и «возится» с ними. */
    static class WorkGoal extends Goal {

        private static final int SEARCH_RADIUS = 14;

        private final SettlerEntity settler;
        @Nullable
        private BlockPos target;
        private int cooldown;
        private int workTicks;

        WorkGoal(SettlerEntity settler) {
            this.settler = settler;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!settler.isWorking() || settler.getHome() == null) {
                return false;
            }
            if (--cooldown > 0) {
                return false;
            }
            target = findWorkSpot();
            cooldown = target == null ? 200 : 20;   // рабочих мест нет — поищем снова через 10 секунд
            return target != null;
        }

        @Override
        public boolean shouldContinue() {
            return target != null && settler.isWorking() && workTicks > 0;
        }

        @Override
        public void start() {
            workTicks = 200 + settler.getRandom().nextInt(200); // 10–20 секунд у одного места
            moveToTarget();
        }

        @Override
        public void stop() {
            target = null;
            settler.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            workTicks--;
            double distSq = settler.squaredDistanceTo(Vec3d.ofCenter(target));
            if (distSq > 2.5 * 2.5) {
                if (settler.getNavigation().isIdle() || workTicks % 40 == 0) {
                    moveToTarget();
                }
            } else {
                settler.getNavigation().stop();
                settler.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
                if (workTicks % 25 == 0) {
                    settler.swingHand(Hand.MAIN_HAND);
                }
            }
        }

        private void moveToTarget() {
            if (target != null) {
                // Идём к самому блоку: путь строится до соседней с ним клетки
                settler.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
        }

        /** Случайное рабочее место рядом с домом. */
        @Nullable
        private BlockPos findWorkSpot() {
            BlockPos home = settler.getHome();
            if (home == null) {
                return null;
            }
            List<BlockPos> found = new ArrayList<>();
            // Вниз ищем глубоко — шахтёр работает в шахте под землёй
            for (BlockPos pos : BlockPos.iterate(home.add(-SEARCH_RADIUS, -12, -SEARCH_RADIUS),
                    home.add(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
                if (settler.isWorkSpot(settler.getWorld().getBlockState(pos))) {
                    found.add(pos.toImmutable());
                }
            }
            return found.isEmpty() ? null : found.get(settler.getRandom().nextInt(found.size()));
        }
    }

    /** Возвращает жителя домой, если он ушёл дальше {@link #HOME_RADIUS} блоков. */
    static class ReturnHomeGoal extends Goal {

        private final SettlerEntity settler;

        ReturnHomeGoal(SettlerEntity settler) {
            this.settler = settler;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        private double distanceSqToHome() {
            BlockPos home = settler.getHome();
            return home == null ? 0 : settler.squaredDistanceTo(Vec3d.ofBottomCenter(home));
        }

        @Override
        public boolean canStart() {
            return settler.getHome() != null && distanceSqToHome() > HOME_RADIUS * HOME_RADIUS;
        }

        @Override
        public boolean shouldContinue() {
            return settler.getHome() != null && distanceSqToHome() > 16 * 16 && !settler.getNavigation().isIdle();
        }

        @Override
        public void start() {
            BlockPos home = settler.getHome();
            if (home == null) {
                return;
            }
            boolean pathFound = settler.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            // Совсем далеко или дороги нет — просто переносим домой
            if (!pathFound || distanceSqToHome() > (HOME_RADIUS + 30) * (HOME_RADIUS + 30)) {
                settler.getNavigation().stop();
                settler.requestTeleport(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            }
        }

        @Override
        public void stop() {
            settler.getNavigation().stop();
        }
    }
}
