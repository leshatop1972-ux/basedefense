package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.artifact.ArtifactClient;
import com.lesha_top1972.basedefense.client.artifact.ArtifactPanelHost;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Вкладка артефактов слева от инвентаря: фон под слотами; клик по вкладке — не «мимо окна» (не выбрасывает предмет). */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> implements ArtifactPanelHost {

    @Shadow
    @Final
    private RecipeBookWidget recipeBook;

    private InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    public int basedefense$left() {
        return this.x;
    }

    @Override
    public boolean basedefense$recipeBookOpen() {
        return this.recipeBook.isOpen();
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void basedefense$artifactPanel(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (ArtifactClient.panelVisible()) {
            ArtifactClient.drawPanel(context, this.x, this.y);
        }
    }

    @Inject(method = "isClickOutsideBounds", at = @At("RETURN"), cancellable = true)
    private void basedefense$artifactPanelClick(double mouseX, double mouseY, int left, int top, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ArtifactClient.panelVisible() && ArtifactClient.inPanel(mouseX, mouseY, left, top)) {
            cir.setReturnValue(false);
        }
    }
}
