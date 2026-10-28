"""
Звуки «Кровопускателя» и его кровавых копий — синтез (numpy + scipy, ffmpeg), по образцу nabat_sounds.py.
Запуск: python tools/bloodletter_sounds.py   → assets/basedefense/sounds/bloodletter/*.ogg и записи в sounds.json

Голос — низкий мокрый шёпот и хрип; вызов копий — нарастающий хор с бульканьем; копьё застывает с хрустом,
летит со свистом, бьёт мокрым хлопком с треском; «Вихрь» — вращающийся мокрый свист.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, choir, lowp, noise, place, reverb  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "bloodletter")
rng = np.random.default_rng(1313)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def dist(x, k):
    return np.tanh(x * k) / np.tanh(k)


def saw(f, t):
    ph = np.cumsum(f * np.ones_like(t)) / SR if np.isscalar(f) else np.cumsum(f) / SR
    return 2 * (ph % 1.0) - 1


def gurgle(sec, rate=18.0, lo=200, hi=900):
    """Бульканье: короткие пузыри с падающим тоном."""
    t = T(sec)
    x = np.zeros_like(t)
    for _ in range(int(rate * sec)):
        at = rng.random() * (sec - 0.06)
        f0 = lo * (hi / lo) ** rng.random()
        L = int(SR * 0.05)
        tt = np.arange(L) / SR
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * tt / 0.05)) / SR) * np.exp(-tt / 0.012)
        place(x, b * (0.3 + 0.7 * rng.random()), at)
    return x


def breath(sec, f0, k=1.0):
    """Мокрый хрип‑шёпот."""
    t = T(sec)
    env = np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.7
    x = bp(noise(sec), 500, 3200) * 0.7
    x += dist(saw(f0 * (1 + 0.04 * np.sin(2 * np.pi * 4 * t)), t), 2.0) * 0.25
    x = sum(bp(x, f * 0.8, f * 1.25) * g for f, g in ((500, 1.0), (1100, 0.7), (2500, 0.3)))
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * 23 * t)
    return x * env * k


def s_ambient():
    for v in range(2):
        sec = 1.8
        x = breath(sec, 62 + v * 9) + gurgle(sec, 6) * 0.25
        save("ambient%d" % v, reverb(x, 0.35, 1.0), vol=0.6)


def s_summon():
    sec = 1.6
    t = T(sec)
    rise = np.clip(t / 1.1, 0, 1) ** 1.5
    x = choir(sec, 82, chord=(1.0, 1.19, 1.5, 1.78), vowel=(520, 900, 2400)) * rise * 0.8
    x += gurgle(sec, 26, 150, 700) * (0.3 + 0.7 * rise)
    x += lowp(noise(sec), 180) * np.exp(-((t - 1.05) / 0.1) ** 2) * 2.0      # глухой толчок в конце
    save("summon", reverb(x, 0.4, 1.3), vol=0.85)


def s_spear_form():
    for v in range(2):
        sec = 0.55
        t = T(sec)
        crack = np.zeros_like(t)
        for _ in range(26):
            at = rng.random() ** 0.7 * 0.4
            L = int(SR * 0.01)
            place(crack, bp(noise(0.01), 1800, 7000) * np.exp(-np.arange(L) / SR / 0.002) * rng.random(), at)
        hum = np.sin(2 * np.pi * (180 + 120 * t / sec) * t) * np.sin(np.pi * t / sec) * 0.25
        save("spear_form%d" % v, reverb(crack + hum + gurgle(sec, 10, 300, 1200) * 0.4, 0.3, 0.6), vol=0.55)


def s_spear_launch():
    for v in range(3):
        sec = 0.6
        t = T(sec)
        sweep = bp(noise(sec), 700 + 300 * v, 4500) * np.exp(-((t - 0.12) / 0.09) ** 2) * 1.4
        low = np.sin(2 * np.pi * (140 - 70 * t / sec) * t) * np.exp(-t / 0.1) * 0.6
        wet = bp(noise(sec), 300, 1400) * np.exp(-((t - 0.05) / 0.03) ** 2) * 0.7
        save("spear_launch%d" % v, reverb(sweep + low + wet, 0.25, 0.7), vol=0.75)


def s_spear_hit():
    for v in range(3):
        sec = 0.7
        t = T(sec)
        thud = (np.sin(2 * np.pi * (75 + 10 * v) * t) * np.exp(-t / 0.06) + lowp(noise(sec), 400) * np.exp(-t / 0.04)) * 1.3
        splat = bp(noise(sec), 400, 2600) * np.exp(-t / 0.05) * 1.1
        crack = bp(noise(sec), 2500, 8000) * np.exp(-t / 0.015) * 0.6
        save("spear_hit%d" % v, reverb(thud + splat + crack + gurgle(sec, 14, 200, 800) * 0.35, 0.3, 0.8), vol=0.85)


def s_whirl():
    sec = 1.7
    t = T(sec)
    env = np.clip(t / 0.25, 0, 1) * np.clip((sec - t) / 0.3, 0, 1)
    rot = 0.5 + 0.5 * np.sin(2 * np.pi * (1.3 + 0.4 * t) * t)                # «проносится» мимо — два оборота
    x = bp(noise(sec), 500, 3500) * (0.35 + 0.65 * rot) * 1.2
    x += gurgle(sec, 30, 150, 600) * 0.5
    x += dist(saw(55 * (1 + 0.1 * rot), t), 1.5) * 0.12
    save("whirl", reverb(x * env, 0.35, 1.0), vol=0.8)


def s_whirl_hit():
    sec = 0.6
    t = T(sec)
    x = (np.sin(2 * np.pi * 60 * t) * np.exp(-t / 0.08) + lowp(noise(sec), 350) * np.exp(-t / 0.05)) * 1.4
    x += bp(noise(sec), 500, 3000) * np.exp(-t / 0.07)
    save("whirl_hit", reverb(x, 0.25, 0.7), vol=0.85)


def s_hurt():
    for v in range(2):
        save("hurt%d" % v, dist(breath(0.4, 120 + v * 25, 2.0), 2.0) + gurgle(0.4, 20) * 0.3, vol=0.75)


def s_death():
    sec = 2.2
    t = T(sec)
    x = breath(1.6, 70, 1.5)
    x = np.concatenate([x, np.zeros(len(t))])[: len(t)]
    x += gurgle(sec, 30, 120, 500) * np.clip(t / 1.2, 0, 1) * 0.6
    x += lowp(noise(sec), 300) * np.exp(-((t - 1.5) / 0.08) ** 2) * 1.8
    save("death", reverb(x, 0.4, 1.2))


def s_step():
    for v in range(3):
        sec = 0.32
        t = T(sec)
        thud = (np.sin(2 * np.pi * (80 + v * 10) * t) * np.exp(-t / 0.045) + lowp(noise(sec), 300) * np.exp(-t / 0.03)) * 1.2
        squelch = bp(noise(sec), 300, 1600) * np.exp(-((t - 0.06) / 0.03) ** 2) * 0.8
        save("step%d" % v, thud + squelch, vol=0.5)


EVENTS = {
    "entity.bloodletter.ambient": (["ambient0", "ambient1"], 32),
    "entity.bloodletter.summon": (["summon"], 40),
    "entity.bloodletter.spear_form": (["spear_form0", "spear_form1"], 24),
    "entity.bloodletter.spear_launch": (["spear_launch0", "spear_launch1", "spear_launch2"], 40),
    "entity.bloodletter.spear_hit": (["spear_hit0", "spear_hit1", "spear_hit2"], 32),
    "entity.bloodletter.whirl": (["whirl"], 32),
    "entity.bloodletter.whirl_hit": (["whirl_hit"], 24),
    "entity.bloodletter.hurt": (["hurt0", "hurt1"], 24),
    "entity.bloodletter.death": (["death"], 40),
    "entity.bloodletter.step": (["step0", "step1", "step2"], 16),
}


def write_json():
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, d) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:bloodletter/" + n, "attenuation_distance": d} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_ambient, s_summon, s_spear_form, s_spear_launch, s_spear_hit, s_whirl, s_whirl_hit, s_hurt,
               s_death, s_step):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
