"""
Голоса жителей поселения — синтез (numpy + scipy, ffmpeg), по образцу iron_sounds.py / nabat_sounds.py.
Запуск: python tools/settler_sounds.py
  → assets/basedefense/sounds/settler/*.ogg и записи в sounds.json

«Голос» собирается из слогов: источник — гармоники с живой дрожью высоты (как связки),
он проходит через форманты гласной (а, о, у, э, и), между слогами — шипящие и смычки (х, с, м, д).
Получается добродушное бормотание без настоящих слов (как у жителей в играх).
Мужские голоса ниже (≈110–130 Гц), женские выше (≈200–240 Гц) и с более высокими формантами.

События (у каждого пола своё):
  entity.settler.<пол>.ambient — бормочет/напевает, когда ходит рядом;
  entity.settler.<пол>.greet   — отзывается, когда по нему кликнули («М-м? Да?»);
  entity.settler.<пол>.yes     — довольное «ага!» (взял еду);
  entity.settler.<пол>.hurt    — короткое «ох!»;
  entity.settler.<пол>.death   — протяжный стон.
"""
import json
import os
import sys

import numpy as np
from scipy.signal import lfilter

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, lowp, noise, place, reverb  # noqa: E402

rng = np.random.default_rng(7310)
EVENTS = {}

# Форманты гласных (Гц) для мужского голоса; у женского — ×1.17
VOWELS = {
    "a": (730, 1090, 2440),
    "o": (500, 850, 2400),
    "u": (330, 800, 2250),
    "e": (530, 1840, 2480),
    "i": (300, 2200, 2950),
    "y": (420, 1500, 2500),   # «ы/ə» — нейтральная
    "m": (280, 1000, 2200),   # носовой гул (закрытый рот)
}


def folder(name):
    ns.OUT = os.path.join(ns.BASE, "sounds", name)


def save(name, x, vol=0.85):
    ns.save(name, x, vol)


def ev(event, files, dist=12):
    EVENTS[event] = ("settler", files, dist)


def resonator(x, f, bw):
    """Двухполюсный резонатор — одна форманта."""
    r = np.exp(-np.pi * bw / SR)
    c = 2 * r * np.cos(2 * np.pi * f / SR)
    return lfilter([1 - r], [1, -c, r * r], x)


def glottal(f0_curve):
    """Источник голоса: сумма гармоник с убыванием (как импульсы связок)."""
    ph = np.cumsum(f0_curve) / SR
    y = np.zeros_like(ph)
    for k in range(1, 28):
        amp = 1.0 / k ** 1.15
        mask = (k * f0_curve) < 5500
        y += amp * np.sin(2 * np.pi * k * ph) * mask
    return y


def syllables(spec, f0, female, breath=0.12, speed=1.0):
    """
    spec — список слогов (гласная, длительность с, сдвиг высоты в полутонах в начале, в конце, громкость, согласная перед).
    Согласная: '' нет, 'h' выдох, 's' свист, 'm' мычание, 'd' смычка, 'sh' шипение.
    """
    fscale = 1.17 if female else 1.0
    parts = []
    for vow, dur, st0, st1, gain, cons in spec:
        dur /= speed
        if cons:
            parts.append(consonant(cons, female) * gain)
        n = int(dur * SR)
        t = np.arange(n) / SR
        semis = st0 + (st1 - st0) * (t / max(dur, 1e-3))
        vib = 0.18 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.15, 0, 1)        # лёгкое вибрато
        jit = 0.25 * lowp(rng.standard_normal(n), 18) / 0.3                     # живая дрожь
        f = f0 * 2 ** ((semis + vib + jit) / 12.0)
        src = glottal(f)
        src += rng.standard_normal(n) * breath                                  # придыхание
        fr = VOWELS[vow]
        out = (resonator(src, fr[0] * fscale, 90) * 1.0
               + resonator(src, fr[1] * fscale, 120) * 0.55
               + resonator(src, fr[2] * fscale, 170) * 0.28)
        a = min(0.04, dur * 0.3)
        env = np.minimum(1, t / a) * np.minimum(1, (dur - t) / min(0.08, dur * 0.4))
        env = np.clip(env, 0, 1) ** 1.2
        parts.append(out * env * gain)
        parts.append(np.zeros(int(0.015 * SR)))
    x = np.concatenate(parts)
    return x / (np.max(np.abs(x)) or 1)


def consonant(kind, female):
    shift = 1.15 if female else 1.0
    if kind == "h":
        sec = 0.07
        t = T(sec)
        return bp(noise(sec), 500 * shift, 2600 * shift) * np.sin(np.pi * t / sec) * 0.35
    if kind == "s":
        sec = 0.08
        t = T(sec)
        return bp(noise(sec), 4200, 9000) * np.sin(np.pi * t / sec) * 0.3
    if kind == "sh":
        sec = 0.09
        t = T(sec)
        return bp(noise(sec), 1800, 5200) * np.sin(np.pi * t / sec) * 0.32
    if kind == "d":
        sec = 0.035
        t = T(sec)
        return bp(noise(sec), 300, 3500) * np.exp(-t / 0.006) * 0.6
    if kind == "m":
        sec = 0.09
        t = T(sec)
        f0 = 200 if female else 115
        return resonator(glottal(np.full(len(t), f0)), 280 * shift, 80) * np.sin(np.pi * t / sec) * 0.5
    return np.zeros(1)


