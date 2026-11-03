"""
Звуки «Громового тигра» — синтез (numpy + scipy, ffmpeg с libvorbis), по образцу sapper_sounds.py.
Запуск: python tools/thunder_tiger_sounds.py → assets/basedefense/sounds/mob/thunder_tiger/*.ogg и записи в sounds.json

Выстрел тесака (резкий хлопок + низкий удар + рёв пламени из выхлопов + щелчок механизма), свирепый выстрел (ниже,
с рыком), звон гильз, барабан (щелчок, патрон, защёлка с трещоткой), свист клинка (лёгкий / тяжёлый), удар по плоти,
звон стали (парирование, щит), рывок, тяжёлые шаги, затяжка сигарой, вспыхнувший клинок, огненная волна, удар в землю,
слом стойки, вспышка колец, шквал, залп всеми патронами, смерть, урон, усмешка (без слов), пинок, тесак в землю,
разрыв дрожи, вращение тесаком, щелчок сигарой.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bp, highp, lowp, noise, place, reverb, rattle  # noqa: E402

ns.OUT = os.path.join(ns.BASE, "sounds", "mob", "thunder_tiger")
rng = np.random.default_rng(1977)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def env(t, a, d):
    return np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / d)


def thud(sec, f0=80, d=0.08, k=1.0):
    t = T(sec)
    body = np.sin(2 * np.pi * f0 * t * (1 - 0.35 * t)) * np.exp(-t / d)
    return (body + lowp(noise(sec), 350) * np.exp(-t / (d * 0.6)) * 0.8) * k


def whoosh(sec, f0, f1, peak=0.5, width=0.18, k=1.0):
    """Свист: полосовой шум, полоса скользит f0 → f1, пик громкости в peak."""
    t = T(sec)
    x = np.zeros_like(t)
    n = noise(sec)
    steps = 24
    for i in range(steps):
        a, b_ = i / steps, (i + 1) / steps
        f = f0 * (f1 / f0) ** ((a + b_) / 2)
        seg = bp(n, f * 0.7, f * 1.4)
        m = (t >= a * sec) & (t < b_ * sec)
        x[m] = seg[m]
    shape = np.exp(-((t - peak * sec) / (width * sec)) ** 2)
    return x * shape * k


def flame(sec, k=1.0, low=300, high=3000):
    """Рёв пламени: шум с хрипом и треском."""
    t = T(sec)
    roar = bp(noise(sec), low, high) * (1 + 0.5 * np.sin(2 * np.pi * 31 * t) * np.sin(2 * np.pi * 7 * t))
    crackle = rattle(sec, 60, 1500, 6000, 0.002) * 0.4
    return (roar + crackle) * env(t, 0.01, sec * 0.35) * k


def ping(sec, f, d=0.12, k=1.0):
    t = T(sec)
    return (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.71 * t) + 0.25 * np.sin(2 * np.pi * f * 5.1 * t)) \
        * np.exp(-t / d) * k


def click(sec, f=2500, d=0.006, k=1.0):
    t = T(sec)
    return (bp(noise(sec), f * 0.6, f * 2) * np.exp(-t / d) * 2 + np.sin(2 * np.pi * f * t) * np.exp(-t / (d * 0.7))) * k


def voice(sec, f0, glide=0.0, vowel=(500, 900, 2300), grit=0.4, breath=0.3):
    """Низкий мужской голос без слов (хмыканье, рык, стон): пила через форманты."""
    t = T(sec)
    f = f0 * (1 + glide * t / sec) * (1 + 0.02 * np.sin(2 * np.pi * 5.0 * t))
    ph = np.cumsum(f) / SR
    saw = (ph % 1.0) - 0.5
    saw += grit * lowp(noise(sec), 500) * 0.3
    y = sum(bp(saw, fr * 0.8, fr * 1.25) * g for fr, g in zip(vowel, (1.0, 0.5, 0.2)))
    y += bp(noise(sec), 500, 3000) * breath * 0.3
    return y


# ---------------------------------------------------------------- оружие
def s_shot():
    for v in range(3):
        sec = 1.3
        t = T(sec)
        crack = bp(noise(sec), 600, 9000) * np.exp(-t / 0.018) * 3.0
        boom = np.sin(2 * np.pi * (68 + v * 7) * t * (1 - 0.3 * t)) * np.exp(-t / 0.16) * 1.6
        body = lowp(noise(sec), 400) * np.exp(-t / 0.12) * 1.2
        fire = flame(sec, 0.9, 250, 2500)
        mech = np.zeros_like(t)
        place(mech, click(0.05, 3200, 0.004, 0.6), 0.0)
        place(mech, click(0.05, 2400, 0.005, 0.5), 0.09 + v * 0.01)
        x = crack + boom + body + fire + mech
        save("shot%d" % v, reverb(x, 0.22, 0.8))


def s_shot_savage():
    for v in range(2):
        sec = 1.6
        t = T(sec)
        crack = bp(noise(sec), 400, 8000) * np.exp(-t / 0.022) * 3.0
        boom = np.sin(2 * np.pi * (48 + v * 5) * t * (1 - 0.25 * t)) * np.exp(-t / 0.25) * 2.0
        growl = voice(sec, 70, -0.2, (400, 800, 2000), grit=1.2, breath=0.6) * env(t, 0.02, 0.35) * 0.6
        fire = flame(sec, 1.3, 200, 2200)
        x = crack + boom + fire + growl + lowp(noise(sec), 250) * np.exp(-t / 0.3) * 1.2
        save("shot_savage%d" % v, reverb(x, 0.28, 1.0))


def s_shell():
    for v in range(3):
        sec = 1.0
        x = np.zeros(int(sec * SR))
        at = 0.02
        amp = 1.0
        f = 3100 + v * 260
        for b_ in range(5):
            place(x, ping(0.25, f * (1 + 0.03 * b_), 0.05, amp), at)
            at += 0.12 * (0.7 ** b_) + 0.02
            amp *= 0.55
        save("shell%d" % v, x, vol=0.55)


def s_drum():
    sec = 0.35
    x = click(sec, 2600, 0.006, 1.0) + click(sec, 1400, 0.01, 0.6)
    save("drum_open", x + lowp(noise(sec), 900) * np.exp(-T(sec) / 0.02) * 0.4, vol=0.7)
    for v in range(2):
        x = np.zeros(int(0.4 * SR))
        place(x, click(0.1, 1800 + v * 200, 0.008, 1.0), 0.0)
        place(x, ping(0.2, 2200 + v * 150, 0.03, 0.5), 0.03)
        save("drum_load%d" % v, x, vol=0.65)
    sec = 0.7
    x = np.zeros(int(sec * SR))
    place(x, click(0.1, 2000, 0.01, 1.4) + thud(0.1, 160, 0.02, 0.6), 0.0)
    for i in range(7):                                     # трещотка барабана
        place(x, click(0.04, 3000 + i * 50, 0.003, 0.5 * (0.85 ** i)), 0.06 + i * 0.045)
    save("drum_close", x, vol=0.75)


def s_swing():
    for v in range(3):
        sec = 0.45
        x = whoosh(sec, 500 + v * 80, 2400 + v * 300, 0.45, 0.16, 1.0) + whoosh(sec, 2000, 6000, 0.5, 0.1, 0.35)
        save("swing%d" % v, x, vol=0.7)
    for v in range(2):
        sec = 0.7
        x = whoosh(sec, 180 + v * 40, 1300, 0.5, 0.2, 1.2) + whoosh(sec, 900, 3200, 0.55, 0.12, 0.4)
        x += lowp(noise(sec), 200) * np.exp(-((T(sec) - 0.35) / 0.12) ** 2) * 0.6
        save("swing_heavy%d" % v, x, vol=0.8)


def s_hit():
    for v in range(3):
        sec = 0.5
        t = T(sec)
        x = thud(sec, 95 + v * 10, 0.05, 1.3) + bp(noise(sec), 800, 4000) * np.exp(-t / 0.03) * 1.2
        x += ping(sec, 1700 + v * 90, 0.04, 0.25)
        save("hit%d" % v, x, vol=0.8)


def s_clang():
    for v in range(2):
        sec = 1.0
        t = T(sec)
        x = ping(sec, 1250 + v * 170, 0.22, 1.0) + ping(sec, 1870 + v * 90, 0.16, 0.7) + ping(sec, 3410, 0.08, 0.5)
        x += bp(noise(sec), 2000, 9000) * np.exp(-t / 0.01) * 1.5
        save("clang%d" % v, reverb(x, 0.2, 0.6), vol=0.8)


def s_dash():
    for v in range(2):
        sec = 0.6
        x = whoosh(sec, 300 + v * 60, 1800, 0.35, 0.22, 1.0) + thud(sec, 70, 0.05, 0.5)
        save("dash%d" % v, x, vol=0.7)


def s_step():
    for v in range(3):
        sec = 0.35
        t = T(sec)
        x = thud(sec, 65 + v * 6, 0.05, 1.0) + bp(noise(sec), 300, 2500) * np.exp(-t / 0.02) * 0.6
        place(x, click(0.05, 3500, 0.003, 0.15), 0.01)          # звякнули пряжки
        save("step%d" % v, x, vol=0.6)


def s_puff():
    sec = 2.2
    t = T(sec)
    inhale = bp(noise(sec), 1500, 6000) * np.exp(-((t - 0.3) / 0.18) ** 2) * 0.6
    crackle = rattle(sec, 40, 2500, 7000, 0.002, start=0.15, fall=1.0) * np.exp(-((t - 0.35) / 0.2) ** 2) * 0.6
    exhale = bp(noise(sec), 300, 2000) * np.exp(-((t - 1.2) / 0.35) ** 2) * 0.9
    hum = voice(sec, 95, -0.1, (450, 850, 2200), grit=0.3, breath=0.4) * np.exp(-((t - 1.15) / 0.25) ** 2) * 0.35
    save("puff", inhale + crackle + exhale + hum, vol=0.55)


def s_ignite():
    sec = 1.6
    t = T(sec)
    whump = thud(sec, 90, 0.1, 1.0) + bp(noise(sec), 200, 1500) * env(t, 0.02, 0.2) * 1.4
    roar = flame(sec, 1.0, 300, 3500) * np.clip(t / 0.2, 0, 1)
    save("ignite", reverb(whump + roar, 0.2, 0.8), vol=0.8)


def s_wave():
    sec = 2.6
    t = T(sec)
    boom = np.sin(2 * np.pi * 45 * t * (1 - 0.2 * t)) * np.exp(-t / 0.4) * 1.6
    roar = flame(sec, 1.4, 150, 2500) * np.exp(-t / 1.0)
    sweep = whoosh(sec, 200, 900, 0.25, 0.3, 0.8)
    rocks = rattle(sec, 90, 300, 3000, 0.008, start=0.05, fall=1.6) * 0.6
    save("wave", reverb(boom + roar + sweep + rocks, 0.3, 1.1))


def s_smash():
    for v in range(2):
        sec = 1.6
        t = T(sec)
        crack = bp(noise(sec), 200, 5000) * np.exp(-t / 0.03) * 2.2
        boom = np.sin(2 * np.pi * (52 + v * 6) * t * (1 - 0.25 * t)) * np.exp(-t / 0.25) * 1.6
        rocks = rattle(sec, 120, 300, 3200, 0.009, start=0.03, fall=1.8) * 0.8
        save("smash%d" % v, reverb(crack + boom + rocks, 0.25, 0.9))


def s_break():
    sec = 1.8
    t = T(sec)
    shatter = rattle(sec, 160, 1500, 7000, 0.006, start=0.0, fall=2.2) * 1.2
    clang = ping(sec, 980, 0.3, 0.8) + ping(sec, 1460, 0.2, 0.5)
    grunt = voice(0.7, 105, -0.35, (520, 950, 2400), grit=1.0, breath=0.7) * env(T(0.7), 0.03, 0.25)
    x = shatter + clang + thud(sec, 70, 0.12, 1.0)
    place(x, grunt * 0.9, 0.08)
    save("break", reverb(x, 0.25, 0.9))


def s_phase():
    sec = 3.0
    t = T(sec)
    rise = whoosh(sec, 150, 2500, 0.45, 0.25, 0.8)
    chime = sum(ping(sec, f, 0.9, 0.35) for f in (523, 659, 784, 1046)) * (t > 0.6)
    burst = np.zeros_like(t)
    place(burst, thud(1.5, 55, 0.3, 1.6) + flame(1.5, 1.2, 200, 3000), 0.62)
    save("phase", reverb(rise + chime + burst, 0.35, 1.2))


def s_flurry():
    for v in range(3):
        sec = 0.5
        x = whoosh(sec, 700 + v * 120, 4000, 0.4, 0.12, 1.0) + whoosh(sec, 300, 1200, 0.45, 0.15, 0.5)
        save("flurry%d" % v, x, vol=0.75)


def s_final():
    sec = 3.4
    t = T(sec)
    x = np.zeros_like(t)
    for i in range(6):
        at = 0.02 + i * 0.07
        L = 1.2
        tt = T(L)
        shot = bp(noise(L), 500, 8000) * np.exp(-tt / 0.02) * 2.0 + np.sin(2 * np.pi * (60 - i * 3) * tt) * np.exp(-tt / 0.2)
        place(x, shot * (1 - i * 0.06), at)
    x += np.sin(2 * np.pi * 38 * t * (1 - 0.15 * t)) * np.exp(-t / 0.7) * 2.0
    x += flame(sec, 1.4, 150, 2600) * np.exp(-t / 1.2)
    x += rattle(sec, 160, 300, 3200, 0.01, start=0.1, fall=1.6) * 0.8
    save("final", reverb(x, 0.35, 1.3))


def s_voice():
    # урон: короткое «хм!» сквозь зубы
    for v in range(2):
        sec = 0.5
        x = voice(sec, 110 + v * 12, -0.3, (480, 900, 2300), grit=0.8, breath=0.5) * env(T(sec), 0.02, 0.14)
        save("hurt%d" % v, x, vol=0.7)
    # усмешка: «хм‑хм» с выдохом через нос
    for v in range(2):
        sec = 1.0
        x = np.zeros(int(sec * SR))
        for i, at in enumerate((0.05, 0.28)):
            part = voice(0.22, 98 - i * 6 + v * 8, -0.15, (300, 700, 2100), grit=0.4, breath=0.9)
            place(x, part * env(T(0.22), 0.01, 0.07), at)
        x += bp(noise(sec), 500, 2500) * np.exp(-((T(sec) - 0.5) / 0.15) ** 2) * 0.3
        save("chuckle%d" % v, x, vol=0.6)
    # смерть: тяжёлый выдох, тесак падает
    sec = 3.0
    t = T(sec)
    x = voice(1.6, 92, -0.45, (450, 820, 2200), grit=0.9, breath=0.8)
    x = np.concatenate([x * env(T(1.6), 0.08, 0.6), np.zeros(len(t))])[: len(t)]
    drop = np.zeros_like(t)
    place(drop, ping(1.0, 1100, 0.25, 0.6) + ping(1.0, 1650, 0.15, 0.4) + thud(1.0, 80, 0.06, 0.8), 1.3)
    place(drop, ping(0.6, 1150, 0.12, 0.25), 1.55)
    save("death", reverb(x + drop, 0.25, 0.9), vol=0.85)


def s_misc():
    for v in range(2):
        sec = 0.5
        save("kick%d" % v, thud(sec, 75 + v * 10, 0.06, 1.4) + whoosh(sec, 300, 1200, 0.1, 0.1, 0.4), vol=0.75)
    sec = 0.9
    t = T(sec)
    x = bp(noise(sec), 600, 5000) * np.exp(-t / 0.04) * 1.4 + thud(sec, 90, 0.08, 1.0)
    x += rattle(sec, 50, 300, 2500, 0.008, start=0.02, fall=1.5) * 0.5 + ping(sec, 1300, 0.2, 0.3)
    save("plant", x, vol=0.75)
    for v in range(2):
        sec = 1.0
        t = T(sec)
        thump = np.sin(2 * np.pi * (60 + v * 8) * t * (1 - 0.3 * t)) * np.exp(-t / 0.15) * 1.6
        crackle = rattle(sec, 90, 600, 4000, 0.004, start=0.0, fall=2.0) * 0.7
        save("burst%d" % v, reverb(thump + crackle + bp(noise(sec), 200, 1500) * np.exp(-t / 0.05), 0.2, 0.7))
    sec = 1.0
    x = np.zeros(int(sec * SR))
    for i in range(5):
        place(x, whoosh(0.2, 600, 2000, 0.5, 0.25, 0.7 - i * 0.08), 0.04 + i * 0.13)
    save("spin", x, vol=0.6)
    sec = 0.5
    x = click(sec, 2000, 0.004, 0.6) + whoosh(sec, 1500, 4000, 0.15, 0.08, 0.4)
    save("flick", x, vol=0.5)


EVENTS = {
    "shot": (["shot0", "shot1", "shot2"], 48), "shot_savage": (["shot_savage0", "shot_savage1"], 56),
    "shell": (["shell0", "shell1", "shell2"], 16), "drum_open": (["drum_open"], 16),
    "drum_load": (["drum_load0", "drum_load1"], 16), "drum_close": (["drum_close"], 20),
    "swing": (["swing0", "swing1", "swing2"], 20), "swing_heavy": (["swing_heavy0", "swing_heavy1"], 24),
    "hit": (["hit0", "hit1", "hit2"], 20), "clang": (["clang0", "clang1"], 28), "dash": (["dash0", "dash1"], 20),
    "step": (["step0", "step1", "step2"], 14), "puff": (["puff"], 12), "ignite": (["ignite"], 32),
    "wave": (["wave"], 48), "smash": (["smash0", "smash1"], 40), "break": (["break"], 32),
    "phase": (["phase"], 64), "flurry": (["flurry0", "flurry1", "flurry2"], 28), "final": (["final"], 64),
    "death": (["death"], 48), "hurt": (["hurt0", "hurt1"], 16), "chuckle": (["chuckle0", "chuckle1"], 16),
    "kick": (["kick0", "kick1"], 16), "plant": (["plant"], 24), "burst": (["burst0", "burst1"], 24),
    "spin": (["spin"], 16), "flick": (["flick"], 12),
}


def write_json():
    import json
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for ev, (files, dist) in EVENTS.items():
        key = "entity.thunder_tiger." + ev
        data[key] = {"subtitle": "subtitles.basedefense.thunder_tiger." + ev,
                     "sounds": [{"name": "basedefense:mob/thunder_tiger/" + n, "attenuation_distance": dist}
                                for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for fn in (s_shot, s_shot_savage, s_shell, s_drum, s_swing, s_hit, s_clang, s_dash, s_step, s_puff, s_ignite,
               s_wave, s_smash, s_break, s_phase, s_flurry, s_final, s_voice, s_misc):
        fn()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов")
