package com.lesha_top1972.basedefense.work;

import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.needs.ComfortLevel;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.UUID;

/**
 * Навыки поселенца: уровень 0…20, опыт, увлечение ({@link Passion}).
 * <h3>Баланс</h3>
 * <ul>
 *   <li>До следующего уровня: 250 + 125 × уровень опыта (до 10 — ≈8 тыс., до 20 — ≈36 тыс.).</li>
 *   <li>Скорость работы: 0.6 + 0.06 × уровень (0 → ×0.6, 10 → ×1.2, 20 → ×1.8) и ещё комфорт:
 *       ужасно ×0.6, плохо ×0.85, хорошо ×1.1.</li>
 *   <li>Ближний бой: урон ×(0.85 + 0.025 × ур.) — от −15% до +35%.</li>
 *   <li>Стрельба: урон стрел так же, меткость 0.5 × (0.7 + 0.04 × ур.) — от 35% до 75%.</li>
 *   <li>Новый поселенец: навыки 0–4, одно большое увлечение (+4…7 уровней) и одно‑два малых (+2…4).</li>
 * </ul>
 */
public class SettlerSkills {

    public static final int MAX = 20;
    private static final UUID MELEE_ID = UUID.fromString("6a1f0c2e-4b8d-4f3a-9e21-7c5d0b1a2e01");

    private final int[] level = new int[Skill.values().length];
    private final float[] xp = new float[Skill.values().length];
    private final Passion[] passion = new Passion[Skill.values().length];
    private boolean generated;

    public SettlerSkills() {
        java.util.Arrays.fill(passion, Passion.NONE);
    }

    public static int xpToNext(int lvl) {
        return 250 + 125 * lvl;
    }

    public int level(Skill s) {
        return level[s.ordinal()];
    }

    public float xp(Skill s) {
        return xp[s.ordinal()];
    }

    public Passion passion(Skill s) {
        return passion[s.ordinal()];
    }

    /** Множитель скорости работы по навыку (с учётом комфорта поселенца). */
    public float workSpeed(SettlerEntity e, Skill s) {
        float k = 0.6f + 0.06f * level(s);
        return k * switch (ComfortLevel.of(e.getComfort())) {
            case AWFUL -> 0.6f;
            case BAD -> 0.85f;
            case NORMAL -> 1.0f;
            case GOOD -> 1.1f;
        };
    }

    /** Шанс бонуса: 1.5% за уровень (до 30%). */
    public float bonusChance(Skill s) {
        return 0.015f * level(s);
    }

    /** Множитель урона (ближний бой / стрельба). */
    public float damageMult(Skill s) {
        return 0.85f + 0.025f * level(s);
    }

    /** Первое появление: случайные навыки и увлечения. */
    public void generateIfNeeded(SettlerEntity e) {
        if (generated) {
            return;
        }
        generated = true;
        Random r = e.getRandom();
        Skill[] all = Skill.values();
        for (int i = 0; i < all.length; i++) {
            level[i] = r.nextInt(5);
        }
        int major = r.nextInt(all.length);
        passion[major] = Passion.MAJOR;
        level[major] = Math.min(MAX, level[major] + 4 + r.nextInt(4));
        int minors = 1 + r.nextInt(2);
        for (int n = 0; n < minors; n++) {
            int i = r.nextInt(all.length);
            if (passion[i] == Passion.NONE) {
                passion[i] = Passion.MINOR;
                level[i] = Math.min(MAX, level[i] + 2 + r.nextInt(3));
            }
        }
        applyModifiers(e);
    }

    /** Опыт за дело (с множителем увлечения); повышение уровня — частицы, звук, сообщение игрокам рядом. */
    public void addXp(SettlerEntity e, Skill s, float base) {
        int i = s.ordinal();
        if (level[i] >= MAX) {
            return;
        }
        xp[i] += base * passion[i].xpMult;
        boolean up = false;
        while (level[i] < MAX && xp[i] >= xpToNext(level[i])) {
            xp[i] -= xpToNext(level[i]);
            level[i]++;
            up = true;
        }
        if (level[i] >= MAX) {
            xp[i] = 0;
        }
        if (up && e.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 2.1, e.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.4f, 1.3f);
            Text msg = Text.translatable("message.basedefense.skill.up", e.getDisplayName(),
                    Text.translatable("skill.basedefense." + s.id), level[i]).formatted(Formatting.GREEN);
            for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(e) < 40 * 40)) {
                p.sendMessage(msg, true);
            }
            applyModifiers(e);
        }
    }

    /** Урон в ближнем бою зависит от навыка (модификатор атрибута). */
    public void applyModifiers(SettlerEntity e) {
        EntityAttributeInstance a = e.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (a == null) {
            return;
        }
        a.removeModifier(MELEE_ID);
        a.addTemporaryModifier(new EntityAttributeModifier(MELEE_ID, "basedefense melee skill",
                damageMult(Skill.MELEE) - 1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("Generated", generated);
        for (Skill s : Skill.values()) {
            NbtCompound c = new NbtCompound();
            c.putInt("Level", level[s.ordinal()]);
            c.putFloat("Xp", xp[s.ordinal()]);
            c.putInt("Passion", passion[s.ordinal()].ordinal());
            n.put(s.id, c);
        }
        return n;
    }

    public void fromNbt(NbtCompound n, SettlerEntity e) {
        generated = n.getBoolean("Generated");
        for (Skill s : Skill.values()) {
            if (n.contains(s.id)) {
                NbtCompound c = n.getCompound(s.id);
                level[s.ordinal()] = Math.max(0, Math.min(MAX, c.getInt("Level")));
                xp[s.ordinal()] = c.getFloat("Xp");
                int p = c.getInt("Passion");
                passion[s.ordinal()] = p >= 0 && p < Passion.values().length ? Passion.values()[p] : Passion.NONE;
            }
        }
        applyModifiers(e);
    }
}
