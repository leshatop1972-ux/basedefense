package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.weapon.BloodSwordCombat;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Arm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Клиент меча кровожада: зажатие ЛКМ (замах / удар / отмена), пакеты, состояние анимации у каждого игрока.
 * Свой игрок анимируется сразу, без ожидания сервера; остальные — по пакету {@link BloodSwordCombat#S2C}.
 */
public final class BloodSwordClient {

    private static final Map<Integer, BloodSwordAnim> ANIMS = new HashMap<>();
    private static final float[] TP_KEYS = new float[11];
    private static long ticks;
    private static int pressTicks;
    /** После удара (или отказа) — ждать, пока ЛКМ отпустят. */
    private static boolean needRelease;

    private BloodSwordClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BloodSwordCombat.S2C, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int act = buf.readByte();
            client.execute(() -> onPacket(client, id, act));
        });
        ClientTickEvents.END_CLIENT_TICK.register(BloodSwordClient::tick);
    }

    /** Время анимаций: тики клиента + доля текущего кадра. */
    public static float now() {
        return ticks + MinecraftClient.getInstance().getTickDelta();
    }

    static BloodSwordAnim anim(int entityId) {
        return ANIMS.isEmpty() ? null : ANIMS.get(entityId);
    }

    /** Свой игрок замахивается — ломать блоки и менять обзор от замедления не нужно. */
    public static boolean localBusy() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) {
            return false;
        }
        BloodSwordAnim a = anim(p.getId());
        return a != null && (a.phase == BloodSwordAnim.CHARGE || a.phase == BloodSwordAnim.SLAM) || pressTicks > 0;
    }

    /** Из миксина BipedEntityModel.setAngles: поза замаха/удара для игроков. */
    public static void applyModel(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(model instanceof PlayerEntityModel)) {
            return;
        }
        // общая модель для всех игроков: сдвиги шеи от прошлой позы сбрасываем (ваниль их не трогает)
        model.head.pivotX = 0f;
        model.head.pivotZ = 0f;
        model.body.pivotX = 0f;
        model.body.pivotZ = 0f;
        model.body.roll = 0f;
        BloodSwordAnim a = anim(entity.getId());
        if (a == null) {
            return;
        }
        float now = now();
        float w = a.weight(now);
        a.tpWeight = w;
        if (w <= 0.001f) {
            return;
        }
        int m = entity.getMainArm() == Arm.RIGHT ? 1 : -1;
        float hipY = (model.rightLeg.pivotY + model.leftLeg.pivotY) * 0.5f;
        float hipZ = (model.rightLeg.pivotZ + model.leftLeg.pivotZ) * 0.5f;
        BloodSwordPose.TP pose = BloodSwordPose.solveThirdPerson(a.thirdPerson(now, TP_KEYS), m, hipY, hipZ,
                model.body.pitch, new BloodSwordPose.TP());
        BloodSwordPose.applyThirdPerson(model, pose, m, w);
        BloodSwordPose.swordThirdPerson(pose, a.tpSword);
    }

    private static void onPacket(MinecraftClient client, int id, int act) {
        if (client.player == null) {
            return;
        }
        float t = ticks;
        BloodSwordAnim a = ANIMS.get(id);
        if (act == BloodSwordCombat.ACT_CANCEL) {
            if (a != null) {
                a.cancel(t);
            }
            if (id == client.player.getId()) {
                needRelease = true;
            }
            return;
        }
        if (id == client.player.getId()) {
            return;                              // своя анимация уже идёт
        }
        if (act == BloodSwordCombat.ACT_CHARGE) {
            ANIMS.put(id, new BloodSwordAnim(BloodSwordAnim.CHARGE, t));
        } else if (act == BloodSwordCombat.ACT_SLAM) {
            ANIMS.put(id, new BloodSwordAnim(BloodSwordAnim.SLAM, t));
        }
    }

    private static void send(int act) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(act);
        ClientPlayNetworking.send(BloodSwordCombat.C2S, buf);
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        BloodSwordFx.tick();
        if (client.player == null || client.world == null) {
            ANIMS.clear();
            pressTicks = 0;
            return;
        }
        input(client);
        float now = ticks;
        Iterator<Map.Entry<Integer, BloodSwordAnim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BloodSwordAnim> e = it.next();
            Entity ent = client.world.getEntityById(e.getKey());
            BloodSwordAnim a = e.getValue();
            if (!(ent instanceof LivingEntity le) || !ent.isAlive() || a.finished(now)) {
                it.remove();
                continue;
            }
            if (ent != client.player && a.phase == BloodSwordAnim.CHARGE && !BloodSwordCombat.holding((net.minecraft.entity.player.PlayerEntity) ent)) {
                a.cancel(now);
            }
            BloodSwordFx.animTick(client, le, a, now);
        }
    }

    private static void input(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        boolean holding = p.getMainHandStack().isOf(ModItems.BLOODTHIRSTER_SWORD);
        boolean down = client.options.attackKey.isPressed() && client.currentScreen == null && !p.isSpectator()
                && !p.isUsingItem();
        BloodSwordAnim a = ANIMS.get(p.getId());
        float now = ticks;
        if (a != null && a.phase == BloodSwordAnim.CHARGE) {
            if (!holding || client.currentScreen != null || !p.isAlive()) {
                a.cancel(now);
                send(BloodSwordCombat.ACT_CANCEL);
            } else if (!down) {
                if (a.age(now) >= BloodSwordCombat.CHARGE_TICKS) {
                    ANIMS.put(p.getId(), new BloodSwordAnim(BloodSwordAnim.SLAM, now));
                    send(BloodSwordCombat.ACT_SLAM);
                } else {
                    a.cancel(now);
                    send(BloodSwordCombat.ACT_CANCEL);
                }
            }
            return;
        }
        if (!down) {
            pressTicks = 0;
            needRelease = false;
            return;
        }
        if (needRelease || a != null && a.phase == BloodSwordAnim.SLAM) {
            return;
        }
        if (!holding || p.getItemCooldownManager().isCoolingDown(ModItems.BLOODTHIRSTER_SWORD)) {
            pressTicks = 0;
            needRelease = holding;
            return;
        }
        if (++pressTicks >= BloodSwordCombat.CHARGE_DELAY) {
            pressTicks = 0;
            needRelease = true;
            ANIMS.put(p.getId(), new BloodSwordAnim(BloodSwordAnim.CHARGE, now));
            send(BloodSwordCombat.ACT_CHARGE);
        }
    }
}
