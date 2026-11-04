package com.lesha_top1972.basedefense.clothing;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Открыть рюкзак (свой или поселенца) — по вкладке «Рюкзак» (пакет с клиента). */
public final class Backpacks {

    public static final Identifier OPEN = new Identifier(Basedefense.MOD_ID, "open_backpack");

    private Backpacks() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(OPEN, (server, player, handler, buf, sender) -> {
            int ownerId = buf.readVarInt();
            server.execute(() -> open(player, ownerId));
        });
    }

    public static void open(ServerPlayerEntity player, int ownerId) {
        LivingEntity owner;
        Inventory clothes;
        if (ownerId < 0) {
            owner = player;
            clothes = Clothing.of(player);
        } else if (player.getServerWorld().getEntityById(ownerId) instanceof SettlerEntity s && s.isAlive()
                && player.squaredDistanceTo(s) < 8.0 * 8.0) {
            owner = s;
            clothes = s.getClothing();
        } else {
            return;
        }
        ItemStack stack = Clothing.backpack(clothes);
        if (!(stack.getItem() instanceof BackpackItem bp)) {
            return;
        }
        int rows = bp.getRows();
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
                buf.writeByte(rows);
                buf.writeVarInt(ownerId);
            }

            @Override
            public Text getDisplayName() {
                return ownerId < 0 ? stack.getName()
                        : Text.translatable("container.basedefense.settler_backpack", owner.getDisplayName());
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                return new BackpackScreenHandler(syncId, inventory, new BackpackScreenHandler.Contents(stack, rows * 9),
                        ownerId, clothes, stack, owner);
            }
        });
    }
}
