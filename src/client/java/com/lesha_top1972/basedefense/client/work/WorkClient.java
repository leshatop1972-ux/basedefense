package com.lesha_top1972.basedefense.client.work;

import com.lesha_top1972.basedefense.work.MineMarkerItem;
import com.lesha_top1972.basedefense.work.MiningZones;
import com.lesha_top1972.basedefense.work.Skill;
import com.lesha_top1972.basedefense.work.WorkNetwork;
import com.lesha_top1972.basedefense.work.WorkType;
import com.lesha_top1972.basedefense.work.SettlerWork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Клиент работы: данные меню «Работа» (от сервера) и рамки шахт, пока в руке разметчик. */
public final class WorkClient {

    /** Последние данные о поселенце (для открытого меню). */
    public static final class Data {
        public int entityId = -1;
        public final int[] level = new int[Skill.values().length];
        public final float[] xp = new float[Skill.values().length];
        public final int[] need = new int[Skill.values().length];
        public final int[] passion = new int[Skill.values().length];
        public final int[] priority = new int[WorkType.values().length];
        public final String[] problem = new String[WorkType.values().length];
        public int forced = -1;
        public int radius;
        public String status = "idle";
        public boolean onPost;
        public final ItemStack[] carry = new ItemStack[SettlerWork.CARRY_SIZE];
    }

    public static Data data = new Data();
    private static final List<Box> ZONES = new ArrayList<>();

    private WorkClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(WorkNetwork.S2C, (client, handler, buf, sender) -> {
            Data d = new Data();
            d.entityId = buf.readVarInt();
            for (int i = 0; i < Skill.values().length; i++) {
                d.level[i] = buf.readByte();
                d.xp[i] = buf.readFloat();
                d.need[i] = buf.readVarInt();
                d.passion[i] = buf.readByte();
            }
            for (int i = 0; i < WorkType.values().length; i++) {
                d.priority[i] = buf.readByte();
                String p = buf.readString();
                d.problem[i] = p.isEmpty() ? null : p;
            }
            d.forced = buf.readByte();
            d.radius = buf.readByte();
            d.status = buf.readString();
            d.onPost = buf.readBoolean();
            for (int i = 0; i < SettlerWork.CARRY_SIZE; i++) {
                d.carry[i] = buf.readItemStack();
            }
            client.execute(() -> data = d);
        });
        ClientPlayNetworking.registerGlobalReceiver(MiningZones.SYNC, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<Box> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                BlockPos a = buf.readBlockPos();
                BlockPos b = buf.readBlockPos();
                list.add(new Box(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1));
            }
            client.execute(() -> {
                ZONES.clear();
                ZONES.addAll(list);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            ZONES.clear();
            data = new Data();
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null || ctx.consumers() == null) {
                return;
            }
            ItemStack held = c.player.getMainHandStack().getItem() instanceof MineMarkerItem ? c.player.getMainHandStack()
                    : c.player.getOffHandStack().getItem() instanceof MineMarkerItem ? c.player.getOffHandStack() : null;
            if (held == null) {
                return;
            }
            Vec3d cam = ctx.camera().getPos();
            MatrixStack m = ctx.matrixStack();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());
            for (Box b : ZONES) {
                WorldRenderer.drawBox(m, vc, b.offset(-cam.x, -cam.y, -cam.z), 1.0f, 0.72f, 0.2f, 1.0f);
            }
            BlockPos first = MineMarkerItem.firstCorner(held);
            if (first != null) {
                WorldRenderer.drawBox(m, vc, new Box(first).expand(0.01).offset(-cam.x, -cam.y, -cam.z), 0.3f, 1.0f, 0.4f, 1.0f);
            }
        });
    }

    /** Отправить действие меню «Работа». */
    public static void send(int entityId, int action, int a, int b) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeByte(action);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        ClientPlayNetworking.send(WorkNetwork.C2S, buf);
    }
}
