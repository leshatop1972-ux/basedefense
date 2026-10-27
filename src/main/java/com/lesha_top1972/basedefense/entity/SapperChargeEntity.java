package com.lesha_top1972.basedefense.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * «Подрыв»: связка шашек, вбитая в стену. Фитиль горит 1.2 с, потом взрыв: стена 3×3 (по mobGriefing),
 * 10 урона в радиусе 3. Висит на стороне стены {@link #face()}.
 */
public class SapperChargeEntity extends Entity {

    public static final int FUSE = 24;
    static final float DAMAGE = 10f;
    private static final DustParticleEffect SPARK = new DustParticleEffect(new Vector3f(1.0f, 0.62f, 0.18f), 0.7f);

    private static final TrackedData<Integer> FACE = DataTracker.registerData(SapperChargeEntity.class,
            TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;
    private BlockPos wall = BlockPos.ORIGIN;

    public SapperChargeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    void setup(SapperEntity owner, BlockPos wall, Direction face) {
        this.ownerId = owner.getUuid();
        this.wall = wall.toImmutable();
        this.dataTracker.set(FACE, face.getHorizontal());
        Vec3d c = Vec3d.ofCenter(wall).add(face.getOffsetX() * 0.62, -0.2, face.getOffsetZ() * 0.62);
        this.refreshPositionAndAngles(c.x, c.y, c.z, face.asRotation(), 0f);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FACE, 0);
    }

    public Direction face() {
        return Direction.fromHorizontal(this.dataTracker.get(FACE));
    }

    public float fuse(float tickDelta) {
        return MathHelper.clamp((this.age + tickDelta) / FUSE, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            // искры бегут по фитилю вверх
            float k = this.fuse(0f);
            Direction f = this.face();
            double x = this.getX() + f.getOffsetX() * 0.12;
            double z = this.getZ() + f.getOffsetZ() * 0.12;
            double y = this.getY() + 0.25 + 0.15 * (1 - k);
            w.addParticle(SPARK, x, y, z, (this.random.nextDouble() - 0.5) * 0.12, 0.08, (this.random.nextDouble() - 0.5) * 0.12);
            if (this.age % 2 == 0) {
                w.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0, 0.02, 0);
            }
            return;
        }
        if (this.age >= FUSE) {
            if (w instanceof ServerWorld s) {
                Entity owner = this.ownerId != null ? s.getEntity(this.ownerId) : null;
                SapperBlast.charge(s, this.wall, this.face(), this.getPos().add(0, 0.2, 0), DAMAGE, owner);
            }
            this.discard();
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.discard();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
