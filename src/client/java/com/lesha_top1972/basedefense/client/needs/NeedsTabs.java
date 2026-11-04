package com.lesha_top1972.basedefense.client.needs;

import com.lesha_top1972.basedefense.client.artifact.ArtifactPanelHost;
import com.lesha_top1972.basedefense.needs.NeedsNetwork;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Вкладки справа от окна — «Инвентарь» и «Потребности» — у игрока (обычный инвентарь) и у поселенца (его меню).
 * Клавиша N — сразу окно потребностей своего персонажа.
 */
public final class NeedsTabs {

    private static KeyBinding key;

    private NeedsTabs() {
    }

    public static void init() {
        ClientNeeds.init();
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.basedefense.needs", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N, "category.basedefense"));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (key.wasPressed()) {
                if (c.player != null && c.currentScreen == null) {
                    c.setScreen(new NeedsScreen(-1));
                }
            }
        });
        // Инвентарь игрока: вкладки справа (книга рецептов сдвигает окно — двигаем вкладки каждый кадр)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }
            SideTab inv = new SideTab(0, 0, new ItemStack(Items.CHEST), Text.translatable("gui.basedefense.tab.inventory"),
                    true, b -> {
                    });
            SideTab needs = new SideTab(0, 0, new ItemStack(Items.APPLE), Text.translatable("gui.basedefense.tab.needs"),
                    false, b -> openPlayerNeeds());
            Screens.getButtons(screen).add(inv);
            Screens.getButtons(screen).add(needs);
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) -> {
                int left = ((ArtifactPanelHost) s).basedefense$left() + 176 - 2;
                int top = (s.height - 166) / 2;
                inv.setPosition(left, top + 4);
                needs.setPosition(left, top + 4 + SideTab.H + 2);
            });
        });
    }

    /** Две вкладки для окна с правым краем right и верхом top. */
    public static void add(Consumer<SideTab> adder, int right, int top, boolean needsSelected, Runnable toInventory,
                           Runnable toNeeds) {
        adder.accept(new SideTab(right - 2, top + 4, new ItemStack(Items.CHEST),
                Text.translatable("gui.basedefense.tab.inventory"), !needsSelected, b -> {
            if (needsSelected) {
                toInventory.run();
            }
        }));
        adder.accept(new SideTab(right - 2, top + 4 + SideTab.H + 2, new ItemStack(Items.APPLE),
                Text.translatable("gui.basedefense.tab.needs"), needsSelected, b -> {
            if (!needsSelected) {
                toNeeds.run();
            }
        }));
    }

    public static void openPlayerNeeds() {
        MinecraftClient c = MinecraftClient.getInstance();
        c.setScreen(new NeedsScreen(-1));
    }

    /** Из меню поселенца — в его потребности (меню на сервере закрываем). */
    public static void openSettlerNeeds(int entityId) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) {
            c.player.closeHandledScreen();
        }
        c.setScreen(new NeedsScreen(entityId));
    }

    /** Из потребностей поселенца — снова его меню (открывает сервер). */
    public static void openSettlerInventory(int entityId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        ClientPlayNetworking.send(NeedsNetwork.OPEN_SETTLER, buf);
    }
}
