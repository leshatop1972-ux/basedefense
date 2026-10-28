package com.lesha_top1972.basedefense.client.artifact;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.artifact.ArtifactSlot;
import com.lesha_top1972.basedefense.artifact.Artifacts;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Артефакты на клиенте: кнопка и вкладка с 5 слотами в инвентаре, клавиша рывка (R) и анимация рывка
 * (3‑е лицо — наклон корпуса, руки назад, ноги в шаге; 1‑е лицо — предмет в руке уходит вниз‑назад,
 * обзор чуть шире) и клавиша взлёта (Y): подброс на 3.5 блока, до 3 раз подряд. Частиц нет — только анимация.
 */
public final class ArtifactClient {

    private static final Identifier BUTTON = new Identifier(Basedefense.MOD_ID, "textures/gui/artifact_button.png");

    /** Вкладка: слева от инвентаря, у брони. */
    public static final int PANEL_X = -31;
    public static final int PANEL_Y = 2;
    public static final int PANEL_W = 30;
    public static final int PANEL_H = Artifacts.SLOTS * 18 + 10;

    private static boolean panelOpen;
    private static KeyBinding dashKey;
    private static long ticks;
    private static int dashLeft;
    private static boolean dashStop;
    private static long cooldownUntil;
    private static final Vector3f DIR = new Vector3f();

    private static KeyBinding leapKey;
    private static int leapUsed;
    private static long leapLast = -1000;
    private static long leapCooldownUntil;
    /** Взлёты (свой и чужие): номер сущности → время начала. */
    private static final Map<Integer, Long> LEAPS = new HashMap<>();

    /** Рывки (свой и чужие): номер сущности → {время начала, вперёд, вбок}. */
    private static final Map<Integer, float[]> DASHES = new HashMap<>();

    private ArtifactClient() {
    }

    /** Вкладка видна (и слоты активны): открыта кнопкой, в обычном инвентаре и без книги рецептов. */
    public static boolean panelVisible() {
        return panelOpen && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen inv
                && !((ArtifactPanelHost) inv).basedefense$recipeBookOpen();
    }

