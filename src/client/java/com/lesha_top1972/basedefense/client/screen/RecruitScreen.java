package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.RecruitScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню военкомата (стиль «стекло»): карточка стражника с описанием, койки казармы
 * (занятые — с мечом), цена вербовки и кнопка «Завербовать».
 */
public class RecruitScreen extends HandledScreen<RecruitScreenHandler> {

    private static final int W = 248;
    private static final int H = 206;
    private static final int HEADER_H = 22;
    private static final int BTN_W = 120;
    private static final int BTN_H = 20;
    private static final int BUNK = 20;

    private static final ItemStack OFFICE = new ItemStack(ModBlocks.RECRUITMENT_OFFICE);
    private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);
    private static final ItemStack SHIELD = new ItemStack(Items.SHIELD);
    private static final ItemStack BED = new ItemStack(Items.RED_BED);
    private static final ItemStack COIN = new ItemStack(ModItems.RESEARCH_COIN);

    public RecruitScreen(RecruitScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = W;
        this.backgroundHeight = H;
    }

    private int btnLeft() {
        return this.x + (W - BTN_W) / 2;
    }

    private int btnTop() {
        return this.y + H - BTN_H - 22;
    }

    private boolean overButton(double mx, double my) {
        return mx >= btnLeft() && mx < btnLeft() + BTN_W && my >= btnTop() && my < btnTop() + BTN_H;
    }

    private boolean canRecruit() {
        return handler.canAfford() && handler.hasFreeBed() && handler.beds() > 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = left + W;
        int bottom = top + H;

        panel(c, left, top, right, bottom);
        header(c, left + 4, top + 4, right - 4, HEADER_H);
        c.drawItem(OFFICE, left + 10, top + 7);
        embossed(c, this.textRenderer, this.title, left + 30, top + 11, TEXT_DARK);

        int guards = handler.guards();
        int beds = handler.beds();
        Text bunks = Text.translatable("screen.basedefense.recruit.bunks", guards, beds);
        int pillW = this.textRenderer.getWidth(bunks) + 8;
        boolean full = beds > 0 && guards >= beds;
        pill(c, this.textRenderer, right - 12 - pillW, top + 9, bunks,
                full ? 0x60602020 : 0x50000000, full ? WARN : TEXT_BODY);

        // Карточка стражника: «портрет» (меч и щит) + имя профессии и описание
        int cl = left + 10;
        int ct = top + HEADER_H + 10;
        int cr = right - 10;
        int cb = ct + 62;
        card(c, cl, ct, cr, cb, CARD, false);
        int pl = cl + 6;
        int pt = ct + 6;
        inset(c, pl, pt, pl + 48, pt + 50, CONTENT);
        c.getMatrices().push();
        c.getMatrices().translate(pl + 8, pt + 9, 0);
        c.getMatrices().scale(2.0f, 2.0f, 1.0f);
        c.drawItem(SHIELD, 0, 0);
        c.getMatrices().pop();
        c.getMatrices().push();
        c.getMatrices().translate(pl + 22, pt + 22, 100);
        c.getMatrices().scale(1.5f, 1.5f, 1.0f);
        c.drawItem(SWORD, 0, 0);
        c.getMatrices().pop();

        int tx = pl + 56;
        embossed(c, this.textRenderer, Text.translatable("entity.basedefense.guard"), tx, ct + 7, TEXT_DARK);
        List<OrderedText> desc = this.textRenderer.wrapLines(
                Text.translatable("screen.basedefense.recruit.desc"), cr - tx - 6);
        int ly = ct + 20;
        for (OrderedText line : desc) {
            if (ly > cb - 10) {
                break;
            }
            c.drawText(this.textRenderer, line, tx, ly, TEXT_BODY, false);
            ly += 10;
        }

        // Койки казармы: занятые — с мечом, свободные — с кроватью (бледно)
        int rowT = cb + 8;
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.recruit.bunks_title"), cl, rowT, TEXT_MUTED, false);
        int shown = Math.max(beds, 1);
        int rowW = shown * BUNK + (shown - 1) * 3;
        int bx = left + (W - rowW) / 2;
        int by = rowT + 12;
        if (beds == 0) {
            embossedCentered(c, this.textRenderer, Text.translatable("screen.basedefense.recruit.no_beds"),
                    left + W / 2, by + 6, BAD);
        } else {
            for (int i = 0; i < beds; i++) {
                int x = bx + i * (BUNK + 3);
                boolean taken = i < guards;
                c.fill(x, by, x + BUNK, by + BUNK, taken ? 0x40FFFFFF : 0x50000000);
                outline(c, x, by, x + BUNK, by + BUNK, taken ? 0x90FFFFFF : 0x30FFFFFF);
                if (taken) {
                    c.drawItem(SWORD, x + 2, by + 2);
                } else {
                    c.getMatrices().push();
                    c.getMatrices().translate(x + 4, by + 4, 0);
                    c.getMatrices().scale(0.75f, 0.75f, 1.0f);
                    c.drawItem(BED, 0, 0);
                    c.getMatrices().pop();
                    c.fill(x + 1, by + 1, x + BUNK - 1, by + BUNK - 1, 0x60181A1E);   // приглушить свободную
                }
            }
        }

        divider(c, left + 12, right - 12, btnTop() - 22);

        // Цена: монета + число (зелёное — хватает, красное — нет)
        int priceY = btnTop() - 16;
        Text price;
        int priceColor;
        if (handler.isCreative()) {
            price = Text.translatable("screen.basedefense.recruit.price_free");
            priceColor = GOOD_LIGHT;
        } else {
            price = Text.translatable("screen.basedefense.recruit.price", RecruitScreenHandler.COST, handler.coins());
            priceColor = handler.canAfford() ? GOOD_LIGHT : BAD;
        }
        int pw = this.textRenderer.getWidth(price) + 18;
        int px = left + (W - pw) / 2;
        c.drawItem(COIN, px, priceY - 4);
        embossed(c, this.textRenderer, price, px + 18, priceY, priceColor);

        // Кнопка
        boolean enabled = canRecruit();
        boolean hover = overButton(mouseX, mouseY);
        button(c, this.textRenderer, btnLeft(), btnTop(), btnLeft() + BTN_W, btnTop() + BTN_H,
                Text.translatable("screen.basedefense.recruit.button"), !enabled ? 2 : hover ? 1 : 0);

        // Почему нельзя (или подсказка)
        Text hint;
        int hintColor = TEXT_MUTED;
        if (beds == 0) {
            hint = Text.translatable("screen.basedefense.recruit.hint.no_beds");
            hintColor = BAD;
        } else if (!handler.hasFreeBed()) {
            hint = Text.translatable("screen.basedefense.recruit.hint.full");
            hintColor = WARN;
        } else if (!handler.canAfford()) {
            hint = Text.translatable("screen.basedefense.recruit.hint.coins", RecruitScreenHandler.COST - handler.coins());
            hintColor = BAD;
        } else {
            hint = Text.translatable("screen.basedefense.recruit.hint.ready");
        }
        embossedCentered(c, this.textRenderer, hint, left + W / 2, bottom - 15, hintColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overButton(mouseX, mouseY)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (canRecruit()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                if (mc.interactionManager != null) {
                    // Сервер всё проверит ещё раз
                    mc.interactionManager.clickButton(this.handler.syncId, RecruitScreenHandler.BUTTON_RECRUIT);
                }
            } else {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
