package com.lesha_top1972.basedefense.client.fx;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.FallingLanceEntity;
import com.lesha_top1972.basedefense.entity.TatteredLancerEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.lesha_top1972.basedefense.entity.TatteredLancerEntity.*;

/**
 * Все «киношные» эффекты боя с Оборванным уланом (только клиент, на сервер не влияют):
 * <ul>
 *   <li>светящийся след острия копья по настоящей позе модели (голубой, у «тройного тарана» — малиновый);</li>
 *   <li>фантомы‑послеобразы на рывках (рисует {@code TatteredLancerRenderer});</li>
 *   <li>блик‑звезда на острие перед атакой, стрелки на земле там, где пройдёт таран;</li>
 *   <li>ударные кольца, трещины, столбы света, серпы взмахов, волна финального выпада;</li>
 *   <li>метка места приземления прыжка, метки под падающими копьями, руническая печать в небе
 *       и стена‑купол зоны при ливне копий;</li>
 *   <li>2‑я фаза: печать под ногами, синий огонь из глаз; нить между уланом и дуэлянтом;</li>
 *   <li>тряска камеры (учитывает настройку «Эффекты искажения»), вспышка экрана, красная кромка
 *       экрана, если стоишь на линии тарана; эффект смерти.</li>
 * </ul>
 * Текстуры — textures/fx/*.png (tools/lancer_fx_textures.py), смешивание аддитивное (RenderLayer.getEyes).
 */
public final class LancerFx {

    private static Identifier tex(String name) {
        return new Identifier(Basedefense.MOD_ID, "textures/fx/" + name + ".png");
    }

    private static final Identifier RING = tex("ring");
    private static final Identifier CRACK = tex("crack");
    private static final Identifier RUNE = tex("rune");
    private static final Identifier GLINT = tex("glint");
    private static final Identifier TRAIL = tex("trail");
    private static final Identifier BEAM = tex("beam");
    private static final Identifier ARROW = tex("arrow");
    private static final Identifier SLASH = tex("slash");
    private static final Identifier MARKER = tex("marker");
    private static final Identifier WALL = tex("wall");
    private static final Identifier LINE = tex("line");

    /** Цвета: холодный голубой огонь, малиновый (кушак) для «тройного тарана», бледное золото. */
    static final float[] CYAN = {0.45f, 0.80f, 1.00f};
    static final float[] SOUL = {0.30f, 0.55f, 1.00f};
    static final float[] CRIMSON = {1.00f, 0.22f, 0.32f};
    static final float[] GOLD = {1.00f, 0.82f, 0.45f};
    static final float[] WHITE = {0.90f, 0.95f, 1.00f};

