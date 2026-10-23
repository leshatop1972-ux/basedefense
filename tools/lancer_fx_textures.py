"""
Текстуры визуальных эффектов босса «Оборванный улан» (textures/fx/*.png).
Запуск из корня проекта:  python tools/lancer_fx_textures.py   (нужен только Pillow)

Все текстуры белые/серые на чёрном: в игре они рисуются с аддитивным смешиванием
(RenderLayer.getEyes) и красятся цветом вершин в коде (LancerFx), поэтому
RGB = яркость (уже умножена на альфу), A = яркость.

  ring.png    — ударное кольцо: резкий яркий край снаружи, мягкий хвост внутрь
  crack.png   — трещины в земле после удара (лучи от центра с изломами)
  rune.png    — руническая печать (кольца, засечки, звезда, руны) — небо при ливне и аура 2-й фазы
  glint.png   — блик-звезда на острие копья (предупреждение перед атакой)
  trail.png   — след острия: v=0 у древка → v=1 у острия (яркая кромка)
  beam.png    — столб/луч: мягкий по ширине, с продольными прожилками
  arrow.png   — шевроны «сюда ударит таран» (идут вдоль v)
  slash.png   — серп удара (дуга) для взмаха и подброса
  marker.png  — метка падения копья / приземления: кольцо с 4 засечками
  wall.png    — стена купола зоны: ярко внизу, тает вверх, прожилки
  line.png    — мягкая линия (нить дуэлянта)
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "basedefense", "textures", "fx")


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def save(name, w, h, fn):
    """fn(u, v) -> (яркость 0..1, белизна 0..1): белизна 1 — чисто белый, 0 — голубоватый оттенок."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    p = img.load()
    for y in range(h):
        for x in range(w):
            res = fn((x + 0.5) / w, (y + 0.5) / h)
            if isinstance(res, tuple):
                b, white = res
            else:
                b, white = res, 1.0
            b = clamp(b)
            if b <= 0.002:
                continue
            # лёгкий холодный оттенок у тусклых частей, белое ядро у ярких
            r = b * (0.80 + 0.20 * white)
            g = b * (0.90 + 0.10 * white)
            bl = b
            p[x, y] = (int(r * 255), int(g * 255), int(bl * 255), int(b * 255))
    img.save(os.path.join(ROOT, name))


def polar(u, v):
    dx, dy = u * 2 - 1, v * 2 - 1
    return math.hypot(dx, dy), math.atan2(dy, dx)


def ring(u, v):
    r, _ = polar(u, v)
    if r > 1:
        return 0
    edge = smooth(1.0, 0.93, r) * smooth(0.55, 0.92, r)          # резкий край, хвост внутрь
    core = math.exp(-((r - 0.9) / 0.035) ** 2)                    # тонкая белая кромка
    return clamp(edge * 0.75 + core), clamp(core * 1.2)


RNG = random.Random(7)
CRACKS = []
for i in range(11):
    a0 = i / 11 * math.tau + RNG.uniform(-0.2, 0.2)
    pts = [(0.0, a0)]
    rr, aa = 0.0, a0
    while rr < 0.95:
        rr += RNG.uniform(0.08, 0.16)
        aa += RNG.uniform(-0.28, 0.28)
        pts.append((min(rr, 0.97), aa))
    CRACKS.append(pts)
    if RNG.random() < 0.7:                                        # ответвление
        k = RNG.randint(2, max(2, len(pts) - 2))
        r1, a1 = pts[k]
        CRACKS.append([(r1, a1), (r1 + 0.18, a1 + RNG.choice((-1, 1)) * 0.4),
                       (min(0.95, r1 + 0.32), a1 + RNG.choice((-1, 1)) * 0.5)])
SEGS = []
for pts in CRACKS:
    for (r0, a0), (r1, a1) in zip(pts, pts[1:]):
        SEGS.append((r0 * math.cos(a0), r0 * math.sin(a0), r1 * math.cos(a1), r1 * math.sin(a1), r0))


def seg_dist(px, py, x0, y0, x1, y1):
    vx, vy = x1 - x0, y1 - y0
    t = clamp(((px - x0) * vx + (py - y0) * vy) / (vx * vx + vy * vy + 1e-9))
    return math.hypot(px - x0 - vx * t, py - y0 - vy * t)


