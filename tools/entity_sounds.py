"""
Звуки «Туманной сущности» и «Кровавой сущности» — синтез (numpy + scipy, ffmpeg), по образцу nabat_sounds.py.
Запуск: python tools/entity_sounds.py   → assets/basedefense/sounds/entity/*.ogg и записи в sounds.json

Туманная — резкие пугающие звуки в духе хоррор‑модов: искажённый визг, цифровые сбои и треск, обратный шёпот,
внезапный низкий удар со звоном в ушах. Кровавая — менее резкие: мокрое рычание, хрип, шипение.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, reverb  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "entity")
rng = np.random.default_rng(666)


def save(name, x, vol=0.95):
    ns.save(name, x, vol)


def dist(x, k):
    """Жёсткий перегруз."""
    return np.tanh(x * k) / np.tanh(k)


def crush(x, bits=5, hold=6):
    """Битовое и частотное «ломание» (цифровой хруст)."""
    q = 2 ** bits
    y = np.round(x * q) / q
    idx = (np.arange(len(y)) // hold) * hold
    return y[np.minimum(idx, len(y) - 1)]


def saw(f, t):
    ph = np.cumsum(f * np.ones_like(t)) / SR if np.isscalar(f) else np.cumsum(f) / SR
    return 2 * (ph % 1.0) - 1


def stutter(x, chunk=0.03, reps=4):
    """Заедание: кусок повторяется несколько раз."""
    n = int(chunk * SR)
    out = []
    i = 0
    while i < len(x):
        part = x[i:i + n]
        out.append(part)
        if rng.random() < 0.35:
            for _ in range(rng.integers(1, reps)):
                out.append(part)
        i += n
    return np.concatenate(out)


# ---------------------------------------------------------------- туманная
def mist_screech():
    for v in range(3):
        sec = 0.9
        t = T(sec)
        f = (1300 + v * 260) * (1 + 0.35 * np.sin(2 * np.pi * (7 + v) * t)) * (1 + 0.6 * t)
        x = sum(saw(f * r, t) for r in (1.0, 1.013, 0.497))
        x += 0.6 * bp(noise(sec), 2000, 9000)
        x = dist(bp(x, 600, 9000), 6)
        env = np.minimum(1, t / 0.008) * np.exp(-t / 0.5)
        x = crush(x * env, bits=4 + v, hold=3 + v)
        save("mist_screech%d" % v, reverb(stutter(x, 0.025), 0.2, 0.5))


def mist_glitch():
    for v in range(3):
        sec = 0.9
        x = np.zeros(int(sec * SR))
        at = 0.0
        while at < sec - 0.05:
            ln = 0.015 + rng.random() * 0.06
            tt = T(ln)
            kind = rng.integers(0, 3)
            if kind == 0:
                part = noise(ln)
            elif kind == 1:
                part = np.sign(np.sin(2 * np.pi * (200 + rng.random() * 3000) * tt))
            else:
                part = saw(80 + rng.random() * 400, tt)
            part *= 0.4 + 0.6 * rng.random()
            if rng.random() < 0.3:
                part = part[::-1]
            place(x, part, at)
            at += ln + (0.0 if rng.random() < 0.6 else rng.random() * 0.06)
        x = crush(dist(x, 3), bits=3, hold=8)
        save("mist_glitch%d" % v, x, vol=0.8)


def mist_whisper():
    for v in range(2):
        sec = 2.2
        t = T(sec)
        n = noise(sec)
        syl = (0.5 + 0.5 * np.sin(2 * np.pi * (3.3 + v) * t + np.sin(2 * np.pi * 0.7 * t) * 3)) ** 3
        x = sum(bp(n, f * 0.85, f * 1.15) * g for f, g in ((700, 1.0), (1250, 0.7), (2600, 0.5), (4200, 0.3))) * syl
        x = x[::-1]                                          # обратный шёпот
        low = dist(np.sin(2 * np.pi * 55 * t) * 0.6 + lowp(noise(sec), 200), 4) * 0.25
        x = (x * 1.6 + low) * np.sin(np.pi * t / sec) ** 0.5
        save("mist_whisper%d" % v, reverb(x, 0.45, 1.2), vol=0.75)


def mist_sting():
    # внезапный низкий удар + визг + звон в ушах
    sec = 2.4
    t = T(sec)
    hit = dist(np.sin(2 * np.pi * 38 * t * (1 - 0.3 * t)) * np.exp(-t / 0.4) * 2 + lowp(noise(sec), 300) * np.exp(-t / 0.15), 5)
    ring = np.sin(2 * np.pi * 4200 * t) * np.exp(-t / 1.2) * 0.25
    scr = dist(saw(1800 * (1 + 0.3 * t), t) + bp(noise(sec), 3000, 9000) * 0.5, 8) * np.exp(-t / 0.25) * 0.6
    save("mist_sting", reverb(hit + ring + crush(scr, 4, 4), 0.3, 1.0))


def mist_hurt():
    for v in range(2):
        sec = 0.35
        t = T(sec)
        x = dist(saw(900 + v * 300, t) + bp(noise(sec), 1500, 8000), 7) * np.exp(-t / 0.12)
        save("mist_hurt%d" % v, crush(x, 4, 5), vol=0.8)


def mist_death():
    sec = 2.4
    t = T(sec)
    f = 1600 * np.exp(-t * 1.2) + 60
    x = dist(saw(f, t) + saw(f * 1.5, t) * 0.5 + bp(noise(sec), 800, 8000) * 0.6, 6) * np.exp(-t / 1.0)
    x = crush(stutter(x, 0.04, 5)[: len(t)], 4, 4)
    tail = highp(noise(sec), 2000) * np.clip((t - 1.2) / 0.3, 0, 1) * np.exp(-(t - 1.2).clip(0) / 0.5) * 0.4
    save("mist_death", reverb(x + tail, 0.35, 1.2))


# ---------------------------------------------------------------- кровавая
def growl(sec, f0, k=1.0):
    t = T(sec)
    am = 0.6 + 0.4 * np.sin(2 * np.pi * 28 * t)
    x = saw(f0 * (1 + 0.05 * np.sin(2 * np.pi * 3 * t)), t) * am
    x = sum(bp(x, f * 0.8, f * 1.25) * g for f, g in ((400, 1.0), (900, 0.6), (2200, 0.25)))
    x += lowp(noise(sec), 500) * 0.3 * am
    return dist(x * k, 2.5) * np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.5


def blood_growl():
    for v in range(2):
        save("blood_growl%d" % v, reverb(growl(1.3, 70 + v * 15), 0.25, 0.8), vol=0.7)


def blood_attack():
    for v in range(3):
        sec = 0.55
        t = T(sec)
        snarl = growl(sec, 110 + v * 20, 1.5)
        wet = bp(noise(sec), 300, 2500) * np.exp(-((t - 0.25) / 0.05) ** 2) * 1.5      # мокрый хлюп
        save("blood_attack%d" % v, snarl + wet, vol=0.75)


def blood_hiss():
    sec = 0.9
    t = T(sec)
    save("blood_hiss", bp(noise(sec), 2500, 8000) * np.sin(np.pi * t / sec) ** 2 + growl(sec, 60) * 0.4, vol=0.6)


def blood_hurt():
    for v in range(2):
        save("blood_hurt%d" % v, growl(0.35, 140 + v * 30, 2.0), vol=0.75)


def blood_step():
    # тяжёлые мокрые шаги: глухой удар + чавканье
    for v in range(3):
        sec = 0.3
        t = T(sec)
        thud = (np.sin(2 * np.pi * (90 + v * 12) * t) * np.exp(-t / 0.04) + lowp(noise(sec), 300) * np.exp(-t / 0.03)) * 1.2
        squelch = bp(noise(sec), 300, 1800) * np.exp(-((t - 0.05) / 0.03) ** 2) * 0.8
        save("blood_step%d" % v, thud + squelch, vol=0.55)


def blood_death():
    sec = 1.8
    t = T(sec)
    x = growl(1.4, 90, 1.5)
    x = np.concatenate([x, np.zeros(len(t))])[: len(t)]
    x += bp(noise(sec), 200, 1500) * np.exp(-((t - 1.2) / 0.1) ** 2) * 1.5
    save("blood_death", reverb(x, 0.3, 1.0))


EVENTS = {
    "entity.mist_entity.screech": (["mist_screech0", "mist_screech1", "mist_screech2"], 32),
    "entity.mist_entity.glitch": (["mist_glitch0", "mist_glitch1", "mist_glitch2"], 20),
    "entity.mist_entity.whisper": (["mist_whisper0", "mist_whisper1"], 16),
    "entity.mist_entity.sting": (["mist_sting"], 24),
    "entity.mist_entity.hurt": (["mist_hurt0", "mist_hurt1"], 16),
    "entity.mist_entity.death": (["mist_death"], 32),
    "entity.blood_entity.growl": (["blood_growl0", "blood_growl1"], 16),
    "entity.blood_entity.attack": (["blood_attack0", "blood_attack1", "blood_attack2"], 20),
    "entity.blood_entity.hiss": (["blood_hiss"], 16),
    "entity.blood_entity.hurt": (["blood_hurt0", "blood_hurt1"], 16),
    "entity.blood_entity.death": (["blood_death"], 24),
    "entity.blood_entity.step": (["blood_step0", "blood_step1", "blood_step2"], 12),
}


def write_json():
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, d) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:entity/" + n, "attenuation_distance": d} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (mist_screech, mist_glitch, mist_whisper, mist_sting, mist_hurt, mist_death,
               blood_growl, blood_attack, blood_hiss, blood_hurt, blood_step, blood_death):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
