package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.client.weapon.fp.FpAnim;
import com.lesha_top1972.basedefense.sound.ModSounds;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Катана на клиенте.
 * <p>
 * 1‑е лицо (свой игрок): клипы assets/basedefense/fpanim/katana.json (сняты с видео‑образца, tools/katana_gen.py):
 * в ножнах у таза (правая рука на рукояти), 3 удара (из ножен — сразу с выхвата), после боя или на бегу — в ножны,
 * зарядка, готовность, особая атака. Плавные переходы, события
 * клипа (звуки, «стоп‑кадр» при попадании), наклон/крен камеры и обзор — из клипа (mixin GameRendererMixin).
 * <p>
 * Ввод: ЛКМ клик — серия (вместо ванильного удара), ЛКМ держать 1.5 с — особая атака, ПКМ держать — блок (KatanaItem).
 * Рывки своего игрока — здесь.
 * <p>
 * 3‑е лицо (все игроки): позы KatanaKeys (стойка у бедра в ножнах, удары, зарядка, особая атака).
 */
public final class KatanaClient {

    // =====================================================================
    // 3‑е лицо
    // =====================================================================
    /** Возврат в ножны: через сколько тиков после удара начать и сколько длится. */
    private static final float SHEATHE_AT = 34f;
    private static final float SHEATHE_LEN = 8f;
    /** Переход между ударами. */
    private static final float BLEND = 1.5f;
    /** Окна следа клинка от 3‑го лица (тики от начала клипа): {от, до, …}. */
    static final float[][] TRAIL_TP = {{0.5f, 6f}, {0.5f, 6f}, {0.5f, 6f, 8.5f, 13f}, {0f, 4f, 12.5f, 15f}};

    static final class State {
        int clip = -1;
        float start = -1000f;
        int prevClip = -1;
        float prevStart = -1000f;
        float charge;
        float block;
        /** Заряжает особую атаку (зажата ЛКМ) — с какого тика (−1 — нет). */
        float chargeStart = -1f;
        final BloodSwordPose.Frame katana = new BloodSwordPose.Frame();
        final BloodSwordPose.Frame saya = new BloodSwordPose.Frame();
        boolean posed;
        /** След от 3‑го лица: {время, основание xyz, острие xyz} в пространстве модели (блоки). */
        final ArrayDeque<float[]> trail = new ArrayDeque<>();
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long ticks;

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
    /** Катана обнажена (иначе — в ножнах на поясе, рука на рукояти). */
    private static boolean drawn;
    /** Когда последний раз бил (часы 1‑го лица) — через SHEATHE_AFTER без ударов убирает в ножны. */
    private static double lastCombat;
    private static double sprintTime;
    private static boolean equipPending;
    private static final double SHEATHE_AFTER = 1.2;
    private static final double SHEATHE_SPRINT = 0.25;

    // ввод и бой (свой игрок)
    private static int comboIdx = -1;
    private static long comboStart = -1000;
    private static long restUntil;
    private static boolean buffered;
    private static boolean wasBlocking;
    /** Тик нажатия ЛКМ (−1 — не нажата): короткий клик — удар, держат — зарядка. */
    private static long pressTick = -1;
    private static boolean chargingLocal;
    private static long chargeStartTick;
    private static int dashTicks;
    private static Vec3d dashDir = Vec3d.ZERO;
    private static int dash2At = -1;
    private static boolean held;
    private static boolean specialRunning;
    /** Древняя сила (шлёт сервер). */
    static int power;

