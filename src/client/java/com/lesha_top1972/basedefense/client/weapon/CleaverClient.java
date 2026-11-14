package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.client.weapon.fp.ArmRig;
import com.lesha_top1972.basedefense.client.weapon.fp.FpAnim;
import com.lesha_top1972.basedefense.sound.ModSounds;
import com.lesha_top1972.basedefense.weapon.CleaverCombat;
import com.lesha_top1972.basedefense.weapon.CleaverItem;
import com.lesha_top1972.basedefense.weapon.CleaverTimings;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Тесак Громового тигра на клиенте (устроен как катана — KatanaClient).
 * <p>
 * 1‑е лицо (свой игрок): клипы assets/basedefense/fpanim/cleaver.json (tools/cleaver_gen.py): стойка, 3 обычных удара,
 * 3 взрывных, перезарядка, зарядки и две особые атаки. Плавные переходы, события клипа (звуки, «стоп‑кадр»),
 * наклон/крен камеры и обзор — из клипа.
 * <p>
 * Ввод: ЛКМ клик — обычный удар серии, ПКМ клик — взрывной удар серии (патрон), зажать ЛКМ — «Тигриный выпад»,
 * зажать ПКМ — «Тесак в узел», клавиша (V) — перезарядка. Шаги, рывок и подскок своего игрока — здесь.
 * <p>
 * 3‑е лицо (все игроки): позы CleaverKeys — тесак лежит на правом плече (покой; после удара возвращается туда же),
 * удары, зарядки, особые атаки, перезарядка; бёдра опускаются при широкой стойке. Руки — суставные (fp/ArmRig):
 * ванильные прячутся, вместо них рисуются гнутые в локте и запястье, кисть — точно на рукояти.
 */
public final class CleaverClient {

    // =====================================================================
    // 3‑е лицо
    // =====================================================================
    /** Из боевой стойки в покой: через сколько тиков после начала удара и сколько длится. */
    private static final float REST_AT = 44f;
    private static final float REST_LEN = 10f;
    private static final float BLEND = 1.5f;
    /** Нагрев клинка: остывает за столько тиков. */
    private static final float COOL_TICKS = 110f;

    static final class State {
        int clip = -1;
        float start = -1000f;
        int prevClip = -1;
        float prevStart = -1000f;
        float charge1;
        float charge2;
        /** Заряжает особую атаку: вид (0 — нет) и с какого тика. */
        int chargeKind;
        float chargeStart = -1f;
        /** Нагрев: значение на момент heatAt (остывает линейно). */
        float heat;
        float heatAt;
        final BloodSwordPose.Frame weapon = new BloodSwordPose.Frame();
        boolean posed;
        /** Суставные руки от 3‑го лица (px модели): решение, тыльная сторона кисти, «наружу». */
        final ArmRig.Sol armR = new ArmRig.Sol();
        final ArmRig.Sol armL = new ArmRig.Sol();
        final Vector3f backR = new Vector3f(0, 0, 1);
        final Vector3f backL = new Vector3f(0, 0, 1);
        final Vector3f outR = new Vector3f(-1, 0, 0);
        final Vector3f outL = new Vector3f(1, 0, 0);
        /** След от 3‑го лица: {время, основание xyz, острие xyz, идёт ли} в пространстве модели (блоки). */
        final ArrayDeque<float[]> trail = new ArrayDeque<>();

        float heat(float now) {
            return Math.max(0f, this.heat - (now - this.heatAt) / COOL_TICKS);
        }

