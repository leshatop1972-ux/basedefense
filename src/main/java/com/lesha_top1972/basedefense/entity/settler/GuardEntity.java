package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.item.SummonsItem;
import com.lesha_top1972.basedefense.military.SquadState;
import com.lesha_top1972.basedefense.screen.GuardScreenHandler;
import com.lesha_top1972.basedefense.structure.TownState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Стражник — военный поселенец. Вербуется в военкомате казармы, приходит с пустыми руками.
 * - ПКМ — своё меню (GuardScreenHandler): оружие (меч, топор или лук), 4 слота брони,
 *   3 слота еды (ест сам, когда ранен), 2 слота стрел (для лука), 2 слота артефактов (пока закрыты).
 * - Нападает на всех мобов мода в {@link #AGGRO} блоках. Мечник/топорщик бежит к врагу,
 *   лучник держится в ~{@link #BOW_RANGE} блоках и стреляет раз в 4 секунды (попадает в половине случаев).
 * - Без отряда (или у отряда нет точки) патрулирует город днём и ночью; у отряда с точкой
 *   бежит к ней и не отходит дальше {@link #LEASH} блоков.
 * - Без оружия (или с луком без стрел) не дерётся — отбегает от врагов.
 * Анимации (клиент, client/entity/settler/GuardAnimations): два вида удара мечом по очереди,
 * удар топором, стрельба из лука, еда, боевая стойка — по номеру действия {@link #ACTION}.
 */
public class GuardEntity extends SettlerEntity {

    // ---------- Баланс ----------
    /** Дальность агра на мобов мода. */
    public static final double AGGRO = 35.0;
    /** Не дальше стольких блоков от точки обороны. */
    public static final double LEASH = 8.0;
    /** Лучник держится на этой дистанции. */
    public static final double BOW_RANGE = 15.0;
    /** Шанс попадания лучника. */
    public static final float BOW_HIT_CHANCE = 0.5f;
    /** Скорости (множитель к атрибуту 0.26): погоня, отход, бег на точку, прогулка. */
    public static final double SPEED_CHASE = 1.0;
    public static final double SPEED_RETREAT = 1.05;
    public static final double SPEED_TO_POINT = 0.95;
    public static final double SPEED_WALK = 0.6;
    /** Перед едой отбегает от врага не ближе стольких блоков (или пока не пройдёт 3 с). */
    public static final double EAT_SAFE_DISTANCE = 9.0;

    // ---------- Действия (номер для анимации) ----------
    public static final int ACT_NONE = 0;
    public static final int ACT_SWORD = 1;
    public static final int ACT_AXE = 2;
    public static final int ACT_BOW = 3;
    public static final int ACT_EAT = 4;

    /** Длительность анимаций (тики) и момент удара/выстрела/укуса. */
    public static final int SWORD_TICKS = 14;
    public static final int SWORD_HIT = 7;
    public static final int SWORD_CYCLE = 18;
    public static final int AXE_TICKS = 22;
    public static final int AXE_HIT = 11;
    public static final int AXE_CYCLE = 28;
    public static final int BOW_TICKS = 30;
    public static final int BOW_RELEASE = 22;
    public static final int BOW_CYCLE = 80;      // выстрел раз в 4 секунды
    public static final int EAT_TICKS = 32;
    public static final int EAT_BITE = 24;
    /** Ест не чаще раза в 30 секунд. */
    public static final int EAT_COOLDOWN = 600;

    /** Слоты сумки: 0–2 еда, 3–4 стрелы, 5–6 артефакты (пока закрыты). */
    public static final int FOOD_START = 0;
    public static final int FOOD_SLOTS = 3;
    public static final int ARROW_START = 3;
    public static final int ARROW_SLOTS = 2;
    public static final int ARTIFACT_START = 5;
    public static final int ARTIFACT_SLOTS = 2;
    public static final int BAG_SIZE = 7;

    public enum Weapon { NONE, SWORD, AXE, BOW }

    /** Режим (для меню). */
    public static final int MODE_PATROL = 0;
    public static final int MODE_DEFEND = 1;
    public static final int MODE_FIGHT = 2;
    public static final int MODE_EAT = 3;
    public static final int MODE_UNARMED = 4;

    /** (номер действия << 4) | вид — номер растёт, чтобы два одинаковых удара подряд тоже проигрались. */
    private static final TrackedData<Integer> ACTION = DataTracker.registerData(GuardEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    /** В бою (есть цель и есть чем драться) — боевая стойка. */
    private static final TrackedData<Boolean> COMBAT = DataTracker.registerData(GuardEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);

    private final SimpleInventory bag = new SimpleInventory(BAG_SIZE);

    @Nullable
    private BlockPos barracks;

    // ---------- Сервер ----------
    @Nullable
    private LivingEntity enemy;
    private int actionType = ACT_NONE;
    private int actionTick;
    private int actionSeq;
    private int nextAttackAge;
    private int eatCooldown;
    private int calmTicks;
    private int squad = -1;
    @Nullable
    private BlockPos defendPoint;
    @Nullable
    private PlayerEntity menuPlayer;
    private int mode = MODE_PATROL;

    // ---------- Клиент (анимация) ----------
    private int clientAction = ACT_NONE;
    private int clientActionSeq;
    private float clientActionStart;
    private float combatBlend;
    private float prevCombatBlend;

    public GuardEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // Снаряжение выпадает целым при гибели; в левой руке — только еда «на время укуса»
            this.setEquipmentDropChance(slot, slot == EquipmentSlot.OFFHAND ? 0.0f : 2.0f);
        }
    }

    public static DefaultAttributeContainer.Builder createGuardAttributes() {
        return SettlerEntity.createSettlerAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ACTION, 0);
        this.dataTracker.startTracking(COMBAT, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LongDoorInteractGoal(this, true));
        this.goalSelector.add(2, new GuardBrain(this));
    }

    // =====================================================================
    // Снаряжение и сумка
    // =====================================================================

    public SimpleInventory getBag() {
        return bag;
    }

    public static Weapon weaponOf(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem) {
            return Weapon.SWORD;
        }
        if (stack.getItem() instanceof AxeItem) {
            return Weapon.AXE;
        }
        if (stack.getItem() instanceof BowItem) {
            return Weapon.BOW;
        }
        return Weapon.NONE;
    }

    public Weapon getWeapon() {
        return weaponOf(this.getMainHandStack());
    }

    /** Чем реально может драться: лук без стрел — всё равно что без оружия. */
    public Weapon getUsableWeapon() {
        Weapon w = getWeapon();
        if (w == Weapon.BOW && findArrows() < 0 && !hasInfinity()) {
            return Weapon.NONE;
        }
        return w;
    }

    private boolean hasInfinity() {
        return EnchantmentHelper.getLevel(Enchantments.INFINITY, this.getMainHandStack()) > 0;
    }

    private int findArrows() {
        for (int i = ARROW_START; i < ARROW_START + ARROW_SLOTS; i++) {
            if (bag.getStack(i).getItem() instanceof ArrowItem) {
                return i;
            }
        }
        return -1;
    }

    private int findFood() {
        for (int i = FOOD_START; i < FOOD_START + FOOD_SLOTS; i++) {
            if (bag.getStack(i).isFood()) {
                return i;
            }
        }
        return -1;
    }

    public int getSquad() {
        return squad;
    }

    public int getMode() {
        return mode;
    }

    // =====================================================================
    // Клик и меню
    // =====================================================================

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        ItemStack held = player.getStackInHand(hand);
        // С Повесткой в руке — записать в выбранный отряд (или выписать из него)
        if (held.getItem() instanceof SummonsItem) {
            if (!this.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
                SummonsItem.toggleMember(sp, held, this);
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            this.getLookControl().lookAt(player, 30.0f, 30.0f);
            this.playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.8f, getSoundPitch());
            menuPlayer = sp;
            GuardEntity self = this;
            sp.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
                    buf.writeVarInt(self.getId());
                }

                @Override
                public Text getDisplayName() {
                    return self.getDisplayName();
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                    return new GuardScreenHandler(syncId, inventory, self);
                }
            });
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    // =====================================================================
    // Тик
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            prevCombatBlend = combatBlend;
            float goal = this.dataTracker.get(COMBAT) ? 1.0f : 0.0f;
            combatBlend += MathHelper.clamp(goal - combatBlend, -0.12f, 0.12f);
            return;
        }
        if (menuPlayer != null && (!menuPlayer.isAlive()
                || !(menuPlayer.currentScreenHandler instanceof GuardScreenHandler))) {
            menuPlayer = null;
        }
        if (eatCooldown > 0) {
            eatCooldown--;
        }
        // Отряд и точка обороны — раз в секунду из общего списка отрядов
        if (this.age % 20 == 0 && this.getWorld() instanceof ServerWorld sw) {
            SquadState state = SquadState.get(sw.getServer());
            squad = state.squadOf(this.getUuid());
            defendPoint = null;
            if (squad >= 0) {
                SquadState.Squad s = state.squad(squad);
                if (s.point() != null && s.dimension().equals(sw.getRegistryKey().getValue().toString())) {
                    defendPoint = s.point();
                }
                state.updateName(this.getUuid(), this.getName().getString());
            }
        }
        // Без врагов 5 секунд — понемногу восстанавливает здоровье
        if (enemy == null) {
            calmTicks++;
            if (calmTicks > 100 && this.age % 80 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0f);
            }
        } else {
            calmTicks = 0;
        }
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ACTION.equals(data) && this.getWorld().isClient) {
            int v = this.dataTracker.get(ACTION);
            if ((v >> 4) != clientActionSeq) {
                clientActionSeq = v >> 4;
                clientAction = v & 15;
                clientActionStart = this.age;
            }
        }
    }

    // ---------- Для анимации (клиент) ----------

    public int getClientAction() {
        return clientAction;
    }

    /** Номер удара: чётный/нечётный — два вида удара мечом по очереди. */
    public int getClientActionSeq() {
        return clientActionSeq;
    }

    public float getClientActionStart() {
        return clientActionStart;
    }

    public float getCombatBlend(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevCombatBlend, combatBlend);
    }

    public static int actionLength(int action) {
        return switch (action) {
            case ACT_SWORD -> SWORD_TICKS;
            case ACT_AXE -> AXE_TICKS;
            case ACT_BOW -> BOW_TICKS;
            case ACT_EAT -> EAT_TICKS;
            default -> 1;
        };
    }

    // =====================================================================
    // Действия (сервер)
    // =====================================================================

    private boolean isActing() {
        return actionType != ACT_NONE;
    }

    private void startAction(int type) {
        actionType = type;
        actionTick = 0;
        actionSeq = (actionSeq + 1) & 0x7FFFFFF;
        this.dataTracker.set(ACTION, (actionSeq << 4) | type);
        switch (type) {
            case ACT_SWORD -> nextAttackAge = this.age + SWORD_CYCLE;
            case ACT_AXE -> nextAttackAge = this.age + AXE_CYCLE;
            case ACT_BOW -> {
                nextAttackAge = this.age + BOW_CYCLE;
                this.setCurrentHand(Hand.MAIN_HAND);   // тетива натягивается (картинка лука)
            }
            case ACT_EAT -> {
                int slot = findFood();
                if (slot >= 0) {
                    this.equipStack(EquipmentSlot.OFFHAND, bag.getStack(slot).copyWithCount(1));
                }
            }
            default -> {
            }
        }
    }

    private void endAction() {
        if (this.isUsingItem()) {
            this.clearActiveItem();
        }
        if (actionType == ACT_EAT) {
            this.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        actionType = ACT_NONE;
    }

    private void tickAction() {
        actionTick++;
        LivingEntity target = enemy;
        switch (actionType) {
            case ACT_SWORD, ACT_AXE -> {
                boolean sword = actionType == ACT_SWORD;
                if (target != null) {
                    faceTowards(target, 25.0f);
                }
                int hit = sword ? SWORD_HIT : AXE_HIT;
                if (actionTick == hit - 2 && target != null) {
                    // Короткий шаг‑выпад перед ударом
                    Vec3d d = target.getPos().subtract(this.getPos()).multiply(1, 0, 1);
                    if (d.lengthSquared() > 0.01) {
                        Vec3d push = d.normalize().multiply(sword ? 0.22 : 0.16);
                        this.addVelocity(push.x, 0.0, push.z);
                    }
                }
                if (actionTick == hit) {
                    this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, sword ? 1.15f : 0.8f);
                    if (target != null && target.isAlive() && inReach(target, 0.9) && this.tryAttack(target)) {
                        // «Кровотёк» на стражнике срабатывает, когда он бьёт (как у игрока)
                        if (this.hasStatusEffect(com.lesha_top1972.basedefense.effect.ModEffects.BLEED)) {
                            com.lesha_top1972.basedefense.effect.ModEffects.triggerBleed(this);
                        }
                        this.playSound(sword ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG
                                : SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.0f);
                    }
                }
                if (actionTick >= (sword ? SWORD_TICKS : AXE_TICKS)) {
                    endAction();
                }
            }
            case ACT_BOW -> {
                this.getNavigation().stop();
                if (target != null) {
                    faceTowards(target, 15.0f);
                    this.getLookControl().lookAt(target, 30.0f, 30.0f);
                }
                if (actionTick == 6) {
                    this.playSound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.4f, 1.4f);   // достаёт стрелу
                }
                if (actionTick == BOW_RELEASE) {
                    if (this.isUsingItem()) {
                        this.clearActiveItem();
                    }
                    if (target != null && target.isAlive()) {
                        shootArrow(target);
                    }
                }
                if (actionTick >= BOW_TICKS) {
                    endAction();
                }
            }
            case ACT_EAT -> {
                this.getNavigation().stop();
                if (actionTick >= 6 && actionTick <= EAT_BITE && actionTick % 4 == 2) {
                    this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.6f, 0.9f + this.random.nextFloat() * 0.2f);
                    ItemStack food = this.getOffHandStack();
                    if (!food.isEmpty() && this.getWorld() instanceof ServerWorld sw) {
                        Vec3d mouth = this.getEyePos().add(this.getRotationVector().multiply(0.3)).add(0, -0.15, 0);
                        sw.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, food),
                                mouth.x, mouth.y, mouth.z, 4, 0.05, 0.05, 0.05, 0.05);
                    }
                }
                if (actionTick == EAT_BITE) {
                    int slot = findFood();
                    if (slot >= 0) {
                        ItemStack stack = bag.getStack(slot);
                        FoodComponent food = stack.getItem().getFoodComponent();
                        float heal = food != null ? Math.max(2.0f, food.getHunger() * 1.5f) : 2.0f;
                        this.heal(heal);
                        stack.decrement(1);
                        bag.markDirty();
                        this.playSound(SoundEvents.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
                    }
                    eatCooldown = EAT_COOLDOWN;
                }
                if (actionTick >= EAT_TICKS) {
                    endAction();
                }
            }
            default -> endAction();
        }
    }

    /** Цель в досягаемости удара (с запасом extra). */
    private boolean inReach(LivingEntity target, double extra) {
        double reach = this.getWidth() * 0.5 + target.getWidth() * 0.5 + 1.5 + extra;
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY() - this.getY();
        return dx * dx + dz * dz <= reach * reach && dy > -2.5 && dy < this.getHeight() + 0.5;
    }

    /** Поворот корпуса к цели не быстрее maxStep градусов за тик. */
    private void faceTowards(Entity target, float maxStep) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float want = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        float yaw = this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - this.getYaw()), -maxStep, maxStep);
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    /** Выстрел: половина стрел летит точно (с упреждением и доводкой), половина — мимо. */
    private void shootArrow(LivingEntity target) {
        ItemStack bow = this.getMainHandStack();
        int slot = findArrows();
        ItemStack arrowStack = slot >= 0 ? bag.getStack(slot) : new ItemStack(Items.ARROW);
        GuardArrowEntity arrow = new GuardArrowEntity(this.getWorld(), this, arrowStack);
        int power = EnchantmentHelper.getLevel(Enchantments.POWER, bow);
        if (power > 0) {
            arrow.setDamage(arrow.getDamage() + power * 0.5 + 0.5);
        }
        int punch = EnchantmentHelper.getLevel(Enchantments.PUNCH, bow);
        if (punch > 0) {
            arrow.setPunch(punch);
        }
        if (EnchantmentHelper.getLevel(Enchantments.FLAME, bow) > 0) {
            arrow.setOnFireFor(100);
        }
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;

        double speed = 2.4;
        Vec3d from = new Vec3d(this.getX(), this.getEyeY() - 0.1, this.getZ());
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.55, 0);
        double flight = aim.distanceTo(from) / speed;
        aim = aim.add(target.getVelocity().multiply(1, 0, 1).multiply(flight));   // упреждение
        boolean hit = this.random.nextFloat() < BOW_HIT_CHANCE;
        if (hit) {
            arrow.setHoming(target);   // попадание: стрела доводится до цели
        } else {
            // Мимо: в сторону на 1.5–2.5 блока и чуть выше/ниже
            Vec3d dir = aim.subtract(from);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x).normalize();
            double off = (1.5 + this.random.nextDouble()) * (this.random.nextBoolean() ? 1 : -1);
            aim = aim.add(side.multiply(off)).add(0, (this.random.nextDouble() - 0.3) * 1.2, 0);
        }
        Vec3d d = aim.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        arrow.setPosition(from.x, from.y, from.z);
        arrow.setVelocity(d.x, d.y + horiz * 0.06, d.z, (float) speed, 0.0f);
        this.getWorld().spawnEntity(arrow);
        this.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 1.0f / (this.random.nextFloat() * 0.4f + 0.8f));

        boolean infinite = hasInfinity() && arrowStack.isOf(Items.ARROW);
        if (slot >= 0 && !infinite) {
            arrowStack.decrement(1);
            bag.markDirty();
        }
    }

    // =====================================================================
    // Враги
    // =====================================================================

    /** Враг стражника: любой живой видимый моб мода, кроме жителей. */
    public static boolean isEnemy(Entity e) {
        if (e instanceof com.lesha_top1972.basedefense.entity.enemy.sapper.SapperEntity sapper && sapper.isUnder()) {
            return false;   // подкопщик под землёй — его не достать
        }
        return e instanceof MobEntity mob && mob.isAlive() && !(e instanceof SettlerEntity) && !mob.isInvisible()
                && Basedefense.MOD_ID.equals(Registries.ENTITY_TYPE.getId(e.getType()).getNamespace());
    }

    @Nullable
    private LivingEntity findEnemy(Weapon weapon) {
        List<MobEntity> list = this.getWorld().getEntitiesByClass(MobEntity.class,
                this.getBoundingBox().expand(AGGRO), GuardEntity::isEnemy);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (MobEntity mob : list) {
            double d2 = this.squaredDistanceTo(mob);
            if (d2 > AGGRO * AGGRO) {
                continue;
            }
            // На точке обороны мечник не гонится за теми, кто далеко от точки
            if (defendPoint != null && weapon != Weapon.BOW
                    && mob.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(defendPoint)) > (LEASH + 5) * (LEASH + 5)) {
                continue;
            }
            double score = d2 * (this.canSee(mob) ? 1.0 : 4.0);
            if (score < bestScore) {
                bestScore = score;
                best = mob;
            }
        }
        return best;
    }

    // =====================================================================
    // Дом, сохранение, смерть
    // =====================================================================

    @Override
    public void setHome(BlockPos pos) {
        super.setHome(pos);
        this.clearPositionTarget();   // стражник ходит по всему городу, а не вокруг дома
    }

    public void setBarracks(@Nullable BlockPos office) {
        this.barracks = office == null ? null : office.toImmutable();
    }

    @Nullable
    public BlockPos getBarracks() {
        return barracks;
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        for (int i = 0; i < ARTIFACT_START; i++) {
            ItemStack stack = bag.getStack(i);
            if (!stack.isEmpty()) {
                this.dropStack(stack.copy());
            }
        }
        bag.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld sw && reason.shouldDestroy()) {
            SquadState.get(sw.getServer()).remove(this.getUuid());
        }
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (barracks != null) {
            nbt.put("Barracks", NbtHelper.fromBlockPos(barracks));
        }
        nbt.put("GuardBag", bag.toNbtList());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        barracks = nbt.contains("Barracks") ? NbtHelper.toBlockPos(nbt.getCompound("Barracks")) : null;
        if (nbt.contains("GuardBag")) {
            bag.readNbtList(nbt.getList("GuardBag", NbtElement.COMPOUND_TYPE));
        }
    }

    // =====================================================================
    // Мозг: бой, оборона точки, патруль
    // =====================================================================

    static class GuardBrain extends Goal {

        private final GuardEntity guard;
        private int searchCooldown;
        @Nullable
        private BlockPos walkTarget;
        private int waitTicks;
        private int walkTicks;
        private int repathCooldown;
        private int retreatTicks;

        GuardBrain(GuardEntity guard) {
            this.guard = guard;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            guard.endAction();
            guard.getNavigation().stop();
        }

        @Override
        public void tick() {
            GuardEntity g = guard;
            // Открыто меню — стоит и смотрит на игрока
            if (g.menuPlayer != null) {
                if (g.isActing()) {
                    g.endAction();
                }
                g.getNavigation().stop();
                g.getLookControl().lookAt(g.menuPlayer, 30.0f, 30.0f);
                g.enemy = null;
                g.dataTracker.set(COMBAT, false);
                return;
            }
            Weapon weapon = g.getUsableWeapon();

            // Цель
            if (g.enemy != null && (!g.enemy.isAlive() || g.enemy.isRemoved() || !isEnemy(g.enemy)
                    || g.squaredDistanceTo(g.enemy) > (AGGRO + 6) * (AGGRO + 6))) {
                g.enemy = null;
            }
            if (--searchCooldown <= 0) {
                searchCooldown = 10;
                LivingEntity found = g.findEnemy(weapon == Weapon.NONE ? Weapon.SWORD : weapon);
                if (found != null && (g.enemy == null || g.squaredDistanceTo(found) + 9 < g.squaredDistanceTo(g.enemy))) {
                    g.enemy = found;
                }
            }
            g.dataTracker.set(COMBAT, g.enemy != null && weapon != Weapon.NONE);

            // Заморожен или оглушён — не бьёт и не стреляет (начатый удар/выстрел срывается)
            boolean disabled = com.lesha_top1972.basedefense.effect.ModEffects.isFrozen(g)
                    || com.lesha_top1972.basedefense.effect.ModEffects.isStunned(g);
            if (disabled && g.isActing() && g.actionType != ACT_EAT) {
                g.endAction();
            }

            if (g.isActing()) {
                g.tickAction();
                return;
            }

            // Ранен — сначала отбегает от врага, потом ест (если есть еда)
            if (g.getHealth() < g.getMaxHealth() * 0.6f && g.eatCooldown <= 0 && g.findFood() >= 0) {
                g.mode = MODE_EAT;
                if (g.enemy != null && g.distanceTo(g.enemy) < EAT_SAFE_DISTANCE && retreatTicks < 60) {
                    retreatTicks++;
                    Vec3d away = g.getPos().subtract(g.enemy.getPos()).multiply(1, 0, 1);
                    if (away.lengthSquared() < 0.01) {
                        away = new Vec3d(1, 0, 0);
                    }
                    moveTo(leash(g.getPos().add(away.normalize().multiply(8))), SPEED_RETREAT);
                    return;
                }
                retreatTicks = 0;
                g.getNavigation().stop();
                g.startAction(ACT_EAT);
                return;
            }
            retreatTicks = 0;
            if (disabled) {
                g.getNavigation().stop();
                return;
            }

            if (g.enemy != null) {
                switch (weapon) {
                    case SWORD, AXE -> {
                        g.mode = MODE_FIGHT;
                        melee(weapon);
                    }
                    case BOW -> {
                        g.mode = MODE_FIGHT;
                        archer();
                    }
                    default -> {
                        g.mode = MODE_UNARMED;
                        flee();
                    }
                }
                return;
            }
            if (g.defendPoint != null) {
                g.mode = MODE_DEFEND;
                holdPoint();
            } else {
                g.mode = weapon == Weapon.NONE ? MODE_UNARMED : MODE_PATROL;
                patrol();
            }
        }

        /** Точка на поводке: если pos дальше LEASH от точки обороны — подтягиваем к краю круга. */
        private Vec3d leash(Vec3d pos) {
            BlockPos p = guard.defendPoint;
            if (p == null) {
                return pos;
            }
            Vec3d c = Vec3d.ofBottomCenter(p);
            Vec3d d = pos.subtract(c).multiply(1, 0, 1);
            if (d.length() <= LEASH) {
                return pos;
            }
            Vec3d r = c.add(d.normalize().multiply(LEASH - 0.5));
            return new Vec3d(r.x, c.y, r.z);
        }

        private void moveTo(Vec3d pos, double speed) {
            if (--repathCooldown <= 0 || guard.getNavigation().isIdle()) {
                repathCooldown = 10;
                guard.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
            }
        }

        private void melee(Weapon weapon) {
            LivingEntity target = guard.enemy;
            walkTarget = null;
            guard.getLookControl().lookAt(target, 30.0f, 30.0f);
            if (guard.inReach(target, 0.0)) {
                guard.getNavigation().stop();
                guard.faceTowards(target, 30.0f);
                if (guard.age >= guard.nextAttackAge) {
                    guard.startAction(weapon == Weapon.AXE ? ACT_AXE : ACT_SWORD);
                }
                return;
            }
            Vec3d want = leash(target.getPos());
            if (guard.defendPoint != null && want.squaredDistanceTo(target.getPos()) > 4.0
                    && guard.getPos().squaredDistanceTo(want) < 2.0) {
                guard.getNavigation().stop();   // дальше поводка не бежит — ждёт врага у края
                guard.faceTowards(target, 20.0f);
                return;
            }
            moveTo(want, SPEED_CHASE);
        }

        private void archer() {
            LivingEntity target = guard.enemy;
            walkTarget = null;
            guard.getLookControl().lookAt(target, 30.0f, 30.0f);
            double dist = guard.distanceTo(target);
            boolean see = guard.canSee(target);
            Vec3d away = guard.getPos().subtract(target.getPos()).multiply(1, 0, 1);
            if (dist < BOW_RANGE - 6 && away.lengthSquared() > 0.01) {
                // Слишком близко — отходит назад
                moveTo(leash(guard.getPos().add(away.normalize().multiply(5))), SPEED_RETREAT);
            } else if (dist > BOW_RANGE + 2 || !see) {
                Vec3d to = leash(target.getPos());
                if (guard.getPos().squaredDistanceTo(to) > 2.0) {
                    moveTo(to, SPEED_CHASE);
                } else {
                    guard.getNavigation().stop();
                }
            } else {
                guard.getNavigation().stop();
                guard.faceTowards(target, 20.0f);
            }
            if (see && dist <= AGGRO && guard.age >= guard.nextAttackAge) {
                guard.getNavigation().stop();
                guard.startAction(ACT_BOW);
            }
        }

        private void flee() {
            LivingEntity target = guard.enemy;
            walkTarget = null;
            if (guard.distanceTo(target) > 12) {
                guard.getNavigation().stop();
                return;
            }
            Vec3d away = guard.getPos().subtract(target.getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 0.01) {
                away = new Vec3d(1, 0, 0);
            }
            moveTo(leash(guard.getPos().add(away.normalize().multiply(8))), SPEED_RETREAT);
        }

        private void holdPoint() {
            BlockPos p = guard.defendPoint;
            Vec3d c = Vec3d.ofBottomCenter(p);
            double d2 = guard.getPos().squaredDistanceTo(c.x, guard.getY(), c.z);
            if (d2 > 3.5 * 3.5 && walkTarget == null) {
                moveTo(c, SPEED_TO_POINT);   // бежит на точку
                return;
            }
            // На точке: изредка переступает в пределах 3 блоков
            if (walkTarget == null) {
                if (--waitTicks <= 0) {
                    waitTicks = 100 + guard.random.nextInt(140);
                    walkTarget = p.add(guard.random.nextInt(7) - 3, 0, guard.random.nextInt(7) - 3);
                    walkTicks = 0;
                }
                return;
            }
            walk(SPEED_WALK * 0.9);
        }

        private void patrol() {
            if (walkTarget == null) {
                if (--waitTicks > 0) {
                    return;
                }
                walkTarget = pickPatrolPoint();
                walkTicks = 0;
                if (walkTarget == null) {
                    waitTicks = 100;
                    return;
                }
            }
            walk(SPEED_WALK);
        }

        private void walk(double speed) {
            BlockPos t = walkTarget;
            walkTicks++;
            if (guard.getBlockPos().getSquaredDistance(t) < 2.5 * 2.5 || walkTicks > 600) {
                walkTarget = null;
                waitTicks = 40 + guard.random.nextInt(100);
                guard.getNavigation().stop();
                return;
            }
            if (guard.getNavigation().isIdle() || walkTicks % 40 == 1) {
                if (!guard.getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, speed)
                        && walkTicks > 40) {
                    walkTarget = null;   // туда не пройти — выберет другое место
                    waitTicks = 20;
                }
            }
        }

        /** Куда идти в патруле: входы домов и площадь города; без города — вокруг дома. */
        @Nullable
        private BlockPos pickPatrolPoint() {
            if (!(guard.getWorld() instanceof ServerWorld sw)) {
                return null;
            }
            String dim = sw.getRegistryKey().getValue().toString();
            TownState town = TownState.get(sw.getServer());
            List<BlockPos> points = new ArrayList<>();
            for (TownState.Entrance e : town.getEntrances(dim)) {
                points.add(e.ground().offset(e.outward(), 2).up());
            }
            TownState.Square square = town.getSquare(dim);
            if (square != null) {
                points.add(square.center().up());
                points.add(square.center().up());
            }
            // Только места в разумных пределах (не через полмира)
            points.removeIf(p -> p.getSquaredDistance(guard.getBlockPos()) > 96 * 96);
            if (points.isEmpty()) {
                BlockPos home = guard.getHome() != null ? guard.getHome() : guard.getBlockPos();
                return home.add(guard.random.nextInt(25) - 12, 0, guard.random.nextInt(25) - 12);
            }
            BlockPos p = points.get(guard.random.nextInt(points.size()));
            return p.add(guard.random.nextInt(5) - 2, 0, guard.random.nextInt(5) - 2);
        }
    }
}
