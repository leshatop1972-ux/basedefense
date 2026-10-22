package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.research.Technology;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Не даёт скрафтить предмет, пока его технология не исследована.
 * Работает и для верстака, и для крафта в инвентаре.
 */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private static void basedefense$lockUnresearched(ScreenHandler handler, World world, PlayerEntity player,
                                                     RecipeInputInventory craftingInventory,
                                                     CraftingResultInventory resultInventory, CallbackInfo ci) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer) || world.getServer() == null) {
            return;
        }
        Optional<CraftingRecipe> match = world.getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, craftingInventory, world);
        if (match.isEmpty()) {
            return;
        }
        Optional<Technology> tech = Technology.byRecipe(match.get().getId());
        if (tech.isEmpty() || ResearchManager.isResearched(world.getServer(), tech.get())) {
            return;
        }

        // Технология не исследована — результат пустой
        resultInventory.setStack(0, ItemStack.EMPTY);
        handler.setPreviousTrackedSlot(0, ItemStack.EMPTY);
        serverPlayer.networkHandler.sendPacket(
                new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
        serverPlayer.sendMessage(Text.translatable("message.basedefense.research.locked", tech.get().getName())
                .formatted(Formatting.RED), true);
        ci.cancel();
    }
}