        void addHeat(float now, float v) {
            this.heat = Math.min(1f, this.heat(now) + v);
            this.heatAt = now;
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long ticks;
    private static boolean bodyDirty;

    // =====================================================================
    // 1‑е лицо (свой игрок)
    // =====================================================================
    private static FpAnim anim;
    private static FpAnim.Clip action;
    private static double actionStart;
    private static int nextEvent;
    private static final FpAnim.Pose cur = new FpAnim.Pose();
    private static final FpAnim.Pose from = new FpAnim.Pose();
    private static final FpAnim.Pose tmpA = new FpAnim.Pose();
    private static final FpAnim.Pose tmpB = new FpAnim.Pose();
    private static double blendStart = -10;
    private static float blendLen = 0.1f;
    private static double clock;
    private static double lastReal = -1;
    private static double hitstopUntil;
    private static boolean active;
    private static boolean held;

    // ввод и бой (свой игрок)
    private static KeyBinding reloadKey;
    private static int comboIdx = -1;
    private static boolean comboBlast;
    private static long comboStart = -1000;
    private static long restUntil;
    /** Заранее нажатый удар: 0 — нет, 1 — обычный, 2 — взрывной. */
    private static int buffered;
    private static long pressL = -1;
    private static long pressR = -1;
    private static boolean wasUse;
    private static int chargingKind;
    private static long chargeStartTick;
    private static boolean specialRunning;
    private static long busyUntil;                    // перезарядка / особая атака — до этого тика удары не идут
    private static long s1ReadyAt;
    private static long s2ReadyAt;
    private static int dashTicks;
    private static Vec3d dashDir = Vec3d.ZERO;
    /** Запланированные шаги «Тесака в узел»: тики и скорость; подскок. */
    private static final long[] stepAt = new long[3];
    private static long hopAt = -1;
    /** Предсказание патронов, пока сервер не прислал новое число. */
    private static int predRounds = -1;
    private static long predUntil;
    /** Внутренняя сила (шлёт сервер). */
    static int inner;

    private CleaverClient() {
    }

    public static void init() {
        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.basedefense.cleaver_reload", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, "category.basedefense"));
        ClientPlayNetworking.registerGlobalReceiver(CleaverCombat.S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int code = buf.readByte();
            client.execute(() -> {
                if (client.player != null && id != client.player.getId()) {
                    remoteAnim(state(id), code);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CleaverCombat.SYNC, (client, handler, buf, sender) -> {
            int v = buf.readVarInt();
            client.execute(() -> inner = v);
        });
        HudRenderCallback.EVENT.register(CleaverHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(CleaverClient::tick);
    }

    private static void remoteAnim(State st, int code) {
        if (code == CleaverCombat.ANIM_CHARGE1 || code == CleaverCombat.ANIM_CHARGE2) {
            st.chargeKind = code == CleaverCombat.ANIM_CHARGE1 ? 1 : 2;
            st.chargeStart = ticks;
        } else if (code == CleaverCombat.ANIM_CHARGE_END) {
            st.chargeKind = 0;
        } else if (code >= 0 && code < CleaverKeys.TP_CLIPS.length) {
            st.chargeKind = 0;
            begin(st, code, ticks);
            if (code >= CleaverCombat.ANIM_B && code < CleaverCombat.ANIM_RELOAD) {
                st.addHeat(ticks, code >= CleaverCombat.ANIM_S1 ? 1f : 0.4f);
            }
        }
    }

    static FpAnim anim() {
        if (anim == null) {
            anim = FpAnim.load("cleaver");
        }
        return anim;
    }

    static float now() {
        return ticks + MinecraftClient.getInstance().getTickDelta();
    }

    static State state(int id) {
        return STATES.computeIfAbsent(id, k -> new State());
    }

    static State get(int id) {
        return STATES.get(id);
    }

    public static boolean holdingLocal() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p != null && CleaverCombat.holding(p);
    }

    /** Патронов в барабане у своего игрока (с учётом только что сделанных выстрелов). */
    static int rounds(PlayerEntity p) {
        int r = CleaverItem.rounds(p.getMainHandStack());
        return predRounds >= 0 && ticks < predUntil ? Math.min(r, predRounds) : r;
    }

    private static void spend(PlayerEntity p, int n) {
        predRounds = Math.max(0, rounds(p) - n);
        predUntil = ticks + 12;
    }

    private static void begin(State s, int idx, float t) {
        s.prevClip = s.clip;
        s.prevStart = s.start;
        s.clip = idx;
        s.start = t;
        s.trail.clear();
    }

    private static void play(SoundEvent ev, float vol, float pitch) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && c.world != null) {
            c.world.playSound(c.player.getX(), c.player.getY(), c.player.getZ(), ev, SoundCategory.PLAYERS, vol, pitch, false);
        }
    }

    private static void send(int act, int arg) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(act);
        buf.writeByte(arg);
        ClientPlayNetworking.send(CleaverCombat.C2S, buf);
    }

    // =====================================================================
    // Ввод
    // =====================================================================
    /** Вместо ванильного удара (MinecraftClient.doAttack). @return true — клик съеден тесаком. */
    public static boolean onAttack() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        if (p == null || !CleaverCombat.holding(p) || p.isSpectator()) {
            return false;
        }
        if (!specialRunning && chargingKind == 0) {
            pressL = ticks;
        }
        return true;
    }

    private static boolean busyLocal() {
        return specialRunning || chargingKind != 0 || ticks < busyUntil;
    }

