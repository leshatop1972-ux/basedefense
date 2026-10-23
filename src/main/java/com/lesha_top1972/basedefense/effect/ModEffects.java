package com.lesha_top1972.basedefense.effect;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

/** Эффекты мода. */
public final class ModEffects {

    /** Заморозка (стрела морозной лучницы): 2 секунды нельзя двигаться и атаковать. */
    public static final StatusEffect FREEZE = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Basedefense.MOD_ID, "freeze"), new FreezeEffect());

    private ModEffects() {
    }

    public static boolean isFrozen(LivingEntity entity) {
        return entity != null && entity.hasStatusEffect(FREEZE);
    }

    public static void init() {
        // Замороженный игрок не может бить, ломать блоки и пользоваться предметами (лук, еда и т.п.)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) ->
                isFrozen(player) ? TypedActionResult.fail(player.getStackInHand(hand))
                        : TypedActionResult.pass(player.getStackInHand(hand)));
    }
}
