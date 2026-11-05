package com.lesha_top1972.basedefense.work;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Виды работ. Приоритет 1 (главное) … 4, 0 — не делает. При равном приоритете — по порядку здесь.
 * <ul>
 *   <li>Готовка — жарит сырую еду со склада у печи/коптильни/костра (печи нужно топливо).</li>
 *   <li>Земледелие — собирает спелое, пересаживает, засевает пустую пашню семенами.</li>
 *   <li>Рубка — валит природные деревья (с живой листвой, не постройки), сажает саженцы. Нужен топор.</li>
 *   <li>Добыча — копает размеченные шахты сверху вниз. Нужна кирка.</li>
 *   <li>Переноска — собирает вещи с земли и относит на склад.</li>
 * </ul>
 */
public enum WorkType {
    COOKING("cooking", Skill.COOKING, Items.SMOKER, 3),
    FARMING("farming", Skill.FARMING, Items.IRON_HOE, 3),
    WOODCUTTING("woodcutting", Skill.WOODCUTTING, Items.IRON_AXE, 3),
    MINING("mining", Skill.MINING, Items.IRON_PICKAXE, 3),
    HAULING("hauling", null, Items.CHEST, 4);

    public final String id;
    @Nullable
    public final Skill skill;
    public final Item icon;
    public final int defaultPriority;

    WorkType(String id, @Nullable Skill skill, Item icon, int defaultPriority) {
        this.id = id;
        this.skill = skill;
        this.icon = icon;
        this.defaultPriority = defaultPriority;
    }
}
