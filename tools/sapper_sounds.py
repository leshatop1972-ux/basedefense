"""
Звуки «Подкопщика» — синтез (numpy + scipy, ffmpeg с libvorbis), по образцу nabat_sounds.py.
Запуск: python tools/sapper_sounds.py   → assets/basedefense/sounds/sapper/*.ogg (моно) и записи в sounds.json

Стук кирки под землёй (глухой), шорох осыпи, чирканье спички и шипение фитиля, сухой хлопок бомбы,
тяжёлый взрыв подрыва с дребезгом камня, шипение дымовой банки, сопение и ворчание, шаги ботинок,
удар «Тычка» о камень, «пых» задутой свечи, бросок, урон, смерть.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, reverb, rattle  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "mob", "sapper")
rng = np.random.default_rng(1309)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def env(t, a, d):
    return np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / d)


def thud(sec, f0=90, d=0.06, k=1.0):
    t = T(sec)
    body = np.sin(2 * np.pi * f0 * t * (1 - 0.3 * t)) * np.exp(-t / d)
    return (body + lowp(noise(sec), 400) * np.exp(-t / (d * 0.6)) * 0.8) * k


def debris(sec, rate, lo=300, hi=2500, fall=1.5):
    """Осыпь: россыпь мелких камешков и земли."""
    return rattle(sec, rate, lo, hi, 0.006, start=0.0, fall=fall) * 0.6 + \
        bp(noise(sec), 200, 1800) * np.exp(-T(sec) / (sec * 0.35)) * 0.4


def s_dig():
    # глухой стук кирки сквозь землю: низкий удар + приглушённый «тук» металла, всё через НЧ
    for v in range(3):
        sec = 0.5
        t = T(sec)
        x = thud(sec, 70 + v * 12, 0.05, 1.4)
        tick = np.sin(2 * np.pi * (900 + v * 70) * t) * np.exp(-t / 0.015) * 0.5
        x = lowp(x + tick + debris(sec, 30, 200, 900) * 0.4, 700)
        save("dig%d" % v, x, vol=0.75)


def s_crumble():
    for v in range(2):
        sec = 0.9
        x = debris(sec, 90 + v * 30, 250, 2600, fall=2.0)
        save("crumble%d" % v, lowp(x, 3000), vol=0.6)


def s_light():
    # чирк спички (шорох + вспышка) и шипение фитиля
    sec = 0.6
    t = T(sec)
    strike = bp(noise(sec), 1500, 7000) * np.exp(-((t - 0.04) / 0.025) ** 2) * 2.0
    flare = bp(noise(sec), 400, 3000) * env(np.maximum(t - 0.06, 0), 0.01, 0.12) * (t > 0.06) * 1.2
    hiss = highp(noise(sec), 3500) * np.clip((t - 0.12) / 0.1, 0, 1) * 0.45 * (1 + 0.3 * np.sin(2 * np.pi * 23 * t))
    crackle = rattle(sec, 40, 2500, 7000, 0.002, start=0.12) * 0.3
    save("light", strike + flare + hiss + crackle, vol=0.7)


def s_fuse():
    sec = 0.7
    t = T(sec)
    hiss = highp(noise(sec), 3000) * (0.6 + 0.4 * np.sin(2 * np.pi * 17 * t) ** 2) * np.minimum(1, t / 0.05)
    save("fuse", hiss + rattle(sec, 50, 3000, 8000, 0.002) * 0.4, vol=0.55)


def s_bomb():
    # сухой хлопок: короткий широкий импульс + низ + осыпь, без долгого рокота
    for v in range(2):
        sec = 1.4
        t = T(sec)
        crack = bp(noise(sec), 300, 6000) * np.exp(-t / 0.03) * 2.2
        boom = np.sin(2 * np.pi * (55 + v * 8) * t * (1 - 0.25 * t)) * np.exp(-t / 0.18) * 1.4
        low = lowp(noise(sec), 250) * np.exp(-t / 0.25) * 1.0
        x = crack + boom + low + debris(sec, 70, 400, 3000) * 0.5
        save("bomb%d" % v, reverb(x, 0.2, 0.7))


def s_blast():
    # подрыв: тяжёлый удар, раскат, дребезг и грохот камня
    sec = 2.6
    t = T(sec)
    crack = bp(noise(sec), 200, 6000) * np.exp(-t / 0.04) * 2.5
    boom = np.sin(2 * np.pi * 42 * t * (1 - 0.2 * t)) * np.exp(-t / 0.35) * 1.8
    rumble = lowp(noise(sec), 180) * np.exp(-t / 0.7) * 1.6
    rocks = rattle(sec, 160, 300, 3500, 0.01, start=0.05, fall=1.8) * 1.1
    x = crack + boom + rumble + rocks
    place(x, debris(1.2, 120, 300, 2500) * 0.8, 0.5)
    save("blast", reverb(x, 0.3, 1.0))


def s_smoke():
    sec = 3.0
    t = T(sec)
    pop = thud(sec, 160, 0.03, 0.8)
    hiss = bp(noise(sec), 1200, 8000) * np.clip(t / 0.08, 0, 1) * np.exp(-t / 1.4) * 0.9
    hiss *= 1 + 0.2 * np.sin(2 * np.pi * 9 * t)
    save("smoke", pop + hiss, vol=0.6)


def voice(sec, f0, glide=0.0, vowel=(500, 900, 2300), grit=0.4, breath=0.3):
    """Ворчание: низкий гнусавый голос с хрипом (пила через форманты)."""
    t = T(sec)
    f = f0 * (1 + glide * t / sec) * (1 + 0.02 * np.sin(2 * np.pi * 5.5 * t))
    ph = np.cumsum(f) / SR
    saw = (ph % 1.0) - 0.5
    saw += grit * lowp(noise(sec), 600) * 0.3
    y = sum(bp(saw, fr * 0.8, fr * 1.25) * g for fr, g in zip(vowel, (1.0, 0.5, 0.2)))
    y += bp(noise(sec), 600, 3000) * breath * 0.3
    return y * np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.6


def s_ambient():
    # сопение (2 вдоха носом) и ворчание
    sec = 1.1
    t = T(sec)
    sniff = np.zeros_like(t)
    for at in (0.05, 0.3):
        st = np.clip((t - at) / 0.16, 0, 1)
        sniff += bp(noise(sec), 1500, 6000) * np.sin(np.pi * st) ** 2 * (st < 1) * 0.9
    save("ambient0", sniff, vol=0.45)
    save("ambient1", voice(0.8, 95, -0.15, (450, 850, 2200)), vol=0.55)
    save("ambient2", voice(1.0, 110, 0.1, (550, 1000, 2400), grit=0.6), vol=0.5)


def s_step():
    for v in range(3):
        sec = 0.25
        t = T(sec)
        x = lowp(noise(sec), 350) * np.exp(-t / 0.025) * 1.6 + np.sin(2 * np.pi * (110 + v * 15) * t) * np.exp(-t / 0.03)
        x += rattle(sec, 12, 1500, 4000, 0.004) * 0.15            # гвозди в подошве
        save("step%d" % v, x, vol=0.5)


def s_jab():
    for v in range(2):
        sec = 0.6
        t = T(sec)
        clang = np.sin(2 * np.pi * (1400 + v * 120) * t) * np.exp(-t / 0.05) + \
            0.6 * np.sin(2 * np.pi * (2900 + v * 200) * t) * np.exp(-t / 0.03)
        hit = bp(noise(sec), 500, 5000) * np.exp(-t / 0.02) * 1.8
        x = clang + hit + thud(sec, 80, 0.05, 0.9) + debris(sec, 60, 400, 3000) * 0.4
        save("jab%d" % v, reverb(x, 0.15, 0.5))


def s_snuff():
    sec = 0.4
    t = T(sec)
    puff = bp(noise(sec), 300, 2500) * np.exp(-((t - 0.08) / 0.05) ** 2) * 1.5
    sizzle = highp(noise(sec), 4000) * np.exp(-np.maximum(t - 0.1, 0) / 0.08) * (t > 0.1) * 0.4
    save("snuff", puff + sizzle, vol=0.5)


def s_throw():
    for v in range(2):
        sec = 0.4
        t = T(sec)
        w = bp(noise(sec), 500 + v * 150, 3500) * np.exp(-((t - 0.12) / 0.06) ** 2) * 2.0
        save("throw%d" % v, w + voice(0.4, 120, -0.2, grit=0.5) * 0.4, vol=0.6)


def s_hurt():
    for v in range(2):
        save("hurt%d" % v, voice(0.35, 150 + v * 20, -0.3, (600, 1100, 2500), grit=0.8, breath=0.5), vol=0.7)


def s_death():
    sec = 1.4
    x = voice(1.0, 130, -0.5, (520, 950, 2300), grit=0.9, breath=0.6)
    x = np.concatenate([x, np.zeros(int(0.4 * SR))])
    place(x, thud(0.5, 70, 0.08, 1.0), 0.8)
    place(x, rattle(0.5, 30, 1200, 4000, 0.004) * 0.4, 0.85)       # банки звякнули
    save("death", x[: int(sec * SR)], vol=0.8)


def s_emerge():
    sec = 1.0
    x = debris(sec, 140, 250, 3000, fall=2.5) * 1.2 + thud(sec, 75, 0.08, 1.0)
    save("emerge", lowp(x, 4000), vol=0.7)


EVENTS = {  # событие → (файлы, дистанция слышимости)
    "entity.sapper.dig": (["dig0", "dig1", "dig2"], 12),
    "entity.sapper.crumble": (["crumble0", "crumble1"], 12),
    "entity.sapper.emerge": (["emerge"], 24),
    "entity.sapper.light": (["light"], 16),
    "entity.sapper.fuse": (["fuse"], 16),
    "entity.sapper.bomb": (["bomb0", "bomb1"], 48),
    "entity.sapper.blast": (["blast"], 64),
    "entity.sapper.smoke": (["smoke"], 20),
    "entity.sapper.ambient": (["ambient0", "ambient1", "ambient2"], 16),
    "entity.sapper.step": (["step0", "step1", "step2"], 12),
    "entity.sapper.jab": (["jab0", "jab1"], 20),
    "entity.sapper.snuff": (["snuff"], 10),
    "entity.sapper.throw": (["throw0", "throw1"], 16),
    "entity.sapper.hurt": (["hurt0", "hurt1"], 16),
    "entity.sapper.death": (["death"], 24),
}


def write_json():
    import json
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, dist) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:mob/sapper/" + n, "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_dig, s_crumble, s_light, s_fuse, s_bomb, s_blast, s_smoke, s_ambient, s_step, s_jab, s_snuff,
               s_throw, s_hurt, s_death, s_emerge):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
