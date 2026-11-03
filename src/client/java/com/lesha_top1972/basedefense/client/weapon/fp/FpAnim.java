package com.lesha_top1972.basedefense.client.weapon.fp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Анимации оружия от 1‑го лица: assets/basedefense/fpanim/&lt;имя&gt;.json (СОЗДАЁТ tools/katana_gen.py).
 * Кадры запечены 60 раз в секунду в пространстве камеры (блоки: x вправо, y вверх, вперёд −z):
 * G(3) — середина правого кулака, q(4) — поворот оружия, Lp(3)/La(3) — левый кулак и ось рукояти в нём,
 * AR(3)/AL(3) — «плечи» за краем экрана, cam(3) — наклон/поворот/крен камеры (градусы), fov, след, свечение,
 * видимость ножен, ножны: точка(3) и поворот(4).
 */
public final class FpAnim {

    public static final int CH = 33;

    public final float fps;
    public final float scale;
    public final float armWidth;
    public final float[] trailBase;
    public final float[] trailTip;
    public final float trailLength;
    public final float[] flare;
    /** Куда уезжают ножны за край экрана, когда исчезают/появляются при плавном переходе. */
    public final Vector3f sayaHide = new Vector3f();
    private final Map<String, Clip> clips = new HashMap<>();

    public static final class Clip {
        public final String name;
        public final float length;
        public final boolean loop;
        public final float[] eventTimes;
        public final String[] events;
        final float[][] frames;

