package com.lesha_top1972.basedefense.client.needs;

import com.lesha_top1972.basedefense.needs.Needs;
import com.lesha_top1972.basedefense.temperature.TempStatus;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Потребности своего игрока на клиенте: раз в секунду присылает сервер (Needs). */
public final class ClientNeeds {

    public static boolean has;
    public static float felt;
    public static float ambient;
    public static TempStatus status = TempStatus.COMFORT;
    public static float comfortMin;
    public static float comfortMax;
    public static float comfort = Needs.COMFORT_START;
    public static int trend;
    public static float enclosure;

    private ClientNeeds() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Needs.SYNC, (client, handler, buf, sender) -> {
            float f = buf.readFloat();
            float a = buf.readFloat();
            TempStatus st = TempStatus.byIndex(buf.readByte());
            float cmin = buf.readFloat();
            float cmax = buf.readFloat();
            float c = buf.readFloat();
            int tr = buf.readByte();
            float enc = buf.readFloat();
            client.execute(() -> {
                felt = f;
                ambient = a;
                status = st;
                comfortMin = cmin;
                comfortMax = cmax;
                comfort = c;
                trend = tr;
                enclosure = enc;
                has = true;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> has = false);
    }
}
