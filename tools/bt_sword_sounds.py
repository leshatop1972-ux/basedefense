"""
Звуки меча кровожада — синтез (numpy + scipy, ffmpeg), по образцу nabat_sounds.py.
Запуск: python tools/bt_sword_sounds.py   → assets/basedefense/sounds/item/*.ogg и записи в sounds.json

  charge — замах (≈1.5 с): учащающееся сердцебиение, нарастающий низкий гул и «вдох» клинка;
  ready  — меч заряжен: тяжёлый удар сердца и тонкий кровавый звон;
  cleave — удар сверху: свист, глухой тяжёлый удар в землю, мокрый всплеск, гул.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, reverb  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "item")
rng = np.random.default_rng(1972)


def beat(f0=48.0, k=1.0):
    """Удар сердца: два мягких низких толчка «тук‑тук»."""
    t = T(0.42)
    one = np.sin(2 * np.pi * f0 * t * (1 - 0.3 * t)) * np.exp(-t * 26) * k
    one += lowp(noise(0.42), 180) * np.exp(-t * 40) * 0.8 * k
    x = np.zeros(int(0.42 * SR))
    place(x, one, 0.0)
    place(x, one[: int(0.3 * SR)] * 0.65, 0.16)
    return x


def charge():
    sec = 1.6
    t = T(sec)
    x = np.zeros(len(t))
    # сердце: всё чаще
    at = 0.0
    gap = 0.48
    while at < sec - 0.3:
        place(x, beat(46 + 18 * at / sec, 0.8 + 0.4 * at / sec), at)
        at += gap
        gap *= 0.8
    # гул: низкая пила, поднимается по высоте и громкости
    f = 55 + 70 * (t / sec) ** 1.5
    ph = np.cumsum(f) / SR
    hum = (2 * (ph % 1.0) - 1) * 0.5 + np.sin(2 * np.pi * ph * 2) * 0.3
    x += lowp(hum, 420) * (t / sec) ** 2 * 0.9
    # «вдох» клинка: шум, раскрывающийся по полосе
    br = noise(sec)
    lo = 300 + 1600 * (t / sec) ** 2
    seg = int(0.05 * SR)
    out = np.zeros_like(br)
    for i in range(0, len(br), seg):
        out[i:i + seg] = bp(br[max(0, i - seg):i + seg], lo[i], lo[i] * 2.4)[-len(br[i:i + seg]):]
    x += out * (t / sec) ** 3 * 0.7
    ns.save("bt_sword_charge", reverb(x, 0.25, 0.8), 0.85)


def ready():
    t = T(1.2)
    x = np.zeros(len(t))
    place(x, beat(40, 1.4), 0.0)
    ring = sum(np.sin(2 * np.pi * f * t + p) * a for f, p, a in ((1320, 0, 1.0), (1985, 1.2, 0.6), (2650, 2.1, 0.35)))
    ring *= np.exp(-t * 4.5) * (1 - np.exp(-t * 60)) * 0.22
    x += ring * (1 + 0.3 * np.sin(2 * np.pi * 6 * t))
    ns.save("bt_sword_ready", reverb(x, 0.35, 1.0), 0.8)


def cleave():
    for v in range(2):
        sec = 1.5
        t = T(sec)
        x = np.zeros(len(t))
        # свист сверху вниз: полоса шума скользит вниз по частоте
        sw_len = 0.16
        ts = T(sw_len)
        w = noise(sw_len)
        cen = 2600 - 1700 * ts / sw_len
        sw = np.zeros_like(w)
        seg = int(0.01 * SR)
        for i in range(0, len(w), seg):
            sw[i:i + seg] = bp(w[max(0, i - 4 * seg):i + seg], cen[i] * 0.6, cen[i] * 1.6)[-len(w[i:i + seg]):]
        sw *= np.sin(np.pi * ts / sw_len) ** 1.5 * 1.2
        place(x, sw, 0.0)
        hit = 0.15
        th = T(sec - hit)
        # тяжёлый удар в землю: суббас с падением высоты + щелчок
        f = 70 * (1 + v * 0.08) * (1 - 0.45 * np.clip(th / 0.4, 0, 1))
        boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-th * 7) * 1.6
        boom += lowp(noise(sec - hit), 400) * np.exp(-th * 18) * 1.4
        boom += highp(noise(sec - hit), 2500) * np.exp(-th * 70) * 0.6
        place(x, boom, hit)
        # мокрый всплеск: короткие щелчки‑капли
        for k in range(14):
            at = hit + 0.02 + rng.random() * 0.25
            d = T(0.05)
            fr = 400 + rng.random() * 900
            drop = np.sin(2 * np.pi * fr * d * (1 + 2 * d)) * np.exp(-d * 90) * (0.25 + rng.random() * 0.3)
            place(x, drop, at)
        x += bp(noise(sec), 300, 1800) * np.exp(-((t - hit - 0.08) / 0.08) ** 2) * 0.8
        # гул‑хвост
        tail = np.sin(2 * np.pi * 52 * th) * np.exp(-th * 2.5) * 0.4
        place(x, tail, hit)
        ns.save("bt_sword_cleave%d" % v, reverb(x, 0.3, 1.1), 0.95)


EVENTS = {
    "item.bloodthirster_sword.charge": (["bt_sword_charge"], 24),
    "item.bloodthirster_sword.ready": (["bt_sword_ready"], 20),
    "item.bloodthirster_sword.cleave": (["bt_sword_cleave0", "bt_sword_cleave1"], 32),
}


def write_json():
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, d) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:item/" + n, "attenuation_distance": d} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    charge()
    ready()
    cleave()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
