package com.lesha_top1972.basedefense.effect;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Collection;

/**
 * Эффекты босса под его полоской здоровья: раз в 10 тиков сервер шлёт игрокам, которые видят босса,
 * список эффектов (номер эффекта, уровень, оставшееся время) с UUID полоски. Клиент — BossEffectsHud.
 * Вызывать из mobTick каждого босса: {@code BossEffectsSync.tick(this, this.bossBar);}
 */
public final class BossEffectsSync {

    public static final Identifier PACKET = new Identifier(Basedefense.MOD_ID, "boss_effects");
    private static final int EVERY = 10;

    private BossEffectsSync() {
    }

    public static void tick(LivingEntity boss, ServerBossBar bar) {
        if (boss.getWorld().isClient || (boss.age + boss.getId()) % EVERY != 0) {
            return;
        }
        Collection<StatusEffectInstance> effects = boss.getStatusEffects();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(bar.getUuid());
        buf.writeVarInt(effects.size());
        for (StatusEffectInstance e : effects) {
            buf.writeVarInt(Registries.STATUS_EFFECT.getRawId(e.getEffectType()));
            buf.writeVarInt(e.getAmplifier());
            buf.writeInt(e.getDuration());
        }
        for (ServerPlayerEntity p : PlayerLookup.tracking(boss)) {
            ServerPlayNetworking.send(p, PACKET, PacketByteBufs.copy(buf));
        }
    }
}
