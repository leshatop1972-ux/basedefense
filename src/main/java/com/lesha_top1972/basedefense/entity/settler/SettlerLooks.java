package com.lesha_top1972.basedefense.entity.settler;

/**
 * Список внешностей жителей (общий для сервера и клиента). Номер внешности хранится у жителя (NBT "Skin")
 * и не зависит от профессии. Текстура — textures/entity/settler/&lt;имя&gt;.png, её рисует
 * tools/settler_textures.py (порядок там такой же). Новые внешности добавлять только в КОНЕЦ списка,
 * чтобы у уже живущих жителей внешность не поменялась.
 */
public final class SettlerLooks {

    /** Имена текстур по номеру внешности. */
    public static final String[] NAMES = {
            "male_1", "male_2", "male_3", "male_4", "male_5",
            "female_1", "female_2", "female_3",
            "male_6", "male_7",
            "female_4", "female_5",
            "male_8", "female_6"
    };

    private static final boolean[] FEMALE = {
            false, false, false, false, false,
            true, true, true,
            false, false,
            true, true,
            false, true
    };

    public static final int COUNT = NAMES.length;

    private SettlerLooks() {
    }

    public static boolean isValid(int skin) {
        return skin >= 0 && skin < COUNT;
    }

    public static boolean isFemale(int skin) {
        return isValid(skin) && FEMALE[skin];
    }
}