    public static void init() {
        ArtifactSlot.visible = ArtifactClient::panelVisible;
        dashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.basedefense.dash", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R, "category.basedefense"));
        leapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.basedefense.leap", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y, "category.basedefense"));
        ClientPlayNetworking.registerGlobalReceiver(Artifacts.LEAP_S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            client.execute(() -> LEAPS.put(id, ticks));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }
            TexturedButtonWidget b = new TexturedButtonWidget(0, 0, 20, 18, 0, 0, 18, BUTTON, 20, 36,
                    btn -> panelOpen = !panelOpen);
            b.setTooltip(Tooltip.of(Text.translatable("gui.basedefense.artifacts")));
            Screens.getButtons(screen).add(b);
            // книга рецептов сдвигает окно — кнопка всегда справа от её кнопки
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) ->
                    b.setPosition(((ArtifactPanelHost) s).basedefense$left() + 126, s.height / 2 - 22));
        });
        ClientPlayNetworking.registerGlobalReceiver(Artifacts.DASH_S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            float f = buf.readFloat();
            float s = buf.readFloat();
            client.execute(() -> DASHES.put(id, new float[]{ticks, f, s}));
        });
        ClientTickEvents.START_CLIENT_TICK.register(ArtifactClient::applyDash);
        ClientTickEvents.END_CLIENT_TICK.register(ArtifactClient::tick);
    }

    /** Фон вкладки (из InventoryScreen.drawBackground): панель в стиле окна и 5 ячеек. */
    public static void drawPanel(DrawContext ctx, int left, int top) {
        int x0 = left + PANEL_X;
        int y0 = top + PANEL_Y;
        int x1 = x0 + PANEL_W;
        int y1 = y0 + PANEL_H;
        ctx.fill(x0, y0, x1, y1, 0xFF000000);
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        ctx.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
        ctx.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
        ctx.fill(x0 + 3, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
        ctx.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 1, 0xFF555555);
        for (int i = 0; i < Artifacts.SLOTS; i++) {
            int sx = left + ArtifactSlot.X - 1;
            int sy = top + ArtifactSlot.Y0 + i * 18 - 1;
            ctx.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
            ctx.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
            ctx.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
            ctx.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
            ctx.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            // пустая ячейка — бледный ромб
            int cx = sx + 9;
            int cy = sy + 9;
            for (int k = -4; k <= 4; k++) {
                int wdt = 4 - Math.abs(k);
                ctx.fill(cx - wdt, cy + k, cx - wdt + 1, cy + k + 1, 0xFF7A7A7A);
                ctx.fill(cx + wdt, cy + k, cx + wdt + 1, cy + k + 1, 0xFF7A7A7A);
            }
        }
    }

    public static boolean inPanel(double mx, double my, int left, int top) {
        return mx >= left + PANEL_X && mx < left + PANEL_X + PANEL_W && my >= top + PANEL_Y && my < top + PANEL_Y + PANEL_H;
    }

    // =====================================================================
    // Рывок
    // =====================================================================
    private static void tick(MinecraftClient client) {
        ticks++;
        if (client.player == null || client.world == null) {
            DASHES.clear();
            dashLeft = 0;
            return;
        }
        while (dashKey.wasPressed()) {
            tryDash(client.player);
        }
        while (leapKey.wasPressed()) {
            tryLeap(client.player);
        }
        DASHES.entrySet().removeIf(e -> ticks - e.getValue()[0] > 10);
        LEAPS.entrySet().removeIf(e -> ticks - e.getValue() > 14);
    }

    private static void tryDash(ClientPlayerEntity p) {
        if (p.isSpectator() || p.hasVehicle() || dashLeft > 0 || ticks < cooldownUntil
                || !Artifacts.has(p, ModItems.DASH_AMULET)
                || p.getItemCooldownManager().isCoolingDown(ModItems.DASH_AMULET)) {
            return;
        }
        float f = p.input.movementForward;
        float s = p.input.movementSideways;
        float len = MathHelper.sqrt(f * f + s * s);
        if (len < 0.01f) {
            f = 1f;
            s = 0f;
        } else {
            f /= len;
            s /= len;
        }
        float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        DIR.set(s * MathHelper.cos(yaw) - f * MathHelper.sin(yaw), 0f, f * MathHelper.cos(yaw) + s * MathHelper.sin(yaw))
                .normalize();
        dashLeft = Artifacts.DASH_TICKS;
        cooldownUntil = ticks + Artifacts.DASH_COOLDOWN;
        DASHES.put(p.getId(), new float[]{ticks, f, s});
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(f);
        buf.writeFloat(s);
        ClientPlayNetworking.send(Artifacts.DASH_C2S, buf);
    }

    /** Взлёт: подброс вверх на 3.5 блока; 3 раза подряд, потом перезарядка 6 с. */
    private static void tryLeap(ClientPlayerEntity p) {
        if (p.isSpectator() || p.hasVehicle() || ticks < leapCooldownUntil || !Artifacts.has(p, ModItems.LEAP_AMULET)
                || p.getItemCooldownManager().isCoolingDown(ModItems.LEAP_AMULET)) {
            return;
        }
        if (ticks - leapLast > Artifacts.LEAP_COOLDOWN) {
            leapUsed = 0;
        }
        leapUsed++;
        leapLast = ticks;
        if (leapUsed >= Artifacts.LEAP_CHARGES) {
            leapUsed = 0;
            leapCooldownUntil = ticks + Artifacts.LEAP_COOLDOWN;
        }
        p.setVelocity(p.getVelocity().x, Artifacts.LEAP_SPEED, p.getVelocity().z);
        p.fallDistance = 0f;
        LEAPS.put(p.getId(), ticks);
        ClientPlayNetworking.send(Artifacts.LEAP_C2S, PacketByteBufs.create());
    }

    /** Скорость рывка ставится до движения игрока в этом тике: 5 тиков по 0.8 блока, потом торможение. */
    private static void applyDash(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) {
            return;
        }
        if (dashLeft > 0) {
            p.setVelocity(DIR.x * Artifacts.DASH_SPEED, 0.0, DIR.z * Artifacts.DASH_SPEED);
            p.fallDistance = 0f;
            dashLeft--;
            dashStop = dashLeft == 0;
        } else if (dashStop) {
            dashStop = false;
            p.setVelocity(DIR.x * 0.12, p.getVelocity().y, DIR.z * 0.12);
        }
    }

    /** Вес анимации рывка 0..1 (быстро нарастает, держится, плавно спадает). */
    public static float weight(int entityId) {
        float[] d = DASHES.get(entityId);
        if (d == null) {
            return 0f;
        }
        float a = ticks + MinecraftClient.getInstance().getTickDelta() - d[0];
        if (a < 0f || a > 8f) {
            return 0f;
        }
        float w = a < 1.5f ? a / 1.5f : a < 5f ? 1f : 1f - (a - 5f) / 3f;
        return w * w * (3f - 2f * w);
    }

    /** Вес анимации взлёта 0..1. */
    public static float leapWeight(int entityId) {
        Long t0 = LEAPS.get(entityId);
        if (t0 == null) {
            return 0f;
        }
        float a = ticks + MinecraftClient.getInstance().getTickDelta() - t0;
        if (a < 0f || a > 12f) {
            return 0f;
        }
        float w = a < 2f ? a / 2f : a < 6f ? 1f : 1f - (a - 6f) / 6f;
        return w * w * (3f - 2f * w);
    }

    /** Для 1‑го лица (предмет в руке уходит вниз) и обзора. */
    public static float localWeight() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? 0f : Math.max(weight(p.getId()), leapWeight(p.getId()) * 0.8f);
    }

    /** 3‑е лицо (после поз оружия): корпус наклонён по ходу рывка, руки отведены назад, ноги в шаге. */
    public static void applyModel(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(model instanceof PlayerEntityModel)) {
            return;
        }
        float lw = leapWeight(entity.getId());
        if (lw > 0f) {                               // взлёт: руки вверх, ноги поджаты
            model.rightArm.pitch += (-2.6f - model.rightArm.pitch) * lw;
            model.leftArm.pitch += (-2.6f - model.leftArm.pitch) * lw;
            model.rightArm.roll += 0.3f * lw;
            model.leftArm.roll -= 0.3f * lw;
            model.rightLeg.pitch += -0.5f * lw;
            model.leftLeg.pitch += -0.2f * lw;
            model.body.pitch += -0.1f * lw;
        }
        float w = weight(entity.getId());
        if (w <= 0f) {
            return;
        }
        float[] d = DASHES.get(entity.getId());
        float f = d[1];
        float s = d[2];
        model.body.pitch += 0.45f * f * w;
        model.body.roll += -0.22f * s * w;
        float back = (0.9f * Math.max(f, 0f) + 0.5f * Math.abs(s) - 0.3f * Math.min(f, 0f)) * w;
        model.rightArm.pitch += back;
        model.leftArm.pitch += back;
        model.rightArm.roll += 0.35f * w;
        model.leftArm.roll -= 0.35f * w;
        model.rightLeg.pitch += -0.7f * w;
        model.leftLeg.pitch += 0.55f * w;
    }
}