    private static final DustParticleEffect DUST_CYAN = new DustParticleEffect(new Vector3f(0.45f, 0.8f, 1.0f), 1.0f);
    private static final DustParticleEffect DUST_CRIMSON = new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.25f), 1.1f);
    private static final DustColorTransitionParticleEffect DUST_FADE =
            new DustColorTransitionParticleEffect(new Vector3f(0.7f, 0.9f, 1.0f), new Vector3f(0.1f, 0.2f, 0.5f), 1.3f);

    private static final Map<TatteredLancerEntity, LancerTrack> TRACKS = new WeakHashMap<>();
    private static final List<Fx> FX = new ArrayList<>();
    private static final int MAX_FX = 256;

    /** Тики клиента (своя шкала времени для эффектов). */
    private static int ticks;
    /** Тряска камеры (0..1, «травма»: смещение ~ травма²). */
    private static float trauma;
    /** Вспышка экрана. */
    private static float flash;
    private static float flashDecay = 0.08f;
    private static int flashRgb = 0x6FA8FF;
    /** Красная кромка экрана: стоишь на линии тарана. */
    private static float danger;
    private static float dangerTarget;

    private LancerFx() {
    }

    public static void init() {
        ClientTickEvents.END_WORLD_TICK.register(LancerFx::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(LancerFx::render);
        HudRenderCallback.EVENT.register(LancerFx::hud);
    }

    public static LancerTrack track(TatteredLancerEntity e) {
        return TRACKS.computeIfAbsent(e, k -> new LancerTrack());
    }

    // =====================================================================================
    // Короткоживущие эффекты
    // =====================================================================================

    enum Kind { RING, CRACK, GLINT, PIERCE, SWEEP_DISK, ARC, PILLAR, WAVE }

    static final class Fx {
        Kind kind;
        Vec3d pos;
        float yaw;
        float start;
        float life;
        float size;
        float size2;
        float[] rgb;
        TatteredLancerEntity owner;

        boolean dead(float now) {
            return now - this.start > this.life;
        }
    }

    private static Fx add(Kind kind, Vec3d pos, float size, float life, float[] rgb) {
        Fx f = new Fx();
        f.kind = kind;
        f.pos = pos;
        f.size = size;
        f.life = life;
        f.rgb = rgb;
        f.start = ticks;
        if (FX.size() >= MAX_FX) {
            FX.remove(0);
        }
        FX.add(f);
        return f;
    }

    private static void ring(Vec3d groundPos, float radius, float life, float[] rgb) {
        add(Kind.RING, groundPos, radius, life, rgb).yaw = (float) (Math.random() * 360);
    }

    private static void crack(Vec3d groundPos, float radius, float life) {
        add(Kind.CRACK, groundPos, radius, life, WHITE).yaw = (float) (Math.random() * 360);
    }

    private static void glint(TatteredLancerEntity owner, float size, float life, float[] rgb) {
        Fx f = add(Kind.GLINT, owner.getPos().add(0, 1.4, 0), size, life, rgb);
        f.owner = owner;
    }

    private static void pillar(Vec3d base, float radius, float height, float life, float[] rgb) {
        add(Kind.PILLAR, base, radius, life, rgb).size2 = height;
    }

    /** Тряска: сила 0..1, гаснет с расстоянием до игрока (radius — где пропадает совсем). */
    private static void shake(Vec3d at, float power, double radius) {
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) {
            return;
        }
        double d = p.getPos().distanceTo(at);
        float k = (float) MathHelper.clamp(1.0 - d / radius, 0.0, 1.0);
        trauma = Math.min(1f, trauma + power * k);
    }

    private static void flash(Vec3d at, float power, double radius, int rgb, float decay) {
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) {
            return;
        }
        double d = p.getPos().distanceTo(at);
        float k = (float) MathHelper.clamp(1.0 - d / radius, 0.0, 1.0);
        if (power * k > flash) {
            flash = power * k;
            flashRgb = rgb;
            flashDecay = decay;
        }
    }

    /** Смещение камеры (градусы yaw, pitch) или null — вызывается из CameraMixin. */
    public static float[] shakeOffset(float tickDelta) {
        if (trauma <= 0.002f) {
            return null;
        }
        MinecraftClient c = MinecraftClient.getInstance();
        double scale = c.options.getDistortionEffectScale().getValue();
        if (scale <= 0.0) {
            return null;
        }
        float t = ticks + tickDelta;
        float s = (float) (trauma * trauma * scale);
        float yaw = 3.0f * s * (MathHelper.sin(t * 1.9f) * 0.6f + MathHelper.sin(t * 3.7f + 1.3f) * 0.4f);
        float pitch = 2.4f * s * (MathHelper.sin(t * 2.3f + 0.7f) * 0.6f + MathHelper.sin(t * 4.1f + 2.1f) * 0.4f);
        return new float[]{yaw, pitch};
    }

    // =====================================================================================
    // Тик: запуск эффектов по времени действия, частицы
    // =====================================================================================

    private static void tick(ClientWorld world) {
        ticks++;
        trauma = Math.max(0f, trauma - 0.05f);
        flash = Math.max(0f, flash - flashDecay);
        danger += (dangerTarget - danger) * 0.35f;
        dangerTarget = 0f;
        float now = ticks;
        FX.removeIf(f -> f.dead(now) || (f.owner != null && f.owner.isRemoved()));

        PlayerEntity player = MinecraftClient.getInstance().player;
        for (Entity e : world.getEntities()) {
            if (e instanceof TatteredLancerEntity l) {
                lancerTick(world, l, player);
            } else if (e instanceof FallingLanceEntity f) {
                lanceTick(world, f);
            }
        }
    }

    static Vec3d forward(float yawDeg) {
        float y = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
    }

    private static Vec3d tipOf(TatteredLancerEntity l) {
        LancerTrack tr = TRACKS.get(l);
        if (tr != null && tr.lastTip != null && l.age + 1 - tr.lastTime < 5) {
            return tr.lastTip;
        }
        return l.getPos().add(forward(l.bodyYaw).multiply(2.2)).add(0, 1.3, 0);
    }

    private static void particle(ClientWorld w, ParticleEffect p, Vec3d at, double vx, double vy, double vz) {
        w.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
    }

    private static void groundBurst(ClientWorld w, TatteredLancerEntity l, double radius, int count, double speed) {
        BlockState ground = w.getBlockState(l.getBlockPos().down());
        Random r = l.getRandom();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rr = radius * (0.3 + 0.7 * r.nextDouble());
            double x = l.getX() + Math.cos(a) * rr;
            double z = l.getZ() + Math.sin(a) * rr;
            if (!ground.isAir()) {
                w.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, l.getY() + 0.1, z,
                        Math.cos(a) * speed, 0.25 + r.nextDouble() * 0.3, Math.sin(a) * speed);
            }
            if (i % 2 == 0) {
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, l.getY() + 0.1, z, Math.cos(a) * speed * 0.6, 0.04,
                        Math.sin(a) * speed * 0.6);
            }
        }
    }

    /** Частицы, летящие к улану по спирали (сбор силы перед атакой). */
    private static void gather(ClientWorld w, TatteredLancerEntity l, double radius, int count, ParticleEffect p, double y) {
        Random r = l.getRandom();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double x = l.getX() + Math.cos(a) * radius;
            double z = l.getZ() + Math.sin(a) * radius;
            double py = l.getY() + y + (r.nextDouble() - 0.5) * 1.2;
            // скорость к центру + закрутка
            double vx = -Math.cos(a) * radius * 0.11 - Math.sin(a) * 0.08;
            double vz = -Math.sin(a) * radius * 0.11 + Math.cos(a) * 0.08;
            w.addParticle(p, x, py, z, vx, 0.0, vz);
        }
    }

    private static void spiral(ClientWorld w, TatteredLancerEntity l, int t, double radius, ParticleEffect p) {
        for (int k = 0; k < 3; k++) {
            double a = t * 0.9 + k * (Math.PI * 2 / 3);
            double y = l.getY() + (t % 10) * 0.25;
            w.addParticle(p, l.getX() + Math.cos(a) * radius, y, l.getZ() + Math.sin(a) * radius,
                    -Math.sin(a) * 0.05, 0.08, Math.cos(a) * 0.05);
        }
    }

    private static int duration(int action) {
        return switch (action) {
            case THRUST -> THRUST_TICKS;
            case CHARGE -> CHARGE_TICKS;
            case LEAP -> 80;
            case LAND -> LAND_TICKS;
            case SWEEP -> SWEEP_TICKS;
            case UPPERCUT -> UPPERCUT_TICKS;
            case BACKSTEP -> BACKSTEP_TICKS;
            case ROAR -> ROAR_TICKS;
            case GRAND -> GRAND_TICKS;
            case RAIN -> RAIN_HOVER_END;
            case RAIN_END -> 100;
            default -> 0;
        };
    }

    static boolean grandDash(int t) {
        return (t >= 16 && t < 25) || (t >= 31 && t < 40) || (t >= 46 && t < 55);
    }

    private static void lancerTick(ClientWorld w, TatteredLancerEntity l, PlayerEntity player) {
        Random r = l.getRandom();
        Vec3d p = l.getPos();
        Vec3d ground = p.add(0, 0.05, 0);
        Vec3d f = forward(l.bodyYaw);

        // --- смерть: души улетают, в конце — столб света ---
        if (l.deathTime > 0) {
            if (l.deathTime == 1) {
                pillar(p, 1.4f, 18f, 40f, SOUL);
                ring(ground, 7f, 22f, CYAN);
                ring(ground, 4f, 14f, WHITE);
                crack(ground, 3.5f, 90f);
                shake(p, 0.8f, 24);
                flash(p, 0.5f, 28, 0x9FC4FF, 0.03f);
            }
            for (int i = 0; i < 3; i++) {
                w.addParticle(i == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL, l.getParticleX(0.6),
                        l.getRandomBodyY(), l.getParticleZ(0.6), 0, 0.06 + r.nextDouble() * 0.05, 0);
            }
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, l.getParticleX(0.8), l.getY() + 0.1, l.getParticleZ(0.8), 0, 0.03, 0);
            return;
        }

        int a = l.clientAction;
        int t = l.age - l.clientActionAge;
        boolean active = a != NONE && t <= duration(a);
        if (active) {
            switch (a) {
                case THRUST -> {
                    if (t == 1) {
                        glint(l, 0.9f, 8f, WHITE);
                    }
                    if (t == 6 || t == 12 || t == 20) {
                        Vec3d tip = tipOf(l);
                        Fx pr = add(Kind.PIERCE, tip, t == 20 ? 3.2f : 2.2f, 5f, CYAN);
                        pr.yaw = l.bodyYaw;
                        for (int i = 0; i < 8; i++) {
                            particle(w, ParticleTypes.ELECTRIC_SPARK, tip, f.x * 0.6 + (r.nextDouble() - 0.5) * 0.3,
                                    (r.nextDouble() - 0.5) * 0.3, f.z * 0.6 + (r.nextDouble() - 0.5) * 0.3);
                        }
                        shake(p, t == 20 ? 0.18f : 0.1f, 8);
                    }
                }
                case CHARGE -> {
                    if (t == 2) {
                        glint(l, 1.4f, 10f, WHITE);
                    }
                    if (t < 11) {
                        gather(w, l, 2.4, 2, ParticleTypes.SOUL_FIRE_FLAME, 1.0);
                        w.addParticle(ParticleTypes.CLOUD, l.getX() - f.x * 0.5, l.getY() + 0.1, l.getZ() - f.z * 0.5,
                                -f.x * 0.15, 0.02, -f.z * 0.15);
                    }
                    if (t == 11) {
                        ring(ground, 2.6f, 8f, CYAN);
                        shake(p, 0.2f, 10);
                    }
                    if (t >= 11 && t < 24) {
                        dashParticles(w, l, f, DUST_CYAN);
                    }
                    if (t == 24) {
                        for (int i = 0; i < 12; i++) {
                            w.addParticle(ParticleTypes.ELECTRIC_SPARK, l.getX() + f.x * 0.6, l.getY() + 0.15,
                                    l.getZ() + f.z * 0.6, f.x * 0.3 + (r.nextDouble() - 0.5) * 0.4, 0.2,
                                    f.z * 0.3 + (r.nextDouble() - 0.5) * 0.4);
                        }
                    }
                }
                case LEAP -> {
                    if (t == 2) {
                        glint(l, 1.2f, 9f, WHITE);
                    }
                    if (t < 10) {
                        gather(w, l, 1.8, 1, ParticleTypes.SOUL_FIRE_FLAME, 0.3);
                    }
                    if (t == 10) {
                        ring(ground, 2.4f, 9f, CYAN);
                        pillar(p, 0.5f, 4f, 7f, CYAN);
                        shake(p, 0.15f, 10);
                    }
                    if (t > 10 && !l.isOnGround()) {
                        w.addParticle(DUST_FADE, l.getParticleX(0.5), l.getBodyY(0.5), l.getParticleZ(0.5), 0, 0, 0);
                    }
                }
                case LAND -> {
                    if (t == 1) {
                        if (l.clientPrevAction == RAIN_END) {
                            ring(ground, 3.5f, 12f, CYAN);
                            crack(ground, 2.4f, 45f);
                            groundBurst(w, l, 2.0, 14, 0.15);
                            shake(p, 0.35f, 16);
                        } else {
                            ring(ground, 4.6f, 12f, WHITE);
                            ring(ground, 3.2f, 16f, CYAN);
                            crack(ground, 3.2f, 70f);
                            pillar(p, 0.9f, 5f, 9f, CYAN);
                            groundBurst(w, l, 3.0, 28, 0.25);
                            shake(p, 0.7f, 18);
                        }
                    }
                }
                case SWEEP -> {
                    if (t == 6) {
                        glint(l, 1.0f, 7f, WHITE);
                    }
                    if (t == 12) {
                        Fx d = add(Kind.SWEEP_DISK, p.add(0, 1.1, 0), 3.8f, 7f, CYAN);
                        d.yaw = l.bodyYaw;
                        ring(ground, 3.8f, 10f, CYAN);
                        for (int i = 0; i < 16; i++) {
                            double ang = i / 16.0 * Math.PI * 2;
                            w.addParticle(ParticleTypes.ELECTRIC_SPARK, l.getX() + Math.cos(ang) * 3.2, l.getY() + 1.1,
                                    l.getZ() + Math.sin(ang) * 3.2, -Math.sin(ang) * 0.3, 0.02, Math.cos(ang) * 0.3);
                        }
                        shake(p, 0.25f, 10);
                    }
                }
                case UPPERCUT -> {
                    if (t == 2) {
                        glint(l, 1.1f, 8f, WHITE);
                    }
                    if (t >= 1 && t <= 9) {
                        spiral(w, l, t, 1.2, ParticleTypes.SOUL_FIRE_FLAME);
                    }
                    if (t == 12) {
                        Fx arc = add(Kind.ARC, p.add(f.multiply(1.6)), 2.6f, 8f, CYAN);
                        arc.yaw = l.bodyYaw;
                        pillar(p.add(f.multiply(1.8)), 0.7f, 5f, 8f, WHITE);
                        shake(p, 0.3f, 10);
                    }
                }
                case BACKSTEP -> {
                    if (t == 1) {
                        ring(ground, 1.8f, 7f, CYAN);
                        for (int i = 0; i < 10; i++) {
                            w.addParticle(ParticleTypes.SOUL, l.getParticleX(0.6), l.getBodyY(0.3 + r.nextDouble() * 0.5),
                                    l.getParticleZ(0.6), f.x * 0.1, 0.03, f.z * 0.1);
                        }
                    }
                }
                case ROAR -> {
                    if (t < 19) {
                        gather(w, l, 3.2, 3, ParticleTypes.SOUL_FIRE_FLAME, 1.2);
                        if (t % 4 == 0) {
                            ring(ground, 3.0f - t * 0.1f, 6f, SOUL);
                        }
                    }
                    if (t == 19) {
                        pillar(p, 1.8f, 16f, 30f, SOUL);
                        pillar(p, 0.7f, 18f, 24f, WHITE);
                        ring(ground, 6.5f, 16f, WHITE);
                        ring(ground, 10f, 26f, SOUL);
                        crack(ground, 3.6f, 100f);
                        for (int i = 0; i < 40; i++) {
                            double ang = r.nextDouble() * Math.PI * 2;
                            w.addParticle(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL,
                                    l.getX(), l.getBodyY(0.5), l.getZ(), Math.cos(ang) * 0.35, 0.05 + r.nextDouble() * 0.2,
                                    Math.sin(ang) * 0.35);
                        }
                        shake(p, 1.0f, 26);
                        flash(p, 0.55f, 32, 0x6FA8FF, 0.035f);
                    }
                }
                case GRAND -> grandTick(w, l, player, t, p, ground, f);
                case RAIN -> rainTick(w, l, player, t, p, ground);
                default -> {
                }
            }
        }

        // --- 2‑я фаза: синий огонь из глаз, тлеющие плечи, огненные следы шагов ---
        if (l.isPhase2Synced()) {
            LancerTrack tr = TRACKS.get(l);
            if (tr != null && tr.eyeL != null && l.age + 1 - tr.lastTime < 5) {
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, tr.eyeL.x, tr.eyeL.y, tr.eyeL.z, -f.x * 0.03, 0.02, -f.z * 0.03);
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, tr.eyeR.x, tr.eyeR.y, tr.eyeR.z, -f.x * 0.03, 0.02, -f.z * 0.03);
            }
            if (l.age % 5 == 0) {
                w.addParticle(ParticleTypes.SOUL, l.getParticleX(0.5), l.getBodyY(0.75 + r.nextDouble() * 0.15),
                        l.getParticleZ(0.5), 0, 0.03, 0);
            }
            double moved = l.getX() - l.prevX;
            double movedZ = l.getZ() - l.prevZ;
            if (l.isOnGround() && moved * moved + movedZ * movedZ > 0.004 && l.age % 3 == 0) {
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, l.getParticleX(0.4), l.getY() + 0.05, l.getParticleZ(0.4),
                        0, 0.01, 0);
            }
        }
    }

    private static void dashParticles(ClientWorld w, TatteredLancerEntity l, Vec3d f, ParticleEffect dust) {
        Random r = l.getRandom();
        BlockState ground = w.getBlockState(l.getBlockPos().down());
        for (int i = 0; i < 2; i++) {
            double sx = (r.nextDouble() - 0.5) * 0.8;
            double sz = (r.nextDouble() - 0.5) * 0.8;
            if (!ground.isAir()) {
                w.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), l.getX() + sx, l.getY() + 0.1,
                        l.getZ() + sz, -f.x * 0.3 + sx * 0.3, 0.25, -f.z * 0.3 + sz * 0.3);
            }
        }
        w.addParticle(dust, l.getParticleX(0.6), l.getBodyY(0.3 + r.nextDouble() * 0.6), l.getParticleZ(0.6), 0, 0, 0);
        w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, l.getX() - f.x * 0.6, l.getBodyY(0.5), l.getZ() - f.z * 0.6,
                -f.x * 0.05, 0.02, -f.z * 0.05);
    }

    /** Окна прицеливания «тройного тарана»: [начало, конец, длина стрелки]. */
    private static final int[][] GRAND_AIM = {{1, 16, 9}, {26, 31, 9}, {41, 46, 9}, {56, 68, 12}};

    private static void grandTick(ClientWorld w, TatteredLancerEntity l, PlayerEntity player, int t, Vec3d p,
                                  Vec3d ground, Vec3d f) {
        Random r = l.getRandom();
        if (t == 1) {
            glint(l, 1.8f, 14f, CRIMSON);
            flash(p, 0.25f, 20, 0xFF3048, 0.05f);
            for (int i = 0; i < 20; i++) {
                w.addParticle(DUST_CRIMSON, l.getParticleX(1.2), l.getRandomBodyY(), l.getParticleZ(1.2), 0, 0.05, 0);
            }
        }
        if (t == 56) {
            glint(l, 2.0f, 12f, CRIMSON);
        }
        if (t == 16 || t == 31 || t == 46) {
            ring(ground, 2.6f, 8f, CRIMSON);
            shake(p, 0.3f, 12);
        }
        if (grandDash(t)) {
            dashParticles(w, l, f, DUST_CRIMSON);
        } else if (t < 68) {
            gather(w, l, 2.0, 1, DUST_CRIMSON, 1.0);
        }
        if (t == 68) {
            Fx wave = add(Kind.WAVE, ground, 12f, 16f, CRIMSON);
            wave.yaw = l.bodyYaw;
            ring(ground, 3.5f, 10f, WHITE);
            crack(ground.add(f.multiply(1.5)), 2.8f, 70f);
            for (int d = 3; d <= 9; d += 3) {
                w.addParticle(ParticleTypes.SONIC_BOOM, l.getX() + f.x * d, l.getY() + 1.0, l.getZ() + f.z * d, 0, 0, 0);
            }
            shake(p, 1.0f, 24);
            flash(p, 0.35f, 18, 0xFF5A6A, 0.05f);
        }
        // красная кромка экрана, если игрок на линии будущего удара
        if (player != null) {
            for (int[] win : GRAND_AIM) {
                if (t >= win[0] && t < win[1] && onLine(l, player, win[2], 1.8)) {
                    dangerTarget = Math.max(dangerTarget, 0.4f + 0.6f * (t - win[0]) / (float) (win[1] - win[0]));
                }
            }
        }
    }

    private static boolean onLine(TatteredLancerEntity l, PlayerEntity p, double length, double halfWidth) {
        Vec3d f = forward(l.bodyYaw);
        Vec3d to = new Vec3d(p.getX() - l.getX(), 0, p.getZ() - l.getZ());
        double along = to.dotProduct(f);
        double side = Math.abs(to.x * f.z - to.z * f.x);
        return along > 0 && along < length && side < halfWidth && Math.abs(p.getY() - l.getY()) < 3;
    }

    private static void rainTick(ClientWorld w, TatteredLancerEntity l, PlayerEntity player, int t, Vec3d p, Vec3d ground) {
        Random r = l.getRandom();
        if (t == 1) {
            ring(ground, 2.8f, 10f, CYAN);
            pillar(p, 0.6f, 7f, 12f, CYAN);
        }
        if (t < RAIN_RISE) {
            spiral(w, l, t, 1.0 + t * 0.03, ParticleTypes.SOUL_FIRE_FLAME);
        }
        if (t == RAIN_RISE) {
            ring(l.clientZone.add(0, 0.06, 0), (float) RAIN_RADIUS, 22f, CYAN);
        }
        if (t == RAIN_START - 10) {
            boolean inside = player != null
                    && player.squaredDistanceTo(l.clientZone.x, player.getY(), l.clientZone.z) < RAIN_RADIUS * RAIN_RADIUS;
            if (inside) {
                flash(player.getPos(), 0.22f, 100, 0x6FA8FF, 0.04f);
            }
        }
        // души кружат вокруг парящего улана
        if (t > RAIN_RISE) {
            double ang = l.age * 0.35;
            for (int k = 0; k < 2; k++) {
                double aa = ang + k * Math.PI;
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, l.getX() + Math.cos(aa) * 1.3, l.getBodyY(0.4),
                        l.getZ() + Math.sin(aa) * 1.3, 0, -0.02, 0);
            }
            if (t >= RAIN_START && t < RAIN_STOP && t % 2 == 0) {
                w.addParticle(ParticleTypes.END_ROD, l.getX(), l.getBodyY(1.0) + 0.3, l.getZ(),
                        (r.nextDouble() - 0.5) * 0.05, 0.4, (r.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    private static void lanceTick(ClientWorld w, FallingLanceEntity f) {
        if (Double.isNaN(f.clientGroundY)) {
            Vec3d from = f.getPos();
            Vec3d to = from.add(0, -48, 0);
            BlockHitResult hit = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, f));
            f.clientGroundY = hit.getType() == HitResult.Type.MISS ? from.y - 48 : hit.getPos().y;
        }
        if (f.isStuck() && !f.clientImpactShown) {
            f.clientImpactShown = true;
            Vec3d g = new Vec3d(f.getX(), f.getY() + 0.05, f.getZ());
            ring(g, 1.7f, 8f, CYAN);
            crack(g, 1.0f, 30f);
            add(Kind.GLINT, g.add(0, 0.4, 0), 0.9f, 5f, WHITE);
            shake(g, 0.2f, 5);
            for (int i = 0; i < 6; i++) {
                w.addParticle(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 0.2, g.z,
                        (w.random.nextDouble() - 0.5) * 0.5, 0.2, (w.random.nextDouble() - 0.5) * 0.5);
            }
        }
    }

    // =====================================================================================
    // Отрисовка
    // =====================================================================================

    private static final int LIGHT = 0xF000F0;

    /** Отрисовщик кадра: позиции уже относительно камеры. */
    private static final class Draw {
        final MatrixStack matrices;
        final VertexConsumerProvider consumers;
        final Vec3d cam;
        final Vector3f right;
        final Vector3f up;

        Draw(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, Quaternionf rot) {
            this.matrices = matrices;
            this.consumers = consumers;
            this.cam = cam;
            this.right = rot.transform(new Vector3f(1, 0, 0));
            this.up = rot.transform(new Vector3f(0, 1, 0));
        }

        VertexConsumer vc(Identifier texture) {
            return this.consumers.getBuffer(RenderLayer.getEyes(texture));
        }

        float x(Vec3d p) {
            return (float) (p.x - this.cam.x);
        }

        float y(Vec3d p) {
            return (float) (p.y - this.cam.y);
        }

        float z(Vec3d p) {
            return (float) (p.z - this.cam.z);
        }

        /** Четырёхугольник с двух сторон; точки — относительно камеры. */
        void quad(Identifier texture, float[] p0, float[] p1, float[] p2, float[] p3,
                  float u0, float v0, float u1, float v1, float[] rgb, float k) {
            if (k <= 0.004f) {
                return;
            }
            VertexConsumer vc = this.vc(texture);
            MatrixStack.Entry e = this.matrices.peek();
            Matrix4f m = e.getPositionMatrix();
            Matrix3f n = e.getNormalMatrix();
            int r = (int) (MathHelper.clamp(rgb[0] * k, 0f, 1f) * 255);
            int g = (int) (MathHelper.clamp(rgb[1] * k, 0f, 1f) * 255);
            int b = (int) (MathHelper.clamp(rgb[2] * k, 0f, 1f) * 255);
            v(vc, m, n, p0, u0, v0, r, g, b);
            v(vc, m, n, p1, u0, v1, r, g, b);
            v(vc, m, n, p2, u1, v1, r, g, b);
            v(vc, m, n, p3, u1, v0, r, g, b);
            v(vc, m, n, p3, u1, v0, r, g, b);
            v(vc, m, n, p2, u1, v1, r, g, b);
            v(vc, m, n, p1, u0, v1, r, g, b);
            v(vc, m, n, p0, u0, v0, r, g, b);
        }

        private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float u, float vv, int r, int g, int b) {
            vc.vertex(m, p[0], p[1], p[2]).color(r, g, b, 255).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV)
                    .light(LIGHT).normal(n, 0, 1, 0).next();
        }

        /** Плоский квадрат на земле (или в небе): центр, полуразмер, поворот (градусы). */
        void flat(Identifier texture, Vec3d c, float half, float rotDeg, float[] rgb, float k) {
            float a = rotDeg * MathHelper.RADIANS_PER_DEGREE;
            float cs = MathHelper.cos(a) * half;
            float sn = MathHelper.sin(a) * half;
            float x = this.x(c);
            float y = this.y(c);
            float z = this.z(c);
            this.quad(texture,
                    new float[]{x - cs + sn, y, z - sn - cs},
                    new float[]{x - cs - sn, y, z - sn + cs},
                    new float[]{x + cs - sn, y, z + sn + cs},
                    new float[]{x + cs + sn, y, z + sn - cs},
                    0, 0, 1, 1, rgb, k);
        }

        /** Полоса на земле от from вдоль dir длиной len, полуширина hw; v — от vStart до vEnd вдоль. */
        void strip(Identifier texture, Vec3d from, Vec3d dir, float len, float hw, float vStart, float vEnd,
                   float[] rgb, float k) {
            Vec3d side = new Vec3d(-dir.z, 0, dir.x).multiply(hw);
            Vec3d to = from.add(dir.multiply(len));
            this.quad(texture,
                    this.rel(from.subtract(side)), this.rel(to.subtract(side)), this.rel(to.add(side)), this.rel(from.add(side)),
                    0, vStart, 1, vEnd, rgb, k);
        }

        float[] rel(Vec3d p) {
            return new float[]{this.x(p), this.y(p), this.z(p)};
        }

        /** Квадрат, повёрнутый к камере. */
        void billboard(Identifier texture, Vec3d c, float half, float rollDeg, float[] rgb, float k) {
            float a = rollDeg * MathHelper.RADIANS_PER_DEGREE;
            float cs = MathHelper.cos(a);
            float sn = MathHelper.sin(a);
            Vector3f rx = new Vector3f(this.right).mul(cs).add(new Vector3f(this.up).mul(sn)).mul(half);
            Vector3f ry = new Vector3f(this.up).mul(cs).sub(new Vector3f(this.right).mul(sn)).mul(half);
            float x = this.x(c);
            float y = this.y(c);
            float z = this.z(c);
            this.quad(texture,
                    new float[]{x - rx.x - ry.x, y - rx.y - ry.y, z - rx.z - ry.z},
                    new float[]{x - rx.x + ry.x, y - rx.y + ry.y, z - rx.z + ry.z},
                    new float[]{x + rx.x + ry.x, y + rx.y + ry.y, z + rx.z + ry.z},
                    new float[]{x + rx.x - ry.x, y + rx.y - ry.y, z + rx.z - ry.z},
                    0, 0, 1, 1, rgb, k);
        }

        /** Лента от a до b, повёрнутая к камере вокруг своей оси (луч, нить, столб). u — поперёк, v — вдоль. */
        void segment(Identifier texture, Vec3d a, Vec3d b, float wa, float wb, float va, float vb, float[] rgb, float k) {
            float[] pa = this.rel(a);
            float[] pb = this.rel(b);
            Vector3f dir = new Vector3f(pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]);
            Vector3f mid = new Vector3f((pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2);
            Vector3f side = dir.cross(mid, new Vector3f());
            if (side.lengthSquared() < 1e-8f) {
                side.set(this.right);
            }
            side.normalize();
            this.quad(texture,
                    new float[]{pa[0] - side.x * wa, pa[1] - side.y * wa, pa[2] - side.z * wa},
                    new float[]{pb[0] - side.x * wb, pb[1] - side.y * wb, pb[2] - side.z * wb},
                    new float[]{pb[0] + side.x * wb, pb[1] + side.y * wb, pb[2] + side.z * wb},
                    new float[]{pa[0] + side.x * wa, pa[1] + side.y * wa, pa[2] + side.z * wa},
                    0, va, 1, vb, rgb, k);
        }
    }

    private static float easeOut(float p) {
        float q = 1f - MathHelper.clamp(p, 0f, 1f);
        return 1f - q * q * q;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.consumers() == null) {
            return;
        }
        float tickDelta = context.tickDelta();
        float now = ticks + tickDelta;
        Draw d = new Draw(context.matrixStack(), context.consumers(), context.camera().getPos(),
                context.camera().getRotation());

        for (Fx f : FX) {
            drawFx(d, f, now);
        }
        for (Entity e : client.world.getEntities()) {
            if (e instanceof TatteredLancerEntity l) {
                drawLancer(d, l, tickDelta, now, client);
            } else if (e instanceof FallingLanceEntity f) {
                drawFallingLance(d, f, tickDelta, now);
            }
        }
    }

    private static void drawFx(Draw d, Fx f, float now) {
        float age = now - f.start;
        float p = MathHelper.clamp(age / f.life, 0f, 1f);
        switch (f.kind) {
            case RING -> {
                float r = f.size * (0.2f + 0.8f * easeOut(p));
                float k = (float) Math.pow(1f - p, 1.4);
                d.flat(RING, f.pos, r, f.yaw + age * 2f, f.rgb, k);
            }
            case CRACK -> {
                float k = p < 0.08f ? p / 0.08f : (float) Math.pow(1f - p, 0.8) * 0.9f;
                d.flat(CRACK, f.pos.add(0, 0.01, 0), f.size, f.yaw, f.rgb, k);
            }
            case GLINT -> {
                Vec3d at = f.owner != null && !f.owner.isRemoved() ? tipOf(f.owner) : f.pos;
                float s = MathHelper.sin(p * MathHelper.PI);
                d.billboard(GLINT, at, f.size * (0.4f + 0.6f * s), age * 9f, f.rgb, s * 1.2f);
                d.billboard(GLINT, at, f.size * 0.6f * s, 45f - age * 5f, WHITE, s * 0.7f);
            }
            case PIERCE -> {
                Vec3d dir = forward(f.yaw);
                float len = f.size * (0.5f + 0.5f * easeOut(p));
                float k = (1f - p) * (1f - p);
                Vec3d a = f.pos.subtract(dir.multiply(0.3));
                Vec3d b = f.pos.add(dir.multiply(len));
                d.segment(BEAM, a, b, 0.35f * (1f - p) + 0.08f, 0.02f, 0f, 1f, f.rgb, k * 1.3f);
                d.billboard(GLINT, f.pos, 0.6f * (1f - p), age * 20f, WHITE, k);
            }
            case SWEEP_DISK -> {
                float r = f.size * (0.8f + 0.2f * easeOut(p));
                float k = 1f - p;
                float rot = f.yaw + 180f + easeOut(p) * 220f;
                d.flat(SLASH, f.pos, r, rot, f.rgb, k * 1.2f);
                d.flat(SLASH, f.pos.add(0, 0.02, 0), r * 0.92f, rot + 180f, WHITE, k * 0.6f);
            }
            case ARC -> {
                // вертикальный серп перед уланом, поднимается вверх
                Vec3d fw = forward(f.yaw);
                Vec3d side = new Vec3d(-fw.z, 0, fw.x).multiply(f.size);
                float rise = 0.6f + 1.6f * easeOut(p);
                Vec3d c = f.pos.add(0, rise, 0);
                Vec3d up = new Vec3d(0, f.size, 0);
                float k = 1f - p;
                d.quad(SLASH, d.rel(c.subtract(side).add(up)), d.rel(c.subtract(side).subtract(up)),
                        d.rel(c.add(side).subtract(up)), d.rel(c.add(side).add(up)), 0, 0, 1, 1, f.rgb, k * 1.3f);
            }
            case PILLAR -> {
                float k = p < 0.12f ? p / 0.12f : (float) Math.pow(1f - p, 1.3);
                float h = f.size2 * easeOut(Math.min(1f, p * 3f));
                float w = f.size * (1f - 0.5f * p);
                d.segment(BEAM, f.pos, f.pos.add(0, h, 0), w, w * 0.6f, 1f, 0f, f.rgb, k);
                d.segment(BEAM, f.pos, f.pos.add(0, h * 0.8, 0), w * 0.35f, w * 0.2f, 1f, 0f, WHITE, k * 0.8f);
            }
            case WAVE -> {
                Vec3d dir = forward(f.yaw);
                float front = f.size * easeOut(Math.min(1f, age / 6f));
                float k = (float) Math.pow(1f - p, 1.2);
                d.strip(BEAM, f.pos.add(dir.multiply(0.5)), dir, front, 1.7f, 0f, 1f, f.rgb, k);
                d.strip(BEAM, f.pos.add(0, 0.01, 0).add(dir.multiply(0.5)), dir, front, 0.6f, 0f, 1f, WHITE, k * 0.8f);
                // стена‑лезвие на фронте волны
                Vec3d side = new Vec3d(-dir.z, 0, dir.x).multiply(1.9);
                Vec3d c = f.pos.add(dir.multiply(0.5 + front));
                Vec3d top = new Vec3d(0, 2.4 * (1f - p * 0.6f), 0);
                float kw = k * (age < 7f ? 1f : 0.5f);
                d.quad(WALL, d.rel(c.subtract(side).add(top)), d.rel(c.subtract(side)), d.rel(c.add(side)),
                        d.rel(c.add(side).add(top)), 0, 0, 1, 1, f.rgb, kw * 1.2f);
            }
        }
    }

    private static void drawLancer(Draw d, TatteredLancerEntity l, float tickDelta, float now, MinecraftClient client) {
        if (l.deathTime > 0) {
            return;
        }
        Vec3d pos = l.getLerpedPos(tickDelta);
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, l.prevBodyYaw, l.bodyYaw);
        Vec3d f = forward(bodyYaw);
        int a = l.clientAction;
        float t = l.age + tickDelta - l.clientActionAge;
        boolean active = a != NONE && t <= duration(a) + 3;
        boolean phase2 = l.isPhase2Synced();

        // --- след острия копья ---
        LancerTrack tr = TRACKS.get(l);
        if (tr != null && tr.count > 1) {
            float base = active ? 1f : (phase2 ? 0.35f : 0f);
            if (base > 0f) {
                float[] rgb = a == GRAND && active ? CRIMSON : CYAN;
                float latest = tr.time[tr.head];
                for (int i = 0; i < tr.count - 1; i++) {
                    int k0 = tr.index(i);
                    int k1 = tr.index(i + 1);
                    float age = latest - tr.time[k1];
                    if (age > 6f) {
                        break;
                    }
                    float dt = Math.max(0.05f, tr.time[k0] - tr.time[k1]);
                    double speed = tr.tip[k0].distanceTo(tr.tip[k1]) / dt;     // блоков за тик
                    float sp = (float) MathHelper.clamp((speed - 0.3) / 0.7, 0.0, 1.0);
                    float fade = 1f - age / 6f;
                    float k = base * sp * fade * fade;
                    if (k <= 0.01f) {
                        continue;
                    }
                    float u0 = (latest - tr.time[k0]) / 6f;
                    float u1 = age / 6f;
                    d.quad(TRAIL, d.rel(tr.inner[k0]), d.rel(tr.tip[k0]), d.rel(tr.tip[k1]), d.rel(tr.inner[k1]),
                            u0, 0f, u1, 1f, rgb, k * 1.3f);
                }
            }
        }

        // --- 2‑я фаза: руническая печать под ногами ---
        if (phase2 && a != RAIN && a != RAIN_END) {
            float grow = a == ROAR && t < 30 ? MathHelper.clamp((t - 19f) / 8f, 0f, 1f) : 1f;
            if (grow > 0f) {
                float pulse = 0.35f + 0.12f * MathHelper.sin(now * 0.12f);
                d.flat(RUNE, pos.add(0, 0.04, 0), 1.8f * grow, now * 1.5f, SOUL, pulse * grow);
            }
        }

        // --- стрелки: куда сейчас ударит таран ---
        if (a == CHARGE && t < 11) {
            float k = 0.2f + 0.6f * t / 11f;
            d.strip(ARROW, pos.add(0, 0.05, 0).add(f.multiply(0.6)), f, 9f, 0.8f, -now * 0.15f, 9f / 4f - now * 0.15f, CYAN, k);
        }
        if (a == GRAND) {
            for (int[] win : GRAND_AIM) {
                if (t >= win[0] && t < win[1]) {
                    float k = 0.3f + 0.7f * (t - win[0]) / (float) (win[1] - win[0]);
                    float len = win[2];
                    float hw = len > 10 ? 1.6f : 0.9f;
                    d.strip(ARROW, pos.add(0, 0.05, 0).add(f.multiply(0.6)), f, len, hw, -now * 0.25f,
                            len / 4f - now * 0.25f, CRIMSON, k);
                }
            }
        }

        // --- метка места приземления прыжка ---
        if (a == LEAP && t > LEAP_WINDUP + 1 && !l.isOnGround()) {
            Vec3d land = predictLanding(l);
            if (land != null) {
                float pulse = 0.65f + 0.35f * MathHelper.sin(now * 0.8f);
                d.flat(MARKER, land.add(0, 0.06, 0), 3.2f, now * 3f, CYAN, pulse);
                d.flat(RING, land.add(0, 0.07, 0), 3.2f * (1f - ((now * 0.08f) % 1f)), 0f, WHITE, 0.4f);
            }
        }
        if (a == RAIN_END && !l.isOnGround()) {
            d.flat(MARKER, new Vec3d(pos.x, l.clientZone.y + 0.06, pos.z), 2.2f, now * 2f, CYAN, 0.6f);
        }

        // --- ливень копий: печать в небе, луч, стена‑купол ---
        if (l.clientRainAge >= 0) {
            float rt = l.age + tickDelta - l.clientRainAge;
            Vec3d zone = l.clientZone;
            if (rt >= 30 && rt <= RAIN_STOP + 15) {
                float k = Math.min(MathHelper.clamp((rt - 30f) / 30f, 0f, 1f), MathHelper.clamp((RAIN_STOP + 15 - rt) / 15f, 0f, 1f));
                boolean raining = rt >= RAIN_START && rt < RAIN_STOP;
                float boost = raining ? 1f + 0.15f * MathHelper.sin(now * 0.9f) : 0.7f;
                Vec3d sky = zone.add(0, 15.0, 0);
                d.flat(RUNE, sky, (float) RAIN_RADIUS * 0.85f, now * 0.8f, CYAN, k * boost);
                d.flat(RUNE, sky.add(0, -0.3, 0), 5f, -now * 1.6f, WHITE, k * boost * 0.6f);
                if (rt >= 50) {
                    float kb = k * MathHelper.clamp((rt - 50f) / 10f, 0f, 1f);
                    d.segment(BEAM, pos.add(0, 1.0, 0), sky, 0.35f, 0.9f, 1f, 0f, SOUL, kb * 0.8f);
                }
            }
            float zk = l.clientZoneAlpha(tickDelta);
            if (zk > 0f) {
                drawWall(d, zone, (float) RAIN_RADIUS, 2.8f + (rt >= RAIN_START ? 0.6f : 0f), now, zk * 0.55f);
            }
        }

        // --- нить дуэлянта ---
        int id = l.getDuelistId();
        if (id != -1 && client.world != null) {
            Entity target = client.world.getEntityById(id);
            if (target instanceof PlayerEntity pl && pl.isAlive() && pl.squaredDistanceTo(l) < 48 * 48) {
                Vec3d from = pos.add(0, 1.5, 0);
                Vec3d to = pl.getLerpedPos(tickDelta).add(0, pl.getHeight() * 0.55, 0);
                if (pl == client.player && client.options.getPerspective().isFirstPerson()) {
                    to = to.add(0, -0.35, 0);
                }
                drawThread(d, from, to, now);
            }
        }
    }

    /** Простая баллистика по движению улана: где он коснётся земли (высота начала прыжка). */
    private static Vec3d predictLanding(TatteredLancerEntity l) {
        double x = l.getX();
        double y = l.getY();
        double z = l.getZ();
        double vx = l.getX() - l.prevX;
        double vy = l.getY() - l.prevY;
        double vz = l.getZ() - l.prevZ;
        double groundY = l.clientLeapY;
        for (int i = 0; i < 80; i++) {
            vy = (vy - 0.08) * 0.98;
            x += vx;
            y += vy;
            z += vz;
            if (vy < 0 && y <= groundY) {
                return new Vec3d(x, groundY, z);
            }
        }
        return null;
    }

    private static void drawWall(Draw d, Vec3d c, float radius, float height, float now, float k) {
        int n = 48;
        float scroll = now * 0.01f;
        for (int i = 0; i < n; i++) {
            double a0 = i / (double) n * Math.PI * 2;
            double a1 = (i + 1) / (double) n * Math.PI * 2;
            Vec3d b0 = c.add(Math.cos(a0) * radius, 0.02, Math.sin(a0) * radius);
            Vec3d b1 = c.add(Math.cos(a1) * radius, 0.02, Math.sin(a1) * radius);
            float u0 = (i / (float) n) * 6f + scroll;
            float u1 = ((i + 1) / (float) n) * 6f + scroll;
            d.quad(WALL, d.rel(b0.add(0, height, 0)), d.rel(b0), d.rel(b1), d.rel(b1.add(0, height, 0)),
                    u0, 0f, u1, 1f, CYAN, k);
        }
    }

    private static void drawThread(Draw d, Vec3d from, Vec3d to, float now) {
        int n = 20;
        double dist = from.distanceTo(to);
        double sag = Math.min(1.2, 0.12 * Math.sqrt(dist) + 0.05 * dist);
        Vec3d dir = to.subtract(from);
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        side = side.lengthSquared() > 1e-6 ? side.normalize() : new Vec3d(1, 0, 0);
        Vec3d prev = from;
        for (int i = 1; i <= n; i++) {
            double s = i / (double) n;
            double wave = Math.sin(s * Math.PI * 3 - now * 0.25) * 0.07 * Math.sin(s * Math.PI);
            Vec3d p = from.add(dir.multiply(s)).add(0, -Math.sin(s * Math.PI) * sag, 0).add(side.multiply(wave));
            float pulse = 0.35f + 0.3f * MathHelper.sin((float) (s * 14 - now * 0.45f));
            float[] rgb = i % 5 == 0 ? CYAN : SOUL;
            d.segment(LINE, prev, p, 0.05f, 0.05f, 0f, 1f, rgb, pulse);
            prev = p;
        }
    }

    private static void drawFallingLance(Draw d, FallingLanceEntity f, float tickDelta, float now) {
        Vec3d p = f.getLerpedPos(tickDelta);
        if (f.isStuck()) {
            return;
        }
        // светящийся след над копьём
        d.segment(BEAM, p.add(0, 0.6, 0), p.add(0, 4.0, 0), 0.22f, 0.04f, 0f, 1f, CYAN, 0.7f);
        // метка на земле: ярче, чем ближе копьё
        if (!Double.isNaN(f.clientGroundY)) {
            double h = p.y - f.clientGroundY;
            float k = (float) MathHelper.clamp(1.0 - h / 18.0, 0.15, 1.0);
            Vec3d g = new Vec3d(p.x, f.clientGroundY + 0.06, p.z);
            d.flat(MARKER, g, 1.3f, now * 4f, CYAN, k * 0.9f);
            d.flat(RING, g.add(0, 0.01, 0), (float) (1.3 * MathHelper.clamp(h / 18.0, 0.05, 1.0)), 0f, WHITE, k * 0.5f);
        }
    }

    // =====================================================================================
    // Экран: вспышка и красная кромка «ты на линии тарана»
    // =====================================================================================

    private static void hud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        if (flash > 0.01f) {
            int a = (int) (MathHelper.clamp(flash, 0f, 1f) * 150);
            context.fill(0, 0, w, h, (a << 24) | (flashRgb & 0xFFFFFF));
        }
        if (danger > 0.02f) {
            float pulse = 0.75f + 0.25f * MathHelper.sin((ticks + tickDelta) * 0.9f);
            int band = Math.max(8, Math.min(w, h) / 14);
            for (int i = 0; i < band; i++) {
                float k = 1f - i / (float) band;
                int a = (int) (110 * k * k * danger * pulse);
                if (a <= 0) {
                    continue;
                }
                int color = (a << 24) | 0xD01830;
                context.fill(i, i, w - i, i + 1, color);
                context.fill(i, h - i - 1, w - i, h - i, color);
                context.fill(i, i + 1, i + 1, h - i - 1, color);
                context.fill(w - i - 1, i + 1, w - i, h - i - 1, color);
            }
        }
    }

    /** Для отладки и других мест: удалить всё (при выходе из мира эффекты гаснут сами). */
    public static void clear() {
        FX.clear();
        trauma = 0f;
        flash = 0f;
        danger = 0f;
        Iterator<LancerTrack> it = TRACKS.values().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
