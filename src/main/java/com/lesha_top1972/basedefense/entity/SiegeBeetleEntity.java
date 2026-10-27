package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.BaseLocationState;
import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * «Осадный жук» — жук‑носорог ≈5 блоков. По умолчанию идёт к базе (блок «База»); упёрся в стену — «Таран»
 * (разгон, удар рогом, ломает блоки 2×2 по mobGriefing). Игрок ближе 4 блоков — переключается на него:
 * «Рогом» (8 урона, отброс ≈8 блоков, «Оглушение» 0.7 с), между — укус жвалами. Раз в 30 с — «Кладка» яйца,
 * из которого вылупляются 2 маленьких жука. Поворот корпуса плавный. Тайминги — по клипам tools/beetle_gen.py.
 */
public class SiegeBeetleEntity extends HostileEntity {

    public static final int NONE = -1;
    public static final int RAM = 0;
    public static final int HORN = 1;
    public static final int BITE = 2;
    public static final int LAY = 3;
    public static final int HURT = 4;
    public static final byte STATUS_BASE = 70;

    static final int RAM_TICKS = 46;
    static final int RAM_ROAR = 3;
    static final int RAM_GO = 10;
    static final int RAM_HIT = 22;
    static final double RAM_SPEED = 0.5;
    static final float RAM_DMG = 10f;
    static final int RAM_CD = 80;
    static final int HORN_TICKS = 30;
    static final int HORN_HIT = 10;
    static final float HORN_DMG = 8f;
    static final int HORN_CD = 120;
    static final int HORN_STUN = 14;
    static final int BITE_TICKS = 16;
    static final int BITE_HIT = 7;
    static final float BITE_DMG = 6f;
    static final int BITE_CD = 40;
    static final int LAY_TICKS = 34;
    static final int LAY_DROP = 20;
    static final int LAY_CD = 600;
    /** От центра до «лба» (хитбокс 3.0). */
    static final double FRONT = 1.5;

    public int clientAction = NONE;
    public int clientActionAge;

