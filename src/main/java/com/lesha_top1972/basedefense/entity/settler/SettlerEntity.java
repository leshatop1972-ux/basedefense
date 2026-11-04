package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.sound.ModSounds;
import com.lesha_top1972.basedefense.needs.Needs;
import com.lesha_top1972.basedefense.temperature.TempStatus;
import com.lesha_top1972.basedefense.temperature.Temperature;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Общий класс поселенца: внешность (см. {@link SettlerLooks}), русское имя с фамилией, голос,
 * «дом» (точка, где появился — вокруг неё гуляет), слой одежды (4 слота под бронёй, см. {@link Clothing})
 * и температура воздуха вокруг (см. {@link Temperature}).
 * Профессий, зданий и поселения больше нет — поведение и меню задаёт {@link GuardEntity}.
 */
public abstract class SettlerEntity extends PathAwareEntity {

    /** Внешность: номер из SettlerLooks (-1 — ещё не выбрана). */
    private static final TrackedData<Integer> SKIN =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Потребности для меню (считает сервер раз в 2 с, см. Needs): ощущаемая температура, самочувствие (TempStatus),
     *  комфорт 0…100, куда он идёт (−1/0/1), сытость 0…20, комфортная температура мин/макс, закрытость места. */
    private static final TrackedData<Float> TEMPERATURE =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> TEMP_STATUS =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> COMFORT =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> TREND =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HUNGER =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> COMFORT_MIN =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> COMFORT_MAX =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> ENCLOSURE =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Есть ли рюкзак (для вкладки «Рюкзак» на клиенте). */
    private static final TrackedData<Boolean> HAS_BACKPACK =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ---------- Голод ----------
    public static final int HUNGER_MAX = 20;
    /** Сытость падает на 1 за столько секунд (≈27 за игровые сутки — 2–3 приёма пищи). */
    public static final float HUNGER_SECONDS = 45f;
    /** Ест сам, когда сытость не больше этого. */
    public static final int HUNGER_EAT = 12;
    /** При сытости 0 — 1 урона раз в столько тиков (не ниже 1 здоровья). */
    private static final int STARVE_EVERY = 300;

    @Nullable
    private BlockPos homePos;
    private float comfortValue = Needs.COMFORT_START;
    private float feltValue = Float.NaN;
    /** Сытость с дробной частью (в данных — округлённая вверх). */
    private float hungerLeft = HUNGER_MAX;
    /** Одежда: 0 — голова, 1 — тело, 2 — ноги, 3 — ступни (см. ClothingType). */
    private final SimpleInventory clothing = new SimpleInventory(Clothing.SLOTS);

