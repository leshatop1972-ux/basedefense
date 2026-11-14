"""
Тесак Громового тигра (оружие игрока): модель‑сетка (широкий клинок‑дао, барабан с патронами, выхлопная труба
с тремя соплами по обуху, рукоять в оплётке, кольцо‑навершие), текстура, маска накала, иконка,
анимации 1‑го лица (рука/руки + тесак + барабан + обойма + камера) и позы 3‑го лица.

    python tools/cleaver_gen.py                 — всё: модель, текстуры, иконки, анимации, CleaverKeys.java
    python tools/cleaver_gen.py --model         — превью модели крупно (tools/cleaver_model.png)
    python tools/cleaver_gen.py --fp <клип>     — кадры клипа глазами игрока (tools/cleaver_fp.png) и GIF
    python tools/cleaver_gen.py --tp [клип]     — позы 3‑го лица (tools/cleaver_tp.png)
    python tools/cleaver_gen.py --check         — проверка: рука не обрывается на экране, кромка идёт по ходу удара

Система координат тесака (px модели): начало — середина правого кулака на рукояти, +y — к острию,
+x — режущая кромка, −x — обух (там барабан, труба и сопла), z — толщина.

Устроено так же, как катана (tools/katana_gen.py): 1‑е лицо печётся по 60 кадров/с в
assets/basedefense/fpanim/cleaver.json, Java (client/weapon/fp) только интерполирует; что видно в превью — то и в игре.
Отличия: оружие одноручное (левая рука свободна — у неё свой путь; на тяжёлых ударах берётся за рукоять),
у кадра 4 добавочных канала: барабан откинут 0..1, поворот барабана (обороты), огонь на клинке 0..1, вспышка сопел 0..1.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import fpview as fv  # noqa: E402
from fpview import nrm, v3  # noqa: E402
import katana_gen as kg  # noqa: E402  (общая математика: сплайны, сетка, кватернионы)
from katana_gen import Mesh, build_box, build_disc, build_tube, frame_q, hermite, lerpc, mesh_batch, mesh_json, slerp, uv_of  # noqa: E402

TOOLS = os.path.dirname(__file__)
ASSETS = os.path.join(TOOLS, "..", "src", "main", "resources", "assets", "basedefense")
JAVA_DIR = os.path.join(TOOLS, "..", "src", "client", "java", "com", "lesha_top1972", "basedefense", "client", "weapon")
JAVA_MAIN = os.path.join(TOOLS, "..", "src", "main", "java", "com", "lesha_top1972", "basedefense", "weapon")
NAME = "cleaver"

# =====================================================================
# Размеры (px модели)
# =====================================================================
RING_Y, RING_R, RING_T = -6.55, 0.95, 0.21      # кольцо‑навершие: центр, радиус, толщина прутка
HANDLE_Y0, HANDLE_Y1 = -5.35, 1.45              # рукоять
LEFT_GRIP = -3.30                               # середина левого кулака при двуручном хвате
RECV_Y0, RECV_Y1 = 1.45, 3.75                   # ствольная коробка (она же гарда)
BLADE_Y0, TIP_Y = 3.55, 19.5                    # клинок
DRUM_C = (-1.78, 2.92, 0.0)                     # центр барабана (ось — вдоль y)
DRUM_R, DRUM_H = 0.95, 1.9
PIPE_Y1 = 12.6                                  # до куда идёт выхлопная труба по обуху
VENTS_Y = (6.4, 9.0, 11.6)                      # сопла
TEX_W, TEX_H = 128, 128

# Кромка и обух — кривые по опорным точкам (x, y); клинок расширяется к последней трети («бычий хвост»).
EDGE_PTS = [(1.02, 3.55), (1.10, 6.0), (1.30, 9.0), (1.66, 12.0), (2.02, 14.6), (2.04, 16.4), (1.52, 18.1),
            (0.20, 19.15), (-1.30, 19.5)]
SPINE_PTS = [(-0.74, 3.55), (-0.78, 6.0), (-0.88, 9.0), (-1.04, 12.0), (-1.26, 14.6), (-1.44, 16.4), (-1.52, 18.1),
             (-1.46, 19.0), (-1.30, 19.5)]
FLAT_X0, FLAT_X1 = -1.75, 2.30                  # рамка развёртки плоскости клинка по x


def _curve(pts, s):
    """Точка кривой Катмулла–Рома по опорным точкам; s — 0..1 (равномерно по номеру точки)."""
    n = len(pts)
    ts = list(range(n))
    return hermite(ts, [v3(p[0], p[1], 0.0) for p in pts], [False] * n, s * (n - 1))


def edge_pt(s):
    return _curve(EDGE_PTS, s)


def spine_pt(s):
    return _curve(SPINE_PTS, s)


def spine_x(y):
    """x обуха на высоте y (для трубы и сопел)."""
    ys = [p[1] for p in SPINE_PTS]
    xs = [p[0] for p in SPINE_PTS]
    return float(np.interp(y, ys, xs))


# =====================================================================
# Текстура. Палитра — как у тесака самого тигра: бордовая сталь, раскалённо‑золотая кромка, золото.
# =====================================================================
PAL = dict(body0=(92, 24, 18), body1=(58, 20, 18), body2=(40, 16, 16), gold0=(240, 174, 13), gold1=(255, 232, 118),
           gold2=(168, 89, 0), cream=(255, 255, 191), dark0=(45, 22, 20), dark1=(33, 15, 15), gun0=(52, 44, 48),
           gun1=(84, 74, 78), gun2=(30, 24, 28), cord0=(96, 20, 18), cord1=(128, 30, 24), cord2=(62, 12, 12),
           soot=(18, 12, 12), red=(196, 40, 24))

R_FLAT = (0, 0, 128, 32)        # плоскость клинка: u — вдоль (от гарды к острию), v — от обуха к кромке
R_BEVEL = (0, 32, 128, 6)       # спуск к кромке: v=0 — сама кромка
R_SPINE = (0, 38, 128, 3)       # обух
R_CORD = (0, 42, 96, 16)        # оплётка рукояти
R_GUN = (96, 42, 16, 4)         # воронёная сталь
R_GOLD = (112, 42, 16, 4)       # золото
R_SOOT = (96, 46, 16, 4)        # нагар (сопла, каморы)
R_RED = (112, 46, 16, 4)        # красная метка патрона
R_BRASS = (96, 50, 16, 4)       # латунь гильз
R_DRUM = (0, 60, 72, 12)        # бок барабана: u — по кругу (6 долов), v — вдоль оси
R_CAP = (72, 60, 18, 18)        # торец барабана: 6 камор
R_PIPE = (0, 74, 64, 6)         # труба: u — вдоль, v — по кругу (побежалость от жара)
R_RECV = (0, 82, 40, 16)        # бок коробки: гравировка — морда тигра, заклёпки
R_RING = (40, 82, 32, 4)        # кольцо‑навершие
R_VENT = (64, 74, 16, 6)        # сопло: золото с копотью у среза
R_WHITE = (120, 120, 8, 8)      # белое (следы, свечение)
WHITE_UV = (124.0 / TEX_W, 124.0 / TEX_H)

STRIPES = [(6.3, 1.5, 0.30), (7.9, 2.0, 0.34), (9.6, 1.7, 0.32), (11.3, 2.3, 0.36), (13.0, 2.0, 0.36),
           (14.7, 2.6, 0.40), (16.3, 2.2, 0.36)]       # тигриные полосы: (y у обуха, длина, полуширина)


def stripe_at(x, y):
    """0..1 — насколько точка плоскости клинка лежит на золотой тигриной полосе (клин от обуха к кромке)."""
    best = 0.0
    for (y0, ln, hw) in STRIPES:
        xs = spine_x(y0)
        d = (x - xs) / ln                                   # 0 у обуха → 1 на конце полосы
        if d < -0.05 or d > 1.0:
            continue
        yc = y0 + 0.75 * (x - xs) + 0.35 * math.sin(d * 3.0)  # полоса идёт к острию и чуть извивается
        w = hw * (1.0 - d) ** 0.8
        k = 1.0 - abs(y - yc) / max(w, 1e-3)
        best = max(best, min(1.0, k * 2.2))
    return max(0.0, best)


def paint():
    """→ (текстура, маска накала). Маска: что светится (кромка всегда, полосы/сопла/труба — от нагрева)."""
    A = np.zeros((TEX_H, TEX_W, 4), np.float32)
    G = np.zeros((TEX_H, TEX_W, 4), np.float32)
    rng = np.random.default_rng(1972)

    def put(x, y, c, k=1.0, glow=None):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            A[y, x] = (min(255, c[0] * k) / 255, min(255, c[1] * k) / 255, min(255, c[2] * k) / 255, 1)
            if glow is not None:
                G[y, x] = (glow[0] / 255, glow[1] / 255, glow[2] / 255, 1)

    # --- плоскость клинка
    x0, y0, w, h = R_FLAT
    for tx in range(w):
        y = BLADE_Y0 + (tx + 0.5) / w * (TIP_Y - BLADE_Y0)
        xs = spine_x(y)
        for ty in range(h):
            x = FLAT_X0 + (ty + 0.5) / h * (FLAT_X1 - FLAT_X0)
            d = (x - xs)                                    # px от обуха
            c = lerpc(PAL['body1'], PAL['body0'], min(1.0, max(0.0, d / 2.2)))
            k = 1.0 + 0.05 * math.sin(tx * 0.7 + ty * 1.9 + math.sin(tx * 0.23) * 3) + rng.uniform(-0.035, 0.035)
            glow = None
            if d < 0.42:                                    # тёмная полоса вдоль обуха (под трубой — закопчено)
                c = lerpc(PAL['body2'], c, max(0.0, d / 0.42))
                if y < PIPE_Y1 + 0.6:
                    glow = (70, 14, 4)
            st = stripe_at(x, y)
            if st > 0.0:
                gc = lerpc(PAL['gold2'], PAL['gold0'], min(1.0, st * 1.3))
                if st > 0.75:
                    gc = PAL['gold1']
                c = lerpc(c, gc, min(1.0, st * 1.6))
                glow = lerpc((90, 30, 0), (255, 170, 40), min(1.0, st))
                k = 1.0 + rng.uniform(-0.03, 0.03)
            if y < 5.05:                                    # у гарды — золотая накладка с насечкой
                c = PAL['gold0'] if int(tx + ty) % 4 else PAL['gold2']
                if 4.75 < y:
                    c = PAL['gold1']
                glow = None
            elif y < 5.3:
                c = PAL['dark1']
            if 12.2 < y < 12.9 and 0.5 < d < 1.3:           # клеймо мастера — тёмный квадрат с золотой точкой
                c = PAL['dark1'] if not (12.4 < y < 12.7 and 0.75 < d < 1.05) else PAL['gold1']
            put(x0 + tx, y0 + ty, c, k, glow)
    # --- спуск: раскалённо‑золотая кромка (светится всегда)
    x0, y0, w, h = R_BEVEL
    for tx in range(w):
        for ty in range(h):
            t = ty / (h - 1)
            c = lerpc(PAL['cream'], PAL['gold0'], min(1.0, t * 1.25))
            if ty == h - 1:
                c = PAL['gold2']
            k = 1.0 + rng.uniform(-0.03, 0.03) + (0.04 if (tx // 3) % 5 == 0 and ty > 1 else 0)
            put(x0 + tx, y0 + ty, c, k, lerpc((255, 236, 150), (200, 96, 10), t))
    x0, y0, w, h = R_SPINE
    for tx in range(w):
        for ty in range(h):
            put(x0 + tx, y0 + ty, PAL['dark0'] if ty != 1 else PAL['body1'], 1.0 + rng.uniform(-0.03, 0.03))
    # --- рукоять: бордовый шнур ромбами, в окошках — золото
    x0, y0, w, h = R_CORD
    period = 10.0
    for tx in range(w):
        for ty in range(h):
            phase = (tx % period) / period
            cy = abs((ty / h * 2) % 2 - 1)
            a1 = abs(phase - 0.5) * 2
            if a1 + cy * 0.9 < 0.50:
                c = PAL['gold0'] if rng.uniform() > 0.25 else PAL['gold2']
                if abs(a1 + cy * 0.9 - 0.50) < 0.12:
                    c = PAL['gold2']
            else:
                c = PAL['cord0']
                if (tx + ty) % 3 == 0:
                    c = PAL['cord1']
                if abs(a1 - (1 - cy)) < 0.09:
                    c = PAL['cord2']
            put(x0 + tx, y0 + ty, c)
    for reg, base, hi in ((R_GUN, PAL['gun0'], PAL['gun1']), (R_GOLD, PAL['gold0'], PAL['gold1']),
                          (R_SOOT, PAL['soot'], PAL['dark1']), (R_RED, PAL['red'], (236, 84, 40)),
                          (R_BRASS, (206, 150, 52), (248, 214, 120)), (R_RING, PAL['gold0'], PAL['gold1'])):
        x0, y0, w, h = reg
        for tx in range(w):
            for ty in range(h):
                put(x0 + tx, y0 + ty, hi if ty == 1 else base, 1.0 + rng.uniform(-0.03, 0.03))
    # --- бок барабана: 6 долов, золотой поясок, насечка у казённой части
    x0, y0, w, h = R_DRUM
    for tx in range(w):
        ph = (tx % 12) / 12.0
        for ty in range(h):
            c = PAL['gun0']
            k = 1.0 + 0.10 * math.cos((ph - 0.5) * 2 * math.pi)
            if 0.30 < ph < 0.70 and 2 <= ty <= 7:           # дол
                c = PAL['gun2']
                k = 1.0 + (0.25 if abs(ph - 0.5) > 0.16 else 0.0)
            if ty in (9,):
                c = PAL['gold0']
            if ty >= 10:
                c = PAL['gun1'] if tx % 2 else PAL['gun0']
            if ty == 0:
                c = PAL['gun1']
            put(x0 + tx, y0 + ty, c, k + rng.uniform(-0.03, 0.03))
    # --- торец барабана: 6 камор с капсюлями
    x0, y0, w, h = R_CAP
    for tx in range(w):
        for ty in range(h):
            dx, dy = tx - (w - 1) / 2, ty - (h - 1) / 2
            r = math.hypot(dx, dy) / ((w - 1) / 2)
            c = PAL['gun1'] if r > 0.86 else PAL['gun0']
            glow = None
            for i in range(6):
                a = i * math.pi / 3
                cx, cy = math.cos(a) * 0.56 * (w - 1) / 2, math.sin(a) * 0.56 * (w - 1) / 2
                dd = math.hypot(dx - cx, dy - cy)
                if dd < 2.3:
                    c = (206, 150, 52) if dd > 1.0 else PAL['red']
                    glow = (120, 40, 6)
            if r < 0.16:
                c = PAL['gold0']
            put(x0 + tx, y0 + ty, c, 1.0 + rng.uniform(-0.03, 0.03), glow)
    # --- труба: воронение, к соплам — побежалость (синева → соломенный), светится от нагрева
    x0, y0, w, h = R_PIPE
    for tx in range(w):
        s = tx / (w - 1)
        for ty in range(h):
            c = lerpc(PAL['gun0'], (70, 60, 96), max(0.0, math.sin(s * math.pi * 3.0)) * 0.7)
            c = lerpc(c, (150, 104, 60), max(0.0, math.sin(s * math.pi * 3.0 - 0.9)) * 0.35)
            k = 1.0 + (0.22 if ty == 1 else 0.0) - (0.15 if ty == h - 1 else 0.0)
            if tx % 16 == 0:
                c = PAL['gold2']
            put(x0 + tx, y0 + ty, c, k + rng.uniform(-0.03, 0.03), (170, 44, 6))
    # --- коробка: воронёная пластина, золотая рамка, морда тигра (полосы) и заклёпки
    x0, y0, w, h = R_RECV
    for tx in range(w):
        for ty in range(h):
            c = PAL['gun0']
            k = 1.0 + rng.uniform(-0.04, 0.04)
            if tx in (0, w - 1) or ty in (0, h - 1):
                c = PAL['gold2']
            elif tx in (1, w - 2) or ty in (1, h - 2):
                c = PAL['gold0']
            elif (tx, ty) in ((4, 4), (4, h - 5), (w - 5, 4), (w - 5, h - 5)):
                c = PAL['gold1']
            else:
                # три косые «тигриные» насечки
                for i in range(3):
                    if abs((tx - 12 - i * 7) - (ty - h / 2) * 0.6) < 1.1 and 3 < ty < h - 4:
                        c = PAL['gold0'] if abs((tx - 12 - i * 7) - (ty - h / 2) * 0.6) < 0.6 else PAL['gold2']
            put(x0 + tx, y0 + ty, c, k)
    # --- сопло: золото, у среза — копоть и жар
    x0, y0, w, h = R_VENT
    for tx in range(w):
        s = tx / (w - 1)
        for ty in range(h):
            c = lerpc(PAL['gold0'], PAL['gold2'], s)
            glow = None
            if s > 0.62:
                c = lerpc(PAL['gold2'], PAL['soot'], (s - 0.62) / 0.38)
                glow = lerpc((120, 40, 0), (255, 150, 30), (s - 0.62) / 0.38)
            put(x0 + tx, y0 + ty, c, 1.0 + (0.15 if ty == 1 else 0) + rng.uniform(-0.03, 0.03), glow)
    x0, y0, w, h = R_WHITE
    for tx in range(w):
        for ty in range(h):
            put(x0 + tx, y0 + ty, (255, 255, 255))
    # нагар в каморах/соплах тоже светится (изнутри)
    x0, y0, w, h = R_SOOT
    G[y0:y0 + h, x0:x0 + w] = (1.0, 0.45, 0.08, 1)
    G[..., 3] = 1.0
    tex = Image.fromarray((np.clip(A, 0, 1) * 255).astype(np.uint8), "RGBA")
    glow = Image.fromarray((np.clip(G, 0, 1) * 255).astype(np.uint8), "RGBA")
    return tex, glow


# =====================================================================
# Геометрия
# =====================================================================
def xform(src, R, t, dst):
    """Перенести грани src в dst: p → R·p + t."""
    R = np.asarray(R, float)
    t = v3(t)
    for V, UV, N in src.q:
        dst.q.append((np.array([R @ p + t for p in V]), UV, np.array([R @ n for n in N])))


def blade_station(s):
    """Сечение клинка на доле s: [кромка, спуск+, обух+, обух−, спуск−] и ширина."""
    E, S = edge_pt(s), spine_pt(s)
    wv = E - S
    w = float(np.linalg.norm(wv))
    d = wv / w if w > 1e-6 else v3(1, 0, 0)
    th = 0.44 - 0.16 * s                                   # толщина: у гарды 0.44 → у острия 0.28
    if s > 0.9:
        th *= max(0.15, (1.0 - s) / 0.1)
    bw = min(0.62, 0.42 * w)                               # ширина спуска
    sh = E - d * bw
    return [E, sh + v3(0, 0, th / 2), S + v3(0, 0, th / 2), S + v3(0, 0, -th / 2), sh + v3(0, 0, -th / 2)], w


def flat_uv(p):
    return uv_of(R_FLAT, (p[1] - BLADE_Y0) / (TIP_Y - BLADE_Y0), (p[0] - FLAT_X0) / (FLAT_X1 - FLAT_X0))


def build_blade(m):
    ss = list(np.linspace(0, 0.72, 30)) + list(np.linspace(0.72, 1.0, 26))[1:]
    rings = [(s, blade_station(s)[0]) for s in ss]
    for i in range(len(rings) - 1):
        s0, A = rings[i]
        s1, B = rings[i + 1]
        u0 = (A[0][1] - BLADE_Y0) / (TIP_Y - BLADE_Y0)
        u1 = (B[0][1] - BLADE_Y0) / (TIP_Y - BLADE_Y0)
        # спуски (кромка v=0 → плечо v=1)
        m.quad([A[0], B[0], B[1], A[1]], [uv_of(R_BEVEL, u0, 0), uv_of(R_BEVEL, u1, 0), uv_of(R_BEVEL, u1, 1), uv_of(R_BEVEL, u0, 1)])
        m.quad([A[4], B[4], B[0], A[0]], [uv_of(R_BEVEL, u0, 1), uv_of(R_BEVEL, u1, 1), uv_of(R_BEVEL, u1, 0), uv_of(R_BEVEL, u0, 0)])
        # плоскости
        m.quad([A[1], B[1], B[2], A[2]], [flat_uv(A[1]), flat_uv(B[1]), flat_uv(B[2]), flat_uv(A[2])])
        m.quad([A[3], B[3], B[4], A[4]], [flat_uv(A[3]), flat_uv(B[3]), flat_uv(B[4]), flat_uv(A[4])])
        # обух
        m.quad([A[2], B[2], B[3], A[3]], [uv_of(R_SPINE, u0, 0), uv_of(R_SPINE, u1, 0), uv_of(R_SPINE, u1, 1), uv_of(R_SPINE, u0, 1)])


def tube_path(m, pts, r, reg, n=8, cap=True):
    """Труба радиусом r вдоль ломаной pts (в плоскости x‑y) — выхлопная труба, сопла, кольцо."""
    pts = [v3(p) for p in pts]
    rings = []
    for i, p in enumerate(pts):
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        tg = nrm(b - a)
        nx = nrm(np.cross(tg, v3(0, 0, 1)))
        rr = r(i / (len(pts) - 1)) if callable(r) else r
        rings.append([p + (nx * math.cos(2 * math.pi * k / n) + v3(0, 0, 1) * math.sin(2 * math.pi * k / n)) * rr
                      for k in range(n)])
    for i in range(len(pts) - 1):
        u0, u1 = i / (len(pts) - 1), (i + 1) / (len(pts) - 1)
        for k in range(n):
            a0, a1, b0, b1 = rings[i][k], rings[i][(k + 1) % n], rings[i + 1][k], rings[i + 1][(k + 1) % n]
            N = [nrm(a0 - pts[i]), nrm(b0 - pts[i + 1]), nrm(b1 - pts[i + 1]), nrm(a1 - pts[i])]
            m.quad([a0, b0, b1, a1], [uv_of(reg, u0, k / n), uv_of(reg, u1, k / n), uv_of(reg, u1, (k + 1) / n),
                                      uv_of(reg, u0, (k + 1) / n)], N)
    if cap:
        for ring, c, flip in ((rings[0], pts[0], True), (rings[-1], pts[-1], False)):
            for k in range(n):
                a, b = ring[k], ring[(k + 1) % n]
                uv = [uv_of(R_SOOT, 0, 0), uv_of(R_SOOT, 1, 0), uv_of(R_SOOT, 0.5, 1)]
                if flip:
                    m.tri(b, a, c, uv[1], uv[0], uv[2])
                else:
                    m.tri(a, b, c, *uv)


def vent_pos(y):
    """Срез сопла на высоте y: точка (в системе тесака) и направление выхлопа."""
    xs = spine_x(y) - 0.34
    d = nrm(v3(-1.0, 0.42, 0.0))
    return v3(xs, y, 0.0) + d * 1.05, d


def build_cleaver():
    """Тесак без барабана (барабан — отдельная часть: крутится и откидывается)."""
    m = Mesh()
    build_blade(m)
    # --- выхлопная труба по обуху: от коробки до верхнего сопла, прижата к обуху
    ys = list(np.linspace(RECV_Y1 + 0.35, PIPE_Y1, 22))
    tube_path(m, [(spine_x(y) - 0.34, y, 0.0) for y in ys], 0.33, R_PIPE, n=8)
    # хомуты, которыми труба прихвачена к клинку
    for y in (5.2, 7.7, 10.3, PIPE_Y1 - 0.25):
        xs = spine_x(y)
        build_box(m, xs - 0.74, xs + 0.30, y - 0.16, y + 0.16, -0.40, 0.40, R_GOLD)
    # --- сопла: короткие раструбы назад‑вверх
    for y in VENTS_Y:
        tip, d = vent_pos(y)
        base = v3(spine_x(y) - 0.34, y - 0.18, 0.0)
        tube_path(m, [base, base + d * 0.55, tip], lambda t: 0.30 + 0.16 * t, R_VENT, n=8)
    # --- коробка (гарда): основание под барабан, щёчки, перемычка над барабаном
    build_box(m, -0.80, 1.34, RECV_Y0, RECV_Y1, -0.58, 0.58, R_GUN)                   # тело коробки
    for z in (-0.60, 0.60):                                                            # гравированные щёчки
        P = [v3(-0.74, RECV_Y0 + 0.08, z), v3(1.28, RECV_Y0 + 0.08, z), v3(1.28, RECV_Y1 - 0.08, z), v3(-0.74, RECV_Y1 - 0.08, z)]
        UV = [uv_of(R_RECV, 0, 1), uv_of(R_RECV, 1, 1), uv_of(R_RECV, 1, 0), uv_of(R_RECV, 0, 0)]
        if z < 0:
            P, UV = P[::-1], UV[::-1]
        m.quad(P, UV)
    build_box(m, -2.86, 1.62, RECV_Y0 - 0.02, RECV_Y0 + 0.40, -0.66, 0.66, R_GOLD)    # нижняя пластина + усик к кромке
    build_box(m, 1.34, 1.95, RECV_Y0 + 0.02, RECV_Y0 + 0.78, -0.34, 0.34, R_GOLD)     # усик‑крюк гарды
    build_box(m, -2.86, -0.60, RECV_Y1 + 0.18, RECV_Y1 + 0.50, -0.50, 0.50, R_GUN)    # перемычка над барабаном
    build_box(m, -2.86, -2.62, RECV_Y0 + 0.40, RECV_Y1 + 0.18, -0.16, 0.16, R_GUN)    # задняя стойка рамки
    build_box(m, -2.30, -1.26, RECV_Y1 + 0.50, RECV_Y1 + 0.74, -0.30, 0.30, R_GOLD)   # переход к трубе
    build_box(m, 0.52, 0.94, 0.92, RECV_Y0, -0.14, 0.14, R_GUN)                        # спуск
    build_box(m, -1.30, -0.80, RECV_Y0 - 0.55, RECV_Y0, -0.20, 0.20, R_GUN)            # курок
    # --- рукоять: к середине чуть тоньше, кольца у гарды и у навершия
    def tr(y):
        t = (y - HANDLE_Y0) / (HANDLE_Y1 - HANDLE_Y0)
        k = 1.0 - 0.08 * math.sin(t * math.pi)
        return 0.58 * k, 0.46 * k
    build_tube(m, list(np.linspace(HANDLE_Y0 + 0.3, HANDLE_Y1 - 0.3, 16)), tr, R_CORD, n=10)
    build_tube(m, [HANDLE_Y1 - 0.3, HANDLE_Y1], lambda y: (0.66, 0.54), R_GOLD, n=10)
    build_tube(m, [HANDLE_Y0, HANDLE_Y0 + 0.3], lambda y: (0.66, 0.54), R_GOLD, n=10, cap0=0.0, cap_reg=R_GOLD)
    build_box(m, -0.20, 0.20, RING_Y + RING_R - 0.1, HANDLE_Y0, -0.20, 0.20, R_GOLD)   # шейка кольца
    # --- кольцо‑навершие (в плоскости клинка)
    n = 16
    ring = [(math.cos(2 * math.pi * i / n) * RING_R, RING_Y + math.sin(2 * math.pi * i / n) * RING_R, 0.0) for i in range(n + 1)]
    tube_path(m, ring, RING_T, R_RING, n=6, cap=False)
    return m


def build_drum():
    """Барабан: ось — y, центр в начале координат (ставится в DRUM_C, крутится вокруг y)."""
    m = Mesh()
    h = DRUM_H / 2
    n = 18
    ang = lambda k: 2 * math.pi * k / n
    for k in range(n):
        a0, a1 = ang(k), ang(k + 1)
        # долы: радиус чуть меньше между каморами
        r0 = DRUM_R * (1.0 - 0.07 * (1 if k % 3 == 1 else 0))
        r1 = DRUM_R * (1.0 - 0.07 * (1 if (k + 1) % 3 == 1 else 0))
        P = [v3(math.cos(a0) * r0, -h, math.sin(a0) * r0), v3(math.cos(a0) * r0, h, math.sin(a0) * r0),
             v3(math.cos(a1) * r1, h, math.sin(a1) * r1), v3(math.cos(a1) * r1, -h, math.sin(a1) * r1)]
        N = [nrm(v3(P[0][0], 0, P[0][2])), nrm(v3(P[1][0], 0, P[1][2])), nrm(v3(P[2][0], 0, P[2][2])), nrm(v3(P[3][0], 0, P[3][2]))]
        u0, u1 = k / n, (k + 1) / n
        m.quad(P, [uv_of(R_DRUM, u0, 1), uv_of(R_DRUM, u0, 0), uv_of(R_DRUM, u1, 0), uv_of(R_DRUM, u1, 1)], N)
        for y, up in ((-h, False), (h, True)):
            a = v3(math.cos(a0) * r0, y, math.sin(a0) * r0)
            b = v3(math.cos(a1) * r1, y, math.sin(a1) * r1)
            c = v3(0, y, 0)
            ua = uv_of(R_CAP, 0.5 + 0.5 * a[0] / DRUM_R, 0.5 + 0.5 * a[2] / DRUM_R)
            ub = uv_of(R_CAP, 0.5 + 0.5 * b[0] / DRUM_R, 0.5 + 0.5 * b[2] / DRUM_R)
            uc = uv_of(R_CAP, 0.5, 0.5)
            if up:
                m.tri(b, a, c, ub, ua, uc)
            else:
                m.tri(a, b, c, ua, ub, uc)
    # ось
    build_tube(m, [-h - 0.22, h + 0.22], lambda y: (0.16, 0.16), R_GOLD, n=6, cap0=0.0, cap1=0.0, cap_reg=R_GOLD)
    return m


def build_loader():
    """Обойма‑«луна»: 6 патронов по кругу на золотой звёздочке. Ось — y, пули смотрят в +y (вставляется в откинутый
    барабан со стороны рукояти); начало координат — ручка, за которую держит левая рука."""
    m = Mesh()
    for i in range(6):
        a = i * math.pi / 3
        cx, cz = math.cos(a) * 0.56 * DRUM_R, math.sin(a) * 0.56 * DRUM_R
        sub = Mesh()
        build_tube(sub, [0.70, 2.00], lambda y: (0.23, 0.23), R_BRASS, n=6, cap0=0.0, cap_reg=R_GOLD)       # гильза
        build_tube(sub, [2.00, 2.24, 2.45], lambda y: ((0.23, 0.23) if y < 2.1 else ((0.15, 0.15) if y < 2.3 else (0.05, 0.05))),
                   R_RED, n=6, cap1=0.0, cap_reg=R_RED)                                                    # пуля‑метка
        xform(sub, np.eye(3), (cx, 0, cz), m)
    build_disc(m, 0.52, 0.70, 0.80, 0.80, 12, R_GOLD, R_GOLD)
    build_tube(m, [-0.20, 0.52], lambda y: (0.20, 0.20), R_GUN, n=6, cap0=0.0, cap_reg=R_GUN)               # ручка
    return m


CLEAVER = build_cleaver()
DRUM = build_drum()
LOADER = build_loader()


def make_icon():
    """Иконка 32×32: широкий клинок по диагонали (золотая кромка снизу‑справа), полосы, барабан, рукоять, кольцо."""
    S = 4                                                   # рисуем крупно и уменьшаем «по большинству»
    IK = 1.40                                               # px иконки на px модели
    big = Image.new("RGBA", (32 * S, 32 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)

    def M(x, y):
        """Точка модели (x — кромка, y — к острию) → иконка: тесак идёт из левого нижнего угла в правый верхний."""
        k = IK * S
        u = (y + 7.5) * k * 0.7071
        return (1.3 * S + u + x * k * 0.7071, 30.7 * S - u + x * k * 0.7071)
    ss = np.linspace(0, 1, 40)
    edge = [edge_pt(s) for s in ss]
    spine = [spine_pt(s) for s in ss]
    d.polygon([M(p[0], p[1]) for p in spine] + [M(p[0], p[1]) for p in edge[::-1]], fill=PAL['body0'] + (255,))
    inner = [edge_pt(s) - nrm(edge_pt(s) - spine_pt(s)) * min(0.62, 0.42 * np.linalg.norm(edge_pt(s) - spine_pt(s))) for s in ss]
    d.polygon([M(p[0], p[1]) for p in inner] + [M(p[0], p[1]) for p in edge[::-1]], fill=PAL['gold1'] + (255,))
    for (y0, ln, hw) in STRIPES:
        xs = spine_x(y0)
        d.polygon([M(xs, y0 - hw), M(xs, y0 + hw), M(xs + ln * 0.8, y0 + 0.75 * ln * 0.8)], fill=PAL['gold0'] + (255,))
    d.line([M(spine_x(y) - 0.3, y) for y in np.linspace(RECV_Y1, PIPE_Y1, 12)], fill=PAL['gun0'] + (255,), width=int(0.75 * S * IK))
    for y in VENTS_Y:
        t, _ = vent_pos(y)
        d.line([M(spine_x(y) - 0.3, y - 0.2), M(t[0], t[1])], fill=PAL['gold0'] + (255,), width=int(0.8 * S))
    d.polygon([M(-0.8, RECV_Y0), M(1.9, RECV_Y0), M(1.34, RECV_Y1), M(-0.8, RECV_Y1)], fill=PAL['gun1'] + (255,))
    d.polygon([M(-2.8, RECV_Y0), M(1.9, RECV_Y0), M(1.9, RECV_Y0 + 0.5), M(-2.8, RECV_Y0 + 0.5)], fill=PAL['gold0'] + (255,))
    c = M(DRUM_C[0], DRUM_C[1])
    rr = DRUM_R * IK * S
    d.ellipse([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], fill=PAL['gun0'] + (255,))
    d.ellipse([c[0] - rr * 0.45, c[1] - rr * 0.45, c[0] + rr * 0.45, c[1] + rr * 0.45], fill=PAL['gold0'] + (255,))
    d.line([M(0, HANDLE_Y0), M(0, HANDLE_Y1)], fill=PAL['cord0'] + (255,), width=int(1.25 * S * IK))
    for y in np.linspace(HANDLE_Y0 + 0.8, HANDLE_Y1 - 0.8, 4):
        p = M(0, y)
        d.ellipse([p[0] - 0.4 * S, p[1] - 0.4 * S, p[0] + 0.4 * S, p[1] + 0.4 * S], fill=PAL['gold0'] + (255,))
    c = M(0, RING_Y)
    rr = RING_R * IK * S
    d.ellipse([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], outline=PAL['gold1'] + (255,), width=int(0.6 * S))
    # уменьшить: цвет — самый частый непрозрачный в клетке 4×4 (чёткие пиксели, без размытия)
    a = np.asarray(big)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    po = out.load()
    for y in range(32):
        for x in range(32):
            cell = a[y * S:(y + 1) * S, x * S:(x + 1) * S].reshape(-1, 4)
            op = cell[cell[:, 3] > 0]
            if len(op) >= S * S * 0.45:
                vals, cnt = np.unique(op, axis=0, return_counts=True)
                po[x, y] = tuple(int(v) for v in vals[cnt.argmax()])
    src = out.copy()
    ps = src.load()
    for y in range(32):                                     # тёмная обводка
        for x in range(32):
            if ps[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and ps[x + dx, y + dy][3]
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = (26, 10, 10, 255)
    return out


def export_mesh():
    data = {"texture": [TEX_W, TEX_H],
            "parts": {"cleaver": mesh_json(CLEAVER), "drum": mesh_json(DRUM), "loader": mesh_json(LOADER)}}
    path = os.path.join(ASSETS, "mesh", NAME + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    print("→", os.path.relpath(path), "%.0f КБ" % (os.path.getsize(path) / 1024))


# =====================================================================
# Превью модели
# =====================================================================
def drum_matrix(open_=0.0, spin=0.0):
    """Барабан в системе тесака: откинут вбок (к +z и чуть назад), повёрнут вокруг своей оси. → (R, t).
    Та же формула — в Java CleaverRender.drum."""
    sw = open_ * math.radians(78)                           # поворот рамки вокруг оси у задней стойки
    hinge = v3(DRUM_C[0] - 0.92, DRUM_C[1], 0.0)
    Rh = fv.rot_axis((0, 1, 0), -sw)
    c = hinge + Rh @ (v3(DRUM_C) - hinge)
    return Rh @ fv.rot_axis((0, 1, 0), spin * 2 * math.pi), c


def weapon_batches(tex, tf, ntf, open_=0.0, spin=0.0, color=(1, 1, 1, 1)):
    Rd, td = drum_matrix(open_, spin)
    return [mesh_batch(CLEAVER, tex, tf, ntf, color),
            mesh_batch(DRUM, tex, lambda p: tf(Rd @ v3(p) + td), lambda n: ntf(Rd @ n), color)]


def model_preview(tex, glow):
    rend = fv.Renderer(1280, 720, fov=30)
    views = [("плоскость", fv.rot_axis((0, 0, 1), math.radians(-62)) @ fv.rot_axis((0, 1, 0), math.radians(18)), 0.0),
             ("с обуха", fv.rot_axis((0, 0, 1), math.radians(-62)) @ fv.rot_axis((0, 1, 0), math.radians(118)), 0.0),
             ("барабан откинут", fv.rot_axis((0, 0, 1), math.radians(-62)) @ fv.rot_axis((0, 1, 0), math.radians(205)), 1.0),
             ("накал", fv.rot_axis((0, 0, 1), math.radians(-62)) @ fv.rot_axis((0, 1, 0), math.radians(-25)), 0.0)]
    out = []
    for i, (label, R, op) in enumerate(views):
        c = v3(0, 6.5, 0)
        tf = lambda p, R=R: R @ ((v3(p) - c) / 16.0) + v3(0, 0, -2.3)
        ntf = lambda n, R=R: R @ n
        bs = weapon_batches(tex, tf, ntf, op, 0.08)
        if op > 0:
            Rd, td = drum_matrix(op, 0)
            lp = td + v3(0, -4.2, 0)
            bs.append(mesh_batch(LOADER, tex, lambda p, R=R: tf(v3(p) + lp), ntf))
        if label == "накал":
            for b in weapon_batches(glow, tf, ntf, op, 0.08):
                b.additive, b.lit = True, False
                bs.append(b)
        img = rend.render(bs, bg=False)
        ImageDraw.Draw(img).text((8, 6), label, fill=(255, 255, 0))
        out.append(img)
    fv.sheet(out, 2, scale=0.75).save(os.path.join(TOOLS, "cleaver_model.png"))


# =====================================================================
# 1‑е лицо: ключи → кадры
# =====================================================================
import limb_rig as LR  # noqa: E402

S1 = 0.94                    # масштаб тесака и рук от 1‑го лица (px → блоки: S1/16)
ARM_W = 0.74                 # толщина рук (доля от обычной)
ARM_LEN = 1.45               # руки от 1‑го лица длиннее настоящих (иначе кулак не дотянется до середины экрана)
FPS = 60
PX = S1 / 16.0
TIPD = TIP_Y * PX            # от кулака до острия, блоки
SHOULDER_R = v3(0.30, -0.42, 0.12)
SHOULDER_L = v3(-0.30, -0.42, 0.12)
POLE_R, POLE_L = v3(0.55, -1.0, 0.45), v3(-0.55, -1.0, 0.45)      # куда смотрят локти: вниз‑наружу‑назад
OUT_R, OUT_L = v3(1, 0, 0), v3(-1, 0, 0)
L_DOWN = (v3(-0.36, -0.95, -0.12), v3(0.1, 0.6, -0.8))           # свободная левая рука — за нижним краем экрана
GRIP2 = ((0.0, LEFT_GRIP, 0.0), (0.0, 1.0, 0.0), (1.0, 0.0, 0.0))  # левая на рукояти (точка, ось, костяшки — в системе тесака)
LOADER_GRIP = 0.15                                           # px от начала обоймы до середины кулака
SPRING_HZ, SPRING_Z = 8.5, 0.5                               # «запаздывание» клинка за кистью: частота и затухание


def blade_frame(B, E):
    """Матрица тесака (столбцы X — кромка, Y — к острию, Z — нормаль плоскости)."""
    Y = nrm(B)
    X = v3(E) - Y * np.dot(E, Y)
    if np.linalg.norm(X) < 1e-4:
        X = np.cross(Y, v3(0, 0, 1))
    X = nrm(X)
    return np.stack([X, Y, np.cross(X, Y)], 1)


def natural_edge(G, B, shoulder):
    """Естественный хват: костяшки (а с ними кромка) смотрят туда же, куда предплечье — от плеча к кулаку."""
    B = nrm(B)
    f = v3(G) - v3(shoulder)
    e = f - B * np.dot(f, B)
    if np.linalg.norm(e) < 1e-4:
        e = np.cross(B, v3(1, 0, 0))
    return nrm(e)


class K:
    """Ключ 1‑го лица. G — середина правого кулака; T — точка на продолжении клинка;
    E — куда смотрит кромка (не задана — естественный хват: туда же, куда предплечье; roll — доворот вокруг клинка, град);
    cut — от этого ключа до следующего кромка ведёт удар; hold — остановка (нулевая скорость);
    левая рука: Lw=(точка px, ось, костяшки) в системе тесака (держится за оружие и едет вместе с ним), либо
    L=(точка, ось «большого пальца») в пространстве камеры, либо ничего — опущена за край экрана;
    tw — поворот корпуса (град), cr — присед (блоки), cam — (наклон, поворот, крен) град, fov — множитель обзора,
    tr — след, gl — свечение зарядки, do — барабан откинут 0..1, ds — поворот барабана (обороты),
    fi — огонь на клинке 0..1, fl — вспышка сопел 0..1, pv — обойма в левой руке видна 0..1."""

    def __init__(self, t, G=None, T=None, E=None, roll=0.0, cut=False, hold=False, L=None, Lw=None, tw=0.0, cr=0.0,
                 cam=(0, 0, 0), fov=1.0, tr=0.0, gl=0.0, do=0.0, ds=0.0, fi=0.0, fl=0.0, pv=0.0):
        self.t = t
        self.G = None if G is None else v3(G)
        self.T = None if T is None else v3(T)
        self.E = None if E is None else v3(E)
        self.roll, self.cut, self.hold = roll, cut, hold
        self.L, self.Lw, self.tw, self.cr = L, Lw, tw, cr
        self.cam, self.fov, self.tr, self.gl = v3(cam), fov, tr, gl
        self.do, self.ds, self.fi, self.fl, self.pv = do, ds, fi, fl, pv


class Clip:
    def __init__(self, name, length, keys, loop=False, events=(), spring=None):
        self.name, self.length, self.keys, self.loop = name, length, keys, loop
        self.events = sorted(events)
        prev = None
        for k in self.keys:
            if k.G is None:
                k.G, k.T = prev.G, prev.T
            prev = k
        self.ts = [k.t for k in keys]
        self.holds = [k.hold for k in keys]
        assert abs(self.ts[-1] - length) < 1e-6, name
        assert all(self.ts[i] < self.ts[i + 1] for i in range(len(keys) - 1)), name
        for k in self.keys:
            B = nrm(k.T - k.G)
            if k.E is None:
                k.E = natural_edge(k.G, B, SHOULDER_R)
            k.E = nrm(k.E - B * np.dot(k.E, B))
            if k.roll:
                k.E = fv.rot_axis(B, math.radians(k.roll)) @ k.E
        self.spring = (not loop) if spring is None else spring
        self._sb = None

    def _h(self, vals, t):
        return hermite(self.ts, vals, self.holds, t, self.length if self.loop else None)

    def raw(self, t):
        if self.loop:
            t = t % self.length
        t = min(max(t, 0.0), self.length)
        ks = self.keys
        G = self._h([k.G for k in ks], t)
        T = self._h([k.T for k in ks], t)
        B = nrm(T - G)
        n = len(ks)
        i = max([k for k in range(n) if self.ts[k] <= t] or [0])
        i = min(i, n - 2)
        j = i + 1
        a = min(1.0, max(0.0, (t - self.ts[i]) / (self.ts[j] - self.ts[i])))
        sm = a * a * (3 - 2 * a)
        E = slerp(ks[i].E, ks[j].E, sm)
        sc = lambda f: float(self._h([np.atleast_1d(np.array(f(k), float)) for k in ks], t)[0])
        lin = lambda f: (1 - a) * f(ks[i]) + a * f(ks[j])
        cut_w = max(0.0, min(1.0, a / 0.08, (1 - a) / 0.12)) if ks[i].cut else 0.0
        return dict(G=G, B=B, E=E, i=i, j=j, a=a, sm=sm, cut=cut_w, t=t,
                    tw=sc(lambda k: k.tw), cr=sc(lambda k: k.cr),
                    cam=self._h([k.cam for k in ks], t), fov=sc(lambda k: k.fov),
                    tr=float(lin(lambda k: k.tr)), gl=float(lin(lambda k: k.gl)),
                    do=min(1.0, max(0.0, sc(lambda k: k.do))), ds=sc(lambda k: k.ds),
                    fi=float(lin(lambda k: k.fi)), fl=float(lin(lambda k: k.fl)), pv=float(lin(lambda k: k.pv)))

    def _sprung(self, t):
        """Клинок с инерцией: направление догоняет «заданное» пружиной — на махе отстаёт от кисти, на остановке
        проскакивает и возвращается. В начале и в конце клипа совпадает с заданным."""
        if not self.spring:
            return None
        dt = 1 / 240.0
        if self._sb is None:
            n = int(round(self.length / dt)) + 1
            w = 2 * math.pi * SPRING_HZ
            b = self.raw(0.0)["B"].copy()
            vel = v3(0, 0, 0)
            out = []
            for i in range(n):
                tt = min(self.length, i * dt)
                tg = self.raw(tt)["B"]
                acc = (tg - b) * (w * w) - vel * (2 * SPRING_Z * w)
                vel = vel + acc * dt
                b = nrm(b + vel * dt)
                vel = vel - b * np.dot(vel, b)
                end = min(1.0, max(0.0, (self.length - tt) / 0.15))      # к концу клипа — ровно в заданную позу
                out.append(nrm(tg + (b - tg) * (end * end * (3 - 2 * end))))
            self._sb = out
        x = min(max(t, 0.0), self.length) / dt
        i = min(int(x), len(self._sb) - 2)
        return nrm(self._sb[i] + (self._sb[i + 1] - self._sb[i]) * (x - i))

    def pose(self, t):
        r = self.raw(t)
        G, B, E = r["G"], r["B"], r["E"]
        if r["cut"] > 0:                                     # кромка — по ходу движения острия
            dt = 1 / 240
            p1 = self.raw(max(0, r["t"] - dt))
            p2 = self.raw(min(self.length, r["t"] + dt))
            v = (p2["G"] + p2["B"] * TIPD * 0.75) - (p1["G"] + p1["B"] * TIPD * 0.75)
            v = v - B * np.dot(v, B)
            if np.linalg.norm(v) > 1e-5:
                E = slerp(E, nrm(v), r["cut"])
        sb = self._sprung(r["t"])
        if sb is not None:
            B = sb
            r["B"] = B
        R = blade_frame(B, E)
        r["R"] = R
        rot = fv.rot_axis((0, 1, 0), math.radians(r["tw"]))
        piv = v3(0, 0, 0.15)
        r["AR"] = rot @ (SHOULDER_R - piv) + piv + v3(0, -r["cr"], 0)
        r["AL"] = rot @ (SHOULDER_L - piv) + piv + v3(0, -r["cr"], 0)
        AL = r["AL"]

        def left(k):
            """→ точка, ось инструмента в кулаке (большой палец), костяшки."""
            if k.Lw is not None:
                p = G + R @ (v3(k.Lw[0]) * PX)
                y = nrm(R @ v3(k.Lw[1]))
                x = nrm(R @ v3(k.Lw[2])) if len(k.Lw) > 2 and k.Lw[2] is not None else natural_edge(p, y, AL)
                return p, y, x
            p, y = (v3(k.L[0]), nrm(v3(k.L[1]))) if k.L is not None else (L_DOWN[0], nrm(L_DOWN[1]))
            return p, y, natural_edge(p, y, AL)
        (p0, y0, x0), (p1_, y1, x1) = left(self.keys[r["i"]]), left(self.keys[r["j"]])
        r["Lp"] = p0 + (p1_ - p0) * r["sm"]
        ly = slerp(y0, y1, r["sm"])
        lx = slerp(x0, x1, r["sm"])
        lx = nrm(lx - ly * np.dot(lx, ly))
        r["Lh"] = nrm(lx * LR.H0[0] + ly * LR.H0[1])         # ось левой кисти
        r["Lb"] = -np.cross(lx, ly)                          # тыльная сторона левой кисти
        # обойма в левом кулаке: ось — по «большому пальцу»
        r["PR"] = np.stack([lx, ly, np.cross(lx, ly)], 1)
        r["PP"] = r["Lp"] - ly * (LOADER_GRIP * PX)
        return r


CLIPS = {}


def clip(c):
    CLIPS[c.name] = c
    return c


def scr_ray(sx, sy):
    """Точка экрана (0..1 слева‑направо, сверху‑вниз; 16:9, поле зрения руки 70°) → луч из глаза."""
    k = math.tan(math.radians(35))
    return v3((sx - 0.5) * 2 * k * 16 / 9, -(sy - 0.5) * 2 * k, -1.0)


def P(t, G, B, E=None, **kw):
    """Ключ через направление клинка B (вместо точки T)."""
    G = v3(G)
    return K(t, G=G, T=G + nrm(v3(B)), E=E, **kw)


REACH_MAX = (ARM_LEN * (LR.LU + LR.LF) + LR.HW) * PX * 1.12


def reach_clamp(G):
    """Кулак не дальше, чем дотянется рука (иначе её пришлось бы растягивать)."""
    d = v3(G) - SHOULDER_R
    n = np.linalg.norm(d)
    if n < REACH_MAX * 0.42:
        return SHOULDER_R + d * (REACH_MAX * 0.42 / max(n, 1e-4))
    return v3(G) if n <= REACH_MAX else SHOULDER_R + d * (REACH_MAX / n)


def S(t, H, T, z, E=None, **kw):
    """Ключ по экрану: H — где кулак, T — где острие (точки экрана), z — глубина кулака (блоки).
    Острие — на луче T на расстоянии TIPD от кулака (дальнее пересечение; если луч не достаёт — ближайшая точка)."""
    G = reach_clamp(scr_ray(*H) * z)
    r = nrm(scr_ray(*T))
    b = np.dot(r, G)
    disc = b * b - (np.dot(G, G) - TIPD * TIPD)
    tip = r * (b + math.sqrt(max(disc, 0.0)))
    B = nrm(tip - G)
    return K(t, G=G, T=G + B, E=E, **kw)


# --- стойка: кулак у правого нижнего края, клинок вверх‑вперёд, кромкой ОТ СЕБЯ (вперёд), чуть довёрнут плоскостью к глазам ---
GUARD_H, GUARD_T, GUARD_Z, GUARD_ROLL = (0.770, 0.960), (0.640, 0.100), 0.56, 12.0


def guard(t, **kw):
    kw.setdefault("roll", GUARD_ROLL)
    return S(t, GUARD_H, GUARD_T, GUARD_Z, **kw)


# конечные позы шагов серии (следующий удар начинается оттуда)
def low_left(t, **kw):       # после 1‑го: клинок внизу слева, остриём влево‑вниз
    return S(t, (0.38, 1.00), (0.03, 0.84), 0.50, **kw)


def high_right(t, **kw):     # после 2‑го: клинок справа, остриём вправо‑вверх
    return S(t, (0.84, 0.84), (1.00, 0.04), 0.55, **kw)


def drum_local(open_=1.0):
    return drum_matrix(open_, 0.0)[1]


def build_clips():
    CLIPS.clear()
    g = guard(0)
    gG, gB = g.G, nrm(g.T - g.G)
    GR = dict(roll=GUARD_ROLL)
    # ---------------- стойка: дыхание (петля 4 с) ----------------
    clip(Clip("idle", 4.0, [
        guard(0.0),
        P(1.0, gG + v3(0.002, 0.006, 0.0), gB + v3(0.010, 0.0, 0.004), cam=(-0.15, 0, 0), **GR),
        P(2.0, gG + v3(0.0, 0.010, 0.002), gB + v3(0.0, 0.0, 0.016), cam=(-0.25, 0, 0.1), **GR),
        P(3.0, gG + v3(-0.002, 0.004, 0.0), gB + v3(-0.008, 0.0, 0.004), cam=(-0.1, 0, 0), **GR),
        guard(4.0),
    ], loop=True))
    # ---------------- достать: снизу, клинок по дуге встаёт в стойку ----------------
    clip(Clip("draw", 0.60, [
        S(0.00, (0.92, 1.40), (1.30, 1.20), 0.50, hold=True),
        S(0.16, (0.86, 1.10), (1.06, 0.50), 0.56, tr=0.2),
        S(0.32, (0.78, 0.92), (0.70, 0.02), 0.58, tr=0.3, cam=(0.4, 0.2, 0.6), roll=14),
        guard(0.60, hold=True),
    ], events=[(0.0, "draw")]))
    # ---------------- удар 1: наискось справа‑сверху вниз‑влево, одной рукой ----------------
    clip(Clip("n1", 0.90, [
        guard(0.00),
        S(0.13, (0.84, 0.80), (0.94, -0.40), 0.55, tw=6, cam=(-0.5, 0.6, 0.8)),
        S(0.25, (0.87, 0.60), (1.14, -0.90), 0.52, tw=13, cr=-0.01, cam=(-1.2, 1.4, 1.8), fov=0.98),
        S(0.30, (0.76, 0.56), (0.74, -0.30), 0.56, cut=True, tr=0.8, tw=8, cam=(-0.6, 0.9, 0.8), fov=1.0),
        S(0.34, (0.58, 0.72), (0.34, 0.20), 0.58, cut=True, tr=1.0, tw=-3, cr=0.01, cam=(0.7, -0.3, -1.4), fov=1.03),
        S(0.38, (0.44, 0.92), (0.08, 0.62), 0.54, cut=True, tr=1.0, tw=-12, cr=0.03, cam=(1.6, -1.1, -2.8), fov=1.04),
        S(0.46, (0.36, 1.02), (-0.03, 0.90), 0.50, tr=0.3, tw=-17, cr=0.04, cam=(1.9, -1.4, -3.2), fov=1.02, hold=True),
        low_left(0.62, tw=-16, cr=0.03, cam=(1.2, -0.8, -2.0)),
        guard(0.90, hold=True),
    ], events=[(0.04, "swing"), (0.34, "hit")]))
    # ---------------- удар 2: наотмашь слева направо, горизонтально ----------------
    clip(Clip("n2", 0.90, [
        low_left(0.00, tw=-16),
        S(0.15, (0.26, 0.88), (-0.14, 0.60), 0.46, tw=-21, cam=(0.2, -1.5, -1.5), fov=0.98),
        S(0.22, (0.30, 0.84), (-0.06, 0.48), 0.48, cut=True, tr=0.8, tw=-17, cam=(0.2, -1.1, -1.0)),
        S(0.26, (0.48, 0.80), (0.36, 0.34), 0.56, cut=True, tr=1.0, tw=-4, cam=(0.2, 0.2, 0.6), fov=1.03),
        S(0.30, (0.70, 0.80), (0.86, 0.34), 0.58, cut=True, tr=1.0, tw=9, cam=(0.3, 1.3, 2.4), fov=1.04),
        S(0.38, (0.86, 0.84), (1.16, 0.26), 0.56, tr=0.3, tw=17, cam=(0.3, 1.9, 3.2), fov=1.02, hold=True),
        high_right(0.56, tw=16, cam=(0.2, 1.1, 1.8)),
        guard(0.90, hold=True),
    ], events=[(0.06, "swing"), (0.27, "hit")]))
    # ---------------- удар 3: двумя руками сверху вниз, клинок врубается в землю ----------------
    over = dict(Lw=GRIP2)
    clip(Clip("n3", 1.15, [
        high_right(0.00, tw=16),
        S(0.16, (0.80, 0.66), (0.80, -0.80), 0.57, tw=8, cr=-0.02, cam=(-1.6, 0.4, 0.6), **over),
        S(0.31, (0.74, 0.44), (0.76, -1.80), 0.55, tw=4, cr=-0.05, cam=(-3.2, 0.2, 0.2), fov=0.97, hold=True, **over),
        S(0.355, (0.64, 0.52), (0.58, -0.40), 0.58, cut=True, tr=1.0, cr=0.0, cam=(-1.0, 0.0, 0.0), fov=1.02, **over),
        S(0.395, (0.58, 0.82), (0.53, 0.28), 0.58, cut=True, tr=1.0, cr=0.06, cam=(2.6, -0.1, -0.2), fov=1.05, **over),
        S(0.435, (0.56, 1.00), (0.52, 0.64), 0.54, tr=0.6, cr=0.10, cam=(4.6, -0.2, -0.4), fov=1.05, **over),
        S(0.48, (0.56, 1.05), (0.515, 0.74), 0.50, cr=0.11, cam=(3.4, -0.2, -0.3), fov=1.03, hold=True, **over),
        S(0.74, (0.56, 1.04), (0.515, 0.735), 0.50, cr=0.09, cam=(2.6, -0.1, -0.2), **over),
        S(0.94, (0.70, 1.02), (0.58, 0.36), 0.57, cr=0.03, cam=(1.0, 0, 0), roll=14),
        guard(1.15, hold=True),
    ], events=[(0.14, "swing_heavy"), (0.41, "hit"), (0.45, "thud")], spring=False))
    # =============== взрывные версии: выстрел из сопел разгоняет клинок ===============
    # ---------------- взрыв 1: широкий мах справа налево, клинок проносит по кругу ----------------
    clip(Clip("b1", 0.95, [
        guard(0.00),
        S(0.12, (0.84, 0.84), (1.20, 0.34), 0.58, tw=14, cam=(-0.2, 1.4, 1.4), fov=0.98),
        S(0.21, (0.86, 0.80), (1.30, 0.36), 0.58, tw=19, cam=(-0.3, 1.9, 1.9), fov=0.96, fi=0.3, hold=True),
        S(0.23, (0.90, 0.78), (1.30, 0.42), 0.58, cut=True, tr=1.0, tw=14, cam=(-0.2, 1.2, 1.0), fov=1.00, fi=1.0, fl=1.0),
        S(0.265, (0.60, 0.80), (0.60, 0.34), 0.66, cut=True, tr=1.0, tw=0, cam=(0.3, -1.0, -2.2), fov=1.10, fi=1.0, fl=0.8),
        S(0.30, (0.26, 0.84), (-0.10, 0.44), 0.62, cut=True, tr=1.0, tw=-16, cam=(0.6, -2.6, -4.6), fov=1.12, fi=1.0, fl=0.3),
        S(0.34, (0.06, 0.96), (-0.40, 0.78), 0.56, tr=0.7, tw=-22, cam=(0.8, -3.2, -5.2), fov=1.08, fi=0.8),
        S(0.42, (0.02, 1.08), (-0.42, 1.10), 0.52, tr=0.2, tw=-24, cam=(0.8, -2.8, -4.4), fov=1.04, fi=0.5, hold=True),
        low_left(0.60, tw=-18, cam=(0.9, -1.6, -2.6), fi=0.25),
        low_left(0.68, tw=-16, cam=(0.8, -1.0, -1.8), fi=0.1),
        guard(0.95, hold=True),
    ], events=[(0.04, "swing_heavy"), (0.22, "shot"), (0.27, "hit")]))
    # ---------------- взрыв 2: восходящий разрез снизу‑слева вверх‑вправо, отдача поднимает клинок ----------------
    clip(Clip("b2", 0.95, [
        low_left(0.00, tw=-16),
        S(0.10, (0.22, 1.20), (-0.20, 1.20), 0.54, (0.3, -0.85, -0.4), tw=-20, cr=0.05, cam=(1.6, -1.2, -1.6), fov=0.98),
        S(0.19, (0.20, 1.22), (-0.24, 1.24), 0.53, (0.3, -0.85, -0.4), tw=-22, cr=0.07, cam=(2.0, -1.4, -1.8), fov=0.96, fi=0.3, hold=True),
        S(0.21, (0.28, 1.12), (0.02, 0.96), 0.58, cut=True, tr=1.0, tw=-16, cr=0.05, cam=(1.4, -0.8, -1.0), fi=1.0, fl=1.0),
        S(0.245, (0.50, 0.80), (0.46, 0.38), 0.64, cut=True, tr=1.0, tw=-2, cr=0.0, cam=(-0.8, 0.4, 1.0), fov=1.08, fi=1.0, fl=0.8),
        S(0.28, (0.72, 0.52), (0.84, 0.04), 0.60, cut=True, tr=1.0, tw=10, cr=-0.04, cam=(-2.8, 1.4, 3.0), fov=1.10, fi=1.0, fl=0.3),
        S(0.32, (0.84, 0.40), (1.08, -0.12), 0.56, tr=0.6, tw=15, cr=-0.05, cam=(-3.6, 1.8, 3.8), fov=1.07, fi=0.8),
        S(0.42, (0.90, 0.46), (1.22, -0.70), 0.54, tr=0.1, tw=17, cr=-0.04, cam=(-3.0, 1.6, 3.2), fov=1.03, fi=0.5, hold=True),
        high_right(0.62, tw=16, cam=(-0.6, 1.2, 2.0), fi=0.2),
        high_right(0.70, tw=16, cam=(0.0, 1.0, 1.6)),
        guard(0.95, hold=True),
    ], events=[(0.04, "swing_heavy"), (0.20, "shot"), (0.25, "hit")]))
    # ---------------- взрыв 3: сверху двумя руками в землю, выстрел в момент удара — взрыв ----------------
    clip(Clip("b3", 1.45, [
        high_right(0.00, tw=16),
        S(0.16, (0.80, 0.64), (0.80, -0.80), 0.57, tw=8, cr=-0.03, cam=(-2.0, 0.4, 0.6), **over),
        S(0.33, (0.74, 0.40), (0.76, -1.80), 0.55, tw=4, cr=-0.07, cam=(-4.2, 0.2, 0.2), fov=0.95, fi=0.4, hold=True, **over),
        S(0.37, (0.64, 0.50), (0.58, -0.40), 0.58, cut=True, tr=1.0, cr=0.0, cam=(-1.4, 0, 0), fov=1.04, fi=1.0, **over),
        S(0.405, (0.58, 0.82), (0.53, 0.28), 0.58, cut=True, tr=1.0, cr=0.07, cam=(3.0, -0.1, -0.2), fov=1.10, fi=1.0, **over),
        S(0.44, (0.56, 1.00), (0.52, 0.64), 0.54, tr=0.8, cr=0.13, cam=(6.0, -0.2, -0.4), fov=1.12, fi=1.0, fl=1.0, **over),
        S(0.48, (0.56, 1.06), (0.515, 0.75), 0.50, cr=0.15, cam=(4.2, 0.3, 0.6), fov=1.08, fi=1.0, fl=0.9, hold=True, **over),
        S(0.56, (0.56, 1.05), (0.515, 0.745), 0.50, cr=0.14, cam=(5.0, -0.3, -0.5), fov=1.05, fi=0.9, fl=0.3, **over),
        S(0.94, (0.56, 1.045), (0.515, 0.74), 0.50, cr=0.12, cam=(3.2, -0.1, -0.2), fi=0.5, **over),
        S(1.10, (0.62, 1.00), (0.44, 0.40), 0.57, cr=0.06, cam=(1.6, 0.4, 0.8), tr=0.3, fi=0.3, **over),
        S(1.24, (0.74, 1.00), (0.58, 0.20), 0.57, cr=0.02, cam=(0.6, 0.2, 0.3), fi=0.1, roll=14),
        guard(1.45, hold=True),
    ], events=[(0.14, "swing_heavy"), (0.42, "hit"), (0.44, "shot"), (0.445, "smash")], spring=False))
    # ---------------- пустой барабан: щелчок курка ----------------
    clip(Clip("dry", 0.30, [
        guard(0.00),
        P(0.05, gG + v3(0.004, -0.012, 0.012), gB + v3(0.02, 0.0, 0.03), cam=(0.5, 0, 0.3), **GR),
        P(0.14, gG + v3(0.0, 0.004, -0.004), gB, cam=(-0.1, 0, 0), **GR),
        guard(0.30, hold=True),
    ], events=[(0.02, "dry")]))
    # ---------------- перезарядка: откинуть барабан, гильзы вон, обойма, защёлкнуть рывком кисти, крутануть ----------------
    # тесак поперёк: клинок вправо‑вверх, кромкой вниз, плоскостью (и откинутым барабаном) к глазам
    RH, RT, RZ, RE = (0.43, 0.82), (0.93, 0.34), 0.47, (0.45, -0.88, 0.12)
    dc = drum_local(1.0)
    LX = None                                                                    # костяшки левой — по предплечью
    L_in = ((dc[0], dc[1] - 1.42, dc[2]), (0, 1, 0), LX)                         # обойма вошла в барабан
    L_near = ((dc[0] - 0.1, dc[1] - 4.3, dc[2] + 0.5), (0, 1, 0), LX)            # обойма под барабаном
    L_far = (v3(-0.42, -0.62, -0.34), v3(0.55, 0.75, -0.35))                     # левая рука несёт обойму снизу
    L_out = (v3(-0.46, -0.86, -0.22), v3(0.2, 0.6, -0.75))
    rk = dict(tw=-10, cr=0.02)
    clip(Clip("reload", 1.80, [
        guard(0.00),
        S(0.16, (0.50, 0.90), (0.84, 0.16), 0.52, (-0.2, -0.6, -0.75), tw=-6, cam=(1.0, -0.8, -0.8)),
        S(0.26, RH, RT, RZ, RE, cam=(1.8, -1.4, -1.2), do=0.0, hold=True, **rk),
        S(0.34, (0.432, 0.824), (0.932, 0.345), RZ, RE, cam=(1.9, -1.5, -1.3), do=1.0, **rk),
        # встряхнуть остриём вверх — гильзы высыпаются
        S(0.44, (0.46, 0.94), (0.80, 0.02), 0.49, (0.75, -0.6, 0.25), cam=(0.8, -1.2, -0.4), do=1.0, **rk),
        S(0.52, (0.46, 0.84), (0.78, 0.00), 0.49, (0.75, -0.6, 0.25), cam=(1.8, -1.4, -1.0), do=1.0, hold=True, **rk),
        S(0.66, RH, RT, RZ, RE, cam=(2.0, -1.5, -1.3), do=1.0, L=L_far, pv=1.0, **rk),
        S(0.84, RH, RT, RZ, RE, cam=(2.2, -1.6, -1.4), do=1.0, Lw=L_near, pv=1.0, hold=True, **rk),
        S(0.95, (0.435, 0.812), (0.935, 0.332), RZ, RE, cam=(2.4, -1.6, -1.4), do=1.0, Lw=L_in, pv=1.0, hold=True, **rk),
        S(1.03, RH, RT, RZ, RE, cam=(2.2, -1.6, -1.4), do=1.0, Lw=L_in, pv=1.0, **rk),
        S(1.15, RH, RT, RZ, RE, cam=(2.0, -1.5, -1.3), do=1.0, L=L_out, pv=0.0, **rk),
        # рывок кисти — барабан захлопывается
        S(1.25, (0.42, 0.84), (0.94, 0.40), 0.47, RE, cam=(1.8, -1.8, -1.8), do=1.0, hold=True, **rk),
        S(1.32, (0.50, 0.80), (0.92, 0.20), 0.50, (0.2, -0.7, -0.6), tw=-4, cam=(1.0, 0.4, 1.2), do=0.0, tr=0.3),
        S(1.42, (0.56, 0.86), (0.86, 0.10), 0.52, (-0.2, -0.6, -0.75), tw=-2, cam=(0.8, 0.2, 0.6), ds=0.0, hold=True),
        S(1.62, (0.70, 0.94), (0.70, 0.10), 0.56, tw=0, cam=(0.3, 0.1, 0.2), ds=2.0, roll=14),
        guard(1.80, ds=2.0, hold=True),
    ], events=[(0.0, "rustle"), (0.27, "drum_open"), (0.46, "shells"), (0.94, "drum_load"), (1.30, "drum_close"), (1.43, "spin")],
        spring=False))
    # =============== особая 1 «Тигриный выпад»: тесак у бедра остриём вперёд, левая рука вперёд → укол на рывке, взрыв ===============
    TH, TT, TZ = (0.72, 0.99), (0.515, 0.57), 0.34                                # у бедра, остриё к прицелу
    TE = (0.25, -0.95, 0.15)
    palm = (v3(-0.20, -0.22, -0.66), v3(0.35, 0.9, 0.1))                          # левая рука выставлена вперёд
    palm2 = (v3(-0.21, -0.21, -0.68), v3(0.35, 0.9, 0.1))
    clip(Clip("charge1", 1.00, [
        guard(0.00),
        S(0.18, (0.76, 1.02), (0.56, 0.50), 0.42, tw=10, cr=0.03, cam=(0.8, 0.6, 0.6)),
        S(0.36, TH, TT, TZ, TE, tw=16, cr=0.07, cam=(1.4, 1.0, 1.0), L=palm, hold=True),
        S(0.80, (0.725, 0.992), (0.515, 0.57), 0.335, TE, tw=18, cr=0.09, cam=(1.8, 1.1, 1.1), fov=0.96, L=palm2, gl=0.5, fi=0.25, ds=0.5),
        S(1.00, (0.73, 0.995), (0.515, 0.57), 0.33, TE, tw=19, cr=0.10, cam=(2.0, 1.2, 1.2), fov=0.94, L=palm, gl=1.0, fi=0.5, ds=1.0, hold=True),
    ], events=[(0.0, "charge"), (0.34, "rev"), (1.0, "ready")], spring=False))
    clip(Clip("charged1", 0.60, [
        S(0.00, (0.73, 0.995), (0.515, 0.57), 0.33, TE, tw=19, cr=0.10, cam=(2.0, 1.2, 1.2), fov=0.94, L=palm, gl=1.0, fi=0.5),
        S(0.15, (0.732, 0.999), (0.517, 0.572), 0.328, TE, tw=19, cr=0.104, cam=(2.05, 1.2, 1.25), fov=0.94, L=palm2, gl=0.75, fi=0.6),
        S(0.30, (0.729, 0.994), (0.514, 0.569), 0.33, TE, tw=19, cr=0.10, cam=(2.0, 1.25, 1.2), fov=0.94, L=palm, gl=1.0, fi=0.5),
        S(0.45, (0.731, 0.998), (0.516, 0.571), 0.329, TE, tw=19, cr=0.098, cam=(2.0, 1.2, 1.18), fov=0.94, L=palm2, gl=0.8, fi=0.6),
        S(0.60, (0.73, 0.995), (0.515, 0.57), 0.33, TE, tw=19, cr=0.10, cam=(2.0, 1.2, 1.2), fov=0.94, L=palm, gl=1.0, fi=0.5),
    ], loop=True))
    fistL = (v3(-0.34, -0.66, -0.26), v3(-0.2, 0.7, -0.6))                        # левая отведена назад
    clip(Clip("special1", 1.30, [
        S(0.00, (0.73, 0.995), (0.515, 0.57), 0.33, TE, tw=19, cr=0.10, cam=(2.0, 1.2, 1.2), fov=0.94, L=palm, gl=1.0, fi=0.6),
        # выстрел назад из сопел — рывок, рука с тесаком выбрасывается вперёд
        S(0.04, (0.66, 0.92), (0.51, 0.55), 0.48, TE, tw=6, cr=0.08, cam=(0.6, 0.4, 0.2), fov=1.14, L=fistL, gl=0.8, fi=1.0, fl=1.0, tr=0.6),
        S(0.10, (0.60, 0.80), (0.505, 0.525), 0.74, TE, tw=-14, cr=0.05, cam=(-0.6, -0.8, -1.2), fov=1.26, L=fistL, gl=0.5, fi=1.0, fl=0.6, tr=1.0),
        S(0.22, (0.595, 0.79), (0.505, 0.52), 0.78, TE, tw=-18, cr=0.04, cam=(-0.8, -1.0, -1.4), fov=1.24, L=fistL, fi=0.9, fl=0.2, tr=0.8, hold=True),
        S(0.30, (0.595, 0.795), (0.505, 0.522), 0.77, TE, tw=-18, cr=0.04, cam=(-0.7, -1.0, -1.3), fov=1.12, L=fistL, fi=0.9),
        # второй патрон — взрыв на острие, отдача задирает клинок
        S(0.34, (0.62, 0.76), (0.50, 0.30), 0.70, tw=-12, cr=0.02, cam=(-3.4, -0.4, -0.6), fov=1.16, L=fistL, fi=1.0, fl=1.0, tr=0.5),
        S(0.42, (0.70, 0.70), (0.58, -0.10), 0.60, tw=-2, cr=0.0, cam=(-4.6, 0.4, 0.8), fov=1.08, fi=0.8, fl=0.3, hold=True),
        S(0.62, (0.74, 0.78), (0.62, 0.00), 0.58, tw=2, cam=(-2.0, 0.4, 0.8), fi=0.5),
        # стряхнуть жар: короткий мах вниз‑вправо
        S(0.74, (0.80, 0.92), (1.10, 0.86), 0.58, cut=True, tr=0.7, tw=8, cam=(1.4, 0.8, 1.8), fi=0.3),
        S(0.82, (0.82, 0.98), (1.14, 1.00), 0.57, tw=9, cam=(1.6, 0.8, 1.9), fi=0.2, hold=True),
        S(1.02, (0.82, 0.98), (1.10, 0.90), 0.58, tw=8, cam=(1.0, 0.5, 1.2), fi=0.1),
        guard(1.30, hold=True),
    ], events=[(0.0, "shot"), (0.0, "dash"), (0.33, "shot"), (0.33, "blast"), (0.72, "flick")]))
    # =============== особая 2 «Тесак в узел»: барабан раскручен, клинок в огне → три взрывных разреза и удар в землю ===============
    CH2, CT2, CZ2 = (0.60, 1.02), (0.50, 0.02), 0.50                               # тесак вертикально перед собой, двумя руками
    clip(Clip("charge2", 1.20, [
        guard(0.00),
        S(0.22, (0.62, 1.03), (0.52, 0.06), 0.54, tw=-4, cr=0.02, cam=(0.6, -0.4, -0.4), Lw=GRIP2, roll=40),
        S(0.40, CH2, CT2, CZ2, tw=-6, cr=0.05, cam=(1.0, -0.6, -0.6), Lw=GRIP2, roll=60, hold=True),
        S(0.90, (0.60, 1.025), (0.50, 0.025), 0.495, tw=-7, cr=0.07, cam=(1.4, -0.6, -0.7), fov=0.95, Lw=GRIP2, roll=60, gl=0.5, fi=0.6, ds=2.0),
        S(1.20, (0.60, 1.03), (0.50, 0.03), 0.49, tw=-8, cr=0.08, cam=(1.6, -0.6, -0.8), fov=0.92, Lw=GRIP2, roll=60, gl=1.0, fi=1.0, ds=5.0, hold=True),
    ], events=[(0.0, "charge"), (0.38, "rev"), (0.80, "ignite"), (1.2, "ready")], spring=False))
    c2 = dict(tw=-8, fov=0.92, Lw=GRIP2, roll=60)
    clip(Clip("charged2", 0.60, [
        S(0.00, (0.60, 1.03), (0.50, 0.03), 0.49, cr=0.08, cam=(1.6, -0.6, -0.8), gl=1.0, fi=1.0, ds=0.0, **c2),
        S(0.15, (0.602, 1.034), (0.502, 0.033), 0.488, cr=0.084, cam=(1.65, -0.6, -0.85), gl=0.8, fi=0.85, ds=0.5, **c2),
        S(0.30, (0.599, 1.029), (0.499, 0.029), 0.49, cr=0.08, cam=(1.6, -0.65, -0.8), gl=1.0, fi=1.0, ds=1.0, **c2),
        S(0.45, (0.601, 1.033), (0.501, 0.032), 0.489, cr=0.078, cam=(1.6, -0.6, -0.78), gl=0.85, fi=0.9, ds=1.5, **c2),
        S(0.60, (0.60, 1.03), (0.50, 0.03), 0.49, cr=0.08, cam=(1.6, -0.6, -0.8), gl=1.0, fi=1.0, ds=2.0, **c2),
    ], loop=True))
    F = dict(Lw=GRIP2, fi=1.0)
    clip(Clip("special2", 2.00, [
        S(0.00, (0.60, 1.03), (0.50, 0.03), 0.49, tw=-8, cr=0.08, cam=(1.6, -0.6, -0.8), fov=0.92, gl=1.0, roll=60, **F),
        # 1: справа налево
        S(0.07, (0.90, 0.78), (1.32, 0.42), 0.57, tw=16, cr=0.04, cam=(-0.2, 1.8, 1.8), fov=0.98, gl=0.8, hold=True, **F),
        S(0.10, (0.86, 0.76), (1.20, 0.40), 0.58, cut=True, tr=1.0, tw=12, cam=(-0.1, 1.0, 0.8), fov=1.04, fl=1.0, **F),
        S(0.13, (0.56, 0.78), (0.52, 0.32), 0.66, cut=True, tr=1.0, tw=-2, cam=(0.3, -1.2, -2.4), fov=1.12, fl=0.7, **F),
        S(0.16, (0.24, 0.84), (-0.14, 0.46), 0.62, cut=True, tr=1.0, tw=-16, cam=(0.6, -2.8, -4.8), fov=1.12, fl=0.2, **F),
        S(0.22, (0.08, 0.98), (-0.38, 0.92), 0.56, tr=0.4, tw=-22, cam=(0.9, -3.0, -4.6), fov=1.06, hold=True, **F),
        # 2: снизу‑слева вверх‑вправо
        S(0.30, (0.20, 1.18), (-0.22, 1.20), 0.54, (0.3, -0.85, -0.4), tw=-22, cr=0.06, cam=(1.8, -1.6, -2.0), fov=0.98, hold=True, **F),
        S(0.33, (0.28, 1.10), (0.02, 0.94), 0.58, cut=True, tr=1.0, tw=-16, cr=0.04, cam=(1.2, -0.8, -1.0), fl=1.0, **F),
        S(0.36, (0.50, 0.80), (0.46, 0.38), 0.64, cut=True, tr=1.0, tw=-2, cam=(-0.8, 0.4, 1.0), fov=1.10, fl=0.7, **F),
        S(0.39, (0.72, 0.52), (0.84, 0.04), 0.60, cut=True, tr=1.0, tw=10, cr=-0.03, cam=(-2.8, 1.4, 3.0), fov=1.12, fl=0.2, **F),
        S(0.45, (0.90, 0.46), (1.22, -0.70), 0.54, tr=0.4, tw=16, cr=-0.04, cam=(-3.2, 1.8, 3.4), fov=1.06, hold=True, **F),
        # 3: сверху‑справа вниз‑влево
        S(0.53, (0.90, 0.48), (1.22, -0.80), 0.54, tw=14, cam=(-1.8, 1.6, 2.4), fov=0.99, hold=True, **F),
        S(0.56, (0.66, 0.58), (0.52, 0.06), 0.60, cut=True, tr=1.0, tw=2, cam=(0.2, 0.2, -0.4), fov=1.06, fl=1.0, **F),
        S(0.59, (0.44, 0.90), (0.12, 0.52), 0.60, cut=True, tr=1.0, tw=-10, cam=(1.6, -1.2, -3.0), fov=1.12, fl=0.7, **F),
        S(0.62, (0.33, 1.12), (-0.06, 0.92), 0.57, tr=0.6, tw=-14, cam=(2.0, -1.4, -3.4), fov=1.08, fl=0.2, **F),
        low_left(0.70, tw=-16, cam=(1.6, -1.2, -2.6), hold=True, **F),
        # 4: подскок, тесак над головой — и в землю; огненная волна
        S(0.86, (0.80, 0.62), (0.80, -0.80), 0.57, tw=6, cr=-0.08, cam=(-3.4, 0.2, 0.2), fov=0.98, **F),
        S(1.00, (0.74, 0.38), (0.76, -1.80), 0.55, tw=2, cr=-0.12, cam=(-5.4, 0.0, 0.0), fov=0.93, gl=1.0, hold=True, **F),
        S(1.03, (0.64, 0.50), (0.58, -0.40), 0.58, cut=True, tr=1.0, cr=-0.02, cam=(-2.0, 0, 0), fov=1.06, **F),
        S(1.06, (0.58, 0.82), (0.53, 0.28), 0.58, cut=True, tr=1.0, cr=0.08, cam=(3.4, -0.1, -0.2), fov=1.14, **F),
        S(1.09, (0.56, 1.00), (0.52, 0.64), 0.54, tr=0.8, cr=0.15, cam=(7.0, -0.2, -0.4), fov=1.18, fl=1.0, **F),
        S(1.12, (0.56, 1.06), (0.515, 0.75), 0.50, cr=0.18, cam=(5.0, 0.4, 0.8), fov=1.12, fl=0.9, hold=True, **F),
        S(1.20, (0.56, 1.05), (0.515, 0.745), 0.50, cr=0.17, cam=(6.0, -0.4, -0.6), fov=1.06, fl=0.3, **F),
        S(1.55, (0.56, 1.045), (0.515, 0.74), 0.50, cr=0.14, cam=(3.6, -0.1, -0.2), Lw=GRIP2, fi=0.6),
        S(1.72, (0.62, 1.00), (0.44, 0.40), 0.57, cr=0.07, cam=(1.8, 0.4, 0.8), tr=0.3, Lw=GRIP2, fi=0.4),
        S(1.85, (0.74, 1.00), (0.58, 0.20), 0.57, cr=0.02, cam=(0.6, 0.2, 0.3), fi=0.2, roll=14),
        guard(2.00, hold=True),
    ], events=[(0.02, "swing_heavy"), (0.10, "shot"), (0.13, "hit"), (0.33, "shot"), (0.36, "hit"), (0.56, "shot"), (0.59, "hit"),
               (0.84, "hop"), (0.90, "swing_heavy"), (1.08, "hit"), (1.09, "shot"), (1.095, "smash"), (1.10, "wave")], spring=False))


build_clips()


# =====================================================================
# Эффекты (превью — как в Java CleaverRender)
# =====================================================================
TRAIL_LEN = 0.12
TRAIL_N = 16
TRAIL_BASE, TRAIL_TIP = (0.55, 7.5), (0.75, TIP_Y - 0.9)      # лента следа: от середины кромки до острия (x, y в px)
COL_STEEL = (1.0, 0.90, 0.72)
COL_FIRE = (1.0, 0.50, 0.12)


def mixc(k):
    return tuple(COL_STEEL[i] + (COL_FIRE[i] - COL_STEEL[i]) * k for i in range(3))


def trail_batch(c, t, M):
    b = fv.Batch(None, additive=True, lit=False)
    pts = []
    for k in range(TRAIL_N + 1):
        tt = max(0.0, t - TRAIL_LEN * k / TRAIL_N)
        rr = c.pose(tt)
        pts.append((M @ (rr["G"] + rr["R"] @ (v3(TRAIL_BASE[0], TRAIL_BASE[1], 0) * PX)),
                    M @ (rr["G"] + rr["R"] @ (v3(TRAIL_TIP[0], TRAIL_TIP[1], 0) * PX)), rr["tr"], rr["fi"]))
    for k in range(TRAIL_N):
        (a0, a1, ia, fa_), (b0, b1, ib, fb_) = pts[k], pts[k + 1]
        fa = (1 - k / TRAIL_N) ** 1.5 * ia
        fb = (1 - (k + 1) / TRAIL_N) ** 1.5 * ib
        ca, cb = mixc(fa_), mixc(fb_)
        col = lambda f, cc, edge: (cc[0] * f, cc[1] * f, cc[2] * f, 0.6 if edge else 0.2)
        b.add([a0, a1, b1, b0], [(0, 0)] * 4, None, [col(fa, ca, False), col(fa, ca, True), col(fb, cb, True), col(fb, cb, False)])
    return b


def flare_batch(p, size, col, g):
    """Блик‑звезда в точке p (в камере), лицом к камере."""
    b = fv.Batch(None, additive=True, lit=False)
    b.nodepth = True
    for (ax, ay, w) in ((1, 0, 0.12), (0, 1, 0.12), (0.7, 0.7, 0.06), (0.7, -0.7, 0.06)):
        u = v3(ax, ay, 0) * size
        v = v3(-ay, ax, 0) * size * w
        c0, c1 = (col[0] * g, col[1] * g, col[2] * g, 0.9), (0.0, 0.0, 0.0, 0.0)
        b.add([p - u, p - v, p + u, p + v], [(0, 0)] * 4, None, [c1, c0, c1, c0])
    h, k = v3(1, 0, 0) * size * 0.4, v3(0, 1, 0) * size * 0.4
    c = (col[0] * g * 0.7, col[1] * g * 0.7, col[2] * g * 0.7, 0.6)
    b.add([p - h - k, p + h - k, p + h + k, p - h + k], [(0, 0)] * 4, None, [c] * 4)
    return b


def jet_batch(r, M, y, g):
    """Струя пламени из сопла: вытянутый крест из двух лент вдоль выхлопа."""
    b = fv.Batch(None, additive=True, lit=False)
    tip, d = vent_pos(y)
    G, R = r["G"], r["R"]
    p0 = M @ (G + R @ (tip * PX))
    dv = M @ (R @ d)
    ln = 0.55 * g
    for side in (M @ (R @ v3(0, 0, 1)), M @ (R @ nrm(np.cross(d, v3(0, 0, 1))))):
        w = side * 0.05 * (0.5 + g)
        hot, mid, off = (1.0 * g, 0.85 * g, 0.5 * g, 1.0), (1.0 * g, 0.45 * g, 0.08 * g, 0.9), (0, 0, 0, 0)
        b.add([p0 - w, p0 + w, p0 + dv * ln * 0.45 + w * 1.7, p0 + dv * ln * 0.45 - w * 1.7], [(0, 0)] * 4, None, [hot, hot, mid, mid])
        b.add([p0 + dv * ln * 0.45 - w * 1.7, p0 + dv * ln * 0.45 + w * 1.7, p0 + dv * ln + w * 0.3, p0 + dv * ln - w * 0.3],
              [(0, 0)] * 4, None, [mid, mid, off, off])
    return b


def fp_arms(r):
    """Решение обеих рук в пространстве камеры (без поворота камеры клипа)."""
    R = r["R"]
    right = LR.solve(r["AR"], r["G"], R @ LR.H0, POLE_R, PX, ARM_LEN, R[:, 1])
    left = LR.solve(r["AL"], r["Lp"], r["Lh"], POLE_L, PX, ARM_LEN, r["PR"][:, 1])
    return right, left


def arm_batch(skin, sol, bn, out, right, M, unit=None, wscale=None, lm=None):
    b = fv.Batch(skin)
    w, h = skin.size
    for V, UV in LR.mesh(sol, bn, out, PX if unit is None else unit, right, wscale=ARM_W if wscale is None else wscale,
                         lm=ARM_LEN if lm is None else lm):
        b.add([M @ p for p in V][::-1], [(u / w, v / h) for u, v in UV][::-1])
    return b


def fp_batches(c, t, tex, glow, skin, trail=True):
    r = c.pose(t)
    M = fv.cam_matrix(*r["cam"])
    G, R = r["G"], r["R"]
    tf = lambda p: M @ (G + R @ (v3(p) * PX))
    ntf = lambda n: M @ (R @ n)
    bs = weapon_batches(tex, tf, ntf, r["do"], r["ds"])
    heat = max(0.22, r["fi"], r["gl"] * 0.6)
    for b in weapon_batches(glow, tf, ntf, r["do"], r["ds"], color=(heat, heat, heat, 1)):
        b.additive, b.lit = True, False
        bs.append(b)
    if r["pv"] > 0.01:
        PP, PR = r["PP"], r["PR"]
        bs.append(mesh_batch(LOADER, tex, lambda p: M @ (PP + PR @ (v3(p) * PX)), lambda n: M @ (PR @ n)))
    right, left = fp_arms(r)
    bs.append(arm_batch(skin, right, R[:, 2], OUT_R, True, M))
    bs.append(arm_batch(skin, left, r["Lb"], OUT_L, False, M))
    if trail and r["tr"] > 0.01:
        bs.append(trail_batch(c, t, M))
    if r["fl"] > 0.02:
        for y in VENTS_Y:
            bs.append(jet_batch(r, M, y, r["fl"]))
        p = M @ (G + R @ (vent_pos(VENTS_Y[1])[0] * PX))
        bs.append(flare_batch(p, 0.20 * r["fl"] * -p[2], (1.0, 0.7, 0.3), r["fl"]))
    if r["gl"] > 0.02:
        p = M @ (G + R @ (v3(0.4, RECV_Y1 + 0.5, 0) * PX))
        bs.append(flare_batch(p, 0.12 * r["gl"] * -p[2], (1.0, 0.75, 0.35), r["gl"]))
    return bs, r, M


def render_fp(c, times, tex, glow, skin, size=(640, 360)):
    rend = fv.Renderer(*size)
    out = []
    for t in times:
        bs, r, M = fp_batches(c, t, tex, glow, skin)
        img = rend.render(bs, cam=M)
        d = ImageDraw.Draw(img)
        d.line((size[0] / 2 - 6, size[1] / 2, size[0] / 2 + 6, size[1] / 2), fill=(255, 255, 255))
        d.line((size[0] / 2, size[1] / 2 - 6, size[0] / 2, size[1] / 2 + 6), fill=(255, 255, 255))
        out.append(img)
    return out, []


def check_fp():
    """Кромка ведёт удар (угол между кромкой и движением острия < 35°); запястье не выломано; рука не растянута."""
    bad = 0
    for name, c in CLIPS.items():
        n = int(c.length * FPS)
        worst_w, worst_k = (0, 0), (1, 0)
        for i in range(1, n):
            t = i / FPS
            r = c.pose(t)
            if r["cut"] > 0.9 and not c.spring:
                p1, p2 = c.pose(t - 1 / 240), c.pose(t + 1 / 240)
                v = (p2["G"] + p2["B"] * TIPD) - (p1["G"] + p1["B"] * TIPD)
                v = v - r["R"][:, 1] * np.dot(v, r["R"][:, 1])
                if np.linalg.norm(v) * 120 > 1.0:
                    cosang = np.dot(nrm(v), r["R"][:, 0])
                    if cosang < math.cos(math.radians(35)):
                        print("  %s %.3f: кромка не по ходу удара (%.0f°)" % (name, t, math.degrees(math.acos(max(-1, min(1, cosang))))))
                        bad += 1
            # запястье: насколько естественная ось кисти расходится с предплечьем (до подгонки)
            R = r["R"]
            h = R @ LR.H0
            W = r["G"] - h * (LR.HW * PX)
            f = nrm(W - r["AR"])
            ang = math.degrees(math.acos(max(-1, min(1, float(np.dot(f, h))))))
            if ang > worst_w[0]:
                worst_w = (ang, t)
            right, left = fp_arms(r)
            if right["k"] > worst_k[0]:
                worst_k = (right["k"], t)
        if worst_w[0] > 100 or worst_k[0] > 1.3:
            print("  %s: запястье до %.0f° (%.2f с), рука растянута ×%.2f (%.2f с)" % (name, worst_w[0], worst_w[1], worst_k[0], worst_k[1]))
            bad += 1
    print("проверка 1‑го лица:", "ок" if bad == 0 else "%d замечаний" % bad)


# =====================================================================
# Запекание 1‑го лица → assets/basedefense/fpanim/cleaver.json (читает Java client/weapon/fp/FpAnim)
# Кадр: G(3) q(4) Lp(3) Lh(3) AR(3) AL(3) cam(3) fov tr gl | pv обойма P(3) Q(4) | do ds fi fl Lb(3) — 40 чисел
# (первые 33 — как у катаны, но AR/AL — плечевые суставы, Lh — ось левой кисти; дальше 7 добавочных каналов:
#  барабан, поворот барабана, огонь, вспышка, тыльная сторона левой кисти)
# =====================================================================
CHANNELS = 40


def bake_frame(c, t):
    r = c.pose(t)
    out = list(r["G"]) + list(frame_q(r["R"])) + list(r["Lp"]) + list(nrm(r["Lh"])) + list(r["AR"]) + list(r["AL"])
    out += list(r["cam"]) + [r["fov"], r["tr"], r["gl"]]
    out += [r["pv"]] + list(r["PP"]) + list(frame_q(r["PR"]))
    out += [r["do"], r["ds"], r["fi"], r["fl"]] + list(nrm(r["Lb"]))
    assert len(out) == CHANNELS
    return [round(float(x), 4) for x in out]


def export_fp():
    data = {"fps": FPS, "scale": S1, "armWidth": ARM_W, "leftGrip": LEFT_GRIP,
            "trail": {"base": list(TRAIL_BASE), "tip": list(TRAIL_TIP), "length": TRAIL_LEN},
            "flare": [0.4, RECV_Y1 + 0.5, 0.0], "clips": {}}
    for name, c in CLIPS.items():
        n = int(round(c.length * FPS))
        frames = [bake_frame(c, min(c.length, i / FPS)) for i in range(n + 1)]
        data["clips"][name] = {"length": c.length, "loop": c.loop,
                               "events": [[round(t, 3), e] for t, e in c.events], "frames": frames}
    path = os.path.join(ASSETS, "fpanim", NAME + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    print("→", os.path.relpath(path), "%.0f КБ" % (os.path.getsize(path) / 1024))


def gif_clip(name, tex, glow, skin, path, size=(480, 270)):
    c = CLIPS[name]
    n = int(c.length * 30)
    frames, _ = render_fp(c, [i / 30 for i in range(n + 1)], tex, glow, skin, size=size)
    frames += [frames[-1]] * 6
    fv.gif(frames, path, fps=30)


# =====================================================================
# 3‑е лицо (px модели игрока от шеи, y вниз, вперёд −z, +x — левая сторона игрока). Как в Java CleaverClient.applyModel:
# кадр {t (тики), хват G, клинок B, нормаль N, левая кисть L, наклон, поворот, нога L, нога R, левая свободна 0..1}
# Бёдра сами опускаются, когда ноги расставлены (выпад, удар в землю) — ступни остаются на земле.
# =====================================================================
import bt_sword_anim as A  # noqa: E402

SCALE_3P = 0.85
BL = TIP_Y * SCALE_3P        # px от хвата до острия


class TK:
    """Ключ 3‑го лица: E — кромка (нулевая и cut — по ходу удара); L=None — левая на рукояти (двуручный хват),
    lf — левая рука свободна (висит и качается как обычно); hold — остановка."""

    def __init__(self, t, G, B, E=(0, 0, 0), L=None, lean=0.0, tw=0.0, ll=0.0, lr=0.0, lf=0.0, cut=False, hold=False):
        self.t, self.G, self.B, self.E, self.L = t, v3(G), nrm(v3(B)), v3(E), L
        self.lean, self.tw, self.ll, self.lr, self.lf, self.cut, self.hold = lean, tw, ll, lr, lf, cut, hold


def perp(B, E):
    B = nrm(v3(B))
    E = v3(E)
    return nrm(E - B * np.dot(E, B))


# покой: рука опущена, тесак остриём вперёд‑вниз, почти волочится — вальяжно
IDLE = dict(G=(-6.6, 11.2, -1.2), B=(0.05, 0.50, -0.86), E=(0.0, 0.86, 0.50), lf=1.0)
# боевая стойка (после удара): тесак перед собой остриём вверх‑вперёд, левая ладонь вперёд
GUARD3 = dict(G=(-6.0, 6.5, -6.5), B=(0.22, -0.76, -0.61), E=(0.0, 0.62, -0.78), L=(5.0, 5.5, -7.0), lean=0.06, ll=-0.15, lr=0.12)
LOW_LEFT = dict(G=(5.0, 10.5, -4.0), B=(0.65, 0.70, -0.30), E=(0.3, -0.5, 0.8), L=(8.0, 7.0, 3.5), lean=0.16, tw=-0.38, ll=-0.35, lr=0.30)
HIGH_RIGHT = dict(G=(-9.0, 4.5, -3.0), B=(-0.93, -0.25, 0.27), E=(-0.25, 0.1, -0.96), L=(6.0, 6.0, -5.0), lean=0.0, tw=0.42, ll=-0.25, lr=0.30)
SMASH = dict(G=(-1.0, 9.5, -8.0), B=(0.0, 0.74, -0.67), E=(0.0, 0.67, 0.74), lean=0.42, ll=-0.72, lr=0.50)
UP = dict(G=(-2.0, -7.0, -1.5), B=(0.0, -0.62, 0.78), E=(0.0, -0.78, -0.62), lean=-0.18, ll=-0.10, lr=0.10)
CHARGE1 = dict(G=(-7.0, 9.3, 1.5), B=(0.13, -0.05, -0.99), E=(0.0, 1.0, -0.05), L=(4.5, 3.5, -8.5), lean=0.22, tw=0.45, ll=-0.50, lr=0.50)
CHARGE2 = dict(G=(-1.5, 6.0, -8.5), B=(0.05, -0.97, -0.24), E=(0.0, 0.24, -0.97), lean=0.10, ll=-0.30, lr=0.30)


def tk(t, base, **kw):
    d = dict(base)
    d.update(kw)
    return TK(t, **d)


def tp_clips():
    """Имя → ключи. Времена — тики; совпадают с клипами 1‑го лица (секунды × 20)."""
    c = {}
    c["n1"] = [tk(0, GUARD3),
               TK(2.0, (-8.5, -1.5, -1.0), (-0.20, -0.62, 0.76), (0.0, -0.77, -0.63), L=(5, 6, -6), lean=-0.05, tw=0.38, ll=-0.2, lr=0.2),
               TK(3.4, (-8.5, -2.5, -0.5), (-0.25, -0.60, 0.76), (0.0, -0.77, -0.63), L=(5, 6, -6), lean=-0.06, tw=0.42, ll=-0.2, lr=0.2, hold=True),
               TK(4.4, (-2.0, 4.0, -9.5), (0.50, -0.35, -0.79), L=(7, 7, -1), lean=0.10, tw=0.05, ll=-0.3, lr=0.25, cut=True),
               TK(5.4, (4.0, 9.0, -6.0), (0.78, 0.45, -0.43), L=(8, 7, 3), lean=0.17, tw=-0.32, ll=-0.35, lr=0.3, cut=True),
               tk(7.2, LOW_LEFT, hold=True), tk(9.6, LOW_LEFT), tk(14, GUARD3)]
    c["n2"] = [tk(0, LOW_LEFT),
               TK(1.8, (3.5, 6.0, -4.5), (0.90, 0.12, 0.42), (0.1, -0.2, -0.97), L=(8, 6, 1), lean=0.10, tw=-0.48, ll=-0.3, lr=0.3, hold=True),
               TK(2.9, (-1.5, 5.0, -9.5), (0.35, -0.05, -0.94), L=(7, 6, -3), lean=0.08, tw=-0.05, ll=-0.3, lr=0.3, cut=True),
               TK(3.9, (-7.0, 4.5, -7.0), (-0.72, -0.12, -0.68), L=(6, 6, -5), lean=0.04, tw=0.30, ll=-0.25, lr=0.3, cut=True),
               tk(5.6, HIGH_RIGHT, hold=True), tk(9.2, HIGH_RIGHT), tk(14, GUARD3)]
    c["n3"] = [tk(0, HIGH_RIGHT),
               tk(2.6, UP, tw=0.10), tk(4.4, UP, G=(-2.0, -7.5, -1.0), hold=True),
               TK(5.4, (-1.0, -3.0, -9.0), (0.0, -0.85, -0.53), lean=0.05, ll=-0.3, lr=0.3, cut=True),
               TK(6.2, (-1.0, 6.0, -9.5), (0.0, 0.35, -0.94), lean=0.30, ll=-0.6, lr=0.45, cut=True),
               tk(7.4, SMASH, hold=True), tk(12.4, SMASH),
               TK(16.0, (-4.0, 8.0, -7.0), (0.15, -0.30, -0.94), (0.0, 0.95, -0.3), lean=0.18, ll=-0.3, lr=0.25),
               tk(20, GUARD3)]
    c["b1"] = [tk(0, GUARD3),
               TK(1.8, (-9.0, 5.0, -2.0), (-0.85, -0.20, 0.49), (-0.5, 0.1, -0.86), L=(5, 6, -6), lean=0.02, tw=0.60, ll=-0.25, lr=0.3),
               TK(3.0, (-9.2, 5.0, -1.5), (-0.82, -0.20, 0.54), (-0.5, 0.1, -0.86), L=(5, 6, -6), lean=0.02, tw=0.66, ll=-0.25, lr=0.3, hold=True),
               TK(3.5, (-6.0, 5.0, -8.0), (-0.20, -0.10, -0.97), L=(7, 6, -3), lean=0.08, tw=0.30, ll=-0.3, lr=0.3, cut=True),
               TK(4.1, (0.0, 5.5, -9.5), (0.60, 0.0, -0.80), L=(8, 6, 0), lean=0.12, tw=-0.10, ll=-0.35, lr=0.3, cut=True),
               TK(4.8, (6.0, 6.5, -6.0), (0.95, 0.10, 0.30), L=(8, 7, 3), lean=0.14, tw=-0.60, ll=-0.4, lr=0.3, cut=True),
               TK(6.8, (7.0, 8.0, -2.5), (0.62, 0.30, 0.72), (0.6, -0.2, -0.77), L=(8, 7, 4), lean=0.15, tw=-0.78, ll=-0.4, lr=0.3, hold=True),
               tk(10.4, LOW_LEFT), tk(11.6, LOW_LEFT), tk(16, GUARD3)]
    c["b2"] = [tk(0, LOW_LEFT),
               TK(1.6, (5.0, 12.0, -2.0), (0.50, 0.80, 0.33), (0.3, -0.5, 0.8), L=(8, 7, 3), lean=0.26, tw=-0.50, ll=-0.55, lr=0.45),
               TK(2.8, (5.0, 12.2, -1.8), (0.48, 0.81, 0.34), (0.3, -0.5, 0.8), L=(8, 7, 3), lean=0.28, tw=-0.52, ll=-0.6, lr=0.48, hold=True),
               TK(3.3, (1.0, 8.0, -8.5), (0.30, 0.50, -0.81), L=(8, 7, 1), lean=0.18, tw=-0.25, ll=-0.45, lr=0.4, cut=True),
               TK(3.9, (-3.0, 2.0, -9.5), (-0.10, -0.50, -0.86), L=(7, 7, -2), lean=0.02, tw=0.05, ll=-0.3, lr=0.3, cut=True),
               TK(4.6, (-7.0, -4.0, -6.0), (-0.30, -0.90, -0.30), L=(6, 7, -4), lean=-0.12, tw=0.28, ll=-0.15, lr=0.2, cut=True),
               TK(6.8, (-8.0, -6.0, -2.0), (-0.35, -0.85, 0.40), (-0.3, -0.3, -0.9), L=(6, 7, -4), lean=-0.16, tw=0.36, ll=-0.1, lr=0.15, hold=True),
               tk(10.4, HIGH_RIGHT), tk(12, HIGH_RIGHT), tk(16, GUARD3)]
    c["b3"] = [tk(0, HIGH_RIGHT),
               tk(2.6, UP, tw=0.10, lean=-0.22), tk(4.8, UP, G=(-2.0, -8.0, -1.0), lean=-0.24, hold=True),
               TK(5.5, (-1.0, -3.0, -9.0), (0.0, -0.85, -0.53), lean=0.05, ll=-0.3, lr=0.3, cut=True),
               TK(6.1, (-1.0, 6.0, -9.5), (0.0, 0.35, -0.94), lean=0.32, ll=-0.65, lr=0.48, cut=True),
               tk(6.8, SMASH, lean=0.46, hold=True), tk(16.0, SMASH, lean=0.44),
               TK(19.2, (-2.0, 8.0, -8.0), (0.30, 0.10, -0.95), (0.0, 0.95, 0.1), lean=0.26, ll=-0.45, lr=0.35),
               TK(22.0, (-5.0, 7.0, -7.0), (0.20, -0.60, -0.77), (0.0, 0.8, -0.6), L=(5, 6, -6), lean=0.12, ll=-0.25, lr=0.2),
               tk(26, GUARD3)]
    back = (8.0, 7.5, 4.0)
    c["special1"] = [tk(0, CHARGE1),
                     TK(1.0, (-3.5, 5.5, -9.0), (0.10, -0.05, -0.99), (0.0, 1.0, -0.05), L=back, lean=0.34, tw=-0.25, ll=-0.75, lr=0.62),
                     TK(2.0, (-2.5, 4.5, -9.5), (0.08, -0.04, -0.99), (0.0, 1.0, -0.05), L=back, lean=0.38, tw=-0.34, ll=-0.80, lr=0.66, hold=True),
                     TK(6.0, (-2.5, 4.6, -9.5), (0.08, -0.04, -0.99), (0.0, 1.0, -0.05), L=back, lean=0.36, tw=-0.34, ll=-0.80, lr=0.66),
                     TK(6.8, (-4.0, 1.0, -8.5), (0.0, -0.60, -0.80), (0.0, 0.8, -0.6), L=back, lean=0.16, tw=-0.20, ll=-0.5, lr=0.45),
                     TK(8.4, (-6.5, -1.5, -6.0), (-0.10, -0.95, -0.30), (0.0, 0.3, -0.95), L=(7, 7, 1), lean=0.0, tw=0.0, ll=-0.3, lr=0.3, hold=True),
                     TK(12.4, (-6.5, -1.0, -6.0), (-0.10, -0.94, -0.32), (0.0, 0.3, -0.95), L=(7, 7, 0), lean=0.02, tw=0.02, ll=-0.25, lr=0.25),
                     TK(14.8, (-8.0, 7.0, -4.0), (-0.60, 0.50, -0.62), L=(6, 7, -2), lean=0.08, tw=0.20, ll=-0.2, lr=0.2, cut=True),
                     TK(16.4, (-8.0, 8.0, -3.5), (-0.55, 0.65, -0.52), (-0.3, 0.4, 0.86), L=(6, 7, -2), lean=0.08, tw=0.22, ll=-0.2, lr=0.2, hold=True),
                     TK(20.4, (-8.0, 8.0, -3.5), (-0.55, 0.65, -0.52), (-0.3, 0.4, 0.86), L=(6, 7, -3), lean=0.07, tw=0.18, ll=-0.2, lr=0.2),
                     tk(26, GUARD3)]
    WR = dict(G=(-9.0, 5.0, -2.0), B=(-0.85, -0.20, 0.49), E=(-0.5, 0.1, -0.86), lean=0.02, tw=0.60, ll=-0.3, lr=0.3)
    LL2 = dict(G=(5.0, 11.5, -2.5), B=(0.52, 0.78, 0.35), E=(0.3, -0.5, 0.8), lean=0.24, tw=-0.50, ll=-0.5, lr=0.42)
    HR2 = dict(G=(-8.0, -5.5, -2.0), B=(-0.35, -0.85, 0.40), E=(-0.3, -0.3, -0.9), lean=-0.14, tw=0.36, ll=-0.15, lr=0.2)
    c["special2"] = [tk(0, CHARGE2),
                     tk(1.4, WR, hold=True),
                     TK(2.0, (-6.0, 5.0, -8.0), (-0.20, -0.10, -0.97), lean=0.08, tw=0.30, ll=-0.3, lr=0.3, cut=True),
                     TK(2.6, (0.0, 5.5, -9.5), (0.60, 0.0, -0.80), lean=0.12, tw=-0.10, ll=-0.35, lr=0.3, cut=True),
                     TK(3.2, (6.0, 6.5, -6.0), (0.95, 0.10, 0.30), lean=0.14, tw=-0.60, ll=-0.4, lr=0.3, cut=True),
                     tk(6.0, LL2, hold=True),
                     TK(6.6, (1.0, 8.0, -8.5), (0.30, 0.50, -0.81), lean=0.18, tw=-0.25, ll=-0.45, lr=0.4, cut=True),
                     TK(7.2, (-3.0, 2.0, -9.5), (-0.10, -0.50, -0.86), lean=0.02, tw=0.05, ll=-0.3, lr=0.3, cut=True),
                     TK(7.8, (-7.0, -4.0, -6.0), (-0.30, -0.90, -0.30), lean=-0.12, tw=0.28, ll=-0.15, lr=0.2, cut=True),
                     tk(10.6, HR2, hold=True),
                     TK(11.2, (-4.0, 0.0, -9.0), (0.25, -0.70, -0.67), lean=0.04, tw=0.12, ll=-0.3, lr=0.3, cut=True),
                     TK(11.8, (1.0, 6.0, -9.0), (0.65, 0.20, -0.73), lean=0.14, tw=-0.15, ll=-0.35, lr=0.3, cut=True),
                     TK(12.4, (4.5, 10.0, -5.0), (0.70, 0.62, -0.35), lean=0.18, tw=-0.35, ll=-0.4, lr=0.3, cut=True),
                     tk(14.0, LOW_LEFT, L=None, hold=True),
                     tk(17.2, UP, tw=0.05, lean=-0.22), tk(20.0, UP, G=(-2.0, -8.0, -1.0), lean=-0.26, hold=True),
                     TK(20.6, (-1.0, -3.0, -9.0), (0.0, -0.85, -0.53), lean=0.05, ll=-0.3, lr=0.3, cut=True),
                     TK(21.2, (-1.0, 6.0, -9.5), (0.0, 0.35, -0.94), lean=0.32, ll=-0.65, lr=0.48, cut=True),
                     tk(21.8, SMASH, lean=0.48, ll=-0.8, lr=0.55, hold=True), tk(31.0, SMASH, lean=0.46),
                     TK(34.4, (-2.0, 8.0, -8.0), (0.30, 0.10, -0.95), (0.0, 0.95, 0.1), lean=0.26, ll=-0.45, lr=0.35),
                     TK(37.0, (-5.0, 7.0, -7.0), (0.20, -0.60, -0.77), (0.0, 0.8, -0.6), L=(5, 6, -6), lean=0.12, ll=-0.25, lr=0.2),
                     tk(40, GUARD3)]
    # перезарядка: тесак поперёк перед грудью (клинок вправо‑вверх от игрока), левая рука — к барабану
    RL = dict(G=(-1.0, 8.0, -8.0), B=(-0.80, -0.50, -0.33), E=(-0.30, 0.82, -0.49), lean=0.10, ll=-0.1, lr=0.1)
    drum3 = (-3.6, 5.2, -9.6)                       # барабан при этой позе
    c["reload"] = [tk(0, GUARD3),
                   tk(3.6, RL, L=(5, 8, -5)), tk(6.8, RL, L=(5, 8, -5), hold=True),
                   tk(8.8, RL, G=(-1.0, 8.5, -8.0), B=(-0.55, -0.80, -0.24), E=(-0.6, 0.55, -0.58), L=(5, 9, -5)),
                   tk(10.4, RL, G=(-1.0, 7.5, -8.0), B=(-0.55, -0.80, -0.24), E=(-0.6, 0.55, -0.58), L=(5, 9, -5), hold=True),
                   tk(13.2, RL, L=(4, 11, -5)), tk(16.8, RL, L=(0.5, 9.0, -8.5), hold=True),
                   tk(19.0, RL, L=drum3, hold=True), tk(20.6, RL, L=drum3),
                   tk(23.0, RL, L=(5, 10, -4)), tk(25.0, RL, L=(5, 10, -4), hold=True),
                   TK(26.4, (-3.0, 7.0, -8.0), (-0.45, -0.75, -0.48), (-0.2, 0.6, -0.77), L=(5, 9, -4), lean=0.08, tw=0.15, ll=-0.1, lr=0.1),
                   TK(28.4, (-4.0, 7.0, -7.5), (-0.20, -0.80, -0.56), (0.0, 0.6, -0.8), L=(5, 7, -6), lean=0.07, tw=0.10, ll=-0.1, lr=0.1, hold=True),
                   tk(36, GUARD3)]
    # времена ключей → под клипы 1‑го лица (замахи стали длиннее): {старый тик: новый}
    remap = {
        "n1": [(3.4, 5.0), (4.4, 6.8), (5.4, 7.6), (7.2, 9.2), (9.6, 12.4), (14, 18)],
        "n2": [(1.8, 3.0), (2.9, 5.2), (3.9, 6.0), (5.6, 7.6), (9.2, 11.2), (14, 18)],
        "n3": [(2.6, 3.2), (4.4, 6.2), (5.4, 7.1), (6.2, 7.9), (7.4, 9.6), (12.4, 14.8), (16, 18.8), (20, 23)],
        "b1": [(1.8, 2.4), (3.0, 4.2), (3.5, 4.6), (4.1, 5.3), (4.8, 6.0), (6.8, 8.4), (10.4, 12), (11.6, 13.6), (16, 19)],
        "b2": [(1.6, 2.0), (2.8, 3.8), (3.3, 4.2), (3.9, 4.9), (4.6, 5.6), (6.8, 8.4), (10.4, 12.4), (12, 14), (16, 19)],
        "b3": [(2.6, 3.2), (4.8, 6.6), (5.5, 7.4), (6.1, 8.1), (6.8, 9.6), (16, 18.8), (19.2, 22), (22, 24.8), (26, 29)],
    }
    for name, m in remap.items():
        xs, ys = [0.0] + [a for a, _ in m], [0.0] + [b for _, b in m]
        for k in c[name]:
            k.t = float(np.interp(k.t, xs, ys))
    return c


TP_ORDER = ["n1", "n2", "n3", "b1", "b2", "b3", "special1", "special2", "reload"]
TP_POSES = [("TP_IDLE", IDLE), ("TP_GUARD", GUARD3), ("TP_CHARGE1", CHARGE1), ("TP_CHARGE2", CHARGE2)]


SH_R, SH_L = v3(-6.0, 2.0, 0.0), v3(6.0, 2.0, 0.0)         # плечевые суставы (от шеи)
REACH_3P = LR.LU + LR.LF + LR.HW - 0.3
REACH_MIN_3P = 6.3
POLE3_R, POLE3_L = v3(-0.6, 0.55, 0.6), v3(0.6, 0.55, 0.6)   # локти: наружу‑вниз‑назад
HANG_L = v3(6.6, 11.3, -0.8)                               # свободная левая кисть


def reach3(G, sh=SH_R):
    d = v3(G) - sh
    n = np.linalg.norm(d)
    if n < REACH_MIN_3P:                                 # слишком близко к плечу — локоть сложился бы пополам
        return sh + d * (REACH_MIN_3P / max(n, 1e-4))
    return v3(G) if n <= REACH_3P else sh + d * (REACH_3P / n)


def tp_row(t, G, B, E, L, lean, tw, ll, lr, lf):
    """L=None — левая на рукояти (последнее число кадра = 1)."""
    B = nrm(B)
    G = reach3(G)
    if E is None or np.linalg.norm(E) < 1e-6:
        E = natural_edge(G, B, SH_R)
    E = perp(B, E)
    N = np.cross(E, B)
    lg = 1.0 if L is None else 0.0
    if L is None:
        L = v3(G) - B * (-LEFT_GRIP * SCALE_3P)
    return [round(float(x), 4) for x in [t] + list(G) + list(B) + list(N) + list(L) + [lean, tw, ll, lr, lf, lg]]


def tp_static(d):
    d = dict(d)
    return tp_row(0.0, v3(d["G"]), v3(d["B"]), d.get("E"), d.get("L"), d.get("lean", 0.0), d.get("tw", 0.0),
                  d.get("ll", 0.0), d.get("lr", 0.0), d.get("lf", 0.0))


def tp_bake(keys):
    """Ключи → кадры каждый тик: Катмулл–Ром по G, острию, L; кромка — по ходу (cut), из ключа или естественная."""
    ts = [k.t for k in keys]
    holds = [k.hold for k in keys]
    for k in keys:
        k.G = reach3(k.G)
    tip = lambda k: k.G + k.B * BL
    grip2 = lambda k: k.G - k.B * (-LEFT_GRIP * SCALE_3P)
    Ls = [v3(k.L) if k.L is not None else grip2(k) for k in keys]
    Es = []
    for k in keys:
        e = k.E
        if np.linalg.norm(e) < 1e-6:
            e = natural_edge(k.G, k.B, SH_R)
        Es.append(perp(k.B, e))
    out = []
    for f in range(int(round(ts[-1])) + 1):
        t = float(f)
        G = hermite(ts, [k.G for k in keys], holds, t)
        T = hermite(ts, [tip(k) for k in keys], holds, t)
        L = hermite(ts, Ls, holds, t)
        B = nrm(T - G)
        i = min(max([k for k in range(len(ts)) if ts[k] <= t] or [0]), len(ts) - 2)
        j = i + 1
        a = min(1.0, max(0.0, (t - ts[i]) / (ts[j] - ts[i])))
        sm = a * a * (3 - 2 * a)
        ki, kj = keys[i], keys[j]
        E = slerp(Es[i], Es[j], sm)
        if ki.cut:
            v = hermite(ts, [tip(k) for k in keys], holds, t + 0.25) - hermite(ts, [tip(k) for k in keys], holds, t - 0.25)
            v = v - B * np.dot(v, B)
            w = max(0.0, min(1.0, a / 0.15, (1 - a) / 0.15))
            if np.linalg.norm(v) > 0.5:
                E = slerp(perp(B, E), nrm(v), w)
        two = ki.L is None and kj.L is None
        lin = lambda f_: f_(ki) + (f_(kj) - f_(ki)) * sm
        out.append(tp_row(t, G, B, E, None if two else L, lin(lambda k: k.lean), lin(lambda k: k.tw), lin(lambda k: k.ll),
                          lin(lambda k: k.lr), lin(lambda k: k.lf)))
    return out


HIP_DROP = 12.0              # длина ноги, px


def hip_drop(ll, lr):
    """На сколько опускаются бёдра, когда ноги расставлены (как в Java)."""
    a = (abs(ll) + abs(lr)) * 0.5
    return HIP_DROP * (1.0 - math.cos(a))


def tp_pose(v):
    """Как Java CleaverClient.applyModel: v = кадр без t. Руки — суставные (limb_rig)."""
    g, b, n, l = v3(v[0:3]), v3(v[3:6]), v3(v[6:9]), v3(v[9:12])
    lean, twist, leg_l, leg_r, lf, lg = v[12:18]
    drop = hip_drop(leg_l, leg_r)
    R = np.stack([v3(c) for c in A.body_R(lean, twist)], 1)
    hip = v3(0, 12 + drop, 0)
    neck = hip + R @ v3(0, -12, 0)
    jr, jl = neck + R @ SH_R, neck + R @ SH_L
    gk = neck + R @ g
    bw, nw = R @ b, R @ n
    z = nrm(nw - bw * np.dot(nw, bw))
    sw = np.stack([np.cross(bw, z), bw, z], 1)
    right = LR.solve(jr, gk, sw @ LR.H0, R @ POLE3_R, 1.0, 1.0, bw)
    if lf > 0.5:
        lk = neck + R @ HANG_L
        hl = nrm(lk - jl)
        bl, ty = R @ v3(1, 0, 0), None
    elif lg > 0.5:
        lk = neck + R @ l
        hl, bl, ty = sw @ LR.H0, -z, bw
    else:
        lk = neck + R @ l
        hl = nrm(lk - jl)
        bl, ty = R @ v3(1, 0, 0), None
    left = LR.solve(jl, lk, hl, R @ POLE3_L, 1.0, 1.0, ty)
    return dict(R=R, neck=neck, hip=hip, right=right, left=left, bn_r=z, bn_l=bl, out_r=R @ v3(-1, 0, 0), out_l=R @ v3(1, 0, 0),
                grip=gk, sword=sw, leg_l=leg_l, leg_r=leg_r)


def render_tp(frames, tex, skin, yaw=0.6, size=(260, 300)):
    rend = fv.Renderer(*size)
    rend.f = size[1] / 2 / math.tan(math.radians(15))
    Ry = fv.rot_axis((0, 1, 0), yaw)

    def W(p):
        p = v3(p)
        q = Ry @ (v3(p[0], -p[1], -p[2]) / 16.0)          # модель (y вниз, вперёд −z) → мир (y вверх, вперёд +z)
        return q + v3(0, 0.45, -5.6)
    out = []
    for v in frames:
        p = tp_pose(v[1:])
        bs = []

        def part(cubes, origin, Rm):
            b = fv.Batch(skin)
            tw_, th_ = skin.size
            for cb in cubes:
                for verts, u1, v1, u2, v2 in fv.sp.cuboid_quads(cb):
                    V = [W(v3(origin) + Rm @ v3(vv)) for vv in verts]
                    b.add(V, [(u2 / tw_, v1 / th_), (u1 / tw_, v1 / th_), (u1 / tw_, v2 / th_), (u2 / tw_, v2 / th_)])
            bs.append(b)
        part([(0, 0, -4, -8, -4, 8, 8, 8, 0)], p["neck"], np.eye(3))
        part([(16, 16, -4, 0, -2, 8, 12, 4, 0)], p["neck"], p["R"])
        for (u, vv, px_, ang) in ((0, 16, -1.9, p["leg_r"]), (16, 48, 1.9, p["leg_l"])):
            c_, s_ = math.cos(ang), math.sin(ang)
            part([(u, vv, -2, 0, -2, 4, 12, 4, 0)], [px_, p["hip"][1], 0], np.array([[1, 0, 0], [0, c_, -s_], [0, s_, c_]]))
        for sol, bn, outv, rgt in ((p["right"], p["bn_r"], p["out_r"], True), (p["left"], p["bn_l"], p["out_l"], False)):
            b = fv.Batch(skin)
            for V, UV in LR.mesh(sol, bn, outv, 1.0, rgt):
                b.add([W(q) for q in V][::-1], [(u / 64.0, w_ / 64.0) for u, w_ in UV][::-1])
            bs.append(b)
        gp, sw = p["grip"], p["sword"]
        flip = lambda n: Ry @ v3(n[0], -n[1], -n[2])
        bs += weapon_batches(tex, lambda q: W(gp + sw @ (v3(q) * SCALE_3P)), lambda n: flip(sw @ n))
        gb = fv.Batch(None, color=(0.25, 0.32, 0.22, 1), lit=False)
        gb.add([W((-16, 24, -22)), W((16, 24, -22)), W((16, 24, 12)), W((-16, 24, 12))], [(0, 0)] * 4)
        bs.append(gb)
        out.append(rend.render(bs, bg=False))
    return out


def arr(rows, ind="            "):
    return ",\n".join(ind + "{" + ", ".join("%.4ff" % x for x in r) + "}" for r in rows)


def write_keys_java():
    clips = tp_clips()
    src = ("package com.lesha_top1972.basedefense.client.weapon;\n\n"
           "/**\n * Позы тесака Громового тигра от 3‑го лица. СОЗДАНО tools/cleaver_gen.py — правь скрипт, а не этот файл.\n"
           " * Кадр: {t (тики), хват x,y,z, клинок x,y,z, нормаль x,y,z, левая кисть x,y,z, наклон, поворот, нога L, нога R,\n"
           " * левая рука свободна 0..1, левая на рукояти 0..1}. px модели игрока от шеи, y вниз, вперёд −z. Кадры — каждый тик.\n * Руки — суставные (fp/ArmRig): хват — точно в точке кадра.\n */\n"
           "final class CleaverKeys {\n\n"
           "    static final float SCALE_3P = %.3ff;\n" % SCALE_3P)
    src += "    /** Барабан: центр (px модели тесака), петля рамки по x, угол откидывания (рад). */\n"
    src += "    static final float[] DRUM = {%.3ff, %.3ff, %.3ff};\n" % DRUM_C
    src += "    static final float DRUM_HINGE = %.3ff;\n    static final float DRUM_SWING = %.4ff;\n" % (DRUM_C[0] - 0.92, math.radians(78))
    src += "    /** Срезы сопел: {x, y, z, направление x, y, z} (px модели тесака). */\n    static final float[][] VENTS = {\n"
    src += arr([list(vent_pos(y)[0]) + list(vent_pos(y)[1]) for y in VENTS_Y]) + "\n    };\n"
    src += "    /** Руки от 1‑го лица: множитель длины; подсказки локтям и «наружу» (пространство камеры). */\n"
    src += "    static final float FP_ARM_LEN = %.3ff;\n" % ARM_LEN
    src += "    static final float[] FP_POLE_R = {%.2ff, %.2ff, %.2ff};\n    static final float[] FP_POLE_L = {%.2ff, %.2ff, %.2ff};\n" % (tuple(POLE_R) + tuple(POLE_L))
    src += "    /** Руки от 3‑го лица (px от шеи): суставы плеч, подсказки локтям, свободная левая кисть. */\n"
    src += "    static final float[] SH_R = {%.2ff, %.2ff, %.2ff};\n    static final float[] SH_L = {%.2ff, %.2ff, %.2ff};\n" % (tuple(SH_R) + tuple(SH_L))
    src += "    static final float[] POLE_R = {%.2ff, %.2ff, %.2ff};\n    static final float[] POLE_L = {%.2ff, %.2ff, %.2ff};\n" % (tuple(POLE3_R) + tuple(POLE3_L))
    src += "    static final float[] HANG_L = {%.2ff, %.2ff, %.2ff};\n" % tuple(HANG_L)
    src += "    /** Белый участок текстуры (для следов и свечения). */\n"
    src += "    static final float WHITE_U = %.5ff;\n    static final float WHITE_V = %.5ff;\n\n" % WHITE_UV
    for name, d in TP_POSES:
        src += "    static final float[][] %s = {\n%s\n    };\n\n" % (name, arr([tp_static(d)]))
    def windows(keys):
        w = []
        for i, k in enumerate(keys[:-1]):
            if k.cut:
                a, b = k.t - 0.3, keys[i + 1].t + 1.2
                if w and a <= w[-1][1]:
                    w[-1][1] = b
                else:
                    w.append([a, b])
        return [x for ab in w for x in ab]
    src += "    /** Окна следа клинка по клипам (тики от начала): {от, до, …}. */\n    static final float[][] TRAIL = {\n"
    src += ",\n".join("            {" + ", ".join("%.1ff" % x for x in windows(clips[n])) + "}" for n in TP_ORDER) + "\n    };\n\n"
    src += "    /** Клипы: " + ", ".join("%d — %s" % (i, n) for i, n in enumerate(TP_ORDER)) + ". */\n"
    src += "    static final float[][][] TP_CLIPS = {\n" + ",\n".join(
        "        {\n" + arr(tp_bake(clips[n])) + "\n        }" for n in TP_ORDER) + "\n    };\n\n"
    src += "    private CleaverKeys() {\n    }\n}\n"
    path = os.path.join(JAVA_DIR, "CleaverKeys.java")
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("→", os.path.relpath(path))


def ev_ticks(name, ev):
    """Тики событий клипа 1‑го лица (секунды × 20, округление)."""
    return [int(round(t * 20)) for t, e in CLIPS[name].events if e == ev]


def write_timings_java():
    """Тайминги для сервера — из тех же клипов, что и анимации: удар всегда совпадает с кадром."""
    L = lambda n: int(round(CLIPS[n].length * 20))
    src = ("package com.lesha_top1972.basedefense.weapon;\n\n"
           "/**\n * Тайминги тесака Громового тигра (тики). СОЗДАНО tools/cleaver_gen.py из клипов анимации — правь скрипт.\n */\n"
           "public final class CleaverTimings {\n\n")
    src += "    /** Длина ударов серии: обычные и взрывные. */\n"
    src += "    public static final int[] N_LEN = {%s};\n" % ", ".join(str(L(n)) for n in ("n1", "n2", "n3"))
    src += "    public static final int[] B_LEN = {%s};\n" % ", ".join(str(L(n)) for n in ("b1", "b2", "b3"))
    src += "    /** Когда бьёт (тики от начала удара). */\n"
    src += "    public static final int[] N_HIT = {%s};\n" % ", ".join(str(ev_ticks(n, "hit")[0]) for n in ("n1", "n2", "n3"))
    src += "    public static final int[] B_HIT = {%s};\n" % ", ".join(str(ev_ticks(n, "hit")[0]) for n in ("b1", "b2", "b3"))
    src += "    /** «Тигриный выпад»: зарядка, длина, взрыв на острие. */\n"
    src += "    public static final int S1_CHARGE = %d;\n    public static final int S1_LEN = %d;\n    public static final int S1_BLAST = %d;\n" % (
        L("charge1"), L("special1"), ev_ticks("special1", "blast")[0])
    src += "    /** «Тесак в узел»: зарядка, длина, три разреза, удар в землю. */\n"
    src += "    public static final int S2_CHARGE = %d;\n    public static final int S2_LEN = %d;\n" % (L("charge2"), L("special2"))
    hits = ev_ticks("special2", "hit")
    src += "    public static final int[] S2_CUTS = {%s};\n    public static final int S2_SMASH = %d;\n    public static final int S2_HOP = %d;\n" % (
        ", ".join(str(h) for h in hits[:3]), ev_ticks("special2", "smash")[0], ev_ticks("special2", "hop")[0])
    src += "    /** Перезарядка: длина и момент, когда патроны в барабане. */\n"
    src += "    public static final int RELOAD_LEN = %d;\n    public static final int RELOAD_LOAD = %d;\n\n" % (L("reload"), ev_ticks("reload", "drum_load")[0])
    src += "    private CleaverTimings() {\n    }\n}\n"
    path = os.path.join(JAVA_MAIN, "CleaverTimings.java")
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("→", os.path.relpath(path))


def save_model_assets():
    tex, glow = paint()
    wdir = os.path.join(ASSETS, "textures", "entity", "weapon")
    tex.save(os.path.join(wdir, NAME + ".png"))
    glow.save(os.path.join(wdir, NAME + "_glow.png"))
    make_icon().save(os.path.join(ASSETS, "textures", "item", "tiger_cleaver.png"))
    export_mesh()
    return tex, glow


def main():
    tex, glow = save_model_assets()
    skin = fv.make_skin()
    export_fp()
    write_keys_java()
    write_timings_java()
    if "--tp" in sys.argv:
        clips = tp_clips()
        k = sys.argv.index("--tp")
        names = [sys.argv[k + 1]] if k + 1 < len(sys.argv) and sys.argv[k + 1] in clips else TP_ORDER
        yaw = float(os.environ.get("YAW", "0.6"))
        rows = []
        for n in names:
            fr = tp_bake(clips[n])
            cnt = 16 if len(names) == 1 else 8
            idx = sorted(set(int(round(x)) for x in np.linspace(0, len(fr) - 1, cnt)))
            rows += render_tp([fr[i] for i in idx], tex, skin, yaw=yaw)
            rows += [rows[-1]] * (cnt - len(idx))
        if len(names) > 1:
            rows = render_tp([tp_static(d) for _, d in TP_POSES], tex, skin, yaw=yaw) + [Image.new("RGB", (260, 300), (30, 30, 34))] * 4 + rows
        fv.sheet(rows, 8, scale=0.62 if len(names) > 1 else 1.0).save(os.path.join(TOOLS, "cleaver_tp.png"))
        print("превью → tools/cleaver_tp.png")
    if "--check" in sys.argv:
        check_fp()
    if "--fp" in sys.argv:
        name = sys.argv[sys.argv.index("--fp") + 1]
        c = CLIPS[name]
        nfr = int(os.environ.get("N", "12"))
        t0, t1 = float(os.environ.get("T0", "0")), float(os.environ.get("T1", str(c.length)))
        times = list(np.linspace(t0, t1, nfr))
        frames, warn = render_fp(c, times, tex, glow, skin)
        fv.sheet(frames, 4, label=["%.2f" % t for t in times]).save(os.path.join(TOOLS, "cleaver_fp.png"))
        if "--gif" in sys.argv:
            gif_clip(name, tex, glow, skin, os.path.join(TOOLS, "cleaver_%s.gif" % name))
        print("\n".join(warn) or "превью → tools/cleaver_fp.png")
    if "--model" in sys.argv:
        model_preview(tex, glow)
        make_icon().resize((256, 256), Image.NEAREST).save(os.path.join(TOOLS, "cleaver_icon.png"))
        print("превью → tools/cleaver_model.png, tools/cleaver_icon.png")


if __name__ == "__main__":
    main()