    /** Короткий клик: удар серии (с учётом темпа и отдыха). blast — ПКМ. */
    private static void click(ClientPlayerEntity p, boolean blast) {
        if (busyLocal() || ticks < restUntil) {
            return;
        }
        if (blast && rounds(p) <= 0) {
            startReload(p);                              // пусто — щелчок курка и сразу перезарядка
            return;
        }
        long age = ticks - comboStart;
        int min = comboBlast ? CleaverCombat.NEXT_MIN_B : CleaverCombat.NEXT_MIN_N;
        if (comboIdx >= 0 && comboIdx < 2 && age < min) {
            if (age >= 2) {
                buffered = blast ? 2 : 1;                // нажал чуть раньше — удар начнётся сам
            }
            return;
        }
        startSwing(p, blast);
    }

    private static void startSwing(ClientPlayerEntity p, boolean blast) {
        if (blast && rounds(p) <= 0) {
            buffered = 0;
            return;
        }
        long age = ticks - comboStart;
        int idx = comboIdx >= 0 && comboIdx < 2 && age <= CleaverCombat.COMBO_WINDOW ? comboIdx + 1 : 0;
        comboIdx = idx;
        comboBlast = blast;
        comboStart = ticks;
        buffered = 0;
        if (idx == 2) {
            restUntil = ticks + (blast ? CleaverCombat.REST_B : CleaverCombat.REST_N);
        }
        dash(p, blast ? CleaverCombat.DASH_B[idx] : CleaverCombat.DASH_N[idx]);
        State st = state(p.getId());
        begin(st, (blast ? CleaverCombat.ANIM_B : CleaverCombat.ANIM_N) + idx, ticks);
        if (blast) {
            spend(p, 1);
            st.addHeat(ticks, 0.4f);
        }
        startAction((blast ? "b" : "n") + (idx + 1), 0.09f);
        send(CleaverCombat.ACT_SWING, idx | (blast ? 4 : 0));
    }

    private static void dash(ClientPlayerEntity p, float speed) {
        Vec3d look = p.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) {
            flat = flat.normalize().multiply(speed * (p.isOnGround() ? 1.0 : 0.35));
            p.addVelocity(flat.x, 0, flat.z);
        }
    }

    private static boolean canCharge(ClientPlayerEntity p, int kind) {
        if (busyLocal() || ticks < restUntil) {
            return false;
        }
        if (kind == 1) {
            return ticks >= s1ReadyAt && rounds(p) >= CleaverCombat.S1_ROUNDS;
        }
        return ticks >= s2ReadyAt && rounds(p) >= 1 && inner >= CleaverCombat.S2_INNER_MIN;
    }

    private static void startCharge(ClientPlayerEntity p, int kind) {
        chargingKind = kind;
        chargeStartTick = ticks;
        State st = state(p.getId());
        st.chargeKind = kind;
        st.chargeStart = ticks;
        buffered = 0;
        comboIdx = -1;
        startAction("charge" + kind, 0.15f);
        send(CleaverCombat.ACT_CHARGE, kind);
    }

    private static int chargeTicks(int kind) {
        return kind == 1 ? CleaverTimings.S1_CHARGE : CleaverTimings.S2_CHARGE;
    }

    private static void releaseCharge(ClientPlayerEntity p) {
        int kind = chargingKind;
        boolean ready = ticks - chargeStartTick >= chargeTicks(kind) - 2;
        chargingKind = 0;
        state(p.getId()).chargeKind = 0;
        if (ready) {
            send(CleaverCombat.ACT_RELEASE, kind);
            startSpecial(p, kind);
        } else {
            send(CleaverCombat.ACT_CANCEL, 0);
            if (action != null && action.name.startsWith("charge")) {
                endAction(0.18f);
            }
        }
    }

    private static void startSpecial(ClientPlayerEntity p, int kind) {
        Vec3d look = p.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        dashDir = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.ZERO;
        specialRunning = true;
        State st = state(p.getId());
        st.addHeat(ticks, 1f);
        if (kind == 1) {
            dashTicks = CleaverCombat.S1_DASH_TICKS;
            s1ReadyAt = ticks + CleaverCombat.S1_COOLDOWN;
            spend(p, CleaverCombat.S1_ROUNDS);
            begin(st, CleaverCombat.ANIM_S1, ticks);
        } else {
            s2ReadyAt = ticks + CleaverCombat.S2_COOLDOWN;
            spend(p, CleaverItem.MAX_ROUNDS);
            for (int i = 0; i < 3; i++) {
                stepAt[i] = ticks + CleaverTimings.S2_CUTS[i] - 1;
            }
            hopAt = ticks + CleaverTimings.S2_HOP;
            begin(st, CleaverCombat.ANIM_S2, ticks);
        }
        startAction("special" + kind, 0.03f);
    }