    protected SettlerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
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
        this.dataTracker.startTracking(SKIN, -1);
        this.dataTracker.startTracking(TEMPERATURE, 20f);
        this.dataTracker.startTracking(TEMP_STATUS, TempStatus.COMFORT.ordinal());
        this.dataTracker.startTracking(COMFORT, Needs.COMFORT_START);
        this.dataTracker.startTracking(TREND, 0);
        this.dataTracker.startTracking(HUNGER, HUNGER_MAX);
        this.dataTracker.startTracking(COMFORT_MIN, Temperature.NAKED_MIN);
        this.dataTracker.startTracking(COMFORT_MAX, Temperature.NAKED_MAX);
        this.dataTracker.startTracking(ENCLOSURE, 0f);
        this.dataTracker.startTracking(HAS_BACKPACK, false);
    }

    // ---------- Внешность ----------

    /** Номер внешности (см. SettlerLooks) или -1, пока не выбрана. */
    public int getSkin() {
        return this.dataTracker.get(SKIN);
    }

    public boolean isFemale() {
        return SettlerLooks.isFemale(getSkin());
    }

    /** Новому поселенцу — случайная внешность и русское имя с фамилией. */
    private void ensureLook() {
        if (getSkin() < 0) {
            this.dataTracker.set(SKIN, this.random.nextInt(SettlerLooks.COUNT));
        }
        if (!this.hasCustomName()) {
            this.setCustomName(Text.literal(SettlerNames.random(this.random, isFemale())));
        }
    }

    // ---------- Температура ----------

    public float getTemperature() {
        return this.dataTracker.get(TEMPERATURE);
    }

    public TempStatus getTempStatus() {
        return TempStatus.byIndex(this.dataTracker.get(TEMP_STATUS));
    }

    public float getComfort() {
        return this.dataTracker.get(COMFORT);
    }

    public int getComfortTrend() {
        return this.dataTracker.get(TREND);
    }

    public float getComfortMin() {
        return this.dataTracker.get(COMFORT_MIN);
    }

    public float getComfortMax() {
        return this.dataTracker.get(COMFORT_MAX);
    }

    public boolean hasBackpack() {
        return this.dataTracker.get(HAS_BACKPACK);
    }

    public float getEnclosure() {
        return this.dataTracker.get(ENCLOSURE);
    }

    // ---------- Голод ----------

    /** Сытость 0…20 (как у игрока). */
    public int getHunger() {
        return this.dataTracker.get(HUNGER);
    }

    public boolean isHungry() {
        return getHunger() <= HUNGER_EAT;
    }

    /** Поел: сытость + hunger (как у еды игрока). */
    public void feed(int hunger) {
        hungerLeft = Math.min(HUNGER_MAX, hungerLeft + hunger);
        this.dataTracker.set(HUNGER, (int) Math.ceil(hungerLeft));
    }

    /** Потребности в виде Needs.State (сервер). */
    private final Needs.State needs = new Needs.State() {
        @Override
        public float comfort() {
            return comfortValue;
        }

        @Override
        public void setComfort(float v) {
            comfortValue = v;
        }

        @Override
        public float felt() {
            return feltValue;
        }

        @Override
        public void setFelt(float v) {
            feltValue = v;
        }

        @Override
        public int hunger() {
            return getHunger();
        }
    };

    /** Раз в 2 с: голод, ощущаемая температура, комфорт и его эффекты. */
    private void updateNeeds(ServerWorld sw) {
        float dt = 2f;
        if (!(this instanceof GuardEntity g && g.isSummoned())) {
            hungerLeft = Math.max(0f, hungerLeft - dt / HUNGER_SECONDS);
            this.dataTracker.set(HUNGER, (int) Math.ceil(hungerLeft));
        }
        Needs.Result r = Needs.update(this, sw, needs, dt, true);
        this.dataTracker.set(TEMPERATURE, Math.round(r.felt() * 10) / 10f);
        this.dataTracker.set(TEMP_STATUS, r.status().ordinal());
        this.dataTracker.set(COMFORT, r.comfort());
        this.dataTracker.set(TREND, r.trend());
        this.dataTracker.set(COMFORT_MIN, r.comfortMin());
        this.dataTracker.set(COMFORT_MAX, r.comfortMax());
        this.dataTracker.set(ENCLOSURE, r.enclosure());
    }

    // ---------- Одежда ----------

    public SimpleInventory getClothing() {
        return clothing;
    }

    // ---------- Дом ----------

    /** Точка, вокруг которой поселенец гуляет без дела. */
    public void setHome(BlockPos pos) {
        this.homePos = pos.toImmutable();
    }

    @Nullable
    public BlockPos getHome() {
        return homePos;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            if (this.homePos == null) {
                setHome(this.getBlockPos());
            }
            if (getSkin() < 0 || !this.hasCustomName()) {
                ensureLook();
            }
            if (this.age % 10 == 0) {
                this.dataTracker.set(HAS_BACKPACK, !Clothing.backpack(clothing).isEmpty());
            }
            if (this.age % 40 == 5 && this.getWorld() instanceof ServerWorld sw) {
                updateNeeds(sw);
            }
            // Голодает — понемногу теряет здоровье (не ниже 1)
            if (hungerLeft <= 0f && this.age % STARVE_EVERY == 0 && this.getHealth() > 1f) {
                this.damage(this.getDamageSources().starve(), 1f);
            }
        }
    }

    /** Гибель: одежда выпадает (снаряжение и сумку роняет подкласс). */
    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        for (int i = 0; i < clothing.size(); i++) {
            ItemStack stack = clothing.getStack(i);
            if (!stack.isEmpty()) {
                this.dropStack(stack.copy());
            }
        }
        clothing.clear();
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
        nbt.putInt("Skin", getSkin());
        nbt.put("Clothing", Clothing.toNbt(clothing));
        nbt.putFloat("Comfort", comfortValue);
        nbt.putFloat("Hunger", hungerLeft);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) {
            setHome(NbtHelper.toBlockPos(nbt.getCompound("Home")));
        }
        int skin = nbt.contains("Skin") ? nbt.getInt("Skin") : -1;
        this.dataTracker.set(SKIN, SettlerLooks.isValid(skin) ? skin : -1);
        if (nbt.contains("Comfort")) {
            comfortValue = nbt.getFloat("Comfort");
            this.dataTracker.set(COMFORT, comfortValue);
        }
        if (nbt.contains("Hunger")) {
            hungerLeft = nbt.getFloat("Hunger");
            this.dataTracker.set(HUNGER, (int) Math.ceil(hungerLeft));
        }
        if (nbt.contains("Clothing", NbtElement.LIST_TYPE)) {
            Clothing.fromNbt(clothing, nbt.getList("Clothing", NbtElement.COMPOUND_TYPE));
        }
    }

    // ---------- Звуки: отдельные события для мужчин и женщин ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return isFemale() ? ModSounds.SETTLER_FEMALE_AMBIENT : ModSounds.SETTLER_MALE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isFemale() ? ModSounds.SETTLER_FEMALE_HURT : ModSounds.SETTLER_MALE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isFemale() ? ModSounds.SETTLER_FEMALE_DEATH : ModSounds.SETTLER_MALE_DEATH;
    }

    /** Отзыв на клик игрока («М-м? Да?»). */
    public SoundEvent getGreetSound() {
        return isFemale() ? ModSounds.SETTLER_FEMALE_GREET : ModSounds.SETTLER_MALE_GREET;
    }

    /** Бормочут не слишком часто: не чаще раза в 10 секунд. */
    @Override
    public int getMinAmbientSoundDelay() {
        return 200;
    }

    /** У каждого свой тембр: высота голоса зависит от внешности, плюс немного случайности. */
    @Override
    public float getSoundPitch() {
        int skin = Math.max(0, getSkin());
        float personal = 1.0f + ((skin * 7) % 5 - 2) * 0.04f;
        return personal + (this.random.nextFloat() - 0.5f) * 0.08f;
    }
}
