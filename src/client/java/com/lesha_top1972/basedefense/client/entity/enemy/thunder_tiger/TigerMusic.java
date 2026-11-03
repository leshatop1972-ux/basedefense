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
 * плавно появляется и затихает. Ванильная музыка на это время выключается. Громкость — ползунок «Музыка».
 */
public final class TigerMusic {

    /** Громкость (до ползунка «Музыка»): негромко, чтобы не забивать звуки боя. */
    public static final float VOLUME = 0.35f;
    private static final double START = 48.0;
    private static final double KEEP = 80.0;

    private static Track current;
    private static int lost;

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
        boolean playing = current != null && !current.isDone();
        ThunderTigerEntity boss = nearest(mc, playing ? KEEP : START);
        boolean fight = boss != null && (playing || !boss.isLazy() || boss.getPhase() > 1 || boss.getAction() >= 0
                || boss.clientAction >= 0);
        if (fight) {
            lost = 0;
            if (!playing) {
                current = new Track();
                mc.getSoundManager().play(current);
            }
            mc.getMusicTracker().stop();
        } else if (playing && ++lost > 60) {
            current.fadeOut();
        }
    }

    private static final class Track extends MovingSoundInstance {
        private boolean fading;

        Track() {
            super(ModSounds.TIGER_MUSIC, SoundCategory.MUSIC, SoundInstance.createRandom());
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.01f;
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
