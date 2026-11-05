package com.lesha_top1972.basedefense.work;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Меню «Работа» поселенца: клиент присылает действие (и раз в секунду — запрос), сервер отвечает данными.
 * Действия: 0 — запрос; 1 — приоритет (вид, 0…4); 2 — приказ (вид или −1 — отменить); 3 — радиус (+/−);
 * 4 — центр работ там, где стоит игрок.
 */
public final class WorkNetwork {

    public static final Identifier C2S = new Identifier(Basedefense.MOD_ID, "work_action");
    public static final Identifier S2C = new Identifier(Basedefense.MOD_ID, "work_sync");

    public static final int REQUEST = 0;
    public static final int PRIORITY = 1;
    public static final int ORDER = 2;
    public static final int RADIUS = 3;
    public static final int CENTER = 4;

    private WorkNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int action = buf.readByte();
            int a = buf.readVarInt();
            int b = buf.readVarInt();
            server.execute(() -> handle(player, id, action, a, b));
        });
    }

    private static void handle(ServerPlayerEntity p, int id, int action, int a, int b) {
        if (!(p.getServerWorld().getEntityById(id) instanceof GuardEntity g) || !g.isAlive()
                || p.squaredDistanceTo(g) > 12 * 12) {
            return;
        }
        SettlerWork w = g.work;
        WorkType[] types = WorkType.values();
        switch (action) {
            case PRIORITY -> {
                if (a >= 0 && a < types.length) {
                    w.setPriority(types[a], b);
                }
            }
            case ORDER -> w.order(a >= 0 && a < types.length ? types[a] : null);
            case RADIUS -> w.setRadius(w.radius() + a);
            case CENTER -> g.setHome(p.getBlockPos());
            default -> {
            }
        }
        send(p, g);
    }

    private static void send(ServerPlayerEntity p, GuardEntity g) {
        SettlerWork w = g.work;
        SettlerSkills s = w.skills;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(g.getId());
        for (Skill sk : Skill.values()) {
            buf.writeByte(s.level(sk));
            buf.writeFloat(s.xp(sk));
            buf.writeVarInt(SettlerSkills.xpToNext(s.level(sk)));
            buf.writeByte(s.passion(sk).ordinal());
        }
        for (WorkType t : WorkType.values()) {
            buf.writeByte(w.priority(t));
            String pr = w.problem(t);
            buf.writeString(pr == null ? "" : pr);
        }
        buf.writeByte(w.forced() == null ? -1 : w.forced().ordinal());
        buf.writeByte(w.radius());
        buf.writeString(w.status());
        buf.writeBoolean(g.getDefendPoint() != null);
        for (int i = 0; i < SettlerWork.CARRY_SIZE; i++) {
            buf.writeItemStack(w.carry().getStack(i));
        }
        ServerPlayNetworking.send(p, S2C, buf);
    }
}
