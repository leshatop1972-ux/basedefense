"""
Звуки катаны — синтез (numpy + scipy, ffmpeg с libvorbis), по образцу thunder_tiger_sounds.py.
Запуск: python tools/katana_sounds.py → assets/basedefense/sounds/item/katana/*.ogg и записи в sounds.json

Свист клинка (тонкий, резкий; 3 варианта), тяжёлый свист, рассечение (2), выхват из ножен («сйинг» — скольжение
стали + короткий отзвук, без чистого «звона»), вложить в ножны (скольжение + щелчок устья), зарядка (нарастающий
поток воздуха), готовность (короткий яркий отблеск), выхват на рывке (выхват + мощный свист + порыв), тибури
(короткий взмах), отложенный разрез (серия рассечений и глухой удар).
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, reverb  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "item", "katana")
rng = np.random.default_rng(2026)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def env(t, a, d):
    return np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / d)


def sweep(sec, f0, f1, peak=0.4, width=0.2, q=0.35):
    """Свист: узкополосный шум, полоса скользит f0 → f1; пик громкости в доле peak."""
    t = T(sec)
    n = noise(sec)
    x = np.zeros_like(t)
    steps = 28
    for i in range(steps):
        a, b = i / steps, (i + 1) / steps
        f = f0 * (f1 / f0) ** ((a + b) / 2)
        seg = bp(n, f * (1 - q), f * (1 + q))
        m = (t >= a * sec) & (t < b * sec)
        x[m] = seg[m]
    return x * np.exp(-((t - peak * sec) / (width * sec)) ** 2)


def scrape(sec, f0, f1, k=1.0):
    """Сталь о сталь/ножны: шершавый высокий шум с «зернистостью»."""
    t = T(sec)
    grain = 1 + 0.6 * np.sign(np.sin(2 * np.pi * (180 + 60 * t) * t))
    return sweep(sec, f0, f1, 0.5, 0.45, 0.25) * grain * k


def shimmer(sec, f, d=0.25, k=1.0):
    """Короткий отблеск стали: неровные (не кратные) обертоны с биением, быстро гаснет — не похож на звук опыта."""
    t = T(sec)
    x = np.zeros_like(t)
    for r, a in ((1.0, 1.0), (1.37, 0.6), (2.11, 0.45), (2.93, 0.3), (3.71, 0.2)):
        ff = f * r
        x += a * np.sin(2 * np.pi * ff * t + rng.random() * 6) * (1 + 0.3 * np.sin(2 * np.pi * (7 + r) * t))
    return x * np.exp(-t / d) * k


def slice_(sec=0.22, k=1.0, low=1.0):
    """Рассечение: резкий щелчок, «влажный» шорох, глухой удар."""
    t = T(sec)
    n = noise(sec)
    crack = highp(n, 2500) * np.exp(-t / 0.006) * 1.6
    wet = bp(n, 900, 4200) * env(t, 0.004, 0.05)
    thud = (np.sin(2 * np.pi * 95 * t * (1 - 0.4 * t)) * np.exp(-t / 0.06) + lowp(n, 300) * np.exp(-t / 0.04)) * 0.9 * low
    return (crack + wet + thud) * k


def s_swing():
    for i, (f0, f1, ln) in enumerate(((700, 4200, 0.30), (900, 3600, 0.27), (600, 4600, 0.33))):
        x = sweep(ln, f0, f1, 0.35, 0.22) * 1.2 + sweep(ln, 220, 900, 0.4, 0.3) * 0.35
        save("swing%d" % i, reverb(x, 0.12, 0.4), 0.8)


def s_swing_heavy():
    x = sweep(0.45, 300, 3200, 0.42, 0.24) + sweep(0.45, 120, 500, 0.45, 0.3) * 0.7
    save("swing_heavy", reverb(x, 0.15, 0.5), 0.9)


def s_hit():
    for i in range(2):
        x = slice_(0.3, 1.0, 1.0 + 0.3 * i)
        place(x, sweep(0.12, 2000, 6000, 0.3, 0.3) * 0.4, 0.0)
        save("hit%d" % i, reverb(x, 0.1, 0.3), 0.95)


def s_draw():
    x = np.zeros(int(0.9 * SR))
    place(x, scrape(0.35, 2500, 7000, 1.0), 0.0)
    place(x, shimmer(0.7, 2850, 0.18, 0.18), 0.30)
    place(x, sweep(0.25, 800, 4000, 0.4, 0.25) * 0.5, 0.28)
    save("draw", reverb(x, 0.2, 0.6), 0.85)


def s_sheathe():
    x = np.zeros(int(0.7 * SR))
    place(x, scrape(0.42, 6000, 2200, 0.8), 0.0)
    t = T(0.05)
    click = highp(noise(0.05), 1800) * np.exp(-t / 0.004) * 2.0 + np.sin(2 * np.pi * 1450 * t) * np.exp(-t / 0.01)
    place(x, click, 0.42)
    save("sheathe", reverb(x, 0.15, 0.4), 0.85)


def s_charge():
    sec = 1.5
    t = T(sec)
    air = sweep(sec, 180, 1400, 0.85, 0.6, 0.5) * (t / sec) ** 1.4
    drone = np.sin(2 * np.pi * (70 + 50 * t / sec) * t) * 0.35 * (t / sec) * (1 + 0.4 * np.sin(2 * np.pi * 6 * t))
    save("charge", reverb(air + drone, 0.2, 0.7), 0.7)


def s_ready():
    x = np.zeros(int(0.8 * SR))
    place(x, shimmer(0.7, 3300, 0.2, 0.5), 0.0)
    place(x, sweep(0.3, 3000, 9000, 0.25, 0.3) * 0.5, 0.0)
    save("ready", reverb(x, 0.3, 0.8), 0.75)


def s_special():
    x = np.zeros(int(1.2 * SR))
    place(x, scrape(0.12, 3000, 8000, 1.0), 0.0)
    place(x, sweep(0.5, 250, 5200, 0.3, 0.25) * 1.6, 0.04)
    place(x, sweep(0.8, 90, 600, 0.3, 0.4) * 0.9, 0.02)
    t = T(0.5)
    place(x, lowp(noise(0.5), 250) * env(t, 0.01, 0.15) * 1.2, 0.03)
    save("special", reverb(x, 0.25, 0.9), 1.0)


def s_flick():
    x = np.zeros(int(0.35 * SR))
    place(x, sweep(0.16, 900, 4500, 0.4, 0.25), 0.0)
    save("flick", reverb(x, 0.1, 0.3), 0.7)


def s_burst():
    x = np.zeros(int(1.2 * SR))
    for i, at in enumerate((0.0, 0.05, 0.09, 0.15)):
        place(x, slice_(0.25, 1.0 - 0.12 * i, 0.6), at)
        place(x, sweep(0.1, 2500, 7000, 0.3, 0.3) * 0.4, at)
    t = T(0.8)
    boom = (np.sin(2 * np.pi * 55 * t * (1 - 0.3 * t)) * np.exp(-t / 0.25) + lowp(noise(0.8), 200) * np.exp(-t / 0.2)) * 1.1
    place(x, boom, 0.15)
    save("burst", reverb(x, 0.3, 1.0), 1.0)


def s_block():
    for i in range(2):
        x = np.zeros(int(0.7 * SR))
        t = T(0.05)
        hitn = highp(noise(0.05), 1500) * np.exp(-t / 0.006) * 2.0
        place(x, hitn, 0.0)
        place(x, shimmer(0.6, 1650 + 260 * i, 0.12, 0.5), 0.0)       # сталь о сталь — короткий, неровный звон
        place(x, scrape(0.18, 5000, 2500, 0.5), 0.01)
        tt = T(0.2)
        place(x, (np.sin(2 * np.pi * 120 * tt) * np.exp(-tt / 0.05)) * 0.6, 0.0)
        save("block%d" % i, reverb(x, 0.2, 0.5), 0.95)


def s_rift():
    """Разлом пространства: низкий гул, «стеклянный» треск, засасывающий свист."""
    sec = 1.6
    x = np.zeros(int(sec * SR))
    t = T(sec)
    drone = np.sin(2 * np.pi * (48 + 20 * t) * t) * np.exp(-t / 0.7) * 0.9
    place(x, drone, 0.0)
    place(x, sweep(1.0, 6000, 300, 0.2, 0.35) * 0.8, 0.0)
    for i in range(10):
        at = 0.05 + rng.random() * 0.5
        tt = T(0.08)
        crack = highp(noise(0.08), 3000) * np.exp(-tt / (0.004 + rng.random() * 0.01)) * (0.6 + rng.random())
        place(x, crack, at)
    place(x, shimmer(1.2, 820, 0.4, 0.25), 0.0)
    save("rift", reverb(x, 0.4, 1.2), 1.0)


EVENTS = {
    "swing": (["swing0", "swing1", "swing2"], 16), "swing_heavy": (["swing_heavy"], 20), "hit": (["hit0", "hit1"], 16),
    "draw": (["draw"], 12), "sheathe": (["sheathe"], 12), "charge": (["charge"], 16), "ready": (["ready"], 16),
    "special": (["special"], 32), "flick": (["flick"], 12), "burst": (["burst"], 32),
    "block": (["block0", "block1"], 16), "rift": (["rift"], 48),
}


def write_json():
    import json
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, dist) in EVENTS.items():
        key = "item.katana." + ev
        sub = "subtitles.basedefense.katana." + ("swing" if ev == "swing_heavy" else ev)
        data[key] = {"subtitle": sub,
                     "sounds": [{"name": "basedefense:item/katana/" + n, "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_swing, s_swing_heavy, s_hit, s_draw, s_sheathe, s_charge, s_ready, s_special, s_flick, s_burst, s_block, s_rift):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
