package com.lesha_top1972.basedefense.client.clothing;

import com.lesha_top1972.basedefense.client.needs.NeedsTabs;
import com.lesha_top1972.basedefense.clothing.BackpackScreenHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

/** Окно рюкзака: ванильное окно сундука на 2–3 ряда; кнопка ↩ в углу — назад в инвентарь (или меню поселенца). */
public class BackpackScreen extends GenericContainerScreen {

    public BackpackScreen(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int owner = this.handler instanceof BackpackScreenHandler b ? b.getOwnerId() : -1;
        // Назад — в инвентарь игрока (или в меню поселенца)
        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("↩"),
                        btn -> NeedsTabs.toInventory(owner))
                .dimensions(this.x + this.backgroundWidth - 18, this.y + 4, 14, 12)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.basedefense.backpack.back")))
                .build());
    }
}
