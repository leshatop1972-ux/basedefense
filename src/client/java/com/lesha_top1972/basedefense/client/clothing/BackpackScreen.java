package com.lesha_top1972.basedefense.client.clothing;

import com.lesha_top1972.basedefense.client.needs.NeedsTabs;
import com.lesha_top1972.basedefense.clothing.BackpackScreenHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

/** Окно рюкзака: ванильное окно сундука на 2–3 ряда + вкладки справа («Инвентарь», «Потребности», «Рюкзак»). */
public class BackpackScreen extends GenericContainerScreen {

    public BackpackScreen(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int owner = this.handler instanceof BackpackScreenHandler b ? b.getOwnerId() : -1;
        NeedsTabs.add(this::addDrawableChild, this.x + this.backgroundWidth, this.y, NeedsTabs.Tab.BACKPACK, owner);
    }
}