    private static void startReload(ClientPlayerEntity p) {
        if (busyLocal() || CleaverItem.rounds(p.getMainHandStack()) >= CleaverItem.MAX_ROUNDS) {
            return;
        }
        busyUntil = ticks + CleaverTimings.RELOAD_LEN - 5;
        buffered = 0;
        comboIdx = -1;
        predRounds = -1;
        begin(state(p.getId()), CleaverCombat.ANIM_RELOAD, ticks);
        startAction("reload", 0.08f);
        send(CleaverCombat.ACT_RELOAD, 0);
    }

    /** Сколько осталось до готовности особой атаки (1 — только что, 0 — готова) — для значков. */
    static float cooldown(int kind, float tickDelta) {
        float left = (kind == 1 ? s1ReadyAt : s2ReadyAt) - ticks - tickDelta;
        return left <= 0 ? 0f : Math.min(1f, left / (kind == 1 ? CleaverCombat.S1_COOLDOWN : CleaverCombat.S2_COOLDOWN));
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        if (client.player == null || client.world == null) {
            STATES.clear();
            buffered = 0;
            action = null;
            inner = 0;
            return;
        }
        ClientPlayerEntity p = client.player;
        boolean holding = CleaverCombat.holding(p);
        if (holding && !held) {                          // достал тесак
            action = null;
            specialRunning = false;
            startAction("draw", 0.02f);
        }
        if (!holding && held) {
            if (chargingKind != 0) {
                chargingKind = 0;
                send(CleaverCombat.ACT_CANCEL, 0);
            }
            specialRunning = false;
            busyUntil = 0;
            dashTicks = 0;
            hopAt = -1;
            pressL = -1;
            pressR = -1;
            buffered = 0;
        }
        held = holding;
        if (holding) {
            // --- серия: заранее нажатый удар ---
            if (buffered != 0 && ticks - comboStart >= (comboBlast ? CleaverCombat.NEXT_MIN_B : CleaverCombat.NEXT_MIN_N)) {
                startSwing(p, buffered == 2);
            }
            boolean screen = client.currentScreen != null;
            boolean downL = client.options.attackKey.isPressed() && !screen;
            boolean downR = client.options.useKey.isPressed() && !screen;
            if (downR && !wasUse && !specialRunning && chargingKind == 0) {
                pressR = ticks;
            }
            wasUse = downR;
            if (chargingKind != 0) {
                boolean down = chargingKind == 1 ? downL : downR;
                if (!down || ticks - chargeStartTick >= chargeTicks(chargingKind) + CleaverCombat.AUTO_RELEASE) {
                    releaseCharge(p);                    // отпустил (или готово и держат долго — бьёт сам)
                    pressL = -1;
                    pressR = -1;
                }
            } else {
                // --- ЛКМ: клик — обычный удар, держат — «Тигриный выпад» ---
                if (pressL >= 0) {
                    if (!downL) {
                        pressL = -1;
                        click(p, false);
                    } else if (ticks - pressL >= CleaverCombat.HOLD_START && canCharge(p, 1)) {
                        pressL = -1;
                        pressR = -1;
                        startCharge(p, 1);
                    }
                }
                // --- ПКМ: клик — взрывной удар, держат — «Тесак в узел» ---
                if (pressR >= 0 && chargingKind == 0) {
                    if (!downR) {
                        pressR = -1;
                        click(p, true);
                    } else if (ticks - pressR >= CleaverCombat.HOLD_START && canCharge(p, 2)) {
                        pressR = -1;
                        pressL = -1;
                        startCharge(p, 2);
                    }
                }
            }
            while (reloadKey.wasPressed()) {
                startReload(p);
            }
            // --- движение особых атак ---
            if (dashTicks > 0) {
                dashTicks--;
                double sp = CleaverCombat.S1_DASH_SPEED;
                p.setVelocity(dashDir.x * sp, Math.min(0.0, p.getVelocity().y * 0.2), dashDir.z * sp);
                p.fallDistance = 0f;
                if (dashTicks == 0) {
                    p.setVelocity(dashDir.x * 0.2, p.getVelocity().y, dashDir.z * 0.2);
                }
            }
            if (specialRunning && hopAt >= 0) {
                for (int i = 0; i < 3; i++) {
                    if (ticks == stepAt[i]) {
                        dash(p, CleaverCombat.S2_STEP[i]);
                    }
                }
                if (ticks == hopAt) {
                    hopAt = -1;
                    if (p.isOnGround()) {
                        Vec3d look = p.getRotationVector();
                        Vec3d flat = new Vec3d(look.x, 0, look.z);
                        flat = flat.lengthSquared() > 1e-4 ? flat.normalize().multiply(0.35) : Vec3d.ZERO;
                        p.setVelocity(flat.x, 0.40, flat.z);
                    }
                }
            }
        } else {
            while (reloadKey.wasPressed()) {
                // не тесак в руке — нажатие пропадает
            }
            wasUse = false;
        }
        // события клипа и время 1‑го лица — и без отрисовки (звуки идут и от 3‑го лица)
        update(0f);
        // частицы у всех игроков с тесаком: зарядка, аура внутренней силы у себя
        for (PlayerEntity pl : client.world.getPlayers()) {
            State st = STATES.get(pl.getId());
            if (st != null && st.chargeKind != 0 && CleaverCombat.holding(pl) && ticks % 2 == 0) {
                Vec3d c = pl.getPos().add(0, 0.9, 0);
                double ang = client.world.random.nextDouble() * Math.PI * 2;
                double rad = 1.2 + client.world.random.nextDouble() * 0.6;
                Vec3d s = c.add(Math.cos(ang) * rad, (client.world.random.nextDouble() - 0.4) * 1.2, Math.sin(ang) * rad);
                Vec3d vel = c.subtract(s).multiply(0.09);
                client.world.addParticle(st.chargeKind == 2 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, s.x, s.y, s.z, vel.x, vel.y, vel.z);
            }
        }
        if (holding && inner >= CleaverCombat.S2_INNER_MIN && ticks % 4 == 0 && !client.options.getPerspective().isFirstPerson()) {
            double ang = client.world.random.nextDouble() * Math.PI * 2;
            client.world.addParticle(ParticleTypes.WAX_ON, p.getX() + Math.cos(ang) * 0.5, p.getY() + 0.2 + client.world.random.nextDouble() * 1.4,
                    p.getZ() + Math.sin(ang) * 0.5, 0, 0.6, 0);
        }
        STATES.entrySet().removeIf(e -> {
            Entity ent = client.world.getEntityById(e.getKey());
            return !(ent instanceof PlayerEntity pl) || !CleaverCombat.holding(pl);
        });
    }

