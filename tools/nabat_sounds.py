"""
Звуки «Набата» и мечника «Отзвук» — синтез (numpy + scipy, ffmpeg с libvorbis).
Запуск: python tools/nabat_sounds.py      → assets/basedefense/sounds/nabat/*.ogg (моно) и записи в sounds.json

Колокол звучит как колокол, а не как «писк», из‑за НЕровных обертонов. Здесь — модальный синтез с обертонами
настоящего литого колокола (относительно основного тона «прайм»): хум 0.5, прайм 1, терция 1.2,
квинта 1.5, номинал 2, десима 2.5, дуодецима 3, верхняя октава 4 … Каждый обертон затухает по‑своему
(высокие — быстрее, хум — дольше всех) и раздвоен на два близких тона — от этого «живое» биение.
Удар — короткий шумовой «клац» по высоким частотам. Сверху — лёгкая реверберация (пространство).
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy.signal import butter, lfilter, sosfilt

SR = 44100
BASE = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
OUT = os.path.join(BASE, "sounds", "nabat")
rng = np.random.default_rng(1972)

# обертоны колокола: (отношение к прайму, громкость, время затухания (с) при прайме 200 Гц)
BELL = [(0.5, 0.55, 9.0), (1.0, 0.80, 6.0), (1.19, 0.55, 4.5), (1.5, 0.35, 3.6), (2.0, 0.65, 3.2), (2.51, 0.30, 2.2),
        (2.66, 0.22, 2.0), (3.01, 0.28, 1.6), (4.0, 0.16, 1.1), (5.03, 0.09, 0.8), (6.2, 0.06, 0.55)]


def T(sec):
    return np.arange(int(sec * SR)) / SR


def env_adr(t, a, d):
    return np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / d)


def bp(x, lo, hi, order=2):
    sos = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band", output="sos")
    return sosfilt(sos, x)


def lowp(x, f, order=2):
    sos = butter(order, f / (SR / 2), btype="low", output="sos")
    return sosfilt(sos, x)


def highp(x, f, order=2):
    sos = butter(order, f / (SR / 2), btype="high", output="sos")
    return sosfilt(sos, x)


def bell(prime, sec, decay_k=1.0, bright=1.0, detune=0.0, strike=1.0, wobble=1.0):
    """Модальный колокол. decay_k — длина звона, bright — доля высоких обертонов, detune — «расстроенность»."""
    t = T(sec)
    out = np.zeros_like(t)
    for i, (r, amp, dec) in enumerate(BELL):
        f = prime * r * (1 + detune * (rng.random() - 0.5) * 0.04 * (i + 1))
        if f > 15000:
            continue
        d = dec * decay_k * (200.0 / prime) ** 0.35
        a = amp * (bright ** max(0, i - 3))
        split = 1.0 + 0.0018 * wobble * (1 + i * 0.3)                 # дублет → биение
        ph = rng.random() * 6.28
        out += a * (np.sin(2 * np.pi * f * t + ph) + 0.6 * np.sin(2 * np.pi * f * split * t + ph * 1.3)) * np.exp(-t / d)
    # удар языка: короткий яркий шум
    n = rng.standard_normal(len(t))
    clack = bp(n, min(1500, prime * 5), min(16000, prime * 40)) * np.exp(-t / 0.012) * 1.8 * strike
    thump = np.sin(2 * np.pi * prime * 0.25 * t) * np.exp(-t / 0.05) * 0.6 * strike
    return (out + clack + thump) * np.minimum(1.0, t / 0.002)


def reverb(x, wet=0.35, size=1.0):
    """Простая реверберация Шрёдера: 4 гребёнки + 2 всепропускающих."""
    out = np.zeros(len(x) + int(SR * 1.5 * size))
    xp = np.concatenate([x, np.zeros(len(out) - len(x))])
    for dl, g in ((0.0297, 0.84), (0.0371, 0.82), (0.0411, 0.80), (0.0437, 0.78)):
        d = int(dl * SR * size)
        a = np.zeros(d + 1)
        a[0] = 1.0
        a[d] = -g
        out += lfilter([1.0], a, xp)
    out /= 4
    for dl, g in ((0.005, 0.7), (0.0017, 0.7)):
        d = int(dl * SR * size)
        b = np.zeros(d + 1)
        b[0] = -g
        b[d] = 1.0
        a = np.zeros(d + 1)
        a[0] = 1.0
        a[d] = -g
        out = lfilter(b, a, out)
    out = lowp(out, 6000)
    return xp * (1 - wet) + out * wet


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def place(total, part, at):
    i = int(at * SR)
    j = min(len(total), i + len(part))
    total[i:j] += part[: j - i]
    return total


def choir(sec, root, chord=(1.0, 1.26, 1.5, 2.0), vowel=(700, 1150, 2600)):
    """Хоровой гул «а‑а»: пилы с вибрато через форманты."""
    t = T(sec)
    x = np.zeros_like(t)
    for k, r in enumerate(chord):
        for v in range(3):
            f = root * r * (1 + (v - 1) * 0.004)
            vib = 1 + 0.004 * np.sin(2 * np.pi * (5.0 + v * 0.3) * t + k)
            ph = 2 * np.pi * np.cumsum(f * vib) / SR
            x += ((ph / (2 * np.pi)) % 1.0 - 0.5)
    y = sum(bp(x, f * 0.8, f * 1.25) * g for f, g in zip(vowel, (1.0, 0.6, 0.25)))
    return y


def rattle(sec, rate, f_lo, f_hi, decay=0.02, start=0.0, fall=1.0):
    """Россыпь мелких металлических щелчков (перья‑трубы, цепь, латы)."""
    t = T(sec)
    x = np.zeros_like(t)
    n = int(rate * sec)
    for _ in range(n):
        at = start + rng.random() ** fall * (sec - start - 0.05)
        f = f_lo * (f_hi / f_lo) ** rng.random()
        L = int(SR * decay * 6)
        tt = np.arange(L) / SR
        click = np.sin(2 * np.pi * f * tt) * np.exp(-tt / decay) + 0.5 * np.sin(2 * np.pi * f * 2.76 * tt) * np.exp(-tt / (decay * 0.6))
        place(x, click * (0.4 + 0.6 * rng.random()), at)
    return x


def fade(x, out=0.05):
    n = int(out * SR)
    if n > 0 and len(x) > n:
        x[-n:] *= np.linspace(1, 0, n)
    return x


SOUNDS = {}


def save(name, x, vol=0.9):
    x = fade(np.asarray(x, dtype=np.float64))
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) or 1
    x = x / peak * vol
    tmp = os.path.join(tempfile.gettempdir(), "nabat_" + name + ".wav")
    with wave.open(tmp, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    os.makedirs(OUT, exist_ok=True)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(tmp)
    SOUNDS.setdefault(name, 1)


# =====================================================================
def s_toll():
    for v in range(2):
        b = bell(108 * (1 + v * 0.03), 5.0, decay_k=1.1, bright=0.85)
        save("toll%d" % v, reverb(b, 0.35, 1.4))


def s_gather():
    t = T(5.0)
    b = bell(82, 5.0, decay_k=1.2, bright=0.8)
    c = choir(5.0, 82 * 2, chord=(1.0, 1.2, 1.5, 2.0)) * env_adr(t, 0.6, 3.0) * 0.5
    save("gather", reverb(b + c, 0.45, 1.6))


def s_chime():
    for v in range(3):
        x = np.zeros(int(1.6 * SR))
        for k in range(4):
            f = rng.choice([620, 740, 830, 990, 1110, 1240]) * (1 + v * 0.02)
            place(x, bell(f, 1.2, decay_k=0.5, bright=1.0, strike=0.6) * (0.5 + 0.5 * rng.random()), k * 0.07 + rng.random() * 0.04)
        save("chime%d" % v, reverb(x, 0.3, 0.8))


def s_summon():
    t = T(2.2)
    n = noise(2.2)
    swell = lowp(n, 900) * np.sin(np.pi * np.minimum(1, t / 2.2)) ** 1.5 * 2.0
    hiss = bp(n, 2000, 7000) * np.exp(-((t - 1.0) / 0.35) ** 2) * 0.6
    x = swell + hiss + rattle(2.2, 14, 1800, 4200, 0.012) * 0.25
    place(x, bell(520, 1.2, decay_k=0.6, bright=0.9, strike=0.5) * 0.35, 1.0)
    save("summon", reverb(x, 0.35, 1.0))


def s_wing():
    for v in range(3):
        t = T(1.3)
        n = noise(1.3)
        whoomp = lowp(n, 260 + v * 30) * np.exp(-((t - 0.25) / 0.18) ** 2) * 6.0
        air = bp(n, 500, 2500) * np.exp(-((t - 0.28) / 0.22) ** 2) * 1.2
        plates = rattle(1.3, 40, 1400, 3800, 0.01, start=0.15, fall=1.6) * 0.35
        save("wing%d" % v, reverb(whoomp + air + plates, 0.25, 0.9))


def s_crash():
    t = T(3.5)
    n = noise(3.5)
    boom = np.sin(2 * np.pi * (55 - 25 * np.minimum(1, t)) * t) * np.exp(-t / 0.5) * 1.6
    body = lowp(n, 300) * np.exp(-t / 0.35) * 4.0
    clang = bell(74, 3.5, decay_k=0.9, bright=0.9, detune=1.5, wobble=3.0) * 0.8
    debris = rattle(3.5, 60, 900, 3500, 0.015, start=0.05, fall=2.0) * 0.5
    save("crash", reverb(boom + body + clang + debris, 0.3, 1.3))


def s_slam():
    for v in range(2):
        t = T(2.2)
        n = noise(2.2)
        thud = np.sin(2 * np.pi * (48 - 18 * np.minimum(1, t * 2)) * t) * np.exp(-t / 0.35) * 1.8
        dirt = lowp(n, 500) * np.exp(-t / 0.25) * 3.0 + bp(n, 800, 3000) * np.exp(-t / 0.08) * 0.8
        clang = bell(96 * (1 + v * 0.05), 2.2, decay_k=0.6, bright=0.8) * 0.5
        save("slam%d" % v, reverb(thud + dirt + clang, 0.25, 1.1))


def s_quake():
    t = T(4.5)
    n = noise(4.5)
    rumble = lowp(n, 90, 3) * 10 * np.minimum(1, t / 0.8) * np.exp(-np.maximum(0, t - 2.5) / 1.0)
    grind = bp(n, 150, 600) * (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t)) * np.minimum(1, t / 1.2) * np.exp(-t / 2.0) * 1.2
    drone = np.sin(2 * np.pi * 41 * t) * np.minimum(1, t / 1.0) * np.exp(-t / 2.5) * 0.6
    save("quake", reverb(rumble + grind + drone, 0.2, 1.2))


def s_requiem():
    t = T(4.0)
    x = choir(4.0, 110, chord=(1.0, 1.19, 1.5, 2.0), vowel=(600, 1000, 2400)) * env_adr(t, 0.8, 4.0) * 0.6
    for k, at in enumerate((0.9, 1.7, 2.5)):
        place(x, bell(220 * (1.0, 1.19, 1.5)[k], 1.8, decay_k=0.8, bright=0.9) * 0.7, at)
    save("requiem", reverb(x, 0.45, 1.5))


def s_requiem_break():
    t = T(2.0)
    n = noise(2.0)
    crackle = bp(n, 1500, 9000) * np.exp(-t / 0.06) * 2.5
    b = bell(150, 2.0, decay_k=0.5, bright=1.0, detune=4.0, wobble=8.0)
    bend = np.interp(np.arange(len(b)) * (1 - 0.15 * np.minimum(1, t / 1.5)), np.arange(len(b)), b)
    save("requiem_break", reverb(bend * 0.8 + crackle, 0.35, 1.0))


def s_heal():
    x = np.zeros(int(2.6 * SR))
    for k, f in enumerate((660, 830, 990, 1320, 1660)):
        place(x, bell(f, 1.6, decay_k=0.5, bright=1.0, strike=0.3) * 0.5, k * 0.11)
    t = T(2.6)
    x += choir(2.6, 220, chord=(1.0, 1.25, 1.5)) * env_adr(t, 0.5, 1.5) * 0.25
    save("heal", reverb(x, 0.45, 1.2))


def s_dome_hit():
    for v in range(3):
        t = T(0.9)
        x = np.zeros_like(t)
        for f in (1900, 2510, 3170, 4400):
            f *= 1 + v * 0.04
            x += np.sin(2 * np.pi * f * t) * np.exp(-t / 0.25) * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t))
        x += bp(noise(0.9), 3000, 9000) * np.exp(-t / 0.02)
        save("dome_hit%d" % v, reverb(x, 0.4, 0.7))


def s_mark():
    x = bell(880, 0.6, decay_k=0.25, bright=1.0, strike=0.8)
    save("mark", reverb(x, 0.3, 0.7))


def s_bell_land():
    for v in range(2):
        t = T(2.0)
        thud = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.12) * 1.0 + lowp(noise(2.0), 400) * np.exp(-t / 0.1) * 2.0
        b = bell(240 * (1 + v * 0.06), 2.0, decay_k=0.7, bright=0.9)
        save("bell_land%d" % v, reverb(thud + b * 0.8, 0.3, 1.0))


def s_bell_hum():
    t = T(2.0)
    x = (np.sin(2 * np.pi * 120 * t) + 0.5 * np.sin(2 * np.pi * 121.3 * t) + 0.3 * np.sin(2 * np.pi * 240.4 * t))
    x *= np.sin(np.pi * t / 2.0) ** 2
    save("bell_hum", reverb(x, 0.3, 0.9), vol=0.6)


def s_bell_break():
    t = T(2.2)
    x = bell(260, 2.2, decay_k=0.4, bright=1.0, detune=6.0, wobble=10.0) * 0.6
    x += rattle(2.2, 90, 1500, 7000, 0.008, start=0.02, fall=2.5) * 0.6
    x += bp(noise(2.2), 1200, 10000) * np.exp(-t / 0.05) * 1.5
    save("bell_break", reverb(x, 0.3, 0.9))


def s_captive():
    t = T(3.5)
    thud = np.sin(2 * np.pi * (60 - 20 * np.minimum(1, t * 2)) * t) * np.exp(-t / 0.4) * 1.6
    b = bell(130, 3.5, decay_k=1.0, bright=0.85) * 0.9
    save("captive", reverb(thud + b, 0.4, 1.4))


def s_soul():
    for v in range(2):
        t = T(1.2)
        n = noise(1.2)
        whisper = bp(n, 900 + v * 300, 3200) * (0.5 + 0.5 * np.sin(2 * np.pi * (2.5 + v) * t)) * np.sin(np.pi * t / 1.2)
        tone = np.sin(2 * np.pi * (440 + 60 * np.sin(2 * np.pi * 1.5 * t)) * t) * 0.15 * np.sin(np.pi * t / 1.2)
        save("soul%d" % v, reverb(whisper + tone, 0.5, 1.0), vol=0.5)


def s_soul_dash():
    t = T(0.9)
    n = noise(0.9)
    f = 600 + 3000 * (t / 0.9) ** 1.5
    whoosh = bp(n, 500, 5000) * np.sin(np.pi * t / 0.9) ** 2 * 1.5
    shimmer = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.25 * np.sin(np.pi * t / 0.9)
    save("soul_dash", reverb(whoosh + shimmer, 0.4, 0.9), vol=0.7)


def s_soul_pop():
    t = T(0.7)
    pop = np.sin(2 * np.pi * (300 + 500 * np.exp(-t * 20)) * t) * np.exp(-t / 0.05)
    save("soul_pop", reverb(pop + bell(1320, 0.7, decay_k=0.2, bright=1.0, strike=0.3) * 0.4, 0.4, 0.8), vol=0.7)


def s_reliquary():
    t = T(2.8)
    x = rattle(2.8, 50, 1600, 4500, 0.012, start=0.1, fall=1.0) * 0.6
    for f in (1240, 1660, 2210):
        x += np.sin(2 * np.pi * f * t + rng.random()) * 0.12 * np.sin(np.pi * t / 2.8) * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t))
    save("reliquary", reverb(x, 0.45, 1.1))


def s_chain_break():
    t = T(1.8)
    snap = bp(noise(1.8), 2000, 9000) * np.exp(-t / 0.03) * 3.0
    x = snap + rattle(1.8, 45, 1500, 4000, 0.012, start=0.05, fall=2.0) * 0.6
    x += bell(700, 1.8, decay_k=0.4, bright=1.0, detune=3.0) * 0.4
    save("chain_break", reverb(x, 0.35, 0.9))


def s_crack():
    t = T(3.0)
    crack = bp(noise(3.0), 800, 9000) * np.exp(-t / 0.08) * 3.0
    b = bell(96, 3.0, decay_k=0.9, bright=0.9, detune=5.0, wobble=12.0)
    save("crack", reverb(crack + b * 0.8, 0.35, 1.3))


def s_intro():
    x = np.zeros(int(6.0 * SR))
    for k, f in enumerate((88, 78, 70)):
        place(x, bell(f, 4.0, decay_k=1.1, bright=0.75) * (0.6 + 0.2 * k), k * 1.3)
    t = T(6.0)
    x += choir(6.0, 70 * 2, chord=(1.0, 1.19, 1.5)) * env_adr(t, 1.5, 3.0) * 0.3
    save("intro", reverb(x, 0.5, 1.8))


def s_death():
    t = T(7.0)
    x = bell(62, 7.0, decay_k=1.5, bright=0.8, detune=2.0, wobble=4.0)
    place(x, bp(noise(1.0), 800, 9000) * np.exp(-T(1.0) / 0.1) * 2.0, 3.0)
    x += choir(7.0, 124, chord=(1.0, 1.19, 1.5)) * env_adr(t, 1.0, 3.5) * 0.35
    save("death", reverb(x, 0.5, 2.0))


def s_ambient():
    for v in range(2):
        t = T(3.5)
        drone = (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 55.4 * t) + 0.3 * np.sin(2 * np.pi * 110.7 * t))
        drone *= np.sin(np.pi * t / 3.5) ** 2
        place(drone, bell(440 * (1 + v * 0.12), 2.0, decay_k=0.6, bright=0.8, strike=0.2) * 0.2, 1.0)
        save("ambient%d" % v, reverb(drone, 0.5, 1.5), vol=0.55)


def s_hurt():
    for v in range(3):
        x = bell(330 * (1 + v * 0.07), 0.9, decay_k=0.25, bright=1.0, detune=1.0)
        save("hurt%d" % v, reverb(x, 0.3, 0.7), vol=0.7)


def s_swing():
    for v in range(2):
        t = T(0.9)
        n = noise(0.9)
        w = bp(n, 250 + v * 60, 1800) * np.exp(-((t - 0.35) / 0.15) ** 2) * 3.0
        ring = np.sin(2 * np.pi * 780 * t) * np.exp(-((t - 0.4) / 0.2) ** 2) * 0.12
        save("swing%d" % v, reverb(w + ring, 0.25, 0.8))


# --- мечник ---
def b_swing():
    for v in range(3):
        t = T(0.55)
        n = noise(0.55)
        w = bp(n, 700 + v * 150, 5000) * np.exp(-((t - 0.18) / 0.07) ** 2) * 2.5
        ring = np.sin(2 * np.pi * (1500 + v * 80) * t) * np.exp(-((t - 0.2) / 0.12) ** 2) * 0.2
        save("blade_swing%d" % v, reverb(w + ring, 0.2, 0.6))


def b_parry():
    for v in range(3):
        x = bell(520 * (1 + v * 0.05), 1.0, decay_k=0.35, bright=1.0, strike=1.5)
        save("blade_parry%d" % v, reverb(x, 0.3, 0.8))


def b_step():
    for v in range(3):
        t = T(0.35)
        thud = lowp(noise(0.35), 300) * np.exp(-t / 0.03) * 2.0
        clank = rattle(0.35, 12, 900, 2600, 0.01, start=0.0, fall=2.5) * 0.5
        save("blade_step%d" % v, thud + clank, vol=0.6)


def b_hurt():
    for v in range(2):
        x = bell(420 * (1 + v * 0.08), 0.6, decay_k=0.18, bright=1.0, detune=2.0, strike=1.2)
        save("blade_hurt%d" % v, reverb(x, 0.25, 0.6), vol=0.7)


def b_death():
    t = T(1.8)
    x = rattle(1.8, 60, 600, 3000, 0.02, start=0.0, fall=1.4) * 0.8
    x += lowp(noise(1.8), 300) * (np.exp(-t / 0.1) + 0.6 * np.exp(-np.abs(t - 0.7) / 0.08)) * 1.5
    x += bell(380, 1.8, decay_k=0.4, bright=0.9) * 0.3
    save("blade_death", reverb(x, 0.3, 0.9))


def b_assemble():
    t = T(1.6)
    x = rattle(1.6, 40, 700, 3000, 0.015, start=0.0, fall=0.5) * 0.6
    x += bp(noise(1.6), 300, 2500) * np.sin(np.pi * t / 1.6) ** 2 * 0.8
    place(x, bell(600, 1.0, decay_k=0.4, bright=1.0) * 0.4, 1.2)
    save("blade_assemble", reverb(x, 0.35, 0.9))


EVENTS = {  # событие → (файлы, дистанция слышимости)
    "entity.nabat.toll": (["toll0", "toll1"], 96), "entity.nabat.gather": (["gather"], 128),
    "entity.nabat.chime": (["chime0", "chime1", "chime2"], 48), "entity.nabat.summon": (["summon"], 64),
    "entity.nabat.wing": (["wing0", "wing1", "wing2"], 64), "entity.nabat.crash": (["crash"], 96),
    "entity.nabat.slam": (["slam0", "slam1"], 96), "entity.nabat.quake": (["quake"], 96),
    "entity.nabat.requiem": (["requiem"], 96), "entity.nabat.requiem_break": (["requiem_break"], 96),
    "entity.nabat.heal": (["heal"], 64), "entity.nabat.dome_hit": (["dome_hit0", "dome_hit1", "dome_hit2"], 48),
    "entity.nabat.mark": (["mark"], 32), "entity.nabat.bell_land": (["bell_land0", "bell_land1"], 64),
    "entity.nabat.bell_hum": (["bell_hum"], 24), "entity.nabat.bell_break": (["bell_break"], 64),
    "entity.nabat.captive": (["captive"], 64), "entity.nabat.soul": (["soul0", "soul1"], 16),
    "entity.nabat.soul_dash": (["soul_dash"], 32), "entity.nabat.soul_pop": (["soul_pop"], 24),
    "entity.nabat.reliquary": (["reliquary"], 64), "entity.nabat.chain_break": (["chain_break"], 64),
    "entity.nabat.crack": (["crack"], 128), "entity.nabat.intro": (["intro"], 160), "entity.nabat.death": (["death"], 160),
    "entity.nabat.ambient": (["ambient0", "ambient1"], 64), "entity.nabat.hurt": (["hurt0", "hurt1", "hurt2"], 48),
    "entity.nabat.swing": (["swing0", "swing1"], 48),
    "entity.nabat_blade.swing": (["blade_swing0", "blade_swing1", "blade_swing2"], 20),
    "entity.nabat_blade.parry": (["blade_parry0", "blade_parry1", "blade_parry2"], 24),
    "entity.nabat_blade.step": (["blade_step0", "blade_step1", "blade_step2"], 16),
    "entity.nabat_blade.hurt": (["blade_hurt0", "blade_hurt1"], 16),
    "entity.nabat_blade.death": (["blade_death"], 24), "entity.nabat_blade.assemble": (["blade_assemble"], 24),
}


def write_json():
    path = os.path.join(BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, dist) in EVENTS.items():
        data[ev] = {"subtitle": "subtitles.basedefense." + ev.split(".", 1)[1],
                    "sounds": [{"name": "basedefense:nabat/" + n, "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_toll, s_gather, s_chime, s_summon, s_wing, s_crash, s_slam, s_quake, s_requiem, s_requiem_break, s_heal,
               s_dome_hit, s_mark, s_bell_land, s_bell_hum, s_bell_break, s_captive, s_soul, s_soul_dash, s_soul_pop,
               s_reliquary, s_chain_break, s_crack, s_intro, s_death, s_ambient, s_hurt, s_swing,
               b_swing, b_parry, b_step, b_hurt, b_death, b_assemble):
        fn()
    write_json()
    print("готово:", len(SOUNDS), "файлов")
