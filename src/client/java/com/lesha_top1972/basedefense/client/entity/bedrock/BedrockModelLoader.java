package com.lesha_top1972.basedefense.client.entity.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Загружает модель из Blockbench в формате Bedrock (assets/basedefense/geo/&lt;имя&gt;.geo.json)
 * и превращает её в обычную модель Minecraft (без GeckoLib).
 * <p>
 * Перевод координат (как «Export Java Entity» в Blockbench): x тот же, y = 24 − y, z тот же,
 * повороты костей те же. Кубы с собственным поворотом становятся отдельной дочерней частью
 * «&lt;кость&gt;_r&lt;номер&gt;». Кость «root» переименовывается в «bone_root» (имя «root» занято корнем модели).
 * Те же формулы — в tools/mob_preview.py (предпросмотр без игры).
 */
public final class BedrockModelLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("basedefense");
    private static final float DEG = (float) (Math.PI / 180.0);

    private BedrockModelLoader() {
    }

    /** Имя кости в Java-модели. */
    public static String javaName(String bedrockName) {
        return "root".equals(bedrockName) ? "bone_root" : bedrockName;
    }

    static JsonObject readJson(String path) {
        String full = "/assets/" + Basedefense.MOD_ID + "/" + path;
        try (InputStream in = BedrockModelLoader.class.getResourceAsStream(full)) {
            if (in == null) {
                throw new IllegalStateException("Нет файла " + full);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + full, e);
        }
    }

    /** Модель assets/basedefense/geo/&lt;name&gt;.geo.json. */
    public static TexturedModelData load(String name) {
        JsonObject geo = readJson(AssetGroups.path("geo", name, ".geo.json"))
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.getAsJsonObject("description");
        int texW = desc.get("texture_width").getAsInt();
        int texH = desc.get("texture_height").getAsInt();

        Map<String, List<JsonObject>> byParent = new LinkedHashMap<>();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject bone = e.getAsJsonObject();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : "";
            byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(bone);
        }

        ModelData data = new ModelData();
        for (JsonObject bone : byParent.getOrDefault("", List.of())) {
            addBone(data.getRoot(), bone, new float[]{0, 0, 0}, byParent);
        }
        return TexturedModelData.of(data, texW, texH);
    }

    private static void addBone(ModelPartData parentData, JsonObject bone, float[] parentAbs,
                                Map<String, List<JsonObject>> byParent) {
        String name = bone.get("name").getAsString();
        float[] p = vec(bone, "pivot", 0);
        float[] jp = {p[0], 24 - p[1], p[2]};
        float[] r = vec(bone, "rotation", 0);
        boolean boneMirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();

        ModelPartBuilder builder = ModelPartBuilder.create();
        List<JsonObject> rotated = new ArrayList<>();
        List<Integer> rotatedIndex = new ArrayList<>();
        JsonArray cubes = bone.has("cubes") ? bone.getAsJsonArray("cubes") : new JsonArray();
        for (int i = 0; i < cubes.size(); i++) {
            JsonObject c = cubes.get(i).getAsJsonObject();
            float[] cr = vec(c, "rotation", 0);
            if (cr[0] != 0 || cr[1] != 0 || cr[2] != 0) {
                rotated.add(c);
                rotatedIndex.add(i);
                continue;
            }
            addCube(builder, c, p[0], p[1], p[2], boneMirror);
        }

        ModelPartData part = parentData.addChild(javaName(name), builder,
                ModelTransform.of(jp[0] - parentAbs[0], jp[1] - parentAbs[1], jp[2] - parentAbs[2],
                        r[0] * DEG, r[1] * DEG, r[2] * DEG));

        for (int k = 0; k < rotated.size(); k++) {
            JsonObject c = rotated.get(k);
            float[] cp = vec(c, "pivot", 0);
            float[] cr = vec(c, "rotation", 0);
            ModelPartBuilder cb = ModelPartBuilder.create();
            addCube(cb, c, cp[0], cp[1], cp[2], boneMirror);
            part.addChild(javaName(name) + "_r" + rotatedIndex.get(k), cb,
                    ModelTransform.of(cp[0] - jp[0], (24 - cp[1]) - jp[1], cp[2] - jp[2],
                            cr[0] * DEG, cr[1] * DEG, cr[2] * DEG));
        }

        for (JsonObject child : byParent.getOrDefault(name, List.of())) {
            addBone(part, child, jp, byParent);
        }
    }

    /** Куб относительно точки (px, py, pz) в координатах Bedrock. */
    private static void addCube(ModelPartBuilder builder, JsonObject c, float px, float py, float pz, boolean boneMirror) {
        float[] o = vec(c, "origin", 0);
        float[] s = vec(c, "size", 0);
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
        boolean mirror = c.has("mirror") ? c.get("mirror").getAsBoolean() : boneMirror;
        int u = 0;
        int v = 0;
        if (c.has("uv") && c.get("uv").isJsonArray()) {
            JsonArray uv = c.getAsJsonArray("uv");
            u = (int) uv.get(0).getAsFloat();
            v = (int) uv.get(1).getAsFloat();
        } else if (c.has("uv")) {
            LOGGER.warn("Модель: UV по граням не поддерживается, куб будет без правильной текстуры");
        }
        builder.uv(u, v).mirrored(mirror).cuboid(
                o[0] - px, py - o[1] - s[1], o[2] - pz,
                s[0], s[1], s[2], new Dilation(inflate));
    }

    private static float[] vec(JsonObject obj, String key, float def) {
        float[] out = {def, def, def};
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray a = obj.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                out[i] = a.get(i).getAsFloat();
            }
        }
        return out;
    }
}
