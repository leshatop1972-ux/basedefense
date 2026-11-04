package com.lesha_top1972.basedefense.client.needs;

import com.lesha_top1972.basedefense.client.artifact.ArtifactPanelHost;
import com.lesha_top1972.basedefense.clothing.Backpacks;
import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.needs.NeedsNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Вкладки справа от окна — «Инвентарь», «Потребности» и «Рюкзак» (только если рюкзак надет) — у игрока
 * (обычный инвентарь) и у поселенца (его меню). owner: −1 — свой игрок, иначе номер сущности поселенца.
 * Клавиша N — сразу окно потребностей своего персонажа.
 */
public final class NeedsTabs {

    public enum Tab { INVENTORY, NEEDS, BACKPACK }

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
            List<SideTab> tabs = new ArrayList<>();
            add(tabs::add, 0, 0, Tab.INVENTORY, -1);
            Screens.getButtons(screen).addAll(tabs);
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) -> {
                int right = ((ArtifactPanelHost) s).basedefense$left() + 176;
                int top = (s.height - 166) / 2;
                for (int i = 0; i < tabs.size(); i++) {
                    tabs.get(i).setPosition(right - 2, top + 4 + i * (SideTab.H + 2));
                }
            });
        });
    }

    /** Вкладки для окна с правым краем right и верхом top. */
    public static void add(Consumer<SideTab> adder, int right, int top, Tab selected, int owner) {
        int x = right - 2;
        adder.accept(new SideTab(x, top + 4, new ItemStack(Items.CHEST), Text.translatable("gui.basedefense.tab.inventory"),
                selected == Tab.INVENTORY, () -> true, b -> {
            if (selected != Tab.INVENTORY) {
                toInventory(owner);
            }
        }));
        adder.accept(new SideTab(x, top + 4 + SideTab.H + 2, new ItemStack(Items.APPLE),
                Text.translatable("gui.basedefense.tab.needs"), selected == Tab.NEEDS, () -> true, b -> {
            if (selected != Tab.NEEDS) {
                toNeeds(owner);
            }
        }));
        adder.accept(new SideTab(x, top + 4 + 2 * (SideTab.H + 2), new ItemStack(
                com.lesha_top1972.basedefense.clothing.ModClothing.BACKPACK),
                Text.translatable("gui.basedefense.tab.backpack"), selected == Tab.BACKPACK,
                () -> selected == Tab.BACKPACK || hasBackpack(owner), b -> {
            if (selected != Tab.BACKPACK) {
                send(Backpacks.OPEN, owner);
            }
        }));
    }

    public static boolean hasBackpack(int owner) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null) {
            return false;
        }
        if (owner < 0) {
            return !Clothing.backpack(Clothing.of(c.player)).isEmpty();
        }
        return c.world.getEntityById(owner) instanceof SettlerEntity s && s.hasBackpack();
    }

    /** Свой инвентарь — открыть на клиенте; инвентарь поселенца — его меню открывает сервер. */
    public static void toInventory(int owner) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) {
            return;
        }
        if (owner < 0) {
            closeContainer(c);
            c.setScreen(new InventoryScreen(c.player));
        } else {
            send(NeedsNetwork.OPEN_SETTLER, owner);
        }
    }

    public static void toNeeds(int owner) {
        MinecraftClient c = MinecraftClient.getInstance();
        closeContainer(c);
        c.setScreen(new NeedsScreen(owner));
    }

    /** Открыто меню‑контейнер (поселенец, рюкзак) — сказать серверу, что закрыли. */
    private static void closeContainer(MinecraftClient c) {
        if (c.player != null && c.currentScreen instanceof HandledScreen<?> && !(c.currentScreen instanceof InventoryScreen)) {
            c.player.closeHandledScreen();
        }
    }

    private static void send(net.minecraft.util.Identifier id, int owner) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(owner);
        ClientPlayNetworking.send(id, buf);
    }
}
