package com.lesha_top1972.basedefense.client.entity.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.animation.Keyframe;
import net.minecraft.client.render.entity.animation.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Загружает анимации Blockbench (Bedrock, assets/basedefense/animations/&lt;имя&gt;.animation.json)
 * во встроенную систему анимаций Minecraft (та же, что у Хранителя и верблюда).
 * <p>
 * Поддерживается: rotation / position / scale, ключи списком [x,y,z], {"pre","post"},
 * {"vector","easing"} (easing запекается промежуточными кадрами). Molang-выражения → 0.
 * Ключ в карте — короткое имя: "animation.iron_knight.attack1" → "attack1".
 */
public final class BedrockAnimations {

    private static final float DEG = (float) (Math.PI / 180.0);
    private static final int EASE_STEPS = 8;

    /** Анимация и её длина (длина в Animation спрятана в record без имени в Yarn, поэтому храним сами). */
    public record Clip(Animation animation, float length, boolean looping) {
    }

    private BedrockAnimations() {
    }

    public static Map<String, Clip> load(String name) {
        return load(name, bone -> true);
    }

    /** То же, но только кости, прошедшие фильтр (имена как в Java-модели). */
    public static Map<String, Clip> load(String name, Predicate<String> boneFilter) {
        JsonObject anims = BedrockModelLoader.readJson(AssetGroups.path("animations", name, ".animation.json"))
                .getAsJsonObject("animations");
        Map<String, Clip> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            String full = e.getKey();
            String shortName = full.substring(full.lastIndexOf('.') + 1);
            JsonObject an = e.getValue().getAsJsonObject();
            float length = an.has("animation_length") ? an.get("animation_length").getAsFloat() : 0.05f;
            boolean loop = an.has("loop") && an.get("loop").isJsonPrimitive()
                    && an.get("loop").getAsJsonPrimitive().isBoolean() && an.get("loop").getAsBoolean();

            Animation.Builder builder = Animation.Builder.create(Math.max(length, 0.05f));
            if (loop) {
                builder.looping();
            }
            if (an.has("bones")) {
                for (Map.Entry<String, JsonElement> b : an.getAsJsonObject("bones").entrySet()) {
                    String bone = BedrockModelLoader.javaName(b.getKey());
                    if (!boneFilter.test(bone)) {
                        continue;
                    }
                    JsonObject chans = b.getValue().getAsJsonObject();
                    addChannel(builder, bone, chans, "rotation", Transformation.Targets.ROTATE);
                    addChannel(builder, bone, chans, "position", Transformation.Targets.TRANSLATE);
                    addChannel(builder, bone, chans, "scale", Transformation.Targets.SCALE);
                }
            }
            out.put(shortName, new Clip(builder.build(), length, loop));
        }
        return out;
    }

    private record Raw(float time, float[] pre, float[] post, String easing) {
    }

    private static void addChannel(Animation.Builder builder, String bone, JsonObject chans, String key,
                                   Transformation.Target target) {
        if (!chans.has(key)) {
            return;
        }
        JsonElement ch = chans.get(key);
        List<Raw> raw = new ArrayList<>();
        if (ch.isJsonObject()) {
            for (Map.Entry<String, JsonElement> k : ch.getAsJsonObject().entrySet()) {
                float t;
                try {
                    t = Float.parseFloat(k.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                JsonElement kv = k.getValue();
                if (kv.isJsonObject()) {
                    JsonObject o = kv.getAsJsonObject();
                    if (o.has("vector")) {
                        float[] v = vec(o.get("vector"), key);
                        raw.add(new Raw(t, v, v, o.has("easing") ? o.get("easing").getAsString() : null));
                    } else {
                        JsonElement pre = o.has("pre") ? o.get("pre") : o.get("post");
                        JsonElement post = o.has("post") ? o.get("post") : pre;
                        raw.add(new Raw(t, vec(pre, key), vec(post, key), null));
                    }
                } else {
                    float[] v = vec(kv, key);
                    raw.add(new Raw(t, v, v, null));
                }
            }
        } else {
            float[] v = vec(ch, key);
            raw.add(new Raw(0, v, v, null));
        }
        if (raw.isEmpty()) {
            return;
        }
        raw.sort((a, b) -> Float.compare(a.time, b.time));

        List<Keyframe> frames = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Raw r = raw.get(i);
            if (i > 0 && r.easing != null && !"linear".equals(r.easing)) {
                Raw prev = raw.get(i - 1);
                for (int s = 1; s < EASE_STEPS; s++) {
                    float f = s / (float) EASE_STEPS;
                    float e = ease(r.easing, f);
                    float[] v = new float[3];
                    for (int j = 0; j < 3; j++) {
                        v[j] = prev.post[j] + (r.pre[j] - prev.post[j]) * e;
                    }
                    frames.add(frame(prev.time + (r.time - prev.time) * f, v, key));
                }
            }
            frames.add(frame(r.time, r.pre, key));
            if (r.post[0] != r.pre[0] || r.post[1] != r.pre[1] || r.post[2] != r.pre[2]) {
                frames.add(frame(r.time + 0.0001f, r.post, key));
            }
        }
        builder.addBoneAnimation(bone, new Transformation(target, frames.toArray(new Keyframe[0])));
    }

    private static Keyframe frame(float time, float[] v, String key) {
        Vector3f vec = switch (key) {
            case "rotation" -> new Vector3f(v[0] * DEG, v[1] * DEG, v[2] * DEG);
            case "position" -> new Vector3f(v[0], -v[1], v[2]);
            default -> new Vector3f(v[0] - 1f, v[1] - 1f, v[2] - 1f);
        };
        return new Keyframe(time, vec, Transformation.Interpolations.LINEAR);
    }

    private static float[] vec(JsonElement e, String key) {
        float def = "scale".equals(key) ? 1f : 0f;
        float[] out = {def, def, def};
        if (e == null) {
            return out;
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) {
                out[i] = num(a.get(i), def);
            }
        } else if (e.isJsonPrimitive()) {
            float v = num(e, def);
            out[0] = v;
            out[1] = v;
            out[2] = v;
        }
        return out;
    }

    private static float num(JsonElement e, float def) {
        try {
            return e.getAsFloat();
        } catch (RuntimeException ex) {
            return def; // Molang-выражение — не поддерживается
        }
    }

    private static float ease(String name, float x) {
        return switch (name) {
            case "easeInQuad" -> x * x;
            case "easeOutQuad" -> 1 - (1 - x) * (1 - x);
            case "easeInOutQuad" -> x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
            case "easeInCubic" -> x * x * x;
            case "easeOutCubic" -> 1 - (float) Math.pow(1 - x, 3);
            case "easeInOutCubic" -> x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
            case "easeInSine" -> 1 - (float) Math.cos(x * Math.PI / 2);
            case "easeOutSine" -> (float) Math.sin(x * Math.PI / 2);
            case "easeInOutSine" -> -((float) Math.cos(Math.PI * x) - 1) / 2;
            default -> x;
        };
    }
}
