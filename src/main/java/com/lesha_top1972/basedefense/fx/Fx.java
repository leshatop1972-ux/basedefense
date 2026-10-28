package com.lesha_top1972.basedefense.fx;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Декоративные эффекты одним пакетом: сервер шлёт «что и где», а частицы рисует клиент ({@code client/fx/FxClient}).
 * Раньше кольца, лучи и линии собирались из десятков вызовов spawnParticles — это десятки пакетов каждому игроку
 * за раз. Теперь — один пакет на эффект, а вид тот же.
 */
public final class Fx {

    public static final Identifier PACKET = new Identifier(Basedefense.MOD_ID, "fx");
    /** Кому слать: игрокам ближе этого расстояния. */
    static final double RANGE = 96.0;

    // --- виды эффектов ---
    /** Кольцо по земле: a — радиус, b — число точек; флаги — из чего (пыль блока, облачка, цветная пыль, синий огонь). */
    public static final int RING = 1;
    /** Лучи пыли блока от центра: a — длина, b — число лучей, x2 — угол первого луча (рад), y2 — шаг по лучу. */
    public static final int RAYS = 2;
    /** Линия от (x,y,z) до (x2,y2,z2): b — число точек; флаги — из чего. */
    public static final int LINE = 3;
    /** Выброс из земли: пыль блока, жар, огонь (+ дым при флаге LOUD). */
    public static final int ERUPTION = 4;
    /** Столб дыма из кадила: a — высота. */
    public static final int COLUMN = 5;
    /** Фронт волны землетрясения: a — радиус; блоки под фронтом клиент берёт сам. */
    public static final int QUAKE_FRONT = 6;
    /** Брызги крови: a — разброс, b — сила (≈ число капель). */
    public static final int BLOOD = 7;
    /** Дуга вокруг (x,z) радиусом a: углы x2..y2 (рад, отсчёт от взгляда), высота z2, b шагов; yaw — в color (биты float). */
    public static final int ARC = 8;
    /** Кольца кровавых гейзеров радиусами 1.5..a. */
    public static final int GEYSER_RING = 9;
    /** Взрыв подкопщика (не ванильный): вспышка, дым, осколки блока, искры, ударная волна по земле; a — радиус, F_LOUD — подрыв (крупнее, куски стены). */
    public static final int BLAST = 10;
    /** Фонтан земли из лаза: комья блока разлетаются вверх и в стороны. */
    public static final int FOUNTAIN = 11;
    /** Удар (таран, «Рогом»): искры, щепки и куски блока в точке, облачко пыли; a — сила, F_LOUD — крупнее. */
    public static final int IMPACT = 12;
    /** Разрыв кровавого кокона кровожада: вспышка, лепестки‑ошмётки веером, брызги, ударная волна по земле (a — радиус). */
    public static final int COCOON_BURST = 13;
    /** «Кровавый разруб» меча кровожада: разлом по земле вперёд (x2, z2 — направление), кровавые всплески,
     *  вертикальный след удара; a — длина, b — сколько задето. */
    public static final int BLOOD_CLEAVE = 14;

    // --- флаги ---
    public static final int F_BLOCK = 1;
    public static final int F_CLOUD = 2;
    public static final int F_DUST = 4;
    public static final int F_SOUL = 8;
    public static final int F_LOUD = 16;
    public static final int F_SWEEP = 32;
    public static final int F_CRIT = 64;
    public static final int F_BLOOD = 128;

    private Fx() {
    }

