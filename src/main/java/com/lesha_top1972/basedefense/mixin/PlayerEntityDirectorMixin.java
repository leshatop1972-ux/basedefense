package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.director.Director;
import com.lesha_top1972.basedefense.director.DirectorTables;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Режиссёр мира»: каждое получение опыта любым игроком (сфера опыта, печь, торговля) — очки шкалы. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDirectorMixin {

    @Inject(method = "addExperience", at = @At("HEAD"))
    private void basedefense$directorXp(int experience, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (experience > 0 && !self.getWorld().isClient && !self.isSpectator()) {
            Director.addPoints(self.getServer(), DirectorTables.POINTS_XP);
        }
    }
}
