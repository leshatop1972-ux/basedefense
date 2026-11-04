package com.lesha_top1972.basedefense.client.season;

import com.lesha_top1972.basedefense.season.Season;
import com.lesha_top1972.basedefense.season.Seasons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Сезон на клиенте: сдвиг календаря приходит с сервера, сезон считается по времени мира каждый тик.
 * Цвет листвы и травы ({@link SeasonColors}) меняется по «стадиям» (осень желтеет за 3 дня); при смене стадии
 * чанки перерисовываются (раз в день, не чаще).
 */
public final class SeasonClient {

    private static long offset;
    private static int stage = -1;

    private SeasonClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Seasons.SYNC, (client, handler, buf, sender) -> {
            long o = buf.readLong();
            client.execute(() -> offset = o);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            offset = 0;
            stage = -1;
            Seasons.setClientSeason(Season.SUMMER);
            SeasonColors.set(Season.SUMMER, 0);
        });
        ClientTickEvents.END_CLIENT_TICK.register(SeasonClient::tick);
    }

    private static void tick(MinecraftClient c) {
        if (c.world == null) {
            return;
        }
        long time = c.world.getTimeOfDay();
        Season s = Seasons.seasonAt(time, offset);
        int day = Seasons.dayInSeason(time, offset);
        Seasons.setClientSeason(s);
        int k = s == Season.AUTUMN ? Math.min(day, 2) : 0;   // осень: 1‑й, 2‑й, 3‑й+ день
        int st = s.ordinal() * 10 + k;
        if (st != stage) {
            stage = st;
            SeasonColors.set(s, k);
            c.worldRenderer.reload();   // заодно сбрасывает кеш цветов биомов
        }
    }
}
