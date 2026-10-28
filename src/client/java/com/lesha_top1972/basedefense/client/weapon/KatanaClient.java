package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.combat.KatanaCombat;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Катана на клиенте: клики ЛКМ → серия из трёх ударов с рывком; позы (стойка у бедра, удары, стойка с
 * обнажённым мечом, возврат в ножны) для 3‑го и 1‑го лица — по ключам {@link KatanaKeys}.
 */
public final class KatanaClient {

    /** Возврат в ножны: через сколько тиков после удара начать и сколько длится. */
    private static final float SHEATHE_AT = 30f;
    private static final float SHEATHE_LEN = 8f;
    /** Переход между ударами (и выхват из ножен). */
    private static final float BLEND = 1.5f;

    /** Состояние у игрока: текущий удар серии (−1 — нет) и прошлый — для плавного перехода. */
    static final class State {
        int clip = -1;
        float start = -1000f;
        int prevClip = -1;
        float prevStart = -1000f;
        final BloodSwordPose.Frame katana = new BloodSwordPose.Frame();
        final BloodSwordPose.Frame saya = new BloodSwordPose.Frame();
        boolean posed;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long ticks;
    private static boolean buffered;
    private static long cooldownUntil;

    private KatanaClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(KatanaCombat.S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int idx = buf.readByte();
            client.execute(() -> {
                if (client.player != null && id != client.player.getId()) {
                    begin(state(id), idx, ticks);
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(KatanaClient::tick);
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
    }

    // =====================================================================
    // Ввод: вызывается вместо ванильного удара (MinecraftClient.doAttack)
    // =====================================================================
    /** @return true — клик съеден катаной. */
    public static boolean onAttack() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        if (p == null || !KatanaCombat.holding(p) || p.isSpectator()) {
            return false;
        }
        if (ticks < cooldownUntil || p.getItemCooldownManager().isCoolingDown(ModItems.KATANA)) {
            return true;
        }
        State s = state(p.getId());
        float age = ticks - s.start;
        if (s.clip >= 0 && age < KatanaCombat.NEXT_MIN) {
            if (age >= 2) {
                buffered = true;                         // нажал чуть раньше — удар начнётся сам
            }
            return true;
        }
        startSwing(p, s);
        return true;
    }

    private static void startSwing(ClientPlayerEntity p, State s) {
        float age = ticks - s.start;
        int idx = s.clip >= 0 && s.clip < 2 && age <= KatanaCombat.COMBO_WINDOW ? s.clip + 1 : 0;
        begin(s, idx, ticks);
        buffered = false;
        if (idx == 2) {
            cooldownUntil = ticks + KatanaCombat.COOLDOWN;
        }
        // рывок вперёд (движение своего игрока считает клиент)
        Vec3d look = p.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() > 1e-4) {
            flat = flat.normalize().multiply(KatanaCombat.DASH[idx] * (p.isOnGround() ? 1.0 : 0.35));
            p.addVelocity(flat.x, 0, flat.z);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(idx);
        ClientPlayNetworking.send(KatanaCombat.C2S, buf);
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        if (client.player == null || client.world == null) {
            STATES.clear();
            buffered = false;
            return;
        }
        ClientPlayerEntity p = client.player;
        if (buffered) {
            State s = STATES.get(p.getId());
            if (!KatanaCombat.holding(p) || s == null) {
                buffered = false;
            } else if (ticks - s.start >= KatanaCombat.NEXT_MIN) {
                startSwing(p, s);
            }
        }
        STATES.entrySet().removeIf(e -> {
            Entity ent = client.world.getEntityById(e.getKey());
            return !(ent instanceof PlayerEntity pl) || !KatanaCombat.holding(pl);
        });
    }

    // =====================================================================
    // Позы
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

    /** Ключ позы удара clip через s тиков (с обнажённой стойкой и возвратом в ножны). */
    private static float[] clip(float[] idle, float[][][] swings, int clip, float s, float[] out) {
        if (clip < 0) {
            System.arraycopy(idle, 1, out, 0, out.length);
            return out;
        }
        float[][] keys = swings[clip];
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

    static float[] values(State st, float[] idle, float[][][] swings, float now, float[] out) {
        clip(idle, swings, st.clip, now - st.start, out);
        float a = now - st.start;
        if (a < BLEND) {
            float[] from = clip(idle, swings, st.prevClip, now - st.prevStart, new float[out.length]);
            lerp(from, out, smooth(a / BLEND), out);
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

    /** Из миксина BipedEntityModel.setAngles: игрок с катаной в руке — стойка или удар. */
    public static void applyModel(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(model instanceof PlayerEntityModel) || !(entity instanceof PlayerEntity pl) || !KatanaCombat.holding(pl)) {
            return;
        }
        State st = state(entity.getId());
        float now = now();
        float[] v = values(st, KatanaKeys.TP_IDLE[0], KatanaKeys.TP_SWINGS, now, new float[16]);
        float hipY = (model.rightLeg.pivotY + model.leftLeg.pivotY) * 0.5f;
        float hipZ = (model.rightLeg.pivotZ + model.leftLeg.pivotZ) * 0.5f;
        float lean = v[12] + model.body.pitch;
        float twist = v[13];
        Matrix3f r = new Matrix3f().rotateY(twist).rotateX(lean);
        Vector3f neck = new Vector3f(0, hipY, hipZ).add(r.transform(new Vector3f(0, -12, 0)));
        Vector3f shR = new Vector3f(neck).add(r.transform(new Vector3f(-5, 2, 0)));
        Vector3f shL = new Vector3f(neck).add(r.transform(new Vector3f(5, 2, 0)));
        Vector3f g = r.transform(new Vector3f(v[0], v[1], v[2])).add(neck);
        Vector3f l = r.transform(new Vector3f(v[9], v[10], v[11])).add(neck);
        Vector3f xRef = r.transform(new Vector3f(1, 0, 0));
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

        frame(handR, r.transform(new Vector3f(v[3], v[4], v[5])), r.transform(new Vector3f(v[6], v[7], v[8])), st.katana);
        st.katana.p.div(16f);
        // ножны — всегда у бедра (поза покоя без наклона корпуса)
        float[] idle = KatanaKeys.TP_IDLE[0];
        Vector3f neck0 = new Vector3f(0, hipY - 12, hipZ);
        Vector3f gi = new Vector3f(idle[1], idle[2], idle[3]).add(neck0);
        Vector3f handI = new Vector3f();
        aim(new Vector3f(neck0).add(-5, 2, 0), gi, -1f, new Vector3f(1, 0, 0), new Matrix3f(), handI);
        frame(handI, new Vector3f(idle[4], idle[5], idle[6]), new Vector3f(idle[7], idle[8], idle[9]), st.saya);
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
}
