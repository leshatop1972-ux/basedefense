package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.SettlerScreenHandler;
import com.lesha_top1972.basedefense.settlement.SettlementItems;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.settlement.SettlementState;
import com.lesha_top1972.basedefense.structure.HousePlans;
import com.lesha_top1972.basedefense.structure.TownState;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.inventory.Inventory;
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
 * Работа: житель с профессией работает всегда сам. Каждый рассвет добыча складывается
 * в сундук его дома ({@link #chestPos}). Если сундука нет или он полон — добыча ждёт
 * у жителя (27 ячеек) и переносится в сундук, как только появится место.
 * По клику открывается меню (улучшить; у повара — ещё «отдать еду из руки»).
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
    /** Где искать сундук, если он не задан: радиус вокруг дома по горизонтали. */
    private static final int CHEST_SEARCH_RADIUS = 8;

    private static final TrackedData<Integer> LEVEL =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Внешность: номер из SettlerLooks (-1 — ещё не выбрана). Не зависит от профессии. */
    private static final TrackedData<Integer> SKIN =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Nullable
    private BlockPos homePos;
    /** Сундук дома, куда житель складывает добычу. */
    @Nullable
    private BlockPos chestPos;
    /** Номер игрового дня, за который уже выдана добыча. */
    private long lastWorkDay = -1;
    /** Добыча, которая не поместилась в сундук (ждёт места). */
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
        this.dataTracker.startTracking(SKIN, -1);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FleeHomeGoal(this));     // рядом враг — бежит домой и закрывает дверь
        this.goalSelector.add(1, new StandStillGoal(this));   // нет запасов или открыто меню — стоит
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(1, new LongDoorInteractGoal(this, true));   // открывает и закрывает двери
        this.goalSelector.add(2, new ReturnHomeGoal(this));
        this.goalSelector.add(3, new WorkGoal(this));         // работает (грядки, загон…)
        this.goalSelector.add(3, new VisitSquareGoal(this));  // иногда ходит на центральную площадь
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

    /** Сколько еды тратит житель в день (с профессией). */
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

    /** На каком расстоянии от дома (по горизонтали) житель ищет рабочие места. */
    public int getWorkSearchRadius() {
        return 14;
    }

    public boolean hasJob() {
        return getWorkLoot(1) != null;
    }

    /** Инструмент, который житель держит в руке (просто для вида). */
    protected ItemStack getDefaultTool() {
        return ItemStack.EMPTY;
    }

    /** Житель гуляет на площади (см. VisitSquareGoal). */
    private boolean visitingSquare;

    /** Житель занят делом вдали от дома (строитель на стройке, прогулка на площадь) — не тянуть его домой. */
    protected boolean isAwayOnDuty() {
        return visitingSquare;
    }

    /** Сколько еды житель тратит в день прямо сейчас. */
    public int getFoodPerDay() {
        return hasJob() ? getWorkFood(getLevel()) : SettlementManager.FOOD_PER_SETTLER;
    }

    public static int getUpgradeCost(int level) {
        return level >= 1 && level < UPGRADE_COST.length ? UPGRADE_COST[level] : 0;
    }

    public int getLevel() {
        return this.dataTracker.get(LEVEL);
    }

    /** Номер внешности (см. SettlerLooks) или -1, пока не выбрана. */
    public int getSkin() {
        return this.dataTracker.get(SKIN);
    }

    public boolean isFemale() {
        return SettlerLooks.isFemale(getSkin());
    }

    /** Новому жителю — случайная внешность и русское имя с фамилией (имя видно над головой и в меню). */
    private void ensureLook() {
        if (getSkin() < 0) {
            this.dataTracker.set(SKIN, this.random.nextInt(SettlerLooks.COUNT));
        }
        if (!this.hasCustomName()) {
            this.setCustomName(Text.literal(SettlerNames.random(this.random, isFemale())));
        }
    }

    private void setLevel(int level) {
        this.dataTracker.set(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
    }

    /** Житель с профессией работает всегда. */
    public boolean isWorking() {
        return hasJob();
    }

    public void setChest(@Nullable BlockPos pos) {
        this.chestPos = pos == null ? null : pos.toImmutable();
    }

    public boolean hasChest() {
        return chestPos != null;
    }

    /** Сколько предметов ждёт у жителя (сундука нет или он полон). */
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

    protected boolean isSettlementFrozen() {
        return this.getWorld() instanceof ServerWorld serverWorld && SettlementManager.isFrozen(serverWorld.getServer());
    }

    private static long currentDay(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / 24000L;
    }

    /** Кнопка «Улучшить»: улучшить жителя за монеты исследований. */
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

    /** Кнопка повара: отдать жителю еду из руки — она превращается в очки продуктов. */
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

    /** Положить добычу в запас жителя (потом она уйдёт в сундук; если запас полон — лишнее пропадает). */
    protected void addLoot(WorkLoot pick) {
        int count = pick.rollCount(this.random);
        while (count > 0) {
            int part = Math.min(count, pick.item().getMaxCount());
            storage.addStack(new ItemStack(pick.item(), part));
            count -= part;
        }
    }

    // =====================================================================
    // Сундук дома
    // =====================================================================

    /** Инвентарь сундука дома или null (сундука нет / чанк не загружен). */
    @Nullable
    private Inventory getChestInventory(ServerWorld world) {
        if (chestPos == null || !world.isChunkLoaded(chestPos)) {
            return null;
        }
        BlockState state = world.getBlockState(chestPos);
        if (state.getBlock() instanceof ChestBlock chest) {
            // Учитывает двойной сундук
            Inventory inventory = ChestBlock.getInventory(chest, state, world, chestPos, true);
            if (inventory != null) {
                return inventory;
            }
        }
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (blockEntity instanceof Inventory inventory) {
            return inventory;
        }
        chestPos = null;   // сундук сломали — поищем новый рядом с домом
        return null;
    }

    /** Ищет ближайший сундук возле дома (для жителей из яйца или если старый сундук сломали). */
    private void findChest(ServerWorld world) {
        BlockPos home = homePos;
        if (home == null) {
            return;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(home.add(-CHEST_SEARCH_RADIUS, -2, -CHEST_SEARCH_RADIUS),
                home.add(CHEST_SEARCH_RADIUS, 3, CHEST_SEARCH_RADIUS))) {
            if (world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                double dist = pos.getSquaredDistance(home);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.toImmutable();
                }
            }
        }
        chestPos = best;
    }

    /** Переносит добычу из запаса жителя в сундук (сколько влезет). */
    private void flushToChest(ServerWorld world) {
        if (getStoredCount() == 0) {
            return;
        }
        Inventory chest = getChestInventory(world);
        if (chest == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = insertInto(chest, stack.copy());
            if (rest.getCount() != before) {
                storage.setStack(i, rest);
                changed = true;
            }
        }
        if (changed) {
            chest.markDirty();
        }
    }

    /** Кладёт предметы в инвентарь: сначала докладывает в такие же стопки, потом в пустые ячейки. */
    private static ItemStack insertInto(Inventory inventory, ItemStack stack) {
        int maxPerSlot = Math.min(stack.getMaxCount(), inventory.getMaxCountPerStack());
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            ItemStack slot = inventory.getStack(i);
            if (!slot.isEmpty() && ItemStack.canCombine(slot, stack) && inventory.isValid(i, stack)) {
                int move = Math.min(stack.getCount(), maxPerSlot - slot.getCount());
                if (move > 0) {
                    slot.increment(move);
                    stack.decrement(move);
                }
            }
        }
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            if (inventory.getStack(i).isEmpty() && inventory.isValid(i, stack)) {
                int move = Math.min(stack.getCount(), maxPerSlot);
                inventory.setStack(i, stack.split(move));
            }
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
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
        if (!this.getWorld().isClient && (getSkin() < 0 || !this.hasCustomName())) {
            ensureLook();
        }
        if (this.getWorld() instanceof ServerWorld serverWorld && this.age % 20 == 1 && this.isAlive()) {
            // Привязка к Базе: житель записан в поселение вместе со своим расходом еды
            updateSettlement(serverWorld);
            // Инструмент в руке (для вида; при смерти не выпадает)
            if (this.getMainHandStack().isEmpty() && !getDefaultTool().isEmpty()) {
                this.equipStack(EquipmentSlot.MAINHAND, getDefaultTool());
                this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }

            boolean frozen = SettlementManager.isFrozen(serverWorld.getServer());
            // Добыча: на каждом рассвете (дни, пока чанк не был загружен, тоже считаются)
            if (hasJob()) {
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
                        flushToChest(serverWorld);
                    }
                }
                // Раз в 5 секунд: нет сундука — ищем; есть добыча в запасе — переносим в сундук
                if (this.age % 100 == 1) {
                    if (chestPos == null) {
                        findChest(serverWorld);
                    }
                    flushToChest(serverWorld);
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
        nbt.putInt("Skin", getSkin());
        if (chestPos != null) {
            nbt.put("Chest", NbtHelper.fromBlockPos(chestPos));
        }
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
        int skin = nbt.contains("Skin") ? nbt.getInt("Skin") : -1;
        this.dataTracker.set(SKIN, SettlerLooks.isValid(skin) ? skin : -1);
        chestPos = nbt.contains("Chest") ? NbtHelper.toBlockPos(nbt.getCompound("Chest")) : null;
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

    /** Житель с профессией ходит к рабочим местам возле дома (грядки, сено…) и «возится» с ними. */
    static class WorkGoal extends Goal {

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
            int r = settler.getWorkSearchRadius();
            // Вниз ищем глубоко — шахтёр работает в шахте под землёй
            for (BlockPos pos : BlockPos.iterate(home.add(-r, -12, -r), home.add(r, 3, r))) {
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
            return settler.getHome() != null && !settler.isAwayOnDuty()
                    && distanceSqToHome() > HOME_RADIUS * HOME_RADIUS;
        }

        @Override
        public boolean shouldContinue() {
            return settler.getHome() != null && !settler.isAwayOnDuty()
                    && distanceSqToHome() > 16 * 16 && !settler.getNavigation().isIdle();
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

    /**
     * Иногда (днём, в среднем раз в несколько минут) житель бросает дела и идёт на центральную площадь:
     * стоит у звонницы или у рыночных прилавков, переходит с места на место.
     * Добычу это не уменьшает — она всё равно появится в сундуке на рассвете.
     */
    static class VisitSquareGoal extends Goal {

        /** Площадь не дальше этого от дома жителя. */
        private static final double MAX_DISTANCE = 128.0;

        private final SettlerEntity settler;
        @Nullable
        private TownState.Square square;
        @Nullable
        private BlockPos spot;
        @Nullable
        private Vec3d lookAt;
        private int timeLeft;
        private int nextSpot;

        VisitSquareGoal(SettlerEntity settler) {
            this.settler = settler;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!(settler.getWorld() instanceof ServerWorld world) || !world.isDay()
                    || settler.getRandom().nextInt(2400) != 0 || settler.isSettlementFrozen()) {
                return false;
            }
            TownState.Square found = TownState.get(world.getServer())
                    .getSquare(world.getRegistryKey().getValue().toString());
            BlockPos home = settler.getHome();
            if (found == null || home == null || !found.center().isWithinDistance(home, MAX_DISTANCE)) {
                return false;
            }
            square = found;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return timeLeft > 0 && square != null && !settler.isSettlementFrozen();
        }

        @Override
        public void start() {
            settler.visitingSquare = true;
            timeLeft = 600 + settler.getRandom().nextInt(900);   // 30–75 секунд
            pickSpot();
        }

        @Override
        public void stop() {
            settler.visitingSquare = false;
            settler.getNavigation().stop();
            spot = null;
        }

        @Override
        public void tick() {
            timeLeft--;
            if (spot == null) {
                return;
            }
            double dist = settler.squaredDistanceTo(Vec3d.ofBottomCenter(spot));
            if (dist > 1.5 * 1.5) {
                if (settler.getNavigation().isIdle() || settler.age % 40 == 0) {
                    boolean ok = settler.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(),
                            spot.getZ() + 0.5, 0.6);
                    if (!ok && dist > 48 * 48) {
                        timeLeft = 0;   // дойти нельзя — бросаем затею
                    }
                }
                return;
            }
            // На месте: смотрит на прилавок или колокол, иногда переходит
            settler.getNavigation().stop();
            if (lookAt != null) {
                settler.getLookControl().lookAt(lookAt.x, lookAt.y, lookAt.z);
            }
            if (--nextSpot <= 0) {
                pickSpot();
            }
        }

        /** Случайное место: у одного из прилавков или на кольце вокруг звонницы. */
        private void pickSpot() {
            TownState.Square sq = square;
            if (sq == null) {
                return;
            }
            nextSpot = 160 + settler.getRandom().nextInt(200);
            if (settler.getRandom().nextBoolean()) {
                // К прилавку: стоит перед ним и смотрит на товар
                int[] m = HousePlans.MARKET_SPOTS[settler.getRandom().nextInt(HousePlans.MARKET_SPOTS.length)];
                spot = sq.offset(m[0], 1, m[1]);
                int towardStall = m[1] < 0 ? -1 : 1;
                lookAt = Vec3d.ofCenter(sq.offset(m[0], 1, m[1] + towardStall)).add(0, 0.6, 0);
            } else {
                int r = 2 + settler.getRandom().nextInt(2);
                int a = settler.getRandom().nextInt(4 * 2 * r);
                int side = a / (2 * r);
                int t = a % (2 * r) - r;
                int x = switch (side) { case 0 -> t; case 1 -> r; case 2 -> -t; default -> -r; };
                int z = switch (side) { case 0 -> -r; case 1 -> t; case 2 -> r; default -> -t; };
                spot = sq.center().add(x, 1, z);
                lookAt = Vec3d.ofCenter(sq.bell());
            }
            settler.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.6);
        }
    }
}
