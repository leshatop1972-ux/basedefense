package com.lesha_top1972.basedefense.client.weapon;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.lesha_top1972.basedefense.client.weapon.BloodSwordKeys.ARM_REACH;
import static com.lesha_top1972.basedefense.client.weapon.BloodSwordKeys.GRIP_GAP;
import static com.lesha_top1972.basedefense.client.weapon.BloodSwordKeys.SCALE_1P;
import static com.lesha_top1972.basedefense.client.weapon.BloodSwordKeys.SCALE_3P;
import static com.lesha_top1972.basedefense.client.weapon.BloodSwordKeys.SHRUG;

/**
 * Математика поз меча кровожада (та же, что в tools/bt_sword_anim.py): сплайн по ключам, обратная кинематика
 * рук (кисть в точке хвата), положение меча; «покойные» позы — как у ванильного меча в руке.
 * m = 1 — правша, −1 — левша (позы зеркалятся по x).
 */
final class BloodSwordPose {

    private static final float DEG = (float) (Math.PI / 180.0);

    private BloodSwordPose() {
    }

    /** Положение и поворот (система меча: +y — клинок, начало — кисть у гарды). */
    static final class Frame {
        final Vector3f p = new Vector3f();
        final Quaternionf q = new Quaternionf();

        Frame set(Vector3f pos, Quaternionf rot) {
            this.p.set(pos);
            this.q.set(rot);
            return this;
        }

        Frame blend(Frame a, Frame b, float w) {
            a.p.lerp(b.p, w, this.p);
            a.q.slerp(b.q, w, this.q);
            return this;
        }

        Vector3f axisY(Vector3f out) {
            return this.q.transform(out.set(0, 1, 0));
        }
    }

