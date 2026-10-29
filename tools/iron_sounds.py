"""
Свои звуки железного зомби, Бордюра с прислужником и звук урона железного рыцаря — синтез (numpy + scipy, ffmpeg),
по образцу nabat_sounds.py.
Запуск: python tools/iron_sounds.py
  → assets/basedefense/sounds/mob/iron_zombie/*.ogg, sounds/mob/curb/*.ogg (кроме струй — их пишет curb_sounds.py),
    sounds/iron_knight/hurt*.ogg и записи в sounds.json
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import nabat_sounds as ns  # noqa: E402
from nabat_sounds import SR, T, bell, bp, highp, lowp, noise, place, rattle, reverb  # noqa: E402

rng = np.random.default_rng(4410)
EVENTS = {}


def folder(name):
    ns.OUT = os.path.join(ns.BASE, "sounds", "mob", name)


def save(name, x, vol=0.9):
    ns.save(name, x, vol)


def ev(event, folder_name, files, dist=16):
    EVENTS[event] = (folder_name, files, dist)


def thud(sec, f0=80, d=0.06, k=1.0):
    t = T(sec)
    body = np.sin(2 * np.pi * f0 * t * (1 - 0.3 * t)) * np.exp(-t / d)
    return (body + lowp(noise(sec), 350) * np.exp(-t / (d * 0.6)) * 0.8) * k


def clank(sec, f, d=0.08, k=1.0):
    """Удар по листу железа: негармонические моды + щелчок."""
    t = T(sec)
    x = np.zeros_like(t)
    for r, a, dd in ((1.0, 1.0, 1.0), (2.32, 0.6, 0.7), (3.87, 0.35, 0.5), (5.1, 0.2, 0.35)):
        x += a * np.sin(2 * np.pi * f * r * t + rng.random() * 6) * np.exp(-t / (d * dd))
    x += bp(noise(sec), 1500, 8000) * np.exp(-t / 0.006) * 1.2
    return x * k


def growl(sec, f0, glide=0.0, vowel=(450, 850, 2200), grit=0.8, breath=0.5):
    """Хриплый голос мертвеца: пила с дрожью через форманты + шумовой хрип."""
    t = T(sec)
    jitter = 1 + 0.06 * lowp(noise(sec), 12) / 0.3
    f = f0 * (1 + glide * t / sec) * jitter
    ph = np.cumsum(f) / SR
    saw = (ph % 1.0) - 0.5
    saw *= 1 + grit * lowp(noise(sec), 80)                                  # «рваный» хрип
    y = sum(bp(saw, fr * 0.8, fr * 1.25) * g for fr, g in zip(vowel, (1.0, 0.55, 0.25)))
    y += bp(noise(sec), 400, 2600) * breath * 0.35
    return y * np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.5


def servo(sec, f0, f1, k=1.0):
    """Сервопривод: жужжание с подъёмом частоты, зубчатый призвук."""
    t = T(sec)
    f = f0 + (f1 - f0) * np.sin(np.pi * 0.5 * np.clip(t / (sec * 0.7), 0, 1))
    ph = np.cumsum(f) / SR
    tone = np.sign(np.sin(2 * np.pi * ph)) * 0.3 + np.sin(2 * np.pi * 2 * ph) * 0.4
    gear = bp(noise(sec), 2000, 5000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * ph / 6)))
    env = np.minimum(1, t / 0.04) * np.minimum(1, (sec - t) / 0.08)
    return lowp(tone + gear * 0.15, 4500) * env * k


# =====================================================================  железный зомби
def zombie():
    folder("iron_zombie")
    for v in range(3):                                 # шаг: тяжёлая ступня + звяк обрывков брони
        sec = 0.35
        x = thud(sec, 70 + v * 9, 0.05, 1.3) + rattle(sec, 10, 900, 3000, 0.008, fall=2.5) * 0.45
        save("step%d" % v, x, vol=0.55)
    for v in range(3):                                 # хрип (эмбиент)
        sec = 1.3 + v * 0.2
        x = growl(sec, 78 + v * 9, -0.25, (420 + v * 40, 800, 2100))
        t = T(sec)
        x += bp(noise(sec), 1500, 4000) * np.sin(np.pi * t / sec) ** 2 * 0.06 * (1 + np.sin(2 * np.pi * 37 * t))
        save("ambient%d" % v, reverb(x, 0.2, 0.7), vol=0.6)
    for v in range(2):                                 # скрежет железа (при рывке и замахе)
        sec = 0.9
        t = T(sec)
        x = bp(noise(sec), 900, 4200) * np.sin(np.pi * t / sec) ** 1.5 * (1 + 0.6 * np.sin(2 * np.pi * (23 + v * 7) * t))
        x += np.sin(2 * np.pi * (1650 + v * 120) * t * (1 + 0.05 * t)) * np.sin(np.pi * t / sec) ** 2 * 0.25
        save("scrape%d" % v, x, vol=0.55)
    for v in range(3):                                 # удар: железный кулак по телу
        sec = 0.5
        x = thud(sec, 95 + v * 10, 0.05, 1.4) + clank(sec, 380 + v * 45, 0.07, 0.5)
        save("attack%d" % v, reverb(x, 0.15, 0.5), vol=0.8)
    for v in range(3):                                 # урон: звяк брони + короткий стон
        sec = 0.5
        x = clank(sec, 420 + v * 60, 0.06, 0.7) + growl(sec, 110 + v * 15, 0.3, (550, 1000, 2400), breath=0.7) * 0.9
        save("hurt%d" % v, reverb(x, 0.2, 0.5), vol=0.7)
    sec = 2.0                                          # смерть: протяжный хрип, падение, звон доспехов
    x = np.zeros(int(sec * SR))
    place(x, growl(1.1, 95, -0.45, (420, 800, 2000), grit=1.0), 0.0)
    place(x, thud(0.8, 55, 0.12, 1.6), 1.0)
    place(x, rattle(0.9, 40, 600, 3200, 0.01, fall=2.2) * 0.8, 1.02)
    save("death", reverb(x, 0.3, 0.9), vol=0.8)
    sec = 0.8                                          # хват: лязг железных пальцев
    x = clank(sec, 520, 0.05, 0.9) + thud(sec, 85, 0.05, 1.0) + rattle(sec, 25, 1000, 3500, 0.006, fall=2) * 0.5
    save("grab", reverb(x, 0.2, 0.6), vol=0.75)
    sec = 0.7                                          # рывок: рёв и шорох
    t = T(sec)
    x = growl(sec, 120, 0.4, (600, 1100, 2500), grit=1.2, breath=0.9) + \
        bp(noise(sec), 300, 2500) * np.exp(-((t - 0.25) / 0.15) ** 2) * 0.8
    save("lunge", reverb(x, 0.2, 0.6), vol=0.75)
    ev("entity.iron_zombie.step", "iron_zombie", ["step0", "step1", "step2"], 12)
    ev("entity.iron_zombie.ambient", "iron_zombie", ["ambient0", "ambient1", "ambient2"], 16)
    ev("entity.iron_zombie.scrape", "iron_zombie", ["scrape0", "scrape1"], 16)
    ev("entity.iron_zombie.attack", "iron_zombie", ["attack0", "attack1", "attack2"], 16)
    ev("entity.iron_zombie.hurt", "iron_zombie", ["hurt0", "hurt1", "hurt2"], 16)
    ev("entity.iron_zombie.death", "iron_zombie", ["death"], 20)
    ev("entity.iron_zombie.grab", "iron_zombie", ["grab"], 16)
    ev("entity.iron_zombie.lunge", "iron_zombie", ["lunge"], 18)


# =====================================================================  железный рыцарь — только урон
def knight():
    folder("iron_knight")
    for v in range(3):
        # удар по латам: звонкий лязг (короткий «колокол» + лист) и глухой стук пустого доспеха изнутри
        sec = 0.8
        b = bell(560 * (1 + v * 0.06), sec, decay_k=0.12, bright=1.1, detune=1.5, strike=1.2)
        x = b * 0.55 + clank(sec, 300 + v * 35, 0.09, 0.8) + thud(sec, 70 + v * 6, 0.07, 1.0)
        x += rattle(sec, 18, 1200, 4500, 0.006, start=0.02, fall=2.5) * 0.35    # дребезг пластин
        save("hurt%d" % v, reverb(x, 0.25, 0.7), vol=0.75)
    ev("entity.iron_knight.hurt", "iron_knight", ["hurt0", "hurt1", "hurt2"], 16)


# =====================================================================  Бордюр и прислужник
def curb():
    folder("curb")
    for v in range(3):                                 # шаг Бордюра: каменно‑металлический бух
        sec = 0.45
        x = thud(sec, 50 + v * 6, 0.09, 1.6) + clank(sec, 210 + v * 25, 0.05, 0.35)
        x += rattle(sec, 8, 300, 1500, 0.01, fall=2) * 0.3
        save("step%d" % v, x, vol=0.6)
    for v in range(3):                                 # шаг прислужника: лёгкий звонкий топ
        sec = 0.25
        x = thud(sec, 120 + v * 15, 0.03, 1.0) + clank(sec, 700 + v * 70, 0.03, 0.35)
        save("minion_step%d" % v, x, vol=0.5)
    for v in range(3):                                 # удар кулаком: тяжёлый стук + лязг
        sec = 0.6
        t = T(sec)
        x = thud(sec, 75 + v * 8, 0.07, 1.6) + clank(sec, 260 + v * 30, 0.08, 0.6)
        x += bp(noise(sec), 300, 3000) * np.exp(-((t - 0.0) / 0.03) ** 2) * 0.5
        save("punch%d" % v, reverb(x, 0.2, 0.6), vol=0.85)
    for v in range(3):                                 # сервоприводы (эмбиент): жужжание и щелчок фиксатора
        sec = 0.9 + v * 0.15
        x = servo(sec, 140 + v * 30, 260 + v * 50)
        place(x, clank(0.2, 900 + v * 80, 0.02, 0.4), sec - 0.18)
        save("servo%d" % v, x, vol=0.45)
    for v in range(3):                                 # урон Бордюра: глухой лязг брони
        sec = 0.6
        x = clank(sec, 240 + v * 30, 0.1, 1.0) + thud(sec, 65, 0.06, 0.8)
        save("hurt%d" % v, reverb(x, 0.25, 0.7), vol=0.75)
    for v in range(2):                                 # урон прислужника: звонче
        sec = 0.4
        x = clank(sec, 620 + v * 80, 0.05, 1.0) + thud(sec, 110, 0.03, 0.6)
        save("minion_hurt%d" % v, reverb(x, 0.2, 0.5), vol=0.65)
    sec = 2.4                                          # смерть Бордюра: сервопривод затихает, обвал
    x = np.zeros(int(sec * SR))
    t = T(1.0)
    whine = servo(1.0, 420, 60) * np.exp(-t / 0.6)
    place(x, whine, 0.0)
    place(x, thud(1.0, 42, 0.18, 2.0), 0.85)
    place(x, rattle(1.3, 70, 250, 2500, 0.012, fall=2.2), 0.88)
    place(x, clank(0.8, 190, 0.15, 0.8), 0.9)
    save("death", reverb(x, 0.35, 1.0), vol=0.85)
    sec = 1.2                                          # смерть прислужника
    x = np.zeros(int(sec * SR))
    place(x, servo(0.5, 600, 120) * 0.8, 0.0)
    place(x, clank(0.7, 540, 0.08, 1.0) + rattle(0.7, 30, 600, 3000, 0.008, fall=2), 0.4)
    save("minion_death", reverb(x, 0.25, 0.7), vol=0.75)
    ev("entity.curb.step", "curb", ["step0", "step1", "step2"], 16)
    ev("entity.curb.punch", "curb", ["punch0", "punch1", "punch2"], 18)
    ev("entity.curb.servo", "curb", ["servo0", "servo1", "servo2"], 14)
    ev("entity.curb.hurt", "curb", ["hurt0", "hurt1", "hurt2"], 16)
    ev("entity.curb.death", "curb", ["death"], 24)
    ev("entity.curb_minion.step", "curb", ["minion_step0", "minion_step1", "minion_step2"], 10)
    ev("entity.curb_minion.hurt", "curb", ["minion_hurt0", "minion_hurt1"], 14)
    ev("entity.curb_minion.death", "curb", ["minion_death"], 16)


def write_json():
    path = os.path.join(ns.BASE, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for e, (fold, files, dist) in EVENTS.items():
        data[e] = {"subtitle": "subtitles.basedefense." + e.split(".", 1)[1],
                   "sounds": [{"name": "basedefense:mob/%s/%s" % (fold, n), "attenuation_distance": dist} for n in files]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    zombie()
    knight()
    curb()
    write_json()
    print("готово:", len(ns.SOUNDS), "файлов,", len(EVENTS), "событий")
