package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.effect.BossEffectsSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Эффекты босса под его полоской здоровья (список приходит с сервера — BossEffectsSync): значки эффектов
 * в ряд по центру под полоской, справа снизу у значка — уровень (у «Картриджа» — число картриджей).
 * Значок мигает, если эффекту осталось меньше 3 секунд. Отступ под рядом добавляет BossBarHudMixin.
 */
public final class BossEffectsHud {

    record Entry(StatusEffect effect, int amplifier, int duration) {
    }

    record Snapshot(List<Entry> list, long receivedAt) {
    }

    private static final Map<UUID, Snapshot> BARS = new HashMap<>();
    /** Через сколько тиков без обновления список считается устаревшим (босс пропал из виду). */
    private static final int STALE = 40;
    static final int ICON = 12;
    static final int STEP = 14;
    /** Сколько места занимает ряд значков под полоской. */
    public static final int ROW_HEIGHT = 15;

    private BossEffectsHud() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BossEffectsSync.PACKET, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            int n = buf.readVarInt();
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                StatusEffect e = Registries.STATUS_EFFECT.get(buf.readVarInt());
                int amp = buf.readVarInt();
                int dur = buf.readInt();
                if (e != null) {
                    list.add(new Entry(e, amp, dur));
                }
            }
            client.execute(() -> {
                if (client.world != null) {
                    BARS.put(id, new Snapshot(list, client.world.getTime()));
                }
            });
        });
    }

    /** Нарисовать ряд значков под полоской (x, y — левый верх полоски шириной 182); вернуть добавочный отступ. */
    public static int draw(DrawContext context, int x, int y, BossBar bar) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return 0;
        }
        Snapshot s = BARS.get(bar.getUuid());
        long now = client.world.getTime();
        if (s == null || s.list().isEmpty() || now - s.receivedAt() > STALE) {
            return 0;
        }
        int total = s.list().size() * STEP - (STEP - ICON);
        int ix = x + 91 - total / 2;
        int iy = y + 7;
        float time = now + client.getTickDelta();
        for (Entry e : s.list()) {
            // оставшееся время с поправкой на время с последнего пакета
            int left = e.duration() < 0 ? Integer.MAX_VALUE : e.duration() - (int) (now - s.receivedAt());
            float alpha = 1f;
            if (left < 60) {
                alpha = 0.45f + 0.55f * Math.abs(MathHelper.sin(time * 0.25f));
            }
            context.fill(ix - 1, iy - 1, ix + ICON + 1, iy + ICON + 1, 0x80000000);
            Sprite sprite = client.getStatusEffectSpriteManager().getSprite(e.effect());
            context.drawSprite(ix, iy, 0, ICON, ICON, sprite, 1f, 1f, 1f, alpha);
            String lvl = Integer.toString(e.amplifier() + 1);
            context.getMatrices().push();
            context.getMatrices().translate(ix + ICON + 1, iy + ICON + 1, 200);
            context.getMatrices().scale(0.75f, 0.75f, 1f);
            int w = client.textRenderer.getWidth(lvl);
            context.drawTextWithShadow(client.textRenderer, lvl, -w, -8, 0xFFFFFF);
            context.getMatrices().pop();
            ix += STEP;
        }
        return ROW_HEIGHT;
    }
}
