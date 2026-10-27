"""
Звуки «Осадного жука», «Маленького жука» и яйца — синтез (numpy + scipy, ffmpeg), по образцу nabat_sounds.py.
Запуск: python tools/beetle_sounds.py   → assets/basedefense/sounds/beetle/*.ogg и записи в sounds.json
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, rattle, reverb  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "beetle")
rng = np.random.default_rng(2207)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def thud(sec, f0, d, k=1.0):
    t = T(sec)
    return (np.sin(2 * np.pi * f0 * t * (1 - 0.3 * t)) * np.exp(-t / d) + lowp(noise(sec), 300) * np.exp(-t / (d * 0.5)) * 0.7) * k


def clicks(sec, rate, lo, hi, dec=0.004, fall=1.0):
    return rattle(sec, rate, lo, hi, dec, start=0.0, fall=fall)


def chirr(sec, f0, rate, k=1.0):
    """Стрекот: шумовые импульсы с частотой rate через полосу вокруг f0."""
    t = T(sec)
    env = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * rate * t))) * np.exp(-((t - sec / 2) / (sec / 2.2)) ** 4)
    return bp(noise(sec), f0 * 0.6, f0 * 1.6) * env * k


def s_step():
    for v in range(3):
        sec = 0.35
        x = thud(sec, 55 + v * 8, 0.06, 1.4) + clicks(sec, 20, 800, 2600, 0.006) * 0.35
        save("step%d" % v, x, vol=0.7)


def s_scrape():
    sec = 0.8
    t = T(sec)
    x = bp(noise(sec), 400, 2200) * np.sin(np.pi * t / sec) ** 2 * (1 + 0.5 * np.sin(2 * np.pi * 31 * t))
    save("scrape", x + clicks(sec, 30, 600, 2000) * 0.3, vol=0.5)


def s_ambient():
    for v in range(2):
        sec = 1.6
        t = T(sec)
        hiss = bp(noise(sec), 1500, 6000) * np.sin(np.pi * t / sec) ** 2 * 0.5
        x = hiss + chirr(sec, 2200 + v * 400, 18 + v * 4, 0.8) + lowp(noise(sec), 120) * 0.3 * np.sin(np.pi * t / sec)
        save("ambient%d" % v, x, vol=0.55)


def s_roar():
    sec = 1.3
    t = T(sec)
    growl = bp(noise(sec), 120, 700) * (1 + 0.8 * np.sin(2 * np.pi * 24 * t)) * np.minimum(1, t / 0.2) * np.exp(-t / 1.0)
    crack = clicks(sec, 140, 700, 3500, 0.003) * np.minimum(1, t / 0.3)
    save("roar", reverb(growl * 1.5 + crack * 0.6 + chirr(sec, 1600, 30, 0.6), 0.25, 0.9))


def s_ram():
    sec = 1.8
    t = T(sec)
    boom = np.sin(2 * np.pi * 45 * t * (1 - 0.2 * t)) * np.exp(-t / 0.25) * 2.0
    crack = bp(noise(sec), 200, 5000) * np.exp(-t / 0.05) * 2.0
    stone = rattle(sec, 150, 300, 3000, 0.01, start=0.03, fall=2.0) * 1.0
    save("ram", reverb(boom + crack + stone, 0.25, 0.9))


def s_horn_swing():
    for v in range(2):
        sec = 0.6
        t = T(sec)
        w = bp(noise(sec), 250 + v * 80, 1800) * np.exp(-((t - 0.22) / 0.09) ** 2) * 2.2
        save("horn_swing%d" % v, w, vol=0.75)


def s_horn_hit():
    sec = 0.7
    t = T(sec)
    x = thud(sec, 70, 0.09, 1.6) + bp(noise(sec), 600, 4000) * np.exp(-t / 0.03) * 1.5 + clicks(sec, 40, 900, 3000) * 0.4
    save("horn_hit", reverb(x, 0.15, 0.6))


def s_mandible():
    for v in range(3):
        sec = 0.3
        x = clicks(sec, 25 + v * 10, 1500, 5000, 0.002, fall=3.0)
        save("mandible%d" % v, x, vol=0.6)


def s_lay():
    sec = 1.2
    t = T(sec)
    squelch = bp(noise(sec), 150, 1200) * (np.exp(-((t - 0.5) / 0.12) ** 2) + 0.7 * np.exp(-((t - 0.85) / 0.08) ** 2)) * 2.0
    glop = np.sin(2 * np.pi * (140 - 60 * t) * t) * np.exp(-((t - 0.88) / 0.06) ** 2) * 0.8
    save("lay", squelch + glop + bp(noise(sec), 2000, 6000) * np.exp(-((t - 0.6) / 0.25) ** 2) * 0.15, vol=0.7)


def s_hatch():
    sec = 1.0
    t = T(sec)
    x = clicks(sec, 120, 800, 4000, 0.003, fall=0.6) * 1.2 + bp(noise(sec), 200, 1500) * np.exp(-((t - 0.3) / 0.1) ** 2) * 1.2
    x += chirr(sec, 3200, 40, 0.4)
    save("hatch", x, vol=0.75)


def s_hurt():
    for v in range(2):
        sec = 0.5
        t = T(sec)
        x = chirr(sec, 1400 + v * 300, 28, 1.0) + clicks(sec, 40, 700, 2500) * 0.6 + thud(sec, 80, 0.05, 0.6)
        save("hurt%d" % v, x, vol=0.7)


def s_death():
    sec = 2.2
    t = T(sec)
    x = chirr(sec, 1200, 22, 0.9) * np.exp(-t / 0.9) + clicks(sec, 80, 500, 2500, 0.006, fall=1.5) * 0.8
    place(x, thud(1.0, 40, 0.3, 2.0), 0.9)
    save("death", reverb(x, 0.3, 1.0))


# --- маленький жук
def s_mite():
    for v in range(2):
        sec = 0.45
        save("mite_chirp%d" % v, chirr(sec, 3500 + v * 600, 40, 1.0) + clicks(sec, 20, 3000, 7000, 0.002) * 0.3, vol=0.5)
    for v in range(2):
        sec = 0.18
        save("mite_step%d" % v, clicks(sec, 30, 1500, 5000, 0.002) + thud(sec, 160, 0.02, 0.4), vol=0.35)
    for v in range(2):
        sec = 0.3
        t = T(sec)
        save("mite_bite%d" % v, clicks(sec, 30, 2000, 6000, 0.002, fall=3.0) + bp(noise(sec), 800, 4000) * np.exp(-t / 0.03),
             vol=0.6)
    sec = 0.8
    save("mite_death", chirr(sec, 3000, 35, 1.0) * np.exp(-T(sec) / 0.4) + clicks(sec, 40, 1500, 5000), vol=0.55)


EVENTS = {
    "entity.siege_beetle.step": (["step0", "step1", "step2"], 24),
    "entity.siege_beetle.scrape": (["scrape"], 16),
    "entity.siege_beetle.ambient": (["ambient0", "ambient1"], 24),
    "entity.siege_beetle.roar": (["roar"], 40),
    "entity.siege_beetle.ram": (["ram"], 48),
    "entity.siege_beetle.horn_swing": (["horn_swing0", "horn_swing1"], 24),
    "entity.siege_beetle.horn_hit": (["horn_hit"], 32),
    "entity.siege_beetle.mandible": (["mandible0", "mandible1", "mandible2"], 16),
    "entity.siege_beetle.lay": (["lay"], 20),
    "entity.siege_beetle.hurt": (["hurt0", "hurt1"], 24),
    "entity.siege_beetle.death": (["death"], 40),
    "entity.beetle_egg.hatch": (["hatch"], 24),
    "entity.beetle_mite.ambient": (["mite_chirp0", "mite_chirp1"], 16),
    "entity.beetle_mite.step": (["mite_step0", "mite_step1"], 10),
    "entity.beetle_mite.bite": (["mite_bite0", "mite_bite1"], 16),
    "entity.beetle_mite.death": (["mite_death"], 16),
}


def write_json():
    import json
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, dist) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:beetle/" + n, "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_step, s_scrape, s_ambient, s_roar, s_ram, s_horn_swing, s_horn_hit, s_mandible, s_lay, s_hatch, s_hurt,
               s_death, s_mite):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