    // =====================================================================
    // 1‑е лицо: клипы
    // =====================================================================
    private static void startAction(String name, float blend) {
        FpAnim.Clip c = anim().clip(name);
        if (c == null) {
            return;
        }
        from.set(cur);
        blendStart = clock;
        blendLen = Math.max(0.001f, blend);
        action = c;
        actionStart = clock;
        nextEvent = 0;
    }

    private static void endAction(float blend) {
        from.set(cur);
        blendStart = clock;
        blendLen = blend;
        action = null;
        specialRunning = false;
    }

    /** Обновить время и позу 1‑го лица (из тика — tickDelta 0, из кадра — настоящий). */
    public static void update(float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        if (p == null || !CleaverCombat.holding(p)) {
            active = false;
            action = null;
            specialRunning = false;
            return;
        }
        double real = (ticks + tickDelta) / 20.0;
        if (lastReal < 0 || real - lastReal > 1.0) {
            lastReal = real;
        }
        double dt = real - lastReal;
        if (dt <= 0 && active) {
            return;
        }
        lastReal = real;
        clock += dt * (real < hitstopUntil ? 0.08 : 1.0);
        boolean first = !active;
        active = true;
        FpAnim a = anim();
        a.sample(a.clip("idle"), (float) clock, tmpA);
        if (first) {
            cur.set(tmpA);
            from.set(tmpA);
        }
        FpAnim.Pose target = tmpA;
        if (action != null) {
            float t = (float) (clock - actionStart);
            fireEvents(p, t);
            if (action != null && !action.loop && t >= action.length) {
                if (action.name.equals("charge1") || action.name.equals("charge2")) {
                    startAction(action.name.equals("charge1") ? "charged1" : "charged2", 0.05f);
                } else {
                    String done = action.name;
                    a.sample(action, action.length, cur);
                    endAction(done.startsWith("special") ? 0.2f : 0.15f);
                }
            }
            if (action != null) {
                a.sample(action, (float) (clock - actionStart), tmpB);
                target = tmpB;
            }
        }
        float w = (float) ((clock - blendStart) / blendLen);
        if (w < 1f) {
            w = MathHelper.clamp(w, 0f, 1f);
            w = w * w * (3f - 2f * w);
            cur.blend(from, target, w);
        } else {
            cur.set(target);
        }
    }

