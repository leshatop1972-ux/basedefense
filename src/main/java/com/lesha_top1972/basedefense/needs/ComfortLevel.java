package com.lesha_top1972.basedefense.needs;

/**
 * Уровни комфорта (шкала 0…100) и что они дают игрокам и поселенцам:
 * <ul>
 *   <li>0…15 «ужасно»: скорость −50%, урон −30%, скорость атаки −25% (дальше — нервные срывы);</li>
 *   <li>15…35 «плохо»: скорость −20%, урон −10%, скорость атаки −10%;</li>
 *   <li>35…75 «нормально»: ничего;</li>
 *   <li>75…100 «хорошо»: скорость +15%, урон +10%, +1 здоровья раз в 10 с.</li>
 * </ul>
 */
public enum ComfortLevel {
    AWFUL(15f, -0.5, -0.3, -0.25),
    BAD(35f, -0.2, -0.1, -0.1),
    NORMAL(75f, 0, 0, 0),
    GOOD(101f, 0.15, 0.1, 0);

    /** Верхняя граница (не включая). */
    public final float below;
    public final double speed;
    public final double damage;
    public final double attackSpeed;

    ComfortLevel(float below, double speed, double damage, double attackSpeed) {
        this.below = below;
        this.speed = speed;
        this.damage = damage;
        this.attackSpeed = attackSpeed;
    }

    public static ComfortLevel of(float comfort) {
        for (ComfortLevel l : values()) {
            if (comfort < l.below) {
                return l;
            }
        }
        return GOOD;
    }
}
