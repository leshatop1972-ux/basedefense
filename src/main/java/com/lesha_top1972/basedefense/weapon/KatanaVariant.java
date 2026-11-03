package com.lesha_top1972.basedefense.weapon;

/**
 * Виды катан: одна механика, разные цифры, модель (assets/basedefense/mesh/&lt;model&gt;.json) и эффекты.
 * <ul>
 *   <li>Катана — серия 4 / 4 / 3+5, «Иссэн» 8 + 12.</li>
 *   <li>Утончённая — урон серии +1, «Иссэн» +3, прочность ×2.</li>
 *   <li>Древняя — урон серии +2; отбитые блоком удары дают «Древнюю силу» (до 20, −1 раз в 10 с);
 *       «Иссэн» — разлом пространства: дальше и шире, урон +5 и +1 за каждую единицу силы, тратит половину силы.</li>
 * </ul>
 */
public enum KatanaVariant {
    BASE("katana", 0f, 0f, 250, false, 4, 1.6, 1.0),
    REFINED("katana_refined", 1f, 3f, 500, false, 4, 1.6, 1.0),
    ANCIENT("katana_ancient", 2f, 5f, 1000, true, 5, 2.4, 1.5);

    /** Имя модели и текстуры. */
    public final String model;
    /** Прибавка к урону каждого удара серии. */
    public final float comboBonus;
    /** Прибавка к урону обеих частей «Иссэн». */
    public final float specialBonus;
    public final int durability;
    /** Копит «Древнюю силу» и бьёт ею (разлом). */
    public final boolean ancient;
    /** Рывок особой атаки: тиков по 2 блока. */
    public final int dashTicks;
    /** Насколько шире задевает на пути рывка (блоки). */
    public final double sweep;
    public final double hitBox;

    KatanaVariant(String model, float comboBonus, float specialBonus, int durability, boolean ancient, int dashTicks,
                  double sweep, double hitBox) {
        this.model = model;
        this.comboBonus = comboBonus;
        this.specialBonus = specialBonus;
        this.durability = durability;
        this.ancient = ancient;
        this.dashTicks = dashTicks;
        this.sweep = sweep;
        this.hitBox = hitBox;
    }
}
