package com.lesha_top1972.basedefense.needs;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/** Клиент → сервер: «открыть меню поселенца» (вкладка «Инвентарь» в окне его потребностей). */
public final class NeedsNetwork {

    public static final Identifier OPEN_SETTLER = new Identifier(Basedefense.MOD_ID, "open_settler_menu");

    private NeedsNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(OPEN_SETTLER, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                if (player.getServerWorld().getEntityById(id) instanceof GuardEntity guard && guard.isAlive()
                        && player.squaredDistanceTo(guard) < 8.0 * 8.0) {
                    guard.openMenu(player);
                }
            });
        });
    }
}
