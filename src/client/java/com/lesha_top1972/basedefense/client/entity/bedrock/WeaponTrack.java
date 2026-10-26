package com.lesha_top1972.basedefense.client.entity.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Дорожки оружия (animations/&lt;моб&gt;.weapon.json, создаёт tools/nabat_gen.py): для каждого клипа —
 * поворот оружия в системе модели (кватернион x, y, z, w) с шагом 1/fps. Оружие — отдельная модель:
 * ставится в точку кисти, а поворот берётся отсюда. Смешивание клипов — взвешенная сумма кватернионов
 * с выравниванием знака (то же, что slerp для малых различий): на переходах оружие не «прокручивается».
 */
public final class WeaponTrack {

    private record Track(float fps, float length, boolean loop, float[][] q) {
    }

    private final Map<String, Track> clips = new HashMap<>();
    /** Начало модели оружия от запястья (в системе оружия, пиксели Java). */
    public final float gripX;
    public final float gripY;
    public final float gripZ;
    public final String hand;

    // накопитель смешивания
    private float ax;
    private float ay;
    private float az;
    private float aw;
    private float total;

    public WeaponTrack(String mob) {
        JsonObject root = BedrockModelLoader.readJson("animations/" + mob + ".weapon.json");
        JsonArray g = root.getAsJsonArray("grip_offset");
        this.gripX = g.get(0).getAsFloat();
        this.gripY = g.get(1).getAsFloat();
        this.gripZ = g.get(2).getAsFloat();
        this.hand = root.has("hand") ? root.get("hand").getAsString() : "right_hand";
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("clips").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            JsonArray arr = c.getAsJsonArray("q");
            float[][] q = new float[arr.size()][4];
            for (int i = 0; i < arr.size(); i++) {
                JsonArray v = arr.get(i).getAsJsonArray();
                for (int k = 0; k < 4; k++) {
                    q[i][k] = v.get(k).getAsFloat();
                }
            }
            this.clips.put(e.getKey(), new Track(c.get("fps").getAsFloat(), c.get("length").getAsFloat(),
                    c.get("loop").getAsBoolean(), q));
        }
    }

    public void reset() {
        this.ax = this.ay = this.az = this.aw = 0f;
        this.total = 0f;
    }

    /** Добавить клип в момент seconds с весом weight (как play() у модели). */
    public void add(String clip, float seconds, float weight) {
        Track tr = this.clips.get(clip);
        if (tr == null || weight <= 0.001f || tr.q.length == 0) {
            return;
        }
        float t = Math.max(0f, seconds);
        if (tr.loop && tr.length > 0) {
            t = t % tr.length;
        }
        float x = Math.min(tr.q.length - 1f, t * tr.fps);
        int i = (int) x;
        float f = x - i;
        float[] a = tr.q[i];
        float[] b = tr.q[Math.min(i + 1, tr.q.length - 1)];
        float qx = a[0] + (b[0] - a[0]) * f;
        float qy = a[1] + (b[1] - a[1]) * f;
        float qz = a[2] + (b[2] - a[2]) * f;
        float qw = a[3] + (b[3] - a[3]) * f;
        // выровнять знак с уже накопленным (q и −q — один и тот же поворот)
        if (this.total > 0 && qx * this.ax + qy * this.ay + qz * this.az + qw * this.aw < 0) {
            qx = -qx;
            qy = -qy;
            qz = -qz;
            qw = -qw;
        }
        this.ax += qx * weight;
        this.ay += qy * weight;
        this.az += qz * weight;
        this.aw += qw * weight;
        this.total += weight;
    }

    /** Итоговый поворот (единичный). */
    public Quaternionf result(Quaternionf out) {
        float l = (float) Math.sqrt(this.ax * this.ax + this.ay * this.ay + this.az * this.az + this.aw * this.aw);
        if (l < 1e-6f) {
            return out.identity();
        }
        return out.set(this.ax / l, this.ay / l, this.az / l, this.aw / l);
    }
}
