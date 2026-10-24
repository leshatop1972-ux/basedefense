package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Каменный столб «Бордюра» (невидимый управляющий объект): за 10 тиков вырастает из земли на HEIGHT блоков,
 * поднимая всех, кто стоит на нём; держится HOLD тиков, потом опускается по блоку — стоящие на нём
 * спускаются без урона от падения. Ставит блоки только в воздух и убирает только свои.
 */
public class CurbPillarEntity extends Entity {

    public static final int HEIGHT = 10;
    static final int HOLD = 80;
    static final int SINK_EVERY = 3;

    private BlockPos base = BlockPos.ORIGIN;
    private BlockState block = Blocks.COBBLED_DEEPSLATE.getDefaultState();
    private int built;
    private int phase;          // 0 — растёт, 1 — стоит, 2 — опускается
    private int timer;
    private final List<Integer> placed = new ArrayList<>();

    public CurbPillarEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    /** Столб на блоке земли ground (растёт над ним). */
    void setup(BlockPos ground) {
        this.base = ground;
        this.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 0f, 0f);
        BlockState g = this.getWorld().getBlockState(ground);
        Block b = g.getBlock();
        if (g.isOf(Blocks.GRASS_BLOCK) || g.isOf(Blocks.PODZOL) || g.isOf(Blocks.MYCELIUM) || g.isOf(Blocks.DIRT_PATH)) {
            this.block = Blocks.DIRT.getDefaultState();
        } else if (!g.isAir() && !g.hasBlockEntity() && !(b instanceof FallingBlock) && g.getFluidState().isEmpty()
                && g.isFullCube(this.getWorld(), ground) && g.getHardness(this.getWorld(), ground) >= 0) {
            this.block = g;
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
        this.timer++;
        switch (this.phase) {
            case 0 -> {
                BlockPos p = this.base.up(1 + this.built);
                if (this.built >= HEIGHT || !s.getBlockState(p).isAir()) {
                    this.phase = 1;
                    this.timer = 0;
                    return;
                }
                // сначала поднять стоящих, потом поставить блок под ними
                double top = p.getY() + 1.0;
                for (LivingEntity e : this.riders(p.getY() - 0.6, p.getY() + 1.6)) {
                    e.requestTeleport(this.base.getX() + 0.5, top + 0.01, this.base.getZ() + 0.5);   // на середину столба
                    e.setVelocity(0, 0, 0);
                    e.velocityModified = true;
                    e.fallDistance = 0;
                }
                s.setBlockState(p, this.block, Block.NOTIFY_ALL);
                this.placed.add(this.built);
                this.built++;
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.block),
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 10, 0.55, 0.3, 0.55, 0.15);
                if (this.built % 2 == 1) {
                    s.playSound(null, p, this.block.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.2f, 0.6f);
                }
            }
            case 1 -> {
                for (LivingEntity e : this.riders(this.base.getY() + this.built - 1.0, this.base.getY() + this.built + 3.0)) {
                    e.fallDistance = 0;
                }
                if (this.timer >= HOLD) {
                    this.phase = 2;
                    this.timer = 0;
                }
            }
            default -> {
                for (LivingEntity e : this.riders(this.base.getY(), this.base.getY() + this.built + 3.0)) {
                    e.fallDistance = 0;
                }
                if (this.timer % SINK_EVERY != 0) {
                    return;
                }
                if (this.placed.isEmpty()) {
                    this.discard();
                    return;
                }
                int lvl = this.placed.remove(this.placed.size() - 1);
                BlockPos p = this.base.up(1 + lvl);
                if (s.getBlockState(p) == this.block) {
                    s.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.block),
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.1);
                    if (lvl % 3 == 0) {
                        s.playSound(null, p, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.8f, 0.7f);
                    }
                }
                this.built = lvl;
            }
        }
    }

    /** Кто стоит над столбом (центр не дальше 0.85 от оси, ноги между minY и maxY). */
    private List<LivingEntity> riders(double minY, double maxY) {
        Box box = new Box(this.base.getX() - 0.5, minY, this.base.getZ() - 0.5,
                this.base.getX() + 1.5, maxY, this.base.getZ() + 1.5);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Monster)
                && Math.abs(e.getX() - (this.base.getX() + 0.5)) < 0.85 && Math.abs(e.getZ() - (this.base.getZ() + 0.5)) < 0.85
                && e.getY() >= minY && e.getY() <= maxY);
    }

    /** Если столб убрали командой — сразу убрать его блоки (при выгрузке чанка — не трогать). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.getWorld() instanceof ServerWorld s) {
            for (int lvl : this.placed) {
                BlockPos p = this.base.up(1 + lvl);
                if (s.getBlockState(p) == this.block) {
                    s.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            this.placed.clear();
        }
        super.remove(reason);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.base = NbtHelper.toBlockPos(nbt.getCompound("Base"));
        this.block = NbtHelper.toBlockState(this.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK),
                nbt.getCompound("Block"));
        this.built = nbt.getInt("Built");
        this.phase = nbt.getInt("Phase");
        this.timer = nbt.getInt("Timer");
        this.placed.clear();
        for (int v : nbt.getIntArray("Placed")) {
            this.placed.add(v);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Base", NbtHelper.fromBlockPos(this.base));
        nbt.put("Block", NbtHelper.fromBlockState(this.block));
        nbt.putInt("Built", this.built);
        nbt.putInt("Phase", this.phase);
        nbt.putInt("Timer", this.timer);
        nbt.putIntArray("Placed", this.placed);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    /** Хозяину столба: кого он поднял (для смены цели). */
    static List<LivingEntity> standingOn(World w, BlockPos ground) {
        Box box = new Box(ground.getX() - 0.5, ground.getY() + 0.4, ground.getZ() - 0.5,
                ground.getX() + 1.5, ground.getY() + 3.0, ground.getZ() + 1.5);
        return w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Monster)
                && Math.abs(e.getX() - (ground.getX() + 0.5)) < 0.85 && Math.abs(e.getZ() - (ground.getZ() + 0.5)) < 0.85);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }
}
