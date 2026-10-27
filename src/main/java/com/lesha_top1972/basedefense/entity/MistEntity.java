package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * «Туманная сущность»: как человек, вся белая с дымно‑серым. Живёт в тумане (будущее событие).
 * 45 HP, удар рукой 7 урона раз в 2 с. Шагов не слышно; рядом с игроком (10 бл) — сбои, треск и обратный шёпот,
 * подобралась ближе 5 бл — резкий удар со звоном в ушах (не чаще раза в 30 с), при атаке — искажённый визг.
 */
public class MistEntity extends HorrorEntity {

    private static final DustParticleEffect FOG = new DustParticleEffect(new Vector3f(0.86f, 0.86f, 0.88f), 1.6f);
    private int scareCd = 60;
    private int stingCd;

    public MistEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 45.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0);
    }

    @Override
    protected int attackTicks() {
        return 16;
    }

    @Override
    protected int attackHit() {
        return 7;
    }

    @Override
    protected float attackDamage() {
        return 7f;
    }

    @Override
    protected void attackSound(int action) {
        this.playSound(ModSounds.MIST_SCREECH, 2.0f, 0.92f + this.random.nextFloat() * 0.16f);
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            // клубится дым
            if (this.age % 3 == 0) {
                w.addParticle(FOG, this.getX() + (this.random.nextDouble() - 0.5) * 0.7, this.getY() + this.random.nextDouble() * 1.9,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
            }
            if (this.random.nextInt(12) == 0) {
                w.addParticle(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
            }
            return;
        }
        if (this.scareCd > 0) {
            this.scareCd--;
        }
        if (this.stingCd > 0) {
            this.stingCd--;
        }
        if (this.age % 10 == 0) {
            PlayerEntity p = w.getClosestPlayer(this, 10.0);
            if (p != null && !p.isCreative() && !p.isSpectator()) {
                double d = this.distanceTo(p);
                if (d < 5.0 && this.stingCd <= 0) {
                    this.stingCd = 600;
                    this.scareCd = 80;
                    w.playSound(null, this.getX(), this.getY() + 1, this.getZ(), ModSounds.MIST_STING, SoundCategory.HOSTILE, 1.4f, 1.0f);
                } else if (this.scareCd <= 0) {
                    this.scareCd = 60 + this.random.nextInt(80);
                    SoundEvent s = this.random.nextBoolean() ? ModSounds.MIST_GLITCH : ModSounds.MIST_WHISPER;
                    w.playSound(null, this.getX(), this.getY() + 1, this.getZ(), s, SoundCategory.HOSTILE, 1.1f,
                            0.85f + this.random.nextFloat() * 0.3f);
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MIST_WHISPER;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MIST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MIST_DEATH;
    }
}
