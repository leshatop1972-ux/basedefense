package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.sound.ModSounds;
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
 * «дом» (точка, где появился — вокруг неё гуляет) и слой одежды (4 слота под бронёй, см. {@link Clothing}).
 * Профессий, зданий и поселения больше нет — поведение и меню задаёт {@link GuardEntity}.
 */
public abstract class SettlerEntity extends PathAwareEntity {

    /** Внешность: номер из SettlerLooks (-1 — ещё не выбрана). */
    private static final TrackedData<Integer> SKIN =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Nullable
    private BlockPos homePos;
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
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) {
            setHome(NbtHelper.toBlockPos(nbt.getCompound("Home")));
        }
        int skin = nbt.contains("Skin") ? nbt.getInt("Skin") : -1;
        this.dataTracker.set(SKIN, SettlerLooks.isValid(skin) ? skin : -1);
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