    // =====================================================================
    // Сплайн Катмулла–Рома по ключам {t, значения…}
    // =====================================================================
    static float[] sample(float[][] keys, float t, float[] out) {
        int n = keys[0].length - 1;
        if (t <= keys[0][0]) {
            System.arraycopy(keys[0], 1, out, 0, n);
            return out;
        }
        int last = keys.length - 1;
        if (t >= keys[last][0]) {
            System.arraycopy(keys[last], 1, out, 0, n);
            return out;
        }
        int i = 0;
        while (keys[i + 1][0] < t) {
            i++;
        }
        float[] p0 = keys[Math.max(0, i - 1)];
        float[] p1 = keys[i];
        float[] p2 = keys[i + 1];
        float[] p3 = keys[Math.min(last, i + 2)];
        float f = (t - p1[0]) / (p2[0] - p1[0]);
        float f2 = f * f;
        float f3 = f2 * f;
        for (int j = 1; j <= n; j++) {
            float a = p0[j], b = p1[j], c = p2[j], d = p3[j];
            out[j - 1] = 0.5f * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f2 + (-a + 3 * b - 3 * c + d) * f3);
        }
        return out;
    }

    /** Поворот: +y → yAxis, +x — как можно ближе к xRef. */
    static Matrix3f basis(Vector3f yAxis, Vector3f xRef, Matrix3f out) {
        Vector3f y = new Vector3f(yAxis).normalize();
        Vector3f x = new Vector3f(xRef).sub(new Vector3f(y).mul(xRef.dot(y)));
        if (x.lengthSquared() < 1e-8f) {
            x.set(0, 0, 1).cross(y);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return out.set(x, y, z);
    }

    /** Углы ModelPart (pitch, yaw, roll): R = Rz·Ry·Rx. */
    static Vector3f euler(Matrix3f r, Vector3f out) {
        float yaw = (float) Math.asin(Math.max(-1f, Math.min(1f, -r.m02)));
        float pitch = (float) Math.atan2(r.m12, r.m22);
        float roll = (float) Math.atan2(r.m01, r.m00);
        return out.set(pitch, yaw, roll);
    }

    // =====================================================================
    // 3‑е лицо (пространство модели игрока, px, y вниз, вперёд −z)
    // =====================================================================
    static final class TP {
        final Vector3f neck = new Vector3f();
        final Vector3f shD = new Vector3f();
        final Vector3f shO = new Vector3f();
        final Vector3f handD = new Vector3f();
        final Vector3f handO = new Vector3f();
        final Matrix3f armD = new Matrix3f();
        final Matrix3f armO = new Matrix3f();
        final Matrix3f sword = new Matrix3f();
        float lean;
        float twist;
        float legL;
        float legR;
    }

    /**
     * v — 11 чисел ключа (хват, клинок, наклон, поворот, ноги, плечи). hip — опора ног (бёдра),
     * baseLean — наклон корпуса ванильной позы (присед).
     */
    static TP solveThirdPerson(float[] v, int m, float hipY, float hipZ, float baseLean, TP o) {
        Vector3f g = new Vector3f(v[0] * m, v[1], v[2]);
        Vector3f b = new Vector3f(v[3] * m, v[4], v[5]).normalize();
        o.lean = v[6] + baseLean;
        o.twist = v[7] * m;
        o.legL = m > 0 ? v[8] : v[9];
        o.legR = m > 0 ? v[9] : v[8];
        float shrug = v[10];
        Matrix3f r = new Matrix3f().rotateY(o.twist).rotateX(o.lean);
        o.neck.set(0, hipY, hipZ).add(r.transform(new Vector3f(0, -12, 0)));
        o.shD.set(o.neck).add(r.transform(new Vector3f((-5 + 0.3f * shrug) * m, 2 - SHRUG * shrug, 0)));
        o.shO.set(o.neck).add(r.transform(new Vector3f((5 - 0.3f * shrug) * m, 2 - SHRUG * shrug, 0)));
        r.transform(g).add(o.neck);
        r.transform(b);
        Vector3f xRef = r.transform(new Vector3f(1, 0, 0));
        aim(o.shD, g, -m, xRef, o.armD, o.handD);
        // вторая кисть — на рукояти ниже первой, там, куда достаёт рука
        float reachO = (float) Math.sqrt(ARM_REACH * ARM_REACH + 1);
        float best = Float.MAX_VALUE;
        Vector3f bestP = new Vector3f();
        Vector3f p = new Vector3f();
        for (int k = 0; k <= 40; k++) {
            float tt = GRIP_GAP * (0.55f + 0.9f * k / 40f) * SCALE_3P;
            p.set(b).mul(-tt).add(o.handD);
            float err = Math.abs(p.distance(o.shO) - reachO);
            if (err < best) {
                best = err;
                bestP.set(p);
            }
        }
        aim(o.shO, bestP, m, xRef, o.armO, o.handO);
        // плоскость клинка: нормаль ≈ ось x корпуса — рубящий удар сверху вниз
        Vector3f z = new Vector3f(xRef).sub(new Vector3f(b).mul(xRef.dot(b))).normalize();
        Vector3f x = new Vector3f(b).cross(z);
        o.sword.set(x, b, z);
        return o;
    }

    /** Направить руку из плеча sh так, чтобы середина кулака (hx, ARM_REACH, 0) пришла в target. */
    private static void aim(Vector3f sh, Vector3f target, float hx, Vector3f xRef, Matrix3f arm, Vector3f hand) {
        Vector3f a = new Vector3f(target);
        Vector3f local = new Vector3f();
        for (int i = 0; i < 4; i++) {
            basis(new Vector3f(a).sub(sh), xRef, arm);
            arm.transform(local.set(hx, ARM_REACH, 0), hand).add(sh);
            a.add(new Vector3f(target).sub(hand));
        }
        basis(new Vector3f(a).sub(sh), xRef, arm);
        arm.transform(local.set(hx, ARM_REACH, 0), hand).add(sh);
    }

    /** Поставить кости модели игрока, смешав с ванильной позой (w — вес 0..1). */
    static void applyThirdPerson(BipedEntityModel<?> model, TP o, int m, float w) {
        Vector3f e = new Vector3f();
        setPart(model.body, o.neck, o.lean, o.twist, 0f, w);
        lerpPivot(model.head, o.neck, w);
        model.hat.copyTransform(model.head);
        ModelPart armD = m > 0 ? model.rightArm : model.leftArm;
        ModelPart armO = m > 0 ? model.leftArm : model.rightArm;
        euler(o.armD, e);
        setPart(armD, o.shD, e.x, e.y, e.z, w);
        euler(o.armO, e);
        setPart(armO, o.shO, e.x, e.y, e.z, w);
        model.rightLeg.pitch += o.legR * w;
        model.leftLeg.pitch += o.legL * w;
    }

    static Frame swordThirdPerson(TP o, Frame out) {
        out.p.set(o.handD).div(16f);
        out.q.setFromNormalized(o.sword);
        return out;
    }

    private static void setPart(ModelPart part, Vector3f pivot, float pitch, float yaw, float roll, float w) {
        lerpPivot(part, pivot, w);
        part.pitch = lerpAngle(part.pitch, pitch, w);
        part.yaw = lerpAngle(part.yaw, yaw, w);
        part.roll = lerpAngle(part.roll, roll, w);
    }

    private static void lerpPivot(ModelPart part, Vector3f pivot, float w) {
        part.pivotX += (pivot.x - part.pivotX) * w;
        part.pivotY += (pivot.y - part.pivotY) * w;
        part.pivotZ += (pivot.z - part.pivotZ) * w;
    }

    private static float lerpAngle(float from, float to, float w) {
        float d = to - from;
        d = (float) Math.IEEEremainder(d, Math.PI * 2);
        return from + d * w;
    }

    // =====================================================================
    // 1‑е лицо (пространство камеры, блоки, y вверх, вперёд −z)
    // =====================================================================
    static final class FP {
        final Frame sword = new Frame();
        final Vector3f dirD = new Vector3f();   // от плеча к кисти (ведущая рука)
        final Vector3f dirO = new Vector3f();
    }

    static FP solveFirstPerson(float[] v, int m, FP o) {
        Vector3f g = new Vector3f(v[0] * m, v[1], v[2]);
        Vector3f b = new Vector3f(v[3] * m, v[4], v[5]).normalize();
        float roll = v[12] * m;
        Vector3f z0 = new Vector3f(1, 0, 0).sub(new Vector3f(b).mul(b.x)).normalize();
        Vector3f z1 = new Vector3f(b).cross(z0);
        Vector3f z = new Vector3f(z0).mul((float) Math.cos(roll)).add(z1.mul((float) Math.sin(roll)));
        Vector3f x = new Vector3f(b).cross(z);
        o.sword.p.set(g);
        o.sword.q.setFromNormalized(new Matrix3f(x, b, z));
        o.dirD.set(-v[6] * m, -v[7], -v[8]).normalize();
        o.dirO.set(-v[9] * m, -v[10], -v[11]).normalize();
        return o;
    }

    /** Кисть второй руки на рукояти (от 1‑го лица). */
    static Vector3f secondHandFirstPerson(Frame sword, Vector3f out) {
        sword.axisY(out).mul(-GRIP_GAP * SCALE_1P / 16f).add(sword.p);
        return out;
    }

    // =====================================================================
    // Покой: меч в руке как ванильный (handheld) — та же точка рукояти и тот же наклон
    // =====================================================================
    /**
     * Поза меча в системе «предмет в руке» (то, что получает ItemRenderer). Ванильный меч — плоская картинка
     * с рукоятью в пикселе (2.5, 2.5) и клинком по диагонали; наш стоит вдоль +y — поэтому ещё поворот на −45°.
     */
    static Frame rest(boolean firstPerson, boolean leftHanded, Frame out) {
        float tx = firstPerson ? 1.13f : 0f;
        float ty = firstPerson ? 3.2f : 4f;
        float tz = firstPerson ? 1.13f : 0.5f;
        float ry = -90f;
        float rz = firstPerson ? 25f : 55f;
        float s = firstPerson ? 0.68f : 0.85f;
        if (leftHanded) {
            tx = -tx;
            ry = -ry;
            rz = -rz;
        }
        Quaternionf q = new Quaternionf().rotationXYZ(0f, ry * DEG, rz * DEG);
        Vector3f h = q.transform(new Vector3f(-5.5f, -5.5f, 0f).mul(s / 16f));
        out.p.set(tx / 16f, ty / 16f, tz / 16f).add(h);
        out.q.set(q).rotateZ(-45f * DEG);
        return out;
    }
}
