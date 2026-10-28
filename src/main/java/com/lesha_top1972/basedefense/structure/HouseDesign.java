package com.lesha_top1972.basedefense.structure;

import net.minecraft.text.Text;

/**
 * Дизайн (архитектурный стиль) постройки. Выбирается в строительном планшете у каждого здания.
 * Дизайны отличаются не только блоками, но и формой: крышей, высотой, крыльцом, башенками.
 * Чертежи — в HousePlans (класс Style), названия и описания — в lang ("design.basedefense.<id>").
 * Номер дизайна (ordinal) хранится в стройплощадках и планшете — новые добавлять только в конец.
 */
public enum HouseDesign {
    /** «Дубовая слобода»: классика — дубовые стены, еловые балки, двускатная крыша. */
    OAK("oak", 0xFFC79A5B),
    /** «Таёжный сруб»: стены из еловых брёвен, торчащие концы брёвен, широкие свесы, крытое крыльцо. */
    SPRUCE("spruce", 0xFF6B8F4E),
    /** «Каменная застава»: каменный кирпич, стены выше, плоская крыша с зубцами, башенки, бойницы. */
    STONE("stone", 0xFF9AA3AD),
    /** «Берёзовый терем»: белые стены в тёмном фахверке, высокая крутая крыша с коньком. */
    BIRCH("birch", 0xFFE6E0C8);

    private final String id;
    /** Цвет-акцент дизайна для интерфейса. */
    private final int color;

    HouseDesign(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public Text getName() {
        return Text.translatable("design.basedefense." + id);
    }

    public Text getDescription() {
        return Text.translatable("design.basedefense." + id + ".desc");
    }

    /** Дизайн по номеру (неизвестный — дубовый). */
    public static HouseDesign byIndex(int index) {
        HouseDesign[] all = values();
        return index >= 0 && index < all.length ? all[index] : OAK;
    }

    /** Дизайн по id (неизвестный или пустой — дубовый, как у старых построек). */
    public static HouseDesign byId(String id) {
        for (HouseDesign d : values()) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return OAK;
    }
}