def crack(u, v):
    px, py = u * 2 - 1, v * 2 - 1
    r = math.hypot(px, py)
    if r > 1:
        return 0
    best = 0.0
    for x0, y0, x1, y1, r0 in SEGS:
        d = seg_dist(px, py, x0, y0, x1, y1)
        width = 0.028 * (1.0 - 0.6 * r0)
        best = max(best, math.exp(-(d / width) ** 2))
    fade = smooth(1.0, 0.6, r)
    glow = 0.35 * math.exp(-(r / 0.25) ** 2)                      # пятно в центре удара
    return clamp(best * fade + glow), clamp(best * 0.8)


RUNES = []
for i in range(16):                                               # простые «руны» из 3–4 штрихов
    strokes = []
    for _ in range(RNG.randint(3, 4)):
        strokes.append((RNG.uniform(-1, 1), RNG.uniform(-1, 1), RNG.uniform(-1, 1), RNG.uniform(-1, 1)))
    RUNES.append(strokes)


def rune(u, v):
    r, ang = polar(u, v)
    if r > 1:
        return 0
    b = 0.0
    for rr, w, s in ((0.97, 0.012, 1.0), (0.90, 0.006, 0.7), (0.70, 0.008, 0.9), (0.64, 0.004, 0.5),
                     (0.30, 0.007, 0.8)):
        b = max(b, s * math.exp(-((r - rr) / w) ** 2))
    # засечки между внешними кольцами
    if 0.90 < r < 0.97:
        k = (ang / math.tau * 72) % 1
        if k < 0.12:
            b = max(b, 0.7)
    # руны в поясе 0.72..0.88
    if 0.72 < r < 0.88:
        n = 16
        seg = (ang + math.pi) / math.tau * n
        idx = int(seg) % n
        lx = (seg % 1 - 0.5) * 2.0                                # −1..1 поперёк сектора
        ly = (r - 0.80) / 0.075                                   # −1..1 по радиусу
        if abs(lx) < 0.75 and abs(ly) < 0.9:
            for x0, y0, x1, y1 in RUNES[idx]:
                d = seg_dist(lx / 0.75, ly / 0.9, x0, y0, x1, y1)
                b = max(b, 0.85 * math.exp(-(d / 0.13) ** 2))
    # восьмиконечная звезда из двух квадратов внутри кольца 0.70
    px, py = u * 2 - 1, v * 2 - 1
    for rot in (0.0, math.pi / 4):
        c, s = math.cos(rot), math.sin(rot)
        x, y = px * c - py * s, px * s + py * c
        d = abs(max(abs(x), abs(y)) - 0.49)
        if r < 0.70:
            b = max(b, 0.6 * math.exp(-(d / 0.007) ** 2))
    # лучи от центра к звезде
    if r < 0.64:
        k = (ang / math.tau * 8) % 1
        d = min(k, 1 - k) * r * 6
        b = max(b, 0.35 * math.exp(-(d / 0.03) ** 2) * smooth(0.0, 0.3, r))
    b = max(b, 0.12 * smooth(1.0, 0.0, r))                       # общее слабое свечение
    return clamp(b), clamp(b * 1.3 - 0.3)


def glint(u, v):
    dx, dy = u * 2 - 1, v * 2 - 1
    r = math.hypot(dx, dy)
    star = math.exp(-abs(dx) * 22) * smooth(1.0, 0.0, abs(dy)) + math.exp(-abs(dy) * 22) * smooth(1.0, 0.0, abs(dx))
    diag = 0.45 * (math.exp(-abs(dx - dy) * 28) + math.exp(-abs(dx + dy) * 28)) * smooth(0.6, 0.0, r)
    core = math.exp(-(r / 0.12) ** 2)
    halo = 0.35 * math.exp(-(r / 0.4) ** 2)
    b = star + diag + core + halo
    return clamp(b), clamp(core + star * 0.6)


def trail(u, v):
    edge = math.exp(-((v - 0.93) / 0.05) ** 2)                    # кромка у острия
    body = v ** 1.6 * 0.75
    streak = 0.85 + 0.15 * math.sin(u * 40 + v * 7)
    return clamp((body + edge) * streak * smooth(0.0, 0.08, 1 - v + 0.08)), clamp(edge)


