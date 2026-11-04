package com.lesha_top1972.basedefense.needs;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.temperature.TempStatus;
import com.lesha_top1972.basedefense.temperature.Temperature;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

/**
 * Потребности игроков и поселенцев: ощущаемая температура, комфорт (0…100) и его эффекты ({@link ComfortLevel}).
 * Голод игрока — ванильный, у поселенца — свой (см. SettlerEntity). Сон — позже.
 * <h3>Ощущаемая температура</h3>
 * Тянется к температуре воздуха плавно: каждую секунду на 5% разницы + 0.15° (вышел из тёплого дома на −20 —
 * почти полностью остынешь примерно за минуту).
 * <h3>Комфорт</h3>
 * Каждую секунду медленно идёт к «цели»:
 * <ul>
 *   <li>в комфортной температуре цель 65; бонусы: в помещении +8, сыт (голод ≥ 15) +8, одет (≥ 3 вещей) +6 — до 87;</li>
 *   <li>вне комфорта цель −5 за каждый градус: на 3° холоднее — 50, на 6° — 35, на 10° — 15, на 13° — 0;</li>
 *   <li>голод ≤ 6 — ещё −15, голод 0 — −30.</li>
 * </ul>
 * Растёт на 0.12 в секунду (≈7 в минуту), падает на 0.1 + 0.6% от разницы в секунду (при сильном холоде ≈30 в минуту,
 * при лёгком — 7–10 в минуту).
 */
