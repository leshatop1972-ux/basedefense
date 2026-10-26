package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Землетрясение» Набата (невидимый управляющий объект): три удара колоколом в землю — от точки удара
 * расходятся волны до 6, 11 и 16 блоков. Кто стоит на земле в волне — 5 урона и подброс (прыжок спасает).
 * Волна выбивает природные блоки (земля, трава, камень, песок, гравий; только при mobGriefing),
 * после третьего удара 3–5 пятен земли (вровень с поверхностью) становятся «Гудящим камнем». Через 45 с всё возвращается.
 */
public class NabatQuakeEntity extends Entity {

    static final double[] RADII = {6.0, 11.0, 16.0};
    static final double WAVE_SPEED = 0.9;
    static final float WAVE_DAMAGE = 5f;
    static final int RESTORE_AFTER = 900;
    static final int RESTORE_PER_TICK = 6;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.3f), 1.0f);

    private LivingEntity owner;
    private int wave = -1;
    private double waveR;
    private final Set<Integer> hit = new HashSet<>();
    private int sinceLast;
    private boolean done;
    /** Выбитые блоки (позиция → что было) и поставленные холмы. */
    private final Map<Long, BlockState> broken = new LinkedHashMap<>();
    private final List<Long> placed = new ArrayList<>();

    public NabatQuakeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    void setOwner(LivingEntity owner) {
        this.owner = owner;
    }

    /** Удар номер i (0..2): волна пошла. */
    void slam(int i) {
        this.wave = i;
        this.waveR = 0.5;
        this.hit.clear();
        this.sinceLast = 0;
        if (i >= RADII.length - 1) {
            this.raiseHills();
        }
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        this.sinceLast++;
        if (this.wave >= 0 && this.waveR < RADII[this.wave]) {
            double prev = this.waveR;
            this.waveR = Math.min(RADII[this.wave], this.waveR + WAVE_SPEED);
            this.waveFront(s, prev, this.waveR);
        }
        if (this.wave >= RADII.length - 1 && this.sinceLast > RESTORE_AFTER) {
            this.done = true;
        }
        if (this.done || (this.owner == null && this.sinceLast > RESTORE_AFTER)) {
            if (this.restoreSome(s)) {
                this.discard();
            }
        }
    }

    private boolean griefing(ServerWorld s) {
        return s.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    private void waveFront(ServerWorld s, double r0, double r1) {
        double cx = this.getX();
        double cz = this.getZ();
        // урон тем, кто на земле в полосе волны
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, new Box(cx - r1 - 1, this.getY() - 3, cz - r1 - 1,
                cx + r1 + 1, this.getY() + 4, cz + r1 + 1), e -> e.isAlive() && !(e instanceof Monster) && !NabatHooks.isKin(e)
                && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            double d = Math.sqrt((e.getX() - cx) * (e.getX() - cx) + (e.getZ() - cz) * (e.getZ() - cz));
            if (d >= r0 - 0.8 && d <= r1 + 0.6 && e.isOnGround() && this.hit.add(e.getId())) {
                e.timeUntilRegen = 0;
                e.damage(this.owner != null ? this.getDamageSources().mobAttack(this.owner) : this.getDamageSources().magic(),
                        WAVE_DAMAGE);
                e.addVelocity(0, 0.5, 0);
                e.velocityModified = true;
            }
        }
        // пыль и выбитые блоки по фронту волны
        int pts = (int) Math.max(12, r1 * 6);
        boolean grief = this.griefing(s);
        for (int i = 0; i < pts; i++) {
            double a = (i + this.random.nextDouble() * 0.5) / pts * Math.PI * 2;
            int x = (int) Math.floor(cx + Math.cos(a) * r1);
            int z = (int) Math.floor(cz + Math.sin(a) * r1);
            int top = s.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(top - this.getY()) > 4) {
                continue;
            }
            BlockPos p = new BlockPos(x, top, z);
            BlockState st = s.getBlockState(p);
            if (!st.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), x + 0.5, top + 1.1, z + 0.5, 3,
                        0.3, 0.1, 0.3, 0.15);
            }
            if (i % 3 == 0) {
                s.spawnParticles(ParticleTypes.CLOUD, x + 0.5, top + 1.2, z + 0.5, 1, 0.1, 0.05, 0.1, 0.03);
            }
            if (grief && this.random.nextFloat() < 0.10f && natural(s, p, st) && !this.broken.containsKey(p.asLong())) {
                this.broken.put(p.asLong(), st);
                s.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        if (this.waveR >= RADII[this.wave]) {
            s.playSound(null, cx, this.getY(), cz, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
    }

    /** Природный блок, который можно выбить и потом вернуть. */
    static boolean natural(ServerWorld s, BlockPos p, BlockState st) {
        if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
            return false;
        }
        float h = st.getHardness(s, p);
        if (h < 0 || h > 3.0f) {
            return false;
        }
        return st.isIn(BlockTags.DIRT) || st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT_PATH) || st.isOf(Blocks.STONE)
                || st.isOf(Blocks.ANDESITE) || st.isOf(Blocks.DIORITE) || st.isOf(Blocks.GRANITE) || st.isOf(Blocks.TUFF)
                || st.isOf(Blocks.DEEPSLATE) || st.isOf(Blocks.SAND) || st.isOf(Blocks.RED_SAND) || st.isOf(Blocks.GRAVEL)
                || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.CLAY);
    }

    /**
     * Пятна гудящего камня вровень с землёй: верхний природный блок заменяется камнем (не горкой).
     * 3–5 пятен по 3–5 блоков; оригинал запоминается и возвращается вместе с выбитыми блоками.
     * Ставятся и при выключенном mobGriefing — они временные.
     */
    private void raiseHills() {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        BlockState stone = ModBlocks.RESONANT_STONE.getDefaultState();
        int patches = 3 + this.random.nextInt(3);
        for (int k = 0; k < patches; k++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double r = 4.0 + this.random.nextDouble() * 10.0;
            int hx = (int) Math.floor(this.getX() + Math.cos(a) * r);
            int hz = (int) Math.floor(this.getZ() + Math.sin(a) * r);
            int[][] cells = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int count = 3 + this.random.nextInt(3);
            for (int c = 0; c < count; c++) {
                int x = hx + cells[c][0];
                int z = hz + cells[c][1];
                int top = s.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (Math.abs(top - this.getY()) > 3) {
                    continue;
                }
                BlockPos p = new BlockPos(x, top, z);
                BlockState st = s.getBlockState(p);
                if (!natural(s, p, st) || this.broken.containsKey(p.asLong())) {
                    continue;
                }
                this.broken.put(p.asLong(), st);
                s.setBlockState(p, stone, Block.NOTIFY_ALL);
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, stone), x + 0.5, top + 1.05, z + 0.5,
                        4, 0.35, 0.05, 0.35, 0.08);
            }
            s.spawnParticles(AMBER, hx + 0.5, this.getY() + 0.6, hz + 0.5, 6, 0.8, 0.2, 0.8, 0.01);
        }
    }

    /** Вернуть часть блоков; true — всё вернули. */
    private boolean restoreSome(ServerWorld s) {
        int n = 0;
        BlockState stone = ModBlocks.RESONANT_STONE.getDefaultState();
        while (!this.placed.isEmpty() && n < RESTORE_PER_TICK) {
            BlockPos p = BlockPos.fromLong(this.placed.remove(this.placed.size() - 1));
            if (s.getBlockState(p).isOf(stone.getBlock())) {
                s.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                if (n % 2 == 0) {
                    s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, stone), p.getX() + 0.5,
                            p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                }
            }
            n++;
        }
        var it = this.broken.entrySet().iterator();
        while (it.hasNext() && n < RESTORE_PER_TICK * 2) {
            var e = it.next();
            BlockPos p = BlockPos.fromLong(e.getKey());
            BlockState cur = s.getBlockState(p);
            if (cur.isAir() || cur.isOf(stone.getBlock()) || cur.isReplaceable()) {
                s.setBlockState(p, e.getValue(), Block.NOTIFY_ALL);
            }
            it.remove();
            n++;
        }
        return this.placed.isEmpty() && this.broken.isEmpty();
    }

    /** Убрали командой — сразу вернуть всё (при выгрузке чанка — не трогать). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.getWorld() instanceof ServerWorld s) {
            while (!this.restoreSome(s)) {
                // всё сразу
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.wave = nbt.getInt("Wave");
        this.waveR = 99;
        this.sinceLast = nbt.getInt("Since");
        this.done = nbt.getBoolean("Done");
        this.placed.clear();
        for (long v : nbt.getLongArray("Placed")) {
            this.placed.add(v);
        }
        this.broken.clear();
        NbtList list = nbt.getList("Broken", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            this.broken.put(c.getLong("Pos"), NbtHelper.toBlockState(
                    this.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK), c.getCompound("State")));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Wave", this.wave);
        nbt.putInt("Since", this.sinceLast);
        nbt.putBoolean("Done", this.done);
        nbt.putLongArray("Placed", this.placed);
        NbtList list = new NbtList();
        for (Map.Entry<Long, BlockState> e : this.broken.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("Pos", e.getKey());
            c.put("State", NbtHelper.fromBlockState(e.getValue()));
            list.add(c);
        }
        nbt.put("Broken", list);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