BEAM_STREAKS = [(RNG.uniform(0.2, 0.8), RNG.uniform(0.02, 0.05), RNG.uniform(0.3, 0.8)) for _ in range(7)]


def beam(u, v):
    x = abs(u * 2 - 1)
    core = math.exp(-(x / 0.18) ** 2)
    soft = 0.5 * math.exp(-(x / 0.55) ** 2)
    st = 0.0
    for c, w, s in BEAM_STREAKS:
        st = max(st, s * math.exp(-((u - c) / w) ** 2) * (0.6 + 0.4 * math.sin(v * 9 + c * 20)))
    return clamp(core + soft + st * 0.4), clamp(core)


def arrow(u, v):
    x = abs(u * 2 - 1)
    # шевроны: «^» вдоль v, по 4 на текстуру
    k = (v * 4 + x * 0.55) % 1
    chev = math.exp(-((k - 0.5) / 0.09) ** 2) * smooth(1.0, 0.75, x)
    sides = 0.5 * math.exp(-((x - 0.95) / 0.04) ** 2)
    fill = 0.12 * smooth(1.0, 0.0, x)
    return clamp(chev + sides + fill), clamp(chev)


def slash(u, v):
    # серп: дуга между двумя смещёнными кругами, толще в середине, острые концы
    dx, dy = u * 2 - 1, v * 2 - 1
    r1 = math.hypot(dx, dy + 0.0)
    r2 = math.hypot(dx, dy + 0.28)
    inside = smooth(0.98, 0.9, r1) * smooth(0.86, 0.96, r2)
    if dy > 0.05:
        inside *= smooth(0.45, 0.05, dy)
    edge = math.exp(-((r1 - 0.93) / 0.03) ** 2) * (1 if dy < 0.1 else smooth(0.45, 0.1, dy))
    return clamp(inside * 0.8 + edge * 0.9), clamp(edge)


def marker(u, v):
    r, ang = polar(u, v)
    if r > 1:
        return 0
    b = math.exp(-((r - 0.88) / 0.05) ** 2)
    b = max(b, 0.55 * math.exp(-((r - 0.62) / 0.025) ** 2))
    k = (ang / math.tau * 4 + 0.125) % 1                          # 4 засечки внутрь
    if 0.35 < r < 0.82 and min(k, 1 - k) < 0.035 / max(r, 0.1):
        b = max(b, 0.8 * smooth(0.35, 0.55, r))
    b = max(b, 0.18 * smooth(0.9, 0.0, r))
    b = max(b, math.exp(-(r / 0.07) ** 2))
    return clamp(b), clamp(b - 0.2)


WALL_STREAKS = [(RNG.random(), RNG.uniform(0.01, 0.03), RNG.uniform(0.3, 1.0)) for _ in range(9)]


def wall(u, v):
    # v=0 сверху, v=1 у земли
    base = v ** 2.2
    st = 0.0
    for c, w, s in WALL_STREAKS:
        d = min(abs(u - c), 1 - abs(u - c))
        st = max(st, s * math.exp(-(d / w) ** 2))
    edge = math.exp(-((v - 0.97) / 0.03) ** 2)
    return clamp(base * (0.55 + 0.45 * st) + edge), clamp(edge)


def line(u, v):
    x = abs(v * 2 - 1)
    return clamp(math.exp(-(x / 0.35) ** 2) + 0.6 * math.exp(-(x / 0.08) ** 2)), clamp(1 - x * 2)


if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    save("ring.png", 128, 128, ring)
    save("crack.png", 128, 128, crack)
    save("rune.png", 256, 256, rune)
    save("glint.png", 64, 64, glint)
    save("trail.png", 64, 32, trail)
    save("beam.png", 32, 128, beam)
    save("arrow.png", 32, 128, arrow)
    save("slash.png", 128, 128, slash)
    save("marker.png", 64, 64, marker)
    save("wall.png", 64, 64, wall)
    save("line.png", 16, 16, line)
    print("Готово:", os.path.normpath(ROOT))