    /** Цвет пыли 0xRRGGBB и её размер ×10 (0.7 → 7) — в одном int: размер в старшем байте. */
    public static int dust(int rgb, float size) {
        return (Math.round(size * 10f) & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int blockId(BlockState st) {
        return st == null || st.isAir() ? 0 : Block.getRawIdFromState(st);
    }

    /** Блок под точкой (для пыли), 0 — воздух. */
    public static int groundId(ServerWorld w, Vec3d p) {
        return blockId(w.getBlockState(BlockPos.ofFloored(p.x, p.y - 0.5, p.z)));
    }

    public static void send(ServerWorld w, int type, Vec3d p, Vec3d p2, float a, float b, int color, int block, int flags) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(type);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat((float) p2.x);
        buf.writeFloat((float) p2.y);
        buf.writeFloat((float) p2.z);
        buf.writeFloat(a);
        buf.writeFloat(b);
        buf.writeInt(color);
        buf.writeVarInt(block);
        buf.writeVarInt(flags);
        for (ServerPlayerEntity pl : PlayerLookup.around(w, p, RANGE)) {
            ServerPlayNetworking.send(pl, PACKET, PacketByteBufs.copy(buf));
        }
    }

    /** Кольцо по земле вокруг c радиусом r. */
    public static void ring(ServerWorld w, Vec3d c, double r, int pts, int color, int flags) {
        send(w, RING, c, Vec3d.ZERO, (float) r, pts, color, (flags & F_BLOCK) != 0 ? groundId(w, c) : 0, flags);
    }

    public static void line(ServerWorld w, Vec3d a, Vec3d b, int pts, int color, int block, int flags) {
        send(w, LINE, a, b, 0f, pts, color, block, flags);
    }

    public static void rays(ServerWorld w, Vec3d c, double len, int rays, double angle0, double step) {
        send(w, RAYS, c, new Vec3d(angle0, step, 0), (float) len, rays, 0, groundId(w, c), F_BLOCK);
    }

    public static void eruption(ServerWorld w, Vec3d p, int color, boolean loud) {
        send(w, ERUPTION, p, Vec3d.ZERO, 0f, 0f, color, groundId(w, p), loud ? F_LOUD : 0);
    }

    public static void column(ServerWorld w, Vec3d p, double h, int color) {
        send(w, COLUMN, p, Vec3d.ZERO, (float) h, 0f, color, 0, 0);
    }

    public static void blood(ServerWorld w, Vec3d p, int count, double spread) {
        send(w, BLOOD, p, Vec3d.ZERO, (float) spread, count, 0, 0, 0);
    }

    public static void arc(ServerWorld w, Vec3d c, float yawDeg, double a0Deg, double a1Deg, double r, double y, int steps, int flags) {
        send(w, ARC, c, new Vec3d(Math.toRadians(a0Deg), Math.toRadians(a1Deg), y), (float) r, steps,
                Float.floatToIntBits(yawDeg), 0, flags);
    }

    public static void geyserRing(ServerWorld w, Vec3d c, double r) {
        send(w, GEYSER_RING, c, Vec3d.ZERO, (float) r, 0f, 0, 0, F_BLOOD);
    }

    /** Взрыв бомбы (big = false) или подрыва (big = true) радиусом r; block — из чего осколки (0 — блок под точкой). */
    public static void blast(ServerWorld w, Vec3d p, double r, boolean big, int block) {
        send(w, BLAST, p, Vec3d.ZERO, (float) r, 0f, 0, block != 0 ? block : groundId(w, p), big ? F_LOUD : 0);
    }

    /** Фонтан земли из лаза. */
    public static void fountain(ServerWorld w, Vec3d p, BlockState st) {
        send(w, FOUNTAIN, p, Vec3d.ZERO, 0f, 0f, 0, blockId(st), F_BLOCK);
    }

    /** Удар в точке p: осколки блока в этой точке (или под ней), искры, пыль. */
    public static void impact(ServerWorld w, Vec3d p, float power, boolean big) {
        BlockState st = w.getBlockState(BlockPos.ofFloored(p));
        int block = st.isAir() ? groundId(w, p) : blockId(st);
        send(w, IMPACT, p, Vec3d.ZERO, power, 0f, 0, block, big ? F_LOUD : 0);
    }

    /** Кровожад вырывается из кокона (центр — у ног), волна радиусом r. */
    public static void cocoonBurst(ServerWorld w, Vec3d c, double r) {
        send(w, COCOON_BURST, c, Vec3d.ZERO, (float) r, 0f, 0, groundId(w, c), F_BLOOD);
    }

    /** Удар меча кровожада: c — точка удара на земле, fwd — направление удара. */
    public static void cleave(ServerWorld w, Vec3d c, Vec3d fwd, double reach, int hits) {
        send(w, BLOOD_CLEAVE, c, fwd, (float) reach, hits, 0, groundId(w, c), F_BLOOD);
    }

    public static void quakeFront(ServerWorld w, Vec3d c, double r) {
        send(w, QUAKE_FRONT, c, Vec3d.ZERO, (float) r, 0f, 0, 0, 0);
    }
}
