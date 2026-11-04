package com.lesha_top1972.basedefense.clothing;

/** Слот одежды (слой под бронёй). Номер = номер ячейки в инвентаре одежды (как у брони: сверху вниз). */
public enum ClothingType {
    HEAD("head"),
    CHEST("chest"),
    LEGS("legs"),
    FEET("feet");

    public final String id;

    ClothingType(String id) {
        this.id = id;
    }
}
