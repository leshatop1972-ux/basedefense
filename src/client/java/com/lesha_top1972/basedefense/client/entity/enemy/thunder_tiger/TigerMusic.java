package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.ThunderTigerEntity;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import java.util.List;

/**
 * Музыка боя с «Громовым тигром» (sounds/music/thunder_tiger_battle.ogg, событие music.thunder_tiger).
 * Начинается, когда тигр рядом (48 бл) и дерётся всерьёз; играет по кругу, пока он жив и не дальше 80 бл;
 * плавно появляется и затихает. Ванильная музыка на это время выключается. Не зависит от ползунка «Музыка»
 * (категория «Общая громкость»), громкость задана здесь — VOLUME.
 */
public final class TigerMusic {

    /** Громкость (умножается только на «Общую громкость»): негромко, чтобы не забивать звуки боя. */
    public static final float VOLUME = 0.3f;
    private static final double START = 48.0;
    private static final double KEEP = 80.0;

    private static Track current;
    private static int lost;
    /** Мир, в котором играли в прошлый тик: при переходе в другое измерение (арена тигра) игра останавливает
     *  все звуки — трек перезапускается, а на поиск тигра в новом мире даётся 10 с. */
    private static Object lastWorld;
    private static int clientTicks;
    private static int startedAt;

    private TigerMusic() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TigerMusic::tick);
    }

    private static ThunderTigerEntity nearest(MinecraftClient mc, double range) {
        List<ThunderTigerEntity> list = mc.world.getEntitiesByClass(ThunderTigerEntity.class,
                mc.player.getBoundingBox().expand(range), e -> e.isAlive());
        ThunderTigerEntity best = null;
        double bd = range * range;
        for (ThunderTigerEntity e : list) {
            double d = e.squaredDistanceTo(mc.player);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            if (current != null) {
                mc.getSoundManager().stop(current);
                current = null;
            }
            return;
        }
        clientTicks++;
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            if (current != null && !current.isDone()) {
                lost = -200;                                   // ждём, пока тигр появится в новом мире
            }
        }
        // Трек остановили снаружи (смена измерения — игра глушит все звуки) — запускаем заново
        if (current != null && !current.isDone() && !current.fading && clientTicks - startedAt > 20
                && !mc.getSoundManager().isPlaying(current)) {
            current = new Track(VOLUME * 0.5f);
            mc.getSoundManager().play(current);
            startedAt = clientTicks;
        }
        boolean playing = current != null && !current.isDone();
        ThunderTigerEntity boss = nearest(mc, playing ? KEEP : START);
        boolean fight = boss != null && (playing || !boss.isLazy() || boss.getPhase() > 1 || boss.getAction() >= 0
                || boss.clientAction >= 0);
        if (fight) {
            lost = 0;
            if (!playing) {
                current = new Track(0.01f);
                mc.getSoundManager().play(current);
                startedAt = clientTicks;
            }
            mc.getMusicTracker().stop();
        } else if (playing && ++lost > 60) {
            current.fadeOut();
        }
    }

    private static final class Track extends MovingSoundInstance {
        private boolean fading;

        Track(float startVolume) {
            super(ModSounds.TIGER_MUSIC, SoundCategory.MASTER, SoundInstance.createRandom());
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = startVolume;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }

        void fadeOut() {
            this.fading = true;
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (this.fading) {
                this.volume -= 0.006f;
                if (this.volume <= 0.001f) {
                    this.setDone();
                }
            } else if (this.volume < VOLUME) {
                this.volume = Math.min(VOLUME, this.volume + 0.01f);
            }
        }
    }
}