    private KatanaClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(KatanaCombat.S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int idx = buf.readByte();
            client.execute(() -> {
                if (client.player != null && id != client.player.getId()) {
                    State st = state(id);
                    if (idx == KatanaCombat.ANIM_CHARGE) {
                        st.chargeStart = ticks;
                    } else if (idx == KatanaCombat.ANIM_CHARGE_END) {
                        st.chargeStart = -1f;
                    } else {
                        st.chargeStart = -1f;
                        begin(st, idx, ticks);
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(KatanaCombat.S2C_BLOCKED, (client, handler, buf, sender) ->
                client.execute(KatanaClient::onBlocked));
        ClientPlayNetworking.registerGlobalReceiver(com.lesha_top1972.basedefense.weapon.KatanaPower.SYNC,
                (client, handler, buf, sender) -> {
                    int v = buf.readVarInt();
                    client.execute(() -> power = v);
                });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(KatanaHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(KatanaClient::tick);
        KatanaWorldFx.init();
    }

    static FpAnim anim() {
        if (anim == null) {
            anim = FpAnim.load("katana");
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
        return p != null && KatanaCombat.holding(p);
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

    // =====================================================================
    // Ввод: вызывается вместо ванильного удара (MinecraftClient.doAttack)
    // =====================================================================
    /** @return true — клик съеден катаной. Удар — когда отпустят (держат дольше — зарядка особой атаки). */
    public static boolean onAttack() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        if (p == null || !KatanaCombat.holding(p) || p.isSpectator()) {
            return false;
        }
        if (!p.isUsingItem() && !specialRunning && !chargingLocal) {
            pressTick = ticks;
        }
        return true;
    }

    /** Короткий клик ЛКМ: удар серии (с учётом темпа и отдыха). */
    private static void click(ClientPlayerEntity p) {
        if (p.isUsingItem() || specialRunning || ticks < restUntil) {
            return;
        }
        long age = ticks - comboStart;
        if (comboIdx >= 0 && comboIdx < 2 && age < KatanaCombat.NEXT_MIN) {
            if (age >= 2) {
                buffered = true;                         // нажал чуть раньше — удар начнётся сам
            }
            return;
        }
        startSwing(p);
    }

    private static boolean canCharge(ClientPlayerEntity p) {
        return !p.isUsingItem() && !specialRunning && !p.getItemCooldownManager().isCoolingDown(p.getMainHandStack().getItem());
    }

    private static void startCharge(ClientPlayerEntity p) {
        chargingLocal = true;
        chargeStartTick = ticks;
        state(p.getId()).chargeStart = ticks;
        buffered = false;
        startAction(drawn ? "charge_drawn" : "charge", drawn ? 0.12f : 0.25f);
        drawn = false;
        sendCharge(1);
    }

    private static void releaseCharge(ClientPlayerEntity p) {
        boolean ready = ticks - chargeStartTick >= KatanaCombat.CHARGE_TICKS - 2;
        chargingLocal = false;
        state(p.getId()).chargeStart = -1f;
        sendCharge(ready ? 2 : 0);
        if (ready) {
            startSpecial(p);
        } else if (charging(action)) {
            endAction(0.15f);                            // отпустил раньше — остаётся в ножнах
        }
    }

    private static void sendCharge(int what) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(what);
        ClientPlayNetworking.send(KatanaCombat.C2S_CHARGE, buf);
    }

    /** Сервер: удар отбит блоком — отдача в руки. */
    private static void onBlocked() {
        if (holdingLocal()) {
            startAction("block_hit", 0.02f);
            hitstopUntil = lastReal + 0.05;
        }
    }

    private static void startSwing(ClientPlayerEntity p) {
        long age = ticks - comboStart;
        int idx = comboIdx >= 0 && comboIdx < 2 && age <= KatanaCombat.COMBO_WINDOW ? comboIdx + 1 : 0;
        comboIdx = idx;
        comboStart = ticks;
        buffered = false;
        if (idx == 2) {
            restUntil = ticks + KatanaCombat.FINISH_REST;
            dash2At = (int) (ticks + KatanaCombat.DASH2_TICK);
        }
        dash(p, KatanaCombat.DASH[idx]);
        begin(state(p.getId()), idx, ticks);
        if (!drawn) {                                    // из ножен — выхват прямо в удар
            drawn = true;
            play(ModSounds.KATANA_DRAW, 0.6f, 1.15f);
            startAction("slash" + (idx + 1), 0.07f, idx == 0 ? 0.07f : 0f);
        } else {
            startAction("slash" + (idx + 1), 0.05f);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(idx);
        ClientPlayNetworking.send(KatanaCombat.C2S, buf);
    }

    private static void dash(ClientPlayerEntity p, float speed) {
        Vec3d look = p.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) {
            flat = flat.normalize().multiply(speed * (p.isOnGround() ? 1.0 : 0.35));
            p.addVelocity(flat.x, 0, flat.z);
        }
    }

    private static void startSpecial(ClientPlayerEntity p) {
        Vec3d look = p.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        dashDir = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.ZERO;
        dashTicks = KatanaCombat.variant(p).dashTicks;
        specialRunning = true;
        drawn = true;                                    // после выхвата катана обнажена (в ножны — как после ударов)
        begin(state(p.getId()), KatanaCombat.ANIM_SPECIAL, ticks);
        startAction("special", 0.03f);
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        if (client.player == null || client.world == null) {
            STATES.clear();
            buffered = false;
            action = null;
            return;
        }
        ClientPlayerEntity p = client.player;
        boolean holding = KatanaCombat.holding(p);
        if (holding && !held) {
            drawn = false;                               // достал катану — она в ножнах на поясе
            action = null;
            equipPending = true;
        }
        held = holding;
        // --- серия: заранее нажатый удар ---
        if (buffered) {
            if (!holding) {
                buffered = false;
            } else if (ticks - comboStart >= KatanaCombat.NEXT_MIN) {
                startSwing(p);
            }
        }
        if (dash2At >= 0 && ticks >= dash2At) {
            dash2At = -1;
            if (holding) {
                dash(p, KatanaCombat.DASH2);
            }
        }
        // --- ЛКМ: короткий клик — удар, держат — зарядка особой атаки ---
        boolean down = client.options.attackKey.isPressed() && client.currentScreen == null;
        if (!holding) {
            pressTick = -1;
            if (chargingLocal) {
                chargingLocal = false;
                sendCharge(0);
            }
        } else if (chargingLocal) {
            if (!down || ticks - chargeStartTick >= KatanaCombat.AUTO_RELEASE) {
                releaseCharge(p);                        // отпустил (или готово и держат долго — бьёт сам)
                pressTick = -1;
            }
        } else if (pressTick >= 0) {
            if (!down) {
                pressTick = -1;
                click(p);
            } else if (ticks - pressTick >= KatanaCombat.HOLD_START && canCharge(p)) {
                pressTick = -1;
                startCharge(p);
            }
        }
        // --- ПКМ держат — блок ---
        boolean blocking = holding && p.isUsingItem() && p.getActiveItem().getItem() instanceof com.lesha_top1972.basedefense.weapon.KatanaItem;
        if (blocking && !wasBlocking) {
            if (chargingLocal) {
                chargingLocal = false;
                sendCharge(0);
            }
            drawn = true;
            startAction("block", 0.08f);
        } else if (!blocking && wasBlocking && action != null && action.name.startsWith("block")) {
            endAction(0.12f);
            lastCombat = clock;
        }
        wasBlocking = blocking;
        if (dashTicks > 0) {
            dashTicks--;
            double sp = KatanaCombat.DASH_SPEED;
            p.setVelocity(dashDir.x * sp, Math.min(0.0, p.getVelocity().y * 0.2), dashDir.z * sp);
            p.fallDistance = 0f;
            if (dashTicks == 0) {
                p.setVelocity(dashDir.x * 0.25, p.getVelocity().y, dashDir.z * 0.25);
            }
        }
        // события клипа и время 1‑го лица — и без отрисовки (звуки идут и от 3‑го лица)
        update(0f);
        // частицы зарядки у всех игроков с катаной
        for (PlayerEntity pl : client.world.getPlayers()) {
            State st = STATES.get(pl.getId());
            if (KatanaCombat.holding(pl) && st != null && st.chargeStart >= 0) {
                KatanaWorldFx.chargeParticles(client.world, pl, (int) (ticks - st.chargeStart), KatanaCombat.variant(pl).ancient);
            }
        }
        STATES.entrySet().removeIf(e -> {
            Entity ent = client.world.getEntityById(e.getKey());
            return !(ent instanceof PlayerEntity pl) || !KatanaCombat.holding(pl);
        });
    }

    // =====================================================================
    // 1‑е лицо: клипы
    // =====================================================================
    private static void startAction(String name, float blend) {
        startAction(name, blend, 0f);
    }

    /** offset — начать клип не с начала (удар из ножен — сразу с того места, где катана ушла за край экрана). */
    private static void startAction(String name, float blend, float offset) {
        FpAnim.Clip c = anim().clip(name);
        if (c == null) {
            return;
        }
        from.set(cur);
        blendStart = clock;
        blendLen = Math.max(0.001f, blend);
        action = c;
        actionStart = clock - offset;
        nextEvent = 0;
        while (nextEvent < c.events.length && c.eventTimes[nextEvent] < offset && !c.events[nextEvent].startsWith("swing")) {
            nextEvent++;
        }
        if (!name.equals("sheathe")) {
            lastCombat = clock;
        }
    }

    private static boolean charging(FpAnim.Clip c) {
        return c != null && (c.name.equals("charge") || c.name.equals("charge_drawn") || c.name.equals("charged"));
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
        if (p == null || !KatanaCombat.holding(p)) {
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
        active = true;
        FpAnim a = anim();
        // покой: обнажена — стойка, иначе — в ножнах на поясе
        a.sample(a.clip(drawn ? "idle" : "sheathed"), (float) clock, tmpA);
        if (equipPending) {                              // достал — рука с катаной поднимается снизу
            equipPending = false;
            from.set(tmpA);
            from.g.add(0f, -0.45f, 0.1f);
            from.lp.add(0f, -0.45f, 0.1f);
            from.sp.add(0f, -0.45f, 0.1f);
            blendStart = clock;
            blendLen = 0.3f;
        }
        sprintTime = p.isSprinting() ? sprintTime + dt : 0.0;
        if (drawn && action == null && (clock - lastCombat > SHEATHE_AFTER || sprintTime > SHEATHE_SPRINT)) {
            startAction("sheathe", 0.12f);
        }
        FpAnim.Pose target = tmpA;
        if (action != null) {
            float t = (float) (clock - actionStart);
            fireEvents(p, t);
            if (!action.loop && t >= action.length) {
                if (action.name.equals("charge") || action.name.equals("charge_drawn")) {
                    startAction("charged", 0.05f);
                } else if (action.name.equals("block_hit") && wasBlocking) {
                    startAction("block", 0.05f);
                } else {
                    String done = action.name;
                    a.sample(action, action.length, cur);
                    endAction(done.equals("special") ? 0.2f : 0.15f);
                    if (done.equals("sheathe")) {
                        drawn = false;
                    } else {
                        lastCombat = clock;
                    }
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
            cur.blend(from, target, w, a.sayaHide);
        } else {
            cur.set(target);
        }
    }

    private static void fireEvents(ClientPlayerEntity p, float t) {
        while (action != null && nextEvent < action.events.length && t >= action.eventTimes[nextEvent]) {
            String ev = action.events[nextEvent++];
            switch (ev) {
                case "swing" -> play(ModSounds.KATANA_SWING, 0.9f, 0.95f + p.getRandom().nextFloat() * 0.1f);
                case "swing_heavy" -> play(ModSounds.KATANA_SWING_HEAVY, 0.9f, 1.0f);
                case "draw" -> play(ModSounds.KATANA_DRAW, 0.7f, 1.0f);
                case "charge" -> play(ModSounds.KATANA_CHARGE, 0.8f, 1.0f);
                case "sheathe" -> play(ModSounds.KATANA_SHEATHE, 0.8f, 1.0f);
                case "ready" -> {
                    play(ModSounds.KATANA_READY, 0.9f, 1.0f);
                    KatanaWorldFx.readyBurst(p.getWorld(), p);
                }
                case "special" -> play(ModSounds.KATANA_SPECIAL, 1.0f, 1.0f);
                case "flick" -> play(ModSounds.KATANA_FLICK, 0.8f, 1.0f);
                case "hit" -> {
                    if (!KatanaCombat.targets(p, p.getWorld()).isEmpty()) {
                        hitstopUntil = lastReal + 0.07;             // «стоп‑кадр» при попадании
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Катана от 1‑го лица сейчас рисуется (своя, в руке, вид из глаз). */
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

    private static float[] clip(int clip, float s, float[] out) {
        float[] idle = KatanaKeys.TP_IDLE[0];
        if (clip < 0 || clip >= KatanaKeys.TP_SWINGS.length) {
            System.arraycopy(idle, 1, out, 0, out.length);
            return out;
        }
        float[][] keys = KatanaKeys.TP_SWINGS[clip];
        float end = keys[keys.length - 1][0];
        if (s <= end) {
            return BloodSwordPose.sample(keys, s, out);
        }
        float[] guard = new float[out.length];
        System.arraycopy(keys[keys.length - 1], 1, guard, 0, out.length);
        float[] rest = new float[out.length];
        System.arraycopy(idle, 1, rest, 0, out.length);
        return lerp(guard, rest, smooth((s - SHEATHE_AT) / SHEATHE_LEN), out);
    }

    static float[] values(State st, float now, float[] out) {
        clip(st.clip, now - st.start, out);
        float a = now - st.start;
        if (a < BLEND) {
            float[] prev = clip(st.prevClip, now - st.prevStart, new float[out.length]);
            lerp(prev, out, smooth(a / BLEND), out);
        }
        if (st.charge > 0.001f) {
            float[] ch = new float[out.length];
            System.arraycopy(KatanaKeys.TP_CHARGE[0], 1, ch, 0, out.length);
            lerp(out, ch, smooth(st.charge), out);
        }
        if (st.block > 0.001f) {
            float[] bl = new float[out.length];
            System.arraycopy(KatanaKeys.TP_BLOCK[0], 1, bl, 0, out.length);
            lerp(out, bl, smooth(st.block), out);
        }
        return out;
    }

    static BloodSwordPose.Frame frame(Vector3f g, Vector3f b, Vector3f n, BloodSwordPose.Frame out) {
        b.normalize();
        Vector3f z = new Vector3f(n).sub(new Vector3f(b).mul(n.dot(b))).normalize();
        Vector3f x = new Vector3f(b).cross(z);
        out.p.set(g);
        out.q.setFromNormalized(new Matrix3f(x, b, z));
        return out;
    }

    /** Из миксина BipedEntityModel.setAngles: игрок с катаной в руке — стойка, удар, зарядка. */
    public static void applyModel(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(model instanceof PlayerEntityModel) || !(entity instanceof PlayerEntity pl) || !KatanaCombat.holding(pl)) {
            return;
        }
        State st = state(entity.getId());
        float now = now();
        float chTarget = st.chargeStart >= 0 ? MathHelper.clamp((now - st.chargeStart) / 6f, 0f, 1f) : 0f;
        st.charge += (chTarget - st.charge) * 0.35f;
        boolean blocking = pl.isUsingItem() && pl.getActiveItem().getItem() instanceof com.lesha_top1972.basedefense.weapon.KatanaItem;
        st.block += ((blocking ? 1f : 0f) - st.block) * 0.4f;
        float[] v = values(st, now, new float[17]);
        float hipY = (model.rightLeg.pivotY + model.leftLeg.pivotY) * 0.5f;
        float hipZ = (model.rightLeg.pivotZ + model.leftLeg.pivotZ) * 0.5f;
        float lean = v[12] + model.body.pitch;
        float twist = v[13];
        float sh = MathHelper.clamp(v[16], 0f, 1f);
        Matrix3f r = new Matrix3f().rotateY(twist).rotateX(lean);
        Vector3f neck = new Vector3f(0, hipY, hipZ).add(r.transform(new Vector3f(0, -12, 0)));
        Vector3f shR = new Vector3f(neck).add(r.transform(new Vector3f(-5, 2, 0)));
        Vector3f shL = new Vector3f(neck).add(r.transform(new Vector3f(5, 2, 0)));
        Vector3f xRef = r.transform(new Vector3f(1, 0, 0));
        // ножны — у бедра (поза покоя без наклона корпуса); «в ножнах» — катана и правая кисть у них
        float[] idle = KatanaKeys.TP_IDLE[0];
        Vector3f neck0 = new Vector3f(0, hipY - 12, hipZ);
        Vector3f gi = new Vector3f(idle[1], idle[2], idle[3]).add(neck0);
        Vector3f handI = new Vector3f();
        aim(new Vector3f(neck0).add(-5, 2, 0), gi, -1f, new Vector3f(1, 0, 0), new Matrix3f(), handI);
        frame(handI, new Vector3f(idle[4], idle[5], idle[6]), new Vector3f(idle[7], idle[8], idle[9]), st.saya);
        Vector3f g = r.transform(new Vector3f(v[0], v[1], v[2])).add(neck);
        g.lerp(handI, sh);
        Vector3f l = r.transform(new Vector3f(v[9], v[10], v[11])).add(neck);
        Matrix3f armR = new Matrix3f();
        Matrix3f armL = new Matrix3f();
        Vector3f handR = new Vector3f();
        Vector3f handL = new Vector3f();
        aim(shR, g, -1f, xRef, armR, handR);
        aim(shL, l, 1f, xRef, armL, handL);

        Vector3f e = new Vector3f();
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
        BloodSwordPose.euler(armR, e);
        set(shR, e, model.rightArm);
        BloodSwordPose.euler(armL, e);
        set(shL, e, model.leftArm);
        float walk = MathHelper.clamp(1f - pl.limbAnimator.getSpeed() * 1.5f, 0f, 1f);  // на ходу ноги — свои
        model.leftLeg.pitch += v[14] * walk;
        model.rightLeg.pitch += v[15] * walk;
        if (model instanceof PlayerEntityModel<?> pm) {
            pm.jacket.copyTransform(model.body);
            pm.rightSleeve.copyTransform(model.rightArm);
            pm.leftSleeve.copyTransform(model.leftArm);
            pm.leftPants.copyTransform(model.leftLeg);
            pm.rightPants.copyTransform(model.rightLeg);
        }

        frame(handR, r.transform(new Vector3f(v[3], v[4], v[5])), r.transform(new Vector3f(v[6], v[7], v[8])), st.katana);
        if (sh > 0f) {
            st.katana.p.lerp(st.saya.p, sh);
            st.katana.q.slerp(st.saya.q, sh);
        }
        st.katana.p.div(16f);
        st.saya.p.div(16f);
        st.posed = true;
    }

    private static void set(Vector3f pivot, Vector3f euler, net.minecraft.client.model.ModelPart part) {
        part.pivotX = pivot.x;
        part.pivotY = pivot.y;
        part.pivotZ = pivot.z;
        part.pitch = euler.x;
        part.yaw = euler.y;
        part.roll = euler.z;
    }

    private static void aim(Vector3f sh, Vector3f target, float hx, Vector3f xRef, Matrix3f arm, Vector3f hand) {
        Vector3f a = new Vector3f(target);
        Vector3f local = new Vector3f();
        for (int i = 0; i < 4; i++) {
            BloodSwordPose.basis(new Vector3f(a).sub(sh), xRef, arm);
            arm.transform(local.set(hx, BloodSwordKeys.ARM_REACH, 0), hand).add(sh);
            a.add(new Vector3f(target).sub(hand));
        }
        BloodSwordPose.basis(new Vector3f(a).sub(sh), xRef, arm);
        arm.transform(local.set(hx, BloodSwordKeys.ARM_REACH, 0), hand).add(sh);
    }

    /** След от 3‑го лица идёт сейчас? */
    static float trailTP(State st, float now) {
        if (st.clip < 0 || st.clip >= TRAIL_TP.length) {
            return 0f;
        }
        float a = now - st.start;
        float[] w = TRAIL_TP[st.clip];
        for (int i = 0; i + 1 < w.length; i += 2) {
            if (a >= w[i] && a <= w[i + 1]) {
                return 1f;
            }
        }
        return 0f;
    }
}
