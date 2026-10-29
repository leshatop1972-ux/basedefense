package com.lesha_top1972.basedefense.director;

/**
 * Флаги для клиентского вида событий (их ставит клиент, DirectorVisuals). Миксины мира (DimensionTypeMixin,
 * BiomeMixin) общие для клиента и сервера, поэтому меняют результат только в потоке клиента:
 * сервер (в том числе встроенный, в той же программе) видит настоящее время и погоду.
 */
public final class VisualHooks {

    /** Поток отрисовки клиента (null на выделенном сервере). */
    public static volatile Thread clientThread;
    /** Кровавая вечная ночь: днём небо и свет как в полночь. */
    public static volatile boolean forceNight;
    /** Снегопад: дождь рисуется снегом. */
    public static volatile boolean snow;

    private VisualHooks() {
    }

    public static boolean onClient() {
        return Thread.currentThread() == clientThread;
    }
}
