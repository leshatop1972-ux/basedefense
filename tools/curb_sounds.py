"""
Звуки Бордюра: синтез реактивной струи (jet_charge, 1.6 с) и рывка (jet_burst, 0.7 с).
Запуск: python tools/curb_sounds.py   (нужны numpy и ffmpeg с libvorbis в PATH)
Пишет assets/basedefense/sounds/curb/*.ogg (моно — иначе звук не будет объёмным).
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "sounds", "curb")
rng = np.random.default_rng(7)


def lp(x, a):
    """Простой фильтр нижних частот: шум → гул."""
    y = np.empty_like(x)
    s = 0.0
    for i, v in enumerate(x):
        s += a * (v - s)
        y[i] = s
    return y


def save(name, x):
    x = x / np.max(np.abs(x)) * 0.9
    tmp = os.path.join(tempfile.gettempdir(), name + ".wav")
    with wave.open(tmp, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    os.makedirs(OUT, exist_ok=True)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)


def jet_charge():
    # гул сопла нарастает, сверху — поднимающийся свист турбины
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    env = np.minimum(1, t / 0.25) * (0.55 + 0.45 * t / 1.6) * np.minimum(1, (1.6 - t) / 0.08)
    roar = lp(noise, 0.08) * 3 + lp(noise, 0.35) * 0.6
    flick = 1 + 0.25 * np.sin(2 * np.pi * 23 * t) * np.sin(2 * np.pi * 3.1 * t)
    f = 380 + 900 * (t / 1.6) ** 1.6
    whine = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.18 + np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR) * 0.07
    save("jet_charge", (roar * flick + whine * np.minimum(1, t / 0.6)) * env)


def jet_burst():
    # низкий хлопок + свист воздуха
    n = int(0.7 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    boom = np.sin(2 * np.pi * (70 - 40 * t) * t) * np.exp(-t * 7) * 1.2
    whoosh = lp(noise, 0.2) * np.exp(-t * 5) * 2.2 + noise * 0.25 * np.exp(-t * 14)
    save("jet_burst", boom + whoosh)


if __name__ == "__main__":
    jet_charge()
    jet_burst()
    print("готово:", OUT)