        Clip(String name, float length, boolean loop, float[][] frames, float[] eventTimes, String[] events) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.frames = frames;
            this.eventTimes = eventTimes;
            this.events = events;
        }
    }

    /** Поза в один момент. */
    public static final class Pose {
        public final Vector3f g = new Vector3f();
        public final Quaternionf q = new Quaternionf();
        public final Vector3f lp = new Vector3f();
        public final Vector3f la = new Vector3f();
        public final Vector3f ar = new Vector3f();
        public final Vector3f al = new Vector3f();
        public final Vector3f cam = new Vector3f();
        public float fov = 1f;
        public float trail;
        public float glow;
        public float saya;
        public final Vector3f sp = new Vector3f();
        public final Quaternionf sq = new Quaternionf();

        public Pose set(Pose o) {
            g.set(o.g);
            q.set(o.q);
            lp.set(o.lp);
            la.set(o.la);
            ar.set(o.ar);
            al.set(o.al);
            cam.set(o.cam);
            fov = o.fov;
            trail = o.trail;
            glow = o.glow;
            saya = o.saya;
            sp.set(o.sp);
            sq.set(o.sq);
            return this;
        }

        /** this = a + (b − a)·w (кватернионы — по кратчайшей дуге). Ножны, которые появляются или исчезают,
         *  уезжают за край экрана (hide), а не пропадают на месте. */
        public Pose blend(Pose a, Pose b, float w) {
            return blend(a, b, w, null);
        }

        public Pose blend(Pose a, Pose b, float w, Vector3f hide) {
            a.g.lerp(b.g, w, g);
            nlerp(a.q, b.q, w, q);
            a.lp.lerp(b.lp, w, lp);
            a.la.lerp(b.la, w, la);
            if (la.lengthSquared() > 1e-8f) {
                la.normalize();
            }
            a.ar.lerp(b.ar, w, ar);
            a.al.lerp(b.al, w, al);
            a.cam.lerp(b.cam, w, cam);
            fov = MathHelper.lerp(w, a.fov, b.fov);
            trail = MathHelper.lerp(w, a.trail, b.trail);
            glow = MathHelper.lerp(w, a.glow, b.glow);
            saya = MathHelper.lerp(w, a.saya, b.saya);
            if (a.saya <= 0.001f) {
                sp.set(b.sp);
                sq.set(b.sq);
                if (hide != null) {
                    sp.add(hide.x * (1f - w), hide.y * (1f - w), hide.z * (1f - w));
                    saya = b.saya;
                }
            } else if (b.saya <= 0.001f) {
                sp.set(a.sp);
                sq.set(a.sq);
                if (hide != null) {
                    sp.add(hide.x * w, hide.y * w, hide.z * w);
                    saya = w < 0.999f ? a.saya : 0f;
                }
            } else {
                a.sp.lerp(b.sp, w, sp);
                nlerp(a.sq, b.sq, w, sq);
            }
            return this;
        }
    }

    static void nlerp(Quaternionf a, Quaternionf b, float w, Quaternionf out) {
        float d = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        float s = d < 0 ? -1f : 1f;
        out.set(a.x + (s * b.x - a.x) * w, a.y + (s * b.y - a.y) * w, a.z + (s * b.z - a.z) * w, a.w + (s * b.w - a.w) * w);
        out.normalize();
    }

    private FpAnim(JsonObject root) {
        this.fps = root.get("fps").getAsFloat();
        this.scale = root.get("scale").getAsFloat();
        this.armWidth = root.get("armWidth").getAsFloat();
        JsonObject tr = root.getAsJsonObject("trail");
        this.trailBase = floats(tr.getAsJsonArray("base"));
        this.trailTip = floats(tr.getAsJsonArray("tip"));
        this.trailLength = tr.get("length").getAsFloat();
        this.flare = floats(root.getAsJsonArray("flare"));
        if (root.has("sayaHide")) {
            float[] h = floats(root.getAsJsonArray("sayaHide"));
            this.sayaHide.set(h[0], h[1], h[2]);
        }
        JsonObject cs = root.getAsJsonObject("clips");
        for (Map.Entry<String, JsonElement> e : cs.entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            JsonArray fr = c.getAsJsonArray("frames");
            float[][] frames = new float[fr.size()][];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = floats(fr.get(i).getAsJsonArray());
            }
            JsonArray ev = c.getAsJsonArray("events");
            List<Float> et = new ArrayList<>();
            List<String> en = new ArrayList<>();
            for (JsonElement x : ev) {
                JsonArray a = x.getAsJsonArray();
                et.add(a.get(0).getAsFloat());
                en.add(a.get(1).getAsString());
            }
            float[] times = new float[et.size()];
            for (int i = 0; i < times.length; i++) {
                times[i] = et.get(i);
            }
            this.clips.put(e.getKey(), new Clip(e.getKey(), c.get("length").getAsFloat(), c.get("loop").getAsBoolean(),
                    frames, times, en.toArray(new String[0])));
        }
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = a.get(i).getAsFloat();
        }
        return f;
    }

    public static FpAnim load(String name) {
        return new FpAnim(FpMesh.readJson("fpanim/" + name + ".json"));
    }

    public Clip clip(String name) {
        return this.clips.get(name);
    }

    /** Поза клипа в момент t (секунды; у петли — по кругу, иначе — с упором в края). */
    public Pose sample(Clip c, float t, Pose out) {
        int n = c.frames.length;
        float f;
        if (c.loop) {
            float len = c.length;
            t = ((t % len) + len) % len;
            f = t * this.fps;
        } else {
            f = MathHelper.clamp(t, 0f, c.length) * this.fps;
        }
        int i = MathHelper.clamp((int) Math.floor(f), 0, n - 1);
        int j = Math.min(i + 1, n - 1);
        float w = MathHelper.clamp(f - i, 0f, 1f);
        float[] a = c.frames[i];
        float[] b = c.frames[j];
        out.g.set(l(a, b, 0, w), l(a, b, 1, w), l(a, b, 2, w));
        nlerp(new Quaternionf(a[3], a[4], a[5], a[6]), new Quaternionf(b[3], b[4], b[5], b[6]), w, out.q);
        out.lp.set(l(a, b, 7, w), l(a, b, 8, w), l(a, b, 9, w));
        out.la.set(l(a, b, 10, w), l(a, b, 11, w), l(a, b, 12, w));
        if (out.la.lengthSquared() > 1e-8f) {
            out.la.normalize();
        }
        out.ar.set(l(a, b, 13, w), l(a, b, 14, w), l(a, b, 15, w));
        out.al.set(l(a, b, 16, w), l(a, b, 17, w), l(a, b, 18, w));
        out.cam.set(l(a, b, 19, w), l(a, b, 20, w), l(a, b, 21, w));
        out.fov = l(a, b, 22, w);
        out.trail = l(a, b, 23, w);
        out.glow = l(a, b, 24, w);
        out.saya = l(a, b, 25, w);
        out.sp.set(l(a, b, 26, w), l(a, b, 27, w), l(a, b, 28, w));
        nlerp(new Quaternionf(a[29], a[30], a[31], a[32]), new Quaternionf(b[29], b[30], b[31], b[32]), w, out.sq);
        return out;
    }

    private static float l(float[] a, float[] b, int k, float w) {
        return a[k] + (b[k] - a[k]) * w;
    }
}
