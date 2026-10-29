package com.lesha_top1972.basedefense.client.director;

import com.lesha_top1972.basedefense.director.DirectorEvent;
import com.lesha_top1972.basedefense.director.VisualHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Вид событий на клиенте: вечный туман (видно ~2 блока, размыто до 7; свет разгоняет туман),
 * кровавое небо и туман («Кровавая луна» ночью, «Кровавая вечная ночь» весь день), снегопад.
 * Сила эффектов плавно нарастает/спадает. Сам туман и цвета ставят BackgroundRendererMixin и ClientWorldSkyMixin.
 */
public final class DirectorVisuals {

    private static float fog, fogPrev;
    private static float blood, bloodPrev;
    private static float light, lightPrev;

    private DirectorVisuals() {
    }

    static void init() {
        VisualHooks.clientThread = Thread.currentThread();
    }

    static void tick(MinecraftClient client) {
        fogPrev = fog;
        bloodPrev = blood;
        lightPrev = light;
        if (client.world == null || client.player == null) {
            fog = blood = 0f;
            VisualHooks.forceNight = false;
            VisualHooks.snow = false;
            return;
        }
        long time = client.world.getTimeOfDay();
        long day = time / 24000L + 1;
        long t = Math.floorMod(time, 24000L);
        boolean today = DirectorClient.active && DirectorClient.todayDay == day;
        DirectorEvent e = DirectorClient.today;
        float fogTarget = today && e == DirectorEvent.ETERNAL_FOG ? 1f : 0f;
        float bloodTarget = today && (e == DirectorEvent.BLOOD_NIGHT
                || (e == DirectorEvent.BLOOD_MOON && t >= 12500 && t < 23500)) ? 1f : 0f;
        fog = approach(fog, fogTarget, 0.015f);
        blood = approach(blood, bloodTarget, 0.01f);
        VisualHooks.forceNight = today && e == DirectorEvent.BLOOD_NIGHT;
        VisualHooks.snow = today && e == DirectorEvent.SNOWFALL;
        // свет у камеры (факелы разгоняют туман) — сглаженно
        BlockPos p = BlockPos.ofFloored(client.gameRenderer.getCamera().getPos());
        float l = client.world.getLightLevel(LightType.BLOCK, p) / 15f;
        light = approach(light, l, 0.04f);
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    public static float fog(float tickDelta) {
        return MathHelper.lerp(tickDelta, fogPrev, fog);
    }

    public static float blood(float tickDelta) {
        return MathHelper.lerp(tickDelta, bloodPrev, blood);
    }

    /** Туман: где начинается и где сплошной (в блоках). */
    public static float fogStart(float tickDelta, float vanilla) {
        float f = fog(tickDelta);
        float l = MathHelper.lerp(tickDelta, lightPrev, light);
        float mine = 1.5f + l * l * 40f;
        return MathHelper.lerp(f, vanilla, Math.min(vanilla, mine));
    }

    public static float fogEnd(float tickDelta, float vanilla) {
        float f = fog(tickDelta);
        float l = MathHelper.lerp(tickDelta, lightPrev, light);
        float mine = 7f + (float) Math.pow(l, 1.5) * 90f;
        return MathHelper.lerp(f, vanilla, Math.min(vanilla, mine));
    }

    /** Цвет тумана/неба с учётом событий. */
    public static Vec3d tint(Vec3d c, float tickDelta, boolean sky) {
        float f = fog(tickDelta);
        float b = blood(tickDelta);
        if (f <= 0f && b <= 0f) {
            return c;
        }
        double lum = (c.x + c.y + c.z) / 3.0;
        if (b > 0f) {
            double k = MathHelper.clamp(0.35 + lum, 0.35, 1.0);
            Vec3d red = new Vec3d(0.42 * k, 0.03 * k, 0.04 * k);
            c = c.lerp(red, b * (sky ? 0.8 : 0.7));
        }
        if (f > 0f) {
            double g = MathHelper.clamp(0.07 + lum * 1.3, 0.07, 0.66);
            c = c.lerp(new Vec3d(g, g * 1.02, g * 1.05), f);
        }
        return c;
    }
}
