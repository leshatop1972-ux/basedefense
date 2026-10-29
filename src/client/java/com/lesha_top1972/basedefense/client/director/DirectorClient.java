package com.lesha_top1972.basedefense.client.director;

import com.lesha_top1972.basedefense.client.artifact.ArtifactClient;
import com.lesha_top1972.basedefense.client.artifact.ArtifactPanelHost;
import com.lesha_top1972.basedefense.client.screen.UiKit;
import com.lesha_top1972.basedefense.director.Director;
import com.lesha_top1972.basedefense.director.DirectorEvent;
import com.lesha_top1972.basedefense.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * «Режиссёр мира» на клиенте: состояние с сервера (Director.SYNC), вкладка «Летопись» у левого края инвентаря
 * (возле брони; открывает {@link DirectorScreen}), утренняя заставка {@link MorningOverlay}.
 * Вкладка не трогает правую часть экрана, где список предметов JEI.
 */
public final class DirectorClient {

    public record Entry(int day, DirectorEvent event) {
    }

    // ---------- Состояние (с сервера) ----------
    static boolean active;
    static long day;
    static int act = 1;
    static int points;
    static DirectorEvent today = DirectorEvent.PEACE;
    static long todayDay;
    static final List<Entry> HISTORY = new ArrayList<>();

    /** Вкладка: ширина и высота. */
    static final int TAB_W = 24;
    static final int TAB_H = 24;

    private DirectorClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Director.SYNC, (client, handler, buf, sender) -> {
            boolean show = buf.readBoolean();
            long d = buf.readVarLong();
            boolean a = buf.readBoolean();
            int ac = buf.readVarInt();
            int pts = buf.readVarInt();
            DirectorEvent t = DirectorEvent.byOrdinal(buf.readVarInt());
            long td = buf.readVarLong();
            int n = buf.readVarInt();
            List<Entry> hist = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                hist.add(new Entry(buf.readVarInt(), DirectorEvent.byOrdinal(buf.readVarInt())));
            }
            client.execute(() -> {
                day = d;
                active = a;
                act = ac;
                points = pts;
                today = t;
                todayDay = td;
                HISTORY.clear();
                HISTORY.addAll(hist);
                if (show) {
                    MorningOverlay.start(t, td, ac);
                    playMorningSound(client, t);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            active = false;
            MorningOverlay.stop();
        });
        ClientTickEvents.END_CLIENT_TICK.register(MorningOverlay::tick);
        HudRenderCallback.EVENT.register(MorningOverlay::render);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen inv)) {
                return;
            }
            Tab tab = new Tab(btn -> client.setScreen(new DirectorScreen(inv)));
            tab.setTooltip(Tooltip.of(Text.translatable("gui.basedefense.director")));
            Screens.getButtons(screen).add(tab);
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) -> {
                ArtifactPanelHost host = (ArtifactPanelHost) s;
                tab.visible = active && !host.basedefense$recipeBookOpen();
                int left = host.basedefense$left();
                // вкладка у левого края окна, напротив шлема; открыты артефакты — левее их панели
                int x = ArtifactClient.panelVisible() ? left + ArtifactClient.PANEL_X - TAB_W + 3 : left - TAB_W + 3;
                tab.setPosition(x, (s.height - 166) / 2 + 6);
            });
        });
    }

    private static void playMorningSound(MinecraftClient client, DirectorEvent e) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_USE, 0.6f, 0.9f));
        if (e.kind == DirectorEvent.Kind.WAVE || e.kind == DirectorEvent.Kind.BOSS) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_RESONATE, 0.8f, 0.6f));
        }
    }

    // =====================================================================
    // Внешний вид событий
    // =====================================================================

    /** Значок события — предмет игры. */
    static ItemStack icon(DirectorEvent e) {
        return new ItemStack(switch (e) {
            case PEACE -> Items.WHEAT;
            case NONE -> Items.POPPY;
            case METEOR -> Items.MAGMA_BLOCK;
            case CARAVAN -> Items.EMERALD;
            case FLOOD -> Items.WATER_BUCKET;
            case SNOWFALL -> Items.SNOW_BLOCK;
            case HEATWAVE -> Items.BLAZE_POWDER;
            case STORM -> Items.LIGHTNING_ROD;
            case WAVE -> Items.IRON_SWORD;
            case ETERNAL_FOG -> Items.PHANTOM_MEMBRANE;
            case BLOOD_MOON -> Items.REDSTONE;
            case IRON_MARCH -> Items.IRON_CHESTPLATE;
            case UNDERMINING -> Items.TNT;
            case FROST_NIGHT -> Items.BLUE_ICE;
            case BLOOD_NIGHT -> Items.NETHER_WART;
            case STRONG_WAVE -> Items.DIAMOND_SWORD;
            case BOSS_WAVE -> ModItems.TATTERED_LANCER_SPAWN_EGG;
        });
    }

    /** Цвет названия события. */
    static int color(DirectorEvent e) {
        return switch (e.kind) {
            case CALM -> UiKit.GOOD_LIGHT;
            case WORLD -> UiKit.WARN;
            case WAVE -> UiKit.BAD;
            case BOSS -> 0xFFFF3B3B;
        };
    }

    static Text whenText(DirectorEvent e) {
        return Text.translatable("director.basedefense.when." + e.when.name().toLowerCase());
    }

    /** Кнопка-вкладка: стеклянный «язычок» с часами. */
    static final class Tab extends ButtonWidget {

        private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);

        Tab(PressAction onPress) {
            super(0, 0, TAB_W, TAB_H, Text.translatable("gui.basedefense.director"), onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            int l = getX();
            int t = getY();
            int r = l + width;
            int b = t + height;
            boolean hover = isHovered();
            c.fill(l, t, r, b, UiKit.FRAME_OUT);
            c.fillGradient(l + 1, t + 1, r, b - 1, UiKit.GLASS_TOP, UiKit.GLASS_BOTTOM);
            c.fill(l + 1, t + 1, r, t + 2, UiKit.FRAME_LIGHT);
            c.fill(l + 1, t + 1, l + 2, b - 1, UiKit.FRAME);
            if (hover) {
                c.fill(l + 2, t + 2, r, b - 1, UiKit.CARD_HOVER);
            }
            c.drawItem(CLOCK, l + 5, t + 4);
            if (today.kind == DirectorEvent.Kind.WAVE || today.kind == DirectorEvent.Kind.BOSS) {
                c.fill(l + 3, b - 5, l + 6, b - 2, UiKit.BAD);    // красная точка — сегодня волна
            }
        }
    }
}