    public SiegeBeetleEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
        this.setStepHeight(1.0f);
        this.moveControl = new SmoothMoveControl(this);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new BeetleGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));   // голова следит за игроком
    }

    // =====================================================================
    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= RAM && a <= HURT) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    void sendAction(int a) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + a));
    }

    Vec3d forward() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    /** Корпус всегда смотрит туда же, куда сам жук (без доворота клиентом — не дёргается). */
    @Override
    protected float turnHead(float bodyRotation, float headRotation) {
        this.bodyYaw = this.getYaw();
        return headRotation;
    }

    /** Плавный поворот: скорость ∝ углу, не больше maxDeg/тик. */
    void turnSmooth(double x, double z, float maxDeg) {
        float want = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        float d = MathHelper.wrapDegrees(want - this.getYaw());
        float yaw = this.getYaw() + MathHelper.clamp(d * 0.15f, -maxDeg, maxDeg);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    float angleTo(double x, double z) {
        float want = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        return Math.abs(MathHelper.wrapDegrees(want - this.getYaw()));
    }

    /** Ходьба навигацией с плавным поворотом корпуса (обычный MoveControl крутит до 90° за тик). */
    static class SmoothMoveControl extends MoveControl {
        SmoothMoveControl(MobEntity e) {
            super(e);
        }

        @Override
        protected float wrapDegrees(float from, float to, float max) {
            float d = MathHelper.wrapDegrees(to - from);
            return from + MathHelper.clamp(d * 0.15f, -4.5f, 4.5f);
        }
    }

    BlockPos basePos() {
        MinecraftServer srv = this.getWorld().getServer();
        if (srv == null) {
            return null;
        }
        BaseLocationState b = BaseLocationState.get(srv);
        if (!b.hasBase() || !this.getWorld().getRegistryKey().equals(b.getDimension())) {
            return null;
        }
        return b.getPos();
    }

    /** До края хитбокса цели от «лба» жука. */
    double frontDist(LivingEntity e) {
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - this.getX(), 0), this.getX() - b.maxX);
        double dz = Math.max(Math.max(b.minZ - this.getZ(), 0), this.getZ() - b.maxZ);
        return Math.max(0, Math.sqrt(dx * dx + dz * dz) - FRONT);
    }

    static boolean valid(LivingEntity t) {
        return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    boolean hits(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    /** Таран ломает всё, кроме обсидиана (и неразрушимого: бедрок, блок «База»), жидкости не трогает. */
    boolean rammable(BlockPos p) {
        World w = this.getWorld();
        BlockState st = w.getBlockState(p);
        return !st.isAir() && st.getFluidState().isEmpty() && !BuiltBlocks.isObsidian(st) && st.getHardness(w, p) >= 0;
    }

    /** Ближайшая постройка в 200 блоках (раз в секунду), см. {@link BuildTargets}. */
    BlockPos build;

    void scanBuild() {
        if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld s && SapperBlast.griefing(s)) {
            this.build = BuildTargets.nearest(s, this.getBlockPos(), BuildTargets.RADIUS, 6);
        } else {
            this.build = null;
        }
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.age % 20 == 0) {
            this.scanBuild();
        }
    }

    /** Стена перед жуком на пути (ноги и выше, полоса шириной 2, в 1–2.5 блоках от «лба»). */
    boolean wallAhead() {
        World w = this.getWorld();
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        for (double d = FRONT + 0.6; d <= FRONT + 2.2; d += 0.8) {
            for (double s = -1.0; s <= 1.0; s += 1.0) {
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = BlockPos.ofFloored(this.getPos().add(f.multiply(d)).add(side.multiply(s))).up(dy);
                    if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty() && this.rammable(p)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Удар тарана: пролом 5 в ширину × 4 в высоту × 2 в глубину перед «лбом» (до 40 блоков), по mobGriefing.
     * Обсидиан, бедрок и «База» не ломаются.
     */
    void ramBlocks() {
        World w = this.getWorld();
        if (!SapperBlast.griefing(w)) {
            return;
        }
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        Set<BlockPos> done = new HashSet<>();
        for (double d = FRONT + 0.3; d <= FRONT + 2.4; d += 0.5) {
            for (double s = -2.2; s <= 2.21; s += 0.55) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = BlockPos.ofFloored(this.getPos().add(f.multiply(d)).add(side.multiply(s))).up(dy);
                    if (done.size() >= 40 || !done.add(p)) {
                        continue;
                    }
                    if (!w.getBlockState(p).isAir() && this.rammable(p) && !this.isGroundBelow(p)) {
                        w.breakBlock(p, false, this);
                    }
                }
            }
        }
    }

    /** Земля под ногами (уровень пола и ниже, природный блок) — таран её не роет. */
    private boolean isGroundBelow(BlockPos p) {
        return p.getY() < this.getBlockY() && natural(this.getWorld().getBlockState(p));
    }

    /** Природный грунт (земля, камень, песок, гравий) — не постройка. */
    static boolean natural(BlockState st) {
        return st.isIn(net.minecraft.registry.tag.BlockTags.DIRT) || st.isIn(net.minecraft.registry.tag.BlockTags.SAND)
                || st.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)
                || st.isOf(net.minecraft.block.Blocks.GRAVEL) || st.isOf(net.minecraft.block.Blocks.DIRT_PATH)
                || st.isOf(net.minecraft.block.Blocks.FARMLAND);
    }

    /** Стоит на постройке (крыша, стена, забор): под ним искусственные блоки. */
    boolean onBuilding() {
        World w = this.getWorld();
        Box b = this.getBoundingBox();
        int n = 0;
        for (double x = b.minX + 0.3; x < b.maxX; x += 1.0) {
            for (double z = b.minZ + 0.3; z < b.maxZ; z += 1.0) {
                BlockPos p = BlockPos.ofFloored(x, b.minY - 0.5, z);
                BlockState st = w.getBlockState(p);
                if (!st.isAir() && !natural(st) && this.rammable(p)) {
                    n++;
                }
            }
        }
        if (n < 2) {
            return false;
        }
        // крыша/стена: под постройкой в 1–4 блоках — пустота или ещё постройка (а не пол на земле)
        BlockPos c = BlockPos.ofFloored(this.getX(), b.minY - 0.5, this.getZ());
        for (int k = 1; k <= 4; k++) {
            BlockState st = w.getBlockState(c.down(k));
            if (st.isAir() || st.getCollisionShape(w, c.down(k)).isEmpty()) {
                return true;
            }
            if (natural(st)) {
                return false;
            }
        }
        return true;
    }

    /** «Пролом вниз»: ломает всё под жуком (2 слоя по размеру хитбокса) — проваливается с крыши. */
    void stompBlocks() {
        World w = this.getWorld();
        if (!SapperBlast.griefing(w)) {
            return;
        }
        Box b = this.getBoundingBox();
        for (int dy = 1; dy <= 2; dy++) {
            for (int x = MathHelper.floor(b.minX); x <= MathHelper.floor(b.maxX - 1e-3); x++) {
                for (int z = MathHelper.floor(b.minZ); z <= MathHelper.floor(b.maxZ - 1e-3); z++) {
                    BlockPos p = new BlockPos(x, MathHelper.floor(b.minY - 0.01) - dy + 1, z);
                    BlockState st = w.getBlockState(p);
                    if (!st.isAir() && this.rammable(p) && !natural(st)) {
                        w.breakBlock(p, false, this);
                    }
                }
            }
        }
    }

    // =====================================================================
    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (!w.isClient && this.age % 15 == 0 && Math.abs(MathHelper.wrapDegrees(this.getYaw() - this.prevYaw)) > 2.0f) {
            this.playSound(ModSounds.BEETLE_SCRAPE, 0.6f, 0.9f + this.random.nextFloat() * 0.2f);    // скрежет хитина на повороте
        }
        if (w.isClient && this.limbAnimator.getSpeed() > 0.15f && this.age % 5 == 0) {
            // пыль из‑под ног
            Vec3d f = this.forward();
            Vec3d side = new Vec3d(-f.z, 0, f.x);
            for (int i = 0; i < 2; i++) {
                double fz = (this.random.nextDouble() - 0.5) * 3.0;
                double sx = (this.random.nextBoolean() ? 1 : -1) * (2.0 + this.random.nextDouble() * 0.6);
                Vec3d p = this.getPos().add(f.multiply(fz)).add(side.multiply(sx));
                BlockState gs = w.getBlockState(BlockPos.ofFloored(p.x, this.getY() - 0.2, p.z));
                if (!gs.isAir()) {
                    w.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, gs), p.x, this.getY() + 0.1, p.z, 0, 0.05, 0);
                }
                if (this.random.nextInt(3) == 0) {
                    w.addParticle(ParticleTypes.POOF, p.x, this.getY() + 0.1, p.z, 0, 0.01, 0);
                }
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean r = super.damage(source, amount);
        if (r && !this.getWorld().isClient && this.random.nextInt(3) == 0) {
            this.goalSelector.getGoals().forEach(g -> {
                if (g.getGoal() instanceof BeetleGoal bg) {
                    bg.flinch();
                }
            });
        }
        return r;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BEETLE_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BEETLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BEETLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.BEETLE_STEP, 0.7f, 0.85f + this.random.nextFloat() * 0.2f);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }

    // =====================================================================
    static class BeetleGoal extends Goal {
        private final SiegeBeetleEntity mob;
        private int action = NONE;
        private int tick;
        private int ramCd = 40;
        private int hornCd = 20;
        private int biteCd;
        private int layCd = LAY_CD / 2;
        private int hurtCd;
        private int pathDelay;
        private int stuck;
        private Vec3d lastPos = Vec3d.ZERO;
        private LivingEntity foe;
        private int farTicks;
        private Vec3d dir = Vec3d.ZERO;
        private boolean impacted;
        /** Таран вниз (стоит на постройке). */
        private boolean stomp;
        private final Set<Integer> hit = new HashSet<>();

        BeetleGoal(SiegeBeetleEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        void flinch() {
            if (this.action == NONE && this.hurtCd <= 0) {
                this.hurtCd = 40;
                this.mob.sendAction(HURT);
            }
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hit.clear();
            this.impacted = false;
            this.mob.getNavigation().stop();
            this.mob.sendAction(a);
        }

        @Override
        public void tick() {
            SiegeBeetleEntity m = this.mob;
            if (this.ramCd > 0) this.ramCd--;
            if (this.hornCd > 0) this.hornCd--;
            if (this.biteCd > 0) this.biteCd--;
            if (this.layCd > 0) this.layCd--;
            if (this.hurtCd > 0) this.hurtCd--;
            this.updateFoe();
            if (this.action != NONE) {
                this.tick++;
                this.run();
                return;
            }
            if (this.layCd <= 0) {
                this.layCd = LAY_CD;
                this.begin(LAY);
                return;
            }
            if (this.foe != null) {
                this.fight(this.foe);
            } else {
                this.march();
            }
        }

        /** Игрок ближе 4 блоков — переагр. Ушёл дальше 12 или 8 с не подходил ближе 4 — назад к базе. */
        private void updateFoe() {
            SiegeBeetleEntity m = this.mob;
            BlockPos base = m.basePos();
            if (this.foe != null) {
                if (!valid(this.foe)) {
                    this.foe = null;
                } else {
                    double d = m.frontDist(this.foe);
                    this.farTicks = d < 4.0 ? 0 : this.farTicks + 1;
                    if (base != null && (d > 12.0 || this.farTicks > 160)) {
                        this.foe = null;
                    }
                }
            }
            if (this.foe == null && m.age % 5 == 0) {
                double r = base != null ? 4.0 + FRONT : 48.0;
                PlayerEntity p = m.getWorld().getClosestPlayer(m.getX(), m.getY(), m.getZ(), r, e -> valid((LivingEntity) e));
                if (p != null) {
                    this.foe = p;
                    this.farTicks = 0;
                }
            }
            m.setTarget(this.foe);
        }

        private void fight(LivingEntity t) {
            SiegeBeetleEntity m = this.mob;
            double d = m.frontDist(t);
            m.getLookControl().lookAt(t, 20f, 20f);
            boolean facing = m.angleTo(t.getX(), t.getZ()) < 30f;
            if (d < 3.0 && this.hornCd <= 0) {
                m.turnSmooth(t.getX(), t.getZ(), 6f);
                if (facing) {
                    this.begin(HORN);
                }
                return;
            }
            if (d < 1.8 && this.biteCd <= 0 && facing) {
                this.begin(BITE);
                return;
            }
            if (d > 1.2) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 8;
                    m.getNavigation().startMovingTo(t, 1.15);
                }
            } else {
                m.getNavigation().stop();
                m.turnSmooth(t.getX(), t.getZ(), 4f);
            }
        }

        /**
         * К ближайшей постройке (заборы, стекло, печки, доски, булыжник, обсидиан, кирпичи — 200 блоков),
         * без построек — к базе; упёрся в стену или застрял — «Таран». Нет ни того, ни другого — стоит.
         */
        private void march() {
            SiegeBeetleEntity m = this.mob;
            BlockPos base = m.build != null ? m.build : m.basePos();
            if (base == null) {
                m.getNavigation().stop();
                return;
            }
            Vec3d bc = Vec3d.ofBottomCenter(base);
            // залез на постройку (крышу, стену) — проламывает её под собой и проваливается
            if (this.ramCd <= 0 && m.isOnGround() && m.onBuilding()) {
                this.stomp = true;
                this.begin(RAM);
                return;
            }
            // постройка прямо перед «лбом» — таранит её
            if (m.build != null && this.ramCd <= 0 && m.squaredDistanceTo(bc.x, m.getY(), bc.z) < (FRONT + 2.2) * (FRONT + 2.2)) {
                m.turnSmooth(bc.x, bc.z, 5f);
                if (m.angleTo(bc.x, bc.z) < 20f) {
                    this.begin(RAM);
                }
                return;
            }
            double dist = Math.sqrt(m.squaredDistanceTo(bc.x, m.getY(), bc.z));
            // застрял: почти не сдвинулся за 2 с
            if (m.age % 40 == 0) {
                this.stuck = m.getPos().squaredDistanceTo(this.lastPos) < 1.0 ? this.stuck + 1 : 0;
                this.lastPos = m.getPos();
            }
            boolean blocked = m.wallAhead() && (m.horizontalCollision || this.stuck > 0 || m.getNavigation().isIdle());
            if (blocked && this.ramCd <= 0 && (dist > 3.0 || m.build != null)) {
                this.begin(RAM);
                return;
            }
            if (this.stuck > 1 && this.ramCd <= 0) {
                // путь упирается в стену в стороне — разворачивается на базу и таранит
                m.turnSmooth(bc.x, bc.z, 4f);
                if (m.angleTo(bc.x, bc.z) < 15f && m.wallAhead()) {
                    this.stuck = 0;
                    this.begin(RAM);
                }
                return;
            }
            if (dist > (m.build != null ? 2.5 : 4.0)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 20;
                    BuildTargets.walkTo(m, bc, 1.0);
                }
            } else {
                m.getNavigation().stop();
            }
        }

        private void run() {
            SiegeBeetleEntity m = this.mob;
            World w = m.getWorld();
            int t = this.tick;
            switch (this.action) {
                case RAM -> {
                    if (t == RAM_ROAR) {
                        m.playSound(ModSounds.BEETLE_ROAR, 1.4f, 0.9f + m.random.nextFloat() * 0.15f);
                    }
                    if (this.stomp) {
                        // «Пролом вниз»: на месте, удар всем телом вниз на RAM_HIT
                        m.setVelocity(0, m.getVelocity().y, 0);
                        if (t == RAM_HIT) {
                            m.stompBlocks();
                            this.trample(RAM_DMG, 1.0);
                            if (w instanceof ServerWorld s) {
                                Fx.impact(s, m.getPos(), 1.0f, true);
                                Fx.ring(s, m.getPos(), 2.4, 18, 0, Fx.F_BLOCK | Fx.F_CLOUD);
                            }
                            m.playSound(ModSounds.BEETLE_RAM, 1.8f, 0.8f);
                        }
                        m.velocityModified = true;
                        if (t >= RAM_TICKS) {
                            this.stomp = false;
                            this.ramCd = RAM_CD / 2;
                            this.done();
                        }
                        return;
                    }
                    if (t < RAM_GO) {
                        // отходит назад для разгона
                        Vec3d b = m.forward().multiply(-0.22);
                        m.setVelocity(b.x, m.getVelocity().y, b.z);
                        this.dir = m.forward();
                    } else if (t < RAM_HIT) {
                        if (!this.impacted && (m.horizontalCollision || m.wallAhead() && t > RAM_GO + 3)) {
                            this.impacted = true;
                        }
                        double sp = this.impacted ? 0.0 : RAM_SPEED;
                        m.setVelocity(this.dir.x * sp, m.getVelocity().y, this.dir.z * sp);
                        this.trample(RAM_DMG, 1.2);
                    } else if (t == RAM_HIT) {
                        m.setVelocity(0, m.getVelocity().y, 0);
                        m.ramBlocks();
                        this.trample(RAM_DMG, 1.4);
                        Vec3d p = m.getPos().add(m.forward().multiply(FRONT + 0.8)).add(0, 1.0, 0);
                        if (w instanceof ServerWorld s) {
                            Fx.impact(s, p, 1.0f, true);
                            Fx.ring(s, m.getPos().add(m.forward().multiply(FRONT)), 2.2, 18, 0, Fx.F_BLOCK | Fx.F_CLOUD);
                        }
                        m.playSound(ModSounds.BEETLE_RAM, 1.8f, 0.9f);
                    } else if (t == RAM_HIT + 4) {
                        Vec3d b = m.forward().multiply(-0.25);
                        m.setVelocity(b.x, 0.1, b.z);
                    }
                    m.velocityModified = true;
                    if (t >= RAM_TICKS) {
                        this.ramCd = RAM_CD;
                        this.done();
                    }
                }
                case HORN -> {
                    LivingEntity f = this.foe;
                    if (f != null && t < HORN_HIT - 1) {
                        m.turnSmooth(f.getX(), f.getZ(), 8f);
                    }
                    if (t == HORN_HIT - 3) {
                        m.playSound(ModSounds.BEETLE_HORN_SWING, 1.2f, 1.0f);
                    }
                    if (t == HORN_HIT) {
                        Vec3d fw = m.forward();
                        if (w instanceof ServerWorld s) {
                            Fx.arc(s, m.getPos(), m.getYaw(), -50, 50, FRONT + 1.6, 1.4, 10, Fx.F_SWEEP);
                        }
                        for (LivingEntity e : this.cone(3.2, 60)) {
                            e.timeUntilRegen = 0;
                            if (e.damage(m.getDamageSources().mobAttack(m), HORN_DMG)) {
                                boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
                                if (!shielded) {
                                    // ≈8 блоков: 1.0 по горизонтали + 0.5 вверх
                                    Vec3d away = new Vec3d(e.getX() - m.getX(), 0, e.getZ() - m.getZ());
                                    away = away.lengthSquared() > 1e-4 ? away.normalize() : fw;
                                    double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) * 0.5;
                                    e.setVelocity(away.x * kb, 0.5, away.z * kb);
                                    e.velocityModified = true;
                                    e.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, HORN_STUN, 0), m);
                                    if (w instanceof ServerWorld s) {
                                        Vec3d a = e.getPos().add(0, 0.3, 0);
                                        Fx.line(s, a, a.add(away.multiply(7.0)), 12, Fx.dust(0x8A7458, 1.4f), 0, Fx.F_DUST);
                                    }
                                }
                                if (w instanceof ServerWorld s) {
                                    Fx.impact(s, e.getPos().add(0, 1.0, 0), 0.6f, false);
                                }
                                m.playSound(ModSounds.BEETLE_HORN_HIT, 1.3f, 1.0f);
                            }
                        }
                    }
                    if (t >= HORN_TICKS) {
                        this.hornCd = HORN_CD;
                        this.done();
                    }
                }
                case BITE -> {
                    if (t == 2) {
                        m.playSound(ModSounds.BEETLE_MANDIBLE, 1.0f, 1.0f);
                    }
                    if (t == BITE_HIT) {
                        for (LivingEntity e : this.cone(2.0, 50)) {
                            e.timeUntilRegen = 0;
                            e.damage(m.getDamageSources().mobAttack(m), BITE_DMG);
                        }
                        m.playSound(ModSounds.BEETLE_MANDIBLE, 1.2f, 0.8f);
                    }
                    if (t >= BITE_TICKS) {
                        this.biteCd = BITE_CD;
                        this.done();
                    }
                }
                case LAY -> {
                    m.setVelocity(0, m.getVelocity().y, 0);
                    if (t == 8) {
                        m.playSound(ModSounds.BEETLE_LAY, 1.0f, 1.0f);
                    }
                    if (t == LAY_DROP && w instanceof ServerWorld s) {
                        Vec3d p = m.getPos().add(m.forward().multiply(-2.4));
                        BeetleEggEntity egg = new BeetleEggEntity(ModEntities.BEETLE_EGG, s);
                        egg.refreshPositionAndAngles(p.x, m.getY(), p.z, m.random.nextFloat() * 360f, 0f);
                        s.spawnEntity(egg);
                        s.spawnParticles(ParticleTypes.ITEM_SLIME, p.x, m.getY() + 0.8, p.z, 14, 0.4, 0.3, 0.4, 0.05);
                        s.spawnParticles(ParticleTypes.DRIPPING_HONEY, p.x, m.getY() + 1.2, p.z, 6, 0.4, 0.2, 0.4, 0.0);
                    }
                    if (t >= LAY_TICKS) {
                        this.done();
                    }
                }
                default -> this.done();
            }
        }

        private void done() {
            this.action = NONE;
            this.stomp = false;
            this.pathDelay = 0;
        }

        /** Кого задевает «лоб» в конусе halfAngle на reach блоков от «лба». */
        private java.util.List<LivingEntity> cone(double reach, double halfAngle) {
            SiegeBeetleEntity m = this.mob;
            Vec3d f = m.forward();
            double cos = Math.cos(Math.toRadians(halfAngle));
            return m.getWorld().getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(reach + 1.0, 1.5, reach + 1.0),
                    e -> {
                        if (!m.hits(e) || this.hit.contains(e.getId())) {
                            return false;
                        }
                        double d = m.frontDist(e);
                        Vec3d to = new Vec3d(e.getX() - m.getX(), 0, e.getZ() - m.getZ());
                        boolean ok = d <= reach && Math.abs(e.getY() - m.getY()) < 3.0
                                && (to.lengthSquared() < 0.01 || to.normalize().dotProduct(f) >= cos);
                        if (ok) {
                            this.hit.add(e.getId());
                        }
                        return ok;
                    });
        }

        /** На разгоне тарана: всех на пути — урон и отброс (каждого один раз). */
        private void trample(float dmg, double knock) {
            SiegeBeetleEntity m = this.mob;
            for (LivingEntity e : this.cone(1.2, 70)) {
                e.timeUntilRegen = 0;
                if (e.damage(m.getDamageSources().mobAttack(m), dmg)) {
                    e.takeKnockback(knock, m.getX() - e.getX(), m.getZ() - e.getZ());
                    e.addVelocity(0, 0.35, 0);
                    e.velocityModified = true;
                }
            }
        }
    }
}
