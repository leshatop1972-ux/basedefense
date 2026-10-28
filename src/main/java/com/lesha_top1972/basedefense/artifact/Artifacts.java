package com.lesha_top1972.basedefense.artifact;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Артефакты: 5 слотов у игрока, проверка «надет ли артефакт», способности.
 * <ul>
 *   <li>Амулет рывка — клавиша R: быстрый плавный рывок на 4 блока в сторону движения (или вперёд),
 *       перезарядка 2 с. Двигает игрока клиент (движение игрока — клиентское), сервер проверяет амулет
 *       и перезарядку, ставит перезарядку на предмет, играет звук и рассылает анимацию остальным.</li>
 * </ul>
 *   <li>Амулет взлёта — клавиша Y: подброс вверх на 3.5 блока, до 3 раз подряд (и в воздухе), потом
 *       перезарядка 6 с; после взлёта до приземления урона от падения нет.</li>
 * Пассивные амулеты (проверяются раз в 10 тиков, см. {@link #updatePassives}):
 *   <li>Вампиризм I/II/III — убийство моба лечит на 4/6/8 HP, у III ещё 1 HP за каждое попадание;</li>
 *   <li>Скорость I/II/III — +8/+16/+25% к скорости;</li>
 *   <li>Жизнь I/II/III — +5/+9/+13 к максимальному здоровью.</li>
 *   <li>Уворот I/II/III — 7/10/13% не получить урон от атаки моба (складываются, максимум 50%);</li>
 *   <li>Плащ вампиризма — 25% шанс: союзники в 25 блоках лечатся на 25% нанесённого урона;</li>
 *   <li>Амулет мудрости — каждый удар по мобу тратит 1/4 уровня опыта: Скорость I на 5 с и +1 HP.</li>
 * Вампиризм и скорость не складываются (действует самый сильный надетый), жизнь складывается.
 * Артефакты не выпадают при смерти — остаются в слотах.
 */
public final class Artifacts {

    public static final int SLOTS = 5;
    public static final Identifier DASH_C2S = new Identifier(Basedefense.MOD_ID, "artifact_dash");
    public static final Identifier DASH_S2C = new Identifier(Basedefense.MOD_ID, "artifact_dash_anim");

    /** Рывок: 5 тиков по 0.8 блока = 4 блока. */
    public static final int DASH_TICKS = 5;
    public static final float DASH_SPEED = 0.8f;
    public static final int DASH_COOLDOWN = 40;

    public static final Identifier LEAP_C2S = new Identifier(Basedefense.MOD_ID, "artifact_leap");
    public static final Identifier LEAP_S2C = new Identifier(Basedefense.MOD_ID, "artifact_leap_anim");
    /** Взлёт: скорость вверх 0.745 — подъём ≈ 3.5 блока; 3 взлёта подряд, потом 6 с перезарядки. */
    public static final float LEAP_SPEED = 0.745f;
    public static final int LEAP_CHARGES = 3;
    public static final int LEAP_COOLDOWN = 120;

    /** Игрок → {сколько взлётов подряд, тик последнего}. */
    private static final Map<UUID, int[]> LEAP_USED = new HashMap<>();
    /** После взлёта до приземления урона от падения нет. */
    private static final Set<UUID> AIRBORNE = new HashSet<>();

    private static final UUID SPEED_ID = UUID.fromString("6b0f6c1e-3b7a-4f0e-9a51-0d6f3a1c7e01");
    private static final UUID LIFE_ID = UUID.fromString("6b0f6c1e-3b7a-4f0e-9a51-0d6f3a1c7e02");
    private static final float[] VAMP_KILL = {0f, 4f, 6f, 8f};
    private static final float VAMP_HIT = 1f;
    private static final double[] SPEED = {0, 0.08, 0.16, 0.25};
    private static final float DODGE_MAX = 0.5f;
    private static final float CLOAK_CHANCE = 0.25f;
    private static final float CLOAK_SHARE = 0.25f;
    private static final double CLOAK_RADIUS = 25.0;
    private static final float WISDOM_COST = 0.25f;
    private static final int WISDOM_SPEED_TICKS = 100;
    private static final float WISDOM_HEAL = 1f;

    private Artifacts() {
    }

    public static SimpleInventory of(PlayerEntity p) {
        return ((ArtifactHolder) p).basedefense$artifacts();
    }

    public static boolean has(PlayerEntity p, Item item) {
        SimpleInventory inv = of(p);
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) {
                return true;
            }
        }
        return false;
    }

    public static void init() {
        // после смерти и возвращения из Энда артефакты остаются
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SimpleInventory from = of(oldPlayer);
            SimpleInventory to = of(newPlayer);
            for (int i = 0; i < from.size(); i++) {
                to.setStack(i, from.getStack(i).copy());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DASH_C2S, (server, player, handler, buf, sender) -> {
            float f = buf.readFloat();
            float s = buf.readFloat();
            server.execute(() -> dash(player, f, s));
        });
        ServerPlayNetworking.registerGlobalReceiver(LEAP_C2S, (server, player, handler, buf, sender) ->
                server.execute(() -> leap(player, server.getTicks())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    updatePassives(p);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> AIRBORNE.removeIf(id -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || p.isOnGround() || p.isTouchingWater() || p.isClimbing()) {
                return true;
            }
            p.fallDistance = 0f;
            return false;
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LEAP_USED.remove(handler.player.getUuid());
            AIRBORNE.remove(handler.player.getUuid());
        });
    }

    /** Самый сильный надетый амулет из ряда I/II/III: 0 — нет ни одного. */
    private static int level(PlayerEntity p, Item i1, Item i2, Item i3) {
        return has(p, i3) ? 3 : has(p, i2) ? 2 : has(p, i1) ? 1 : 0;
    }

    /** Скорость (самый сильный) и здоровье (сумма всех амулетов жизни) — модификаторами атрибутов. */
    private static void updatePassives(ServerPlayerEntity p) {
        SimpleInventory inv = of(p);
        double life = 0;
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.getStack(i).getItem();
            if (it == ModItems.LIFE_AMULET_1) {
                life += 5;
            } else if (it == ModItems.LIFE_AMULET_2) {
                life += 9;
            } else if (it == ModItems.LIFE_AMULET_3) {
                life += 13;
            }
        }
        double speed = SPEED[level(p, ModItems.SPEED_AMULET_1, ModItems.SPEED_AMULET_2, ModItems.SPEED_AMULET_3)];
        setModifier(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, "basedefense speed amulet", speed,
                EntityAttributeModifier.Operation.MULTIPLY_BASE);
        setModifier(p, EntityAttributes.GENERIC_MAX_HEALTH, LIFE_ID, "basedefense life amulet", life,
                EntityAttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    private static void setModifier(PlayerEntity p, EntityAttribute attr, UUID id, String name, double value,
                                    EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) {
            return;
        }
        EntityAttributeModifier cur = inst.getModifier(id);
        if (cur != null && cur.getValue() == value && cur.getOperation() == op) {
            return;
        }
        if (cur != null) {
            inst.removeModifier(id);
        }
        if (value != 0) {
            // постоянный — сохраняется с игроком, чтобы при входе здоровье сверх 20 не обрезалось
            inst.addPersistentModifier(new EntityAttributeModifier(id, name, value, op));
        }
    }

    /** Вызывается после урона по существу (LivingEntityCombatMixin): вампиризм, плащ вампиризма, мудрость. */
    public static void onDamaged(LivingEntity target, DamageSource source, float dealt) {
        if (dealt <= 0 || !(target instanceof MobEntity) || target instanceof SettlerEntity
                || !(source.getAttacker() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity p = (ServerPlayerEntity) source.getAttacker();
        if (!p.isAlive()) {
            return;
        }
        float heal = 0f;
        int lvl = level(p, ModItems.VAMPIRE_AMULET_1, ModItems.VAMPIRE_AMULET_2, ModItems.VAMPIRE_AMULET_3);
        if (lvl > 0) {
            heal += lvl == 3 ? VAMP_HIT : 0f;
            if (target.isDead()) {
                heal += VAMP_KILL[lvl];
            }
        }
        // Мудрость: только удар рукой/оружием (не стрела). 1/4 уровня опыта → Скорость I на 5 с и +1 HP.
        if (source.getSource() == p && has(p, ModItems.WISDOM_AMULET) && spendQuarterLevel(p)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, WISDOM_SPEED_TICKS, 0, false, true, true));
            heal += WISDOM_HEAL;
        }
        if (heal > 0) {
            p.heal(heal);
        }
        // Плащ вампиризма: с шансом 25% все союзники в 25 блоках (игроки и жители, вместе с носителем)
        // лечатся на 25% прошедшего урона.
        if (has(p, ModItems.VAMPIRE_CLOAK) && p.getRandom().nextFloat() < CLOAK_CHANCE) {
            float amount = dealt * CLOAK_SHARE;
            Box box = p.getBoundingBox().expand(CLOAK_RADIUS);
            double r2 = CLOAK_RADIUS * CLOAK_RADIUS;
            for (LivingEntity ally : p.getWorld().getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && (e instanceof PlayerEntity || e instanceof SettlerEntity))) {
                if (ally.squaredDistanceTo(p) <= r2 && !(ally instanceof PlayerEntity pl && pl.isSpectator())) {
                    ally.heal(amount);
                    ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.HEART,
                            ally.getX(), ally.getBodyY(1.0) + 0.4, ally.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    /** Списать 1/4 уровня опыта; false — опыта не хватает (ничего не списано). */
    private static boolean spendQuarterLevel(ServerPlayerEntity p) {
        int level = p.experienceLevel;
        float progress = p.experienceProgress - WISDOM_COST;
        if (progress < 0f) {
            if (level <= 0) {
                return false;
            }
            level--;
            progress += 1f;
        }
        int points = Math.max(1, Math.round(p.getNextLevelExperience() * WISDOM_COST));
        p.totalExperience = Math.max(0, p.totalExperience - points);
        p.experienceProgress = progress;
        p.setExperienceLevel(level);   // заодно отправляет полоску опыта клиенту
        return true;
    }

    /** Шанс уворота от атаки моба: сумма всех амулетов уворота (складываются), не больше 50%. */
    public static float dodgeChance(PlayerEntity p) {
        SimpleInventory inv = of(p);
        float c = 0f;
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.getStack(i).getItem();
            if (it == ModItems.DODGE_AMULET_1) {
                c += 0.07f;
            } else if (it == ModItems.DODGE_AMULET_2) {
                c += 0.10f;
            } else if (it == ModItems.DODGE_AMULET_3) {
                c += 0.13f;
            }
        }
        return Math.min(c, DODGE_MAX);
    }

    /** Из миксина урона игрока: true — увернулся (урон отменяется). */
    public static boolean tryDodge(ServerPlayerEntity p, DamageSource source) {
        if (!(source.getAttacker() instanceof MobEntity) || p.timeUntilRegen > 10) {
            return false;
        }
        float c = dodgeChance(p);
        if (c <= 0f || p.getRandom().nextFloat() >= c) {
            return false;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
                SoundCategory.PLAYERS, 0.8f, 1.6f);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getBodyY(0.5), p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        p.sendMessage(Text.translatable("message.basedefense.dodge").formatted(Formatting.AQUA), true);
        return true;
    }

    private static void leap(ServerPlayerEntity player, int now) {
        if (!player.isAlive() || player.isSpectator() || !has(player, ModItems.LEAP_AMULET)
                || player.getItemCooldownManager().isCoolingDown(ModItems.LEAP_AMULET)) {
            return;
        }
        int[] u = LEAP_USED.computeIfAbsent(player.getUuid(), k -> new int[]{0, -1000});
        if (now - u[1] > LEAP_COOLDOWN) {
            u[0] = 0;
        }
        u[0]++;
        u[1] = now;
        if (u[0] >= LEAP_CHARGES) {
            u[0] = 0;
            player.getItemCooldownManager().set(ModItems.LEAP_AMULET, LEAP_COOLDOWN);
        }
        player.fallDistance = 0;
        AIRBORNE.add(player.getUuid());
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                SoundCategory.PLAYERS, 0.5f, 1.5f);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                ServerPlayNetworking.send(other, LEAP_S2C, buf);
            }
        }
    }

    private static void dash(ServerPlayerEntity player, float f, float s) {
        if (!player.isAlive() || player.isSpectator() || !has(player, ModItems.DASH_AMULET)
                || player.getItemCooldownManager().isCoolingDown(ModItems.DASH_AMULET)) {
            return;
        }
        player.getItemCooldownManager().set(ModItems.DASH_AMULET, DASH_COOLDOWN);
        player.fallDistance = 0;
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                SoundCategory.PLAYERS, 0.45f, 1.9f);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.5f, 0.6f);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(player.getId());
                buf.writeFloat(f);
                buf.writeFloat(s);
                ServerPlayNetworking.send(other, DASH_S2C, buf);
            }
        }
    }
}
