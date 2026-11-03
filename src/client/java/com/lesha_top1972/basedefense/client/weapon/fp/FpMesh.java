package com.lesha_top1972.basedefense.client.weapon.fp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Модель оружия сеткой (не кубами): assets/basedefense/mesh/&lt;имя&gt;.json — СОЗДАЁТ скрипт (tools/katana_gen.py).
 * Части (катана, ножны…) — списки четырёхугольников: на вершину x, y, z (px модели), u, v (0..1), нормаль.
 * Рисуется в текущей матрице: 1 px модели = 1 единица (масштаб задаёт вызывающий).
 */
public final class FpMesh {

    private static final int STRIDE = 8;
    private final Map<String, float[]> parts = new HashMap<>();

    private FpMesh() {
    }

    public static FpMesh load(String name) {
        FpMesh m = new FpMesh();
        JsonObject root = readJson("mesh/" + name + ".json");
        JsonObject ps = root.getAsJsonObject("parts");
        for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            float[] f = new float[a.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = a.get(i).getAsFloat();
            }
            m.parts.put(e.getKey(), f);
        }
        return m;
    }

    static JsonObject readJson(String path) {
        String full = "/assets/" + Basedefense.MOD_ID + "/" + path;
        try (InputStream in = FpMesh.class.getResourceAsStream(full)) {
            if (in == null) {
                throw new IllegalStateException("Нет файла " + full);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + full, e);
        }
    }

    /** Нарисовать часть: argb — множитель цвета (0xFFFFFFFF — как есть). */
    public void render(String part, MatrixStack matrices, VertexConsumer vc, int light, int overlay, int argb) {
        float[] d = this.parts.get(part);
        if (d == null) {
            return;
        }
        int a = argb >>> 24 & 0xFF;
        int r = argb >>> 16 & 0xFF;
        int g = argb >>> 8 & 0xFF;
        int b = argb & 0xFF;
        MatrixStack.Entry e = matrices.peek();
        Matrix4f pos = e.getPositionMatrix();
        Matrix3f nrm = e.getNormalMatrix();
        for (int i = 0; i + STRIDE <= d.length; i += STRIDE) {
            vc.vertex(pos, d[i], d[i + 1], d[i + 2]).color(r, g, b, a).texture(d[i + 3], d[i + 4]).overlay(overlay)
                    .light(light).normal(nrm, d[i + 5], d[i + 6], d[i + 7]).next();
        }
    }
}