def room(x, wet=0.12):
    """Немного «воздуха» вокруг голоса."""
    return reverb(x, wet, 0.35)


# =====================================================================
def voice(gender):
    female = gender == "female"
    folder("settler")
    rnd = np.random.default_rng(11 if female else 5)
    base = 210.0 if female else 118.0

    def f0(spread=0.06):
        return base * (1 + rnd.uniform(-spread, spread))

    # --- Бормотание/напевание (ambient): 5 вариантов ---
    amb = [
        [("m", 0.32, 0, 2, 0.8, ""), ("m", 0.36, 2, -1, 0.7, "")],                         # «М-м-м…» (напевает)
        [("a", 0.12, 0, 1, 0.8, "h"), ("o", 0.16, 2, 0, 0.9, "m"), ("y", 0.14, 0, -2, 0.7, "d")],   # «ха-мо-ды»
        [("e", 0.11, 1, 2, 0.8, "s"), ("a", 0.13, 3, 1, 0.9, "d"), ("u", 0.2, 1, -1, 0.7, "m")],    # «се-да-му»
        [("o", 0.15, 0, 3, 0.8, "h"), ("i", 0.12, 3, 4, 0.7, "sh"), ("a", 0.22, 2, -2, 0.8, "m")],  # «хо-ши-ма»
        [("m", 0.18, 0, 4, 0.7, ""), ("m", 0.18, 4, 2, 0.7, ""), ("m", 0.28, 2, 5, 0.7, "")],      # напевает мелодию
    ]
    names = []
    for i, spec in enumerate(amb):
        x = syllables(spec, f0(), female, breath=0.1)
        save("%s_ambient%d" % (gender, i), room(x), vol=0.55)
        names.append("%s_ambient%d" % (gender, i))
    ev("entity.settler.%s.ambient" % gender, names, 12)

    # --- Отзыв на клик (greet): «М-м?», «Да?», «Что такое?» с подъёмом интонации ---
    greet = [
        [("m", 0.12, 0, 0, 0.6, ""), ("a", 0.22, 0, 5, 0.9, "")],                         # «м-А?»
        [("a", 0.2, 0, 4, 1.0, "d")],                                                     # «Да?»
        [("o", 0.1, 0, 1, 0.8, "sh"), ("o", 0.1, 1, 2, 0.8, "d"), ("a", 0.2, 2, 6, 0.9, "")],  # «шо-до-а?»
        [("e", 0.12, 0, 2, 0.9, "h"), ("i", 0.18, 3, 6, 0.85, "")],                       # «Хе-и?»
    ]
    names = []
    for i, spec in enumerate(greet):
        x = syllables(spec, f0(0.04), female, breath=0.08, speed=1.05)
        save("%s_greet%d" % (gender, i), room(x), vol=0.7)
        names.append("%s_greet%d" % (gender, i))
    ev("entity.settler.%s.greet" % gender, names, 10)

    # --- Довольное «ага!» (yes) ---
    yes = [
        [("a", 0.09, 2, 3, 0.8, ""), ("a", 0.18, 5, 2, 1.0, "h")],     # «а-ГА»
        [("u", 0.1, 0, 2, 0.8, "m"), ("o", 0.2, 4, 1, 1.0, "h")],      # «му-ХО» (довольно)
    ]
    names = []
    for i, spec in enumerate(yes):
        x = syllables(spec, f0(0.03), female, breath=0.1)
        save("%s_yes%d" % (gender, i), room(x), vol=0.7)
        names.append("%s_yes%d" % (gender, i))
    ev("entity.settler.%s.yes" % gender, names, 10)

    # --- Урон: короткое «ох!»/«ай!» с резким спадом и выдохом ---
    hurt = [
        [("o", 0.16, 6, -3, 1.0, "h")],
        [("a", 0.14, 7, -2, 1.0, "")],
        [("y", 0.15, 5, -4, 1.0, "h")],
    ]
    names = []
    for i, spec in enumerate(hurt):
        x = syllables(spec, f0(0.03) * 1.12, female, breath=0.25)
        save("%s_hurt%d" % (gender, i), room(x, 0.08), vol=0.8)
        names.append("%s_hurt%d" % (gender, i))
    ev("entity.settler.%s.hurt" % gender, names, 14)

    # --- Смерть: длинный стон, голос срывается в выдох ---
    x = syllables([("a", 0.5, 3, -6, 1.0, "h"), ("o", 0.45, -5, -10, 0.6, "")], f0(0.02), female, breath=0.3)
    tail = consonant("h", female)
    x = np.concatenate([x, tail * 0.6])
    save("%s_death" % gender, room(x, 0.2), vol=0.75)
    ev("entity.settler.%s.death" % gender, ["%s_death" % gender], 16)


def write_json():
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for e, (fold, files, dist) in EVENTS.items():
        data[e] = {"subtitle": "subtitles.basedefense." + e.split(".", 1)[1],
                   "sounds": [{"name": "basedefense:%s/%s" % (fold, n), "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    voice("male")
    voice("female")
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов,", len(EVENTS), "событий")