public final class Needs {

    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "needs_sync");
    public static final float COMFORT_START = 60f;

    // ---------- Ощущаемая температура ----------
    private static final float FELT_SHARE = 0.05f;
    private static final float FELT_MIN_STEP = 0.15f;
    // ---------- Цель комфорта ----------
    private static final float TARGET_COMFY = 65f;
    private static final float PER_DEGREE = 5f;
    private static final float BONUS_INDOORS = 8f;
    private static final float BONUS_FED = 8f;
    private static final float BONUS_DRESSED = 6f;
    private static final int FED_HUNGER = 15;
    private static final int HUNGRY = 6;
    private static final float PENALTY_HUNGRY = 15f;
    private static final float PENALTY_STARVING = 30f;
    // ---------- Скорость изменения (в секунду) ----------
    private static final float UP_RATE = 0.12f;
    private static final float DOWN_RATE = 0.1f;
    private static final float DOWN_SHARE = 0.006f;
    // ---------- Хороший комфорт ----------
    private static final int REGEN_EVERY = 200;

    private static final UUID SPEED_ID = UUID.fromString("3c5e1f0a-8d2b-4b6e-9f41-6a2d1c0e7b11");
    private static final UUID DAMAGE_ID = UUID.fromString("3c5e1f0a-8d2b-4b6e-9f41-6a2d1c0e7b12");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("3c5e1f0a-8d2b-4b6e-9f41-6a2d1c0e7b13");

    /** Где хранятся потребности (игрок — миксин, поселенец — поля). */
    public interface State {
        float comfort();

        void setComfort(float v);

        float felt();

        void setFelt(float v);

        /** Сытость 0…20. */
        int hunger();
    }

    /** Итог обновления — для пакета игроку и данных поселенца. */
    public record Result(float felt, float ambient, TempStatus status, float comfortMin, float comfortMax,
                         float comfort, int trend, float enclosure) {
        public ComfortLevel level() {
            return ComfortLevel.of(comfort);
        }
    }

    private Needs() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Needs::tick);
        // Смена мира (Энд) — потребности остаются; смерть — комфорт заново
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            NeedsHolder from = (NeedsHolder) oldPlayer;
            NeedsHolder to = (NeedsHolder) newPlayer;
            if (alive) {
                to.basedefense$setComfort(from.basedefense$comfort());
                to.basedefense$setFelt(from.basedefense$felt());
            }
        });
    }

    public static State of(PlayerEntity p) {
        NeedsHolder h = (NeedsHolder) p;
        return new State() {
            @Override
            public float comfort() {
                return h.basedefense$comfort();
            }

            @Override
            public void setComfort(float v) {
                h.basedefense$setComfort(v);
            }

            @Override
            public float felt() {
                return h.basedefense$felt();
            }

            @Override
            public void setFelt(float v) {
                h.basedefense$setFelt(v);
            }

            @Override
            public int hunger() {
                return p.getHungerManager().getFoodLevel();
            }
        };
    }

    /** Ощущаемая температура игрока (или fallback, если ещё не считалась). */
    public static float felt(PlayerEntity p, float fallback) {
        float f = ((NeedsHolder) p).basedefense$felt();
        return Float.isNaN(f) ? fallback : f;
    }

    // =====================================================================
    // Игроки: раз в секунду — расчёт и пакет
    // =====================================================================

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 3) {
            return;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) {
                continue;
            }
            boolean affect = !p.isCreative();
            Result r = update(p, p.getServerWorld(), of(p), 1f, affect);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeFloat(r.felt());
            buf.writeFloat(r.ambient());
            buf.writeByte(r.status().ordinal());
            buf.writeFloat(r.comfortMin());
            buf.writeFloat(r.comfortMax());
            buf.writeFloat(r.comfort());
            buf.writeByte(r.trend());
            buf.writeFloat(r.enclosure());
            ServerPlayNetworking.send(p, SYNC, buf);
        }
    }

    // =====================================================================
    // Общий расчёт (игрок и поселенец)
    // =====================================================================

    /**
     * Обновить потребности за dt секунд. affect = false (творческий режим) — комфорт не меняется и эффектов нет,
     * температура считается для показа.
     */
    public static Result update(LivingEntity e, ServerWorld w, State st, float dt, boolean affect) {
        Temperature.Sample s = Temperature.sample(w, e.getBlockPos(), null);

        // Ощущаемая температура — плавно к воздуху
        float felt = st.felt();
        if (Float.isNaN(felt)) {
            felt = s.temp();
        } else {
            float diff = s.temp() - felt;
            float step = (Math.abs(diff) * FELT_SHARE + FELT_MIN_STEP) * dt;
            felt += Math.signum(diff) * Math.min(Math.abs(diff), step);
        }
        st.setFelt(felt);

        float[] c = Temperature.comfort(e);
        TempStatus status = TempStatus.of(felt, c[0], c[1]);
        float comfort = st.comfort();
        int trend = 0;
        if (affect) {
            float target = target(e, felt, c, s.enclosure(), st.hunger());
            float diff = target - comfort;
            if (diff > 0) {
                comfort += Math.min(diff, UP_RATE * dt);
            } else if (diff < 0) {
                comfort -= Math.min(-diff, (DOWN_RATE + DOWN_SHARE * -diff) * dt);
            }
            comfort = MathHelper.clamp(comfort, 0f, 100f);
            st.setComfort(comfort);
            trend = Math.abs(target - comfort) < 1f ? 0 : target > comfort ? 1 : -1;
            apply(e, ComfortLevel.of(comfort), dt);
        } else {
            apply(e, ComfortLevel.NORMAL, dt);
        }
        return new Result(felt, s.temp(), status, c[0], c[1], comfort, trend, s.enclosure());
    }

    /** Цель комфорта (см. описание класса). */
    private static float target(LivingEntity e, float felt, float[] c, float enclosure, int hunger) {
        float dev = felt < c[0] ? c[0] - felt : felt > c[1] ? felt - c[1] : 0f;
        float t = TARGET_COMFY - PER_DEGREE * dev;
        if (dev == 0f) {
            if (enclosure >= Temperature.INDOORS) {
                t += BONUS_INDOORS;
            }
            if (hunger >= FED_HUNGER) {
                t += BONUS_FED;
            }
            if (Temperature.clothingPieces(e) >= 3) {
                t += BONUS_DRESSED;
            }
        }
        if (hunger <= 0) {
            t -= PENALTY_STARVING;
        } else if (hunger <= HUNGRY) {
            t -= PENALTY_HUNGRY;
        }
        return MathHelper.clamp(t, 0f, 100f);
    }

    /** Эффекты уровня комфорта: множители скорости, урона, скорости атаки; при хорошем — лечение. */
    private static void apply(LivingEntity e, ComfortLevel level, float dt) {
        setModifier(e, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, "basedefense comfort speed", level.speed);
        setModifier(e, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, "basedefense comfort damage", level.damage);
        setModifier(e, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED_ID, "basedefense comfort attack speed",
                level.attackSpeed);
        if (level == ComfortLevel.GOOD && e.age % REGEN_EVERY < 20 * dt && e.getHealth() < e.getMaxHealth()
                && !(e instanceof GuardEntity g && g.isSummoned())) {
            e.heal(1f);
        }
    }

    private static void setModifier(LivingEntity e, EntityAttribute attr, UUID id, String name, double value) {
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) {
            return;
        }
        EntityAttributeModifier old = inst.getModifier(id);
        if (old != null && old.getValue() == value) {
            return;
        }
        if (old != null) {
            inst.removeModifier(id);
        }
        if (value != 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