    private static void fireEvents(ClientPlayerEntity p, float t) {
        while (action != null && nextEvent < action.events.length && t >= action.eventTimes[nextEvent]) {
            String ev = action.events[nextEvent++];
            float rnd = p.getRandom().nextFloat();
            switch (ev) {
                case "swing" -> play(ModSounds.TIGER_SWING, 0.9f, 0.95f + rnd * 0.1f);
                case "swing_heavy" -> play(ModSounds.TIGER_SWING_HEAVY, 0.9f, 0.95f + rnd * 0.1f);
                case "draw", "flick", "spin" -> play(ModSounds.TIGER_FLICK, 0.7f, ev.equals("spin") ? 1.3f : 1.0f);
                case "shot" -> play(ModSounds.TIGER_SHOT, 1.0f, 0.97f + rnd * 0.08f);
                case "dash" -> play(ModSounds.TIGER_DASH, 1.0f, 1.0f);
                case "charge", "ignite" -> play(ModSounds.TIGER_IGNITE, 0.8f, ev.equals("charge") ? 1.1f : 0.9f);
                case "rev" -> play(ModSounds.TIGER_DRUM_CLOSE, 0.6f, 1.4f);
                case "ready" -> play(ModSounds.KATANA_READY, 0.9f, 0.8f);
                case "drum_open" -> play(ModSounds.TIGER_DRUM_OPEN, 0.8f, 1.0f);
                case "shells" -> play(ModSounds.TIGER_SHELL, 0.8f, 1.0f);
                case "drum_load" -> play(ModSounds.TIGER_DRUM_LOAD, 0.8f, 1.0f);
                case "drum_close" -> play(ModSounds.TIGER_DRUM_CLOSE, 0.8f, 1.0f);
                case "hit" -> {
                    if (hitsSomething(p)) {
                        hitstopUntil = lastReal + (comboBlast || specialRunning ? 0.09 : 0.07);   // «стоп‑кадр» при попадании
                    }
                }
                default -> {
                }
            }
        }
    }

    private static boolean hitsSomething(ClientPlayerEntity p) {
        if (specialRunning) {
            return !CleaverCombat.targets(p, p.getWorld(), 4.6, 110).isEmpty();
        }
        return comboIdx >= 0 && !CleaverCombat.stepTargets(p, p.getWorld(), comboIdx, comboBlast).isEmpty();
    }

    /** Тесак от 1‑го лица сейчас рисуется (свой, в руке, вид из глаз). */
    public static boolean firstPersonActive() {
        MinecraftClient c = MinecraftClient.getInstance();
        return active && c.options.getPerspective().isFirstPerson() && holdingLocal();
    }

    static FpAnim.Pose pose() {
        return cur;
    }

    static FpAnim.Clip actionClip() {
        return action;
    }

    static float actionTime() {
        return (float) (clock - actionStart);
    }

    /** Камера (mixin GameRendererMixin, tiltViewWhenHurt): крен, наклон и поворот из клипа. */
    public static void applyCamera(MatrixStack matrices, float tickDelta) {
        update(tickDelta);
        if (!firstPersonActive()) {
            return;
        }
        Vector3f c = cur.cam;
        if (c.z != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c.z));
        }
        if (c.x != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c.x));
        }
        if (c.y != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.y));
        }
    }

    /** Множитель обзора (рывок — шире, замах — чуть уже). */
    public static float fovMultiplier() {
        return firstPersonActive() ? MathHelper.clamp(cur.fov, 0.8f, 1.35f) : 1f;
    }

    // =====================================================================
    // 3‑е лицо: позы
    // =====================================================================
    private static final int N = 18;

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float[] lerp(float[] a, float[] b, float w, float[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * w;
        }
        return out;
    }

    private static float[] row(float[][] pose) {
        float[] out = new float[N];
        System.arraycopy(pose[0], 1, out, 0, N);
        return out;
    }

    /** Поза клипа: во время клипа — его кадры; после — боевая стойка, затем плавно в покой. */
    private static float[] clip(int clip, float s, float[] out) {
        if (clip < 0 || clip >= CleaverKeys.TP_CLIPS.length) {
            System.arraycopy(CleaverKeys.TP_IDLE[0], 1, out, 0, N);
            return out;
        }
        float[][] keys = CleaverKeys.TP_CLIPS[clip];
        float end = keys[keys.length - 1][0];
        if (s <= end) {
            return BloodSwordPose.sample(keys, s, out);
        }
        return lerp(row(CleaverKeys.TP_GUARD), row(CleaverKeys.TP_IDLE), smooth((s - Math.max(REST_AT, end + 12f)) / REST_LEN), out);
    }

    static float[] values(State st, float now, float[] out) {
        clip(st.clip, now - st.start, out);
        float a = now - st.start;
        if (a < BLEND) {
            float[] prev = clip(st.prevClip, now - st.prevStart, new float[N]);
            lerp(prev, out, smooth(a / BLEND), out);
        }
        if (st.charge1 > 0.001f) {
            lerp(out, row(CleaverKeys.TP_CHARGE1), smooth(st.charge1), out);
        }
        if (st.charge2 > 0.001f) {
            lerp(out, row(CleaverKeys.TP_CHARGE2), smooth(st.charge2), out);
        }
        return out;
    }

    /** Из миксина BipedEntityModel.setAngles: игрок с тесаком в руке — стойка, удары, зарядки. */
    public static void applyModel(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(model instanceof PlayerEntityModel) || !(entity instanceof PlayerEntity pl)) {
            return;
        }
        // длина ванильных рук — обычная (тесак делает их короче у своего владельца, см. ниже)
        model.rightArm.yScale = 1f;
        model.leftArm.yScale = 1f;
        if (!CleaverCombat.holding(pl)) {
            if (bodyDirty && !KatanaCombat.holding(pl)) {    // вернуть сдвинутые нами опоры (ваниль их не сбрасывает)
                bodyDirty = false;
                model.body.pivotX = 0f;
                model.body.pivotZ = 0f;
                model.head.pivotX = 0f;
                model.head.pivotZ = 0f;
                model.hat.copyTransform(model.head);
            }
            return;
        }
        State st = state(entity.getId());
        float now = now();
        st.charge1 += ((st.chargeKind == 1 ? MathHelper.clamp((now - st.chargeStart) / 6f, 0f, 1f) : 0f) - st.charge1) * 0.35f;
        st.charge2 += ((st.chargeKind == 2 ? MathHelper.clamp((now - st.chargeStart) / 6f, 0f, 1f) : 0f) - st.charge2) * 0.35f;
        float[] v = values(st, now, new float[N]);
        float walk = MathHelper.clamp(1f - pl.limbAnimator.getSpeed() * 1.5f, 0f, 1f);  // на ходу ноги — свои
        float legL = v[14] * walk;
        float legR = v[15] * walk;
        // бёдра опускаются, когда ноги расставлены (ступни остаются на земле)
        float drop = 12f * (1f - MathHelper.cos((Math.abs(legL) + Math.abs(legR)) * 0.5f));
        float hipY = (model.rightLeg.pivotY + model.leftLeg.pivotY) * 0.5f + drop;
        float hipZ = (model.rightLeg.pivotZ + model.leftLeg.pivotZ) * 0.5f;
        float lean = v[12] + model.body.pitch;
        float twist = v[13];
        boolean free = v[16] > 0.5f;
        boolean grips = v[17] > 0.5f;
        float swingL = model.leftArm.pitch;              // ванильное покачивание свободной руки
        Matrix3f r = new Matrix3f().rotateY(twist).rotateX(lean);
        Vector3f neck = new Vector3f(0, hipY, hipZ).add(r.transform(new Vector3f(0, -12, 0)));
        Vector3f jR = r.transform(vec(CleaverKeys.SH_R)).add(neck);
        Vector3f jL = r.transform(vec(CleaverKeys.SH_L)).add(neck);
        Vector3f g = r.transform(new Vector3f(v[0], v[1], v[2])).add(neck);
        Vector3f bw = r.transform(new Vector3f(v[3], v[4], v[5])).normalize();
        Vector3f nw = r.transform(new Vector3f(v[6], v[7], v[8]));
        Vector3f z = new Vector3f(nw).sub(new Vector3f(bw).mul(nw.dot(bw))).normalize();
        Vector3f x = new Vector3f(bw).cross(z);
        // правая: кисть на рукояти (костяшки — к кромке), локоть — наружу‑вниз‑назад
        Vector3f hand = new Vector3f(x).mul(ArmRig.H0.x).add(new Vector3f(bw).mul(ArmRig.H0.y)).normalize();
        ArmRig.solve(jR, g, hand, r.transform(vec(CleaverKeys.POLE_R)), 1f, 1f, bw, st.armR);
        st.backR.set(z);
        r.transform(st.outR.set(-1, 0, 0));
        // левая: на рукояти / по ключу / свободно висит и качается
        Vector3f lk;
        Vector3f handL;
        Vector3f handleL = null;
        r.transform(st.outL.set(1, 0, 0));
        if (free) {
            Vector3f hang = vec(CleaverKeys.HANG_L).sub(vec(CleaverKeys.SH_L)).rotateX(swingL * 0.8f);
            lk = r.transform(hang).add(jL);
            handL = new Vector3f(lk).sub(jL).normalize();
            st.backL.set(st.outL);
        } else if (grips) {
            lk = r.transform(new Vector3f(v[9], v[10], v[11])).add(neck);
            handL = new Vector3f(hand);
            st.backL.set(z).negate();
            handleL = bw;
        } else {
            lk = r.transform(new Vector3f(v[9], v[10], v[11])).add(neck);
            handL = new Vector3f(lk).sub(jL).normalize();
            st.backL.set(st.outL);
        }
        ArmRig.solve(jL, lk, handL, r.transform(vec(CleaverKeys.POLE_L)), 1f, 1f, handleL, st.armL);

        model.body.pivotX = neck.x;
        model.body.pivotY = neck.y;
        model.body.pivotZ = neck.z;
        model.body.pitch = lean;
        model.body.yaw = twist;
        model.body.roll = 0f;
        model.head.pivotX = neck.x;
        model.head.pivotY = neck.y;
        model.head.pivotZ = neck.z;
        model.hat.copyTransform(model.head);
        bodyDirty = true;
        // ванильные руки спрятаны (их рисует CleaverRender суставными); их поворот — по плечу до локтя и вдвое короче:
        // так наплечники брони и рукава одежды садятся на плечо, а не торчат прямой палкой
        Vector3f xRef = r.transform(new Vector3f(1, 0, 0));
        Matrix3f m = new Matrix3f();
        Vector3f e = new Vector3f();
        BloodSwordPose.basis(new Vector3f(st.armR.e).sub(st.armR.s), xRef, m);
        BloodSwordPose.euler(m, e);
        set(r.transform(new Vector3f(-5, 2, 0)).add(neck), e, model.rightArm);
        BloodSwordPose.basis(new Vector3f(st.armL.e).sub(st.armL.s), xRef, m);
        BloodSwordPose.euler(m, e);
        set(r.transform(new Vector3f(5, 2, 0)).add(neck), e, model.leftArm);
        model.rightArm.yScale = 0.5f;
        model.leftArm.yScale = 0.5f;
        model.rightArm.visible = false;
        model.leftArm.visible = false;
        model.leftLeg.pivotY += drop;
        model.rightLeg.pivotY += drop;
        model.leftLeg.pitch += legL;
        model.rightLeg.pitch += legR;
        if (model instanceof PlayerEntityModel<?> pm) {
            pm.jacket.copyTransform(model.body);
            pm.rightSleeve.visible = false;
            pm.leftSleeve.visible = false;
            pm.leftPants.copyTransform(model.leftLeg);
            pm.rightPants.copyTransform(model.rightLeg);
        }
        KatanaClient.frame(g, bw, nw, st.weapon);
        st.weapon.p.div(16f);
        st.posed = true;
    }

    private static Vector3f vec(float[] v) {
        return new Vector3f(v[0], v[1], v[2]);
    }

    private static void set(Vector3f pivot, Vector3f euler, net.minecraft.client.model.ModelPart part) {
        part.pivotX = pivot.x;
        part.pivotY = pivot.y;
        part.pivotZ = pivot.z;
        part.pitch = euler.x;
        part.yaw = euler.y;
        part.roll = euler.z;
    }

    /** След от 3‑го лица идёт сейчас? */
    static float trailTP(State st, float now) {
        if (st.clip < 0 || st.clip >= CleaverKeys.TRAIL.length) {
            return 0f;
        }
        float a = now - st.start;
        float[] w = CleaverKeys.TRAIL[st.clip];
        for (int i = 0; i + 1 < w.length; i += 2) {
            if (a >= w[i] && a <= w[i + 1]) {
                return 1f;
            }
        }
        return 0f;
    }

    /** Клип 3‑го лица — «огненный» (взрывные удары и особые атаки)? */
    static boolean fiery(State st) {
        return st.clip >= CleaverCombat.ANIM_B && st.clip < CleaverCombat.ANIM_RELOAD;
    }

    /** Барабан откинут (3‑е лицо, перезарядка): 0..1. */
    static float drumOpenTP(State st, float now) {
        if (st.clip != CleaverCombat.ANIM_RELOAD) {
            return 0f;
        }
        float a = now - st.start;
        return Math.min(smooth((a - 5f) / 2f), 1f - smooth((a - 25f) / 1.5f));
    }
}
