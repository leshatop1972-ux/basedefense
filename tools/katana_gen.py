"""
Катана: модель (катана и ножны), текстура, иконка 16×16, позы трёх ударов и стойки «рука на рукояти у бедра».

    python tools/katana_gen.py            — geo/katana(.geo.json), geo/katana_saya, textures/entity/katana.png,
                                            textures/item/katana.png, client/weapon/KatanaKeys.java
    python tools/katana_gen.py --preview  — ещё и tools/mob_preview.png (3‑е лицо сбоку и спереди, 1‑е лицо)

Модель простая (без двойной детализации). Система меча та же, что у меча кровожада: начало — правая кисть
на рукояти, +y — клинок, +x — лезвие (режущая кромка), z — толщина. Ножны — в той же системе (клинок внутри).
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import Model, Painter, faces, mix, mul  # noqa: E402
import bt_sword_gen as G  # noqa: E402  (растеризатор превью)
import bt_sword_anim as A  # noqa: E402  (векторы, сплайн, руки)
import asset_groups  # noqa: E402  (папки мобов: geo/animations/textures)

ROOT = G.ROOT
JAVA = os.path.join(os.path.dirname(__file__), "..", "src", "client", "java", "com", "lesha_top1972", "basedefense",
                    "client", "weapon", "KatanaKeys.java")

SCALE_3P = 0.80
SCALE_1P = 0.85
GRIP_GAP = 3.6            # px модели между кистями (двуручный хват в стойке и в ударе сверху)

STEEL = (206, 212, 220)
STEEL_D = (140, 148, 160)
HAMON = (242, 246, 250)
EDGE = (255, 255, 255)
BLACK = (22, 20, 24)
BLACK_L = (46, 42, 48)
WRAP = (30, 26, 34)
SAME = (226, 220, 204)
GOLD = (196, 160, 70)
RED = (150, 24, 32)
RED_L = (196, 44, 48)

# =====================================================================
# Модель (строится в KK раз крупнее — на текстуре хватает пикселей на линию закалки и оплётку;
# в игре рисуется с масштабом 1/KK)
# =====================================================================
KK = 4.0
K = Model("katana", 128, 128)
K.bone("katana", None, (0, 0, 0))
S = Model("katana_saya", 128, 128)
S.bone("saya", None, (0, 0, 0))


def centered(m, bone, cx, y0, w, h, d, mat, face, rot=None, pivot=None):
    o = ((cx - w / 2) * KK, y0 * KK, -d / 2 * KK)
    sz = (w * KK, h * KK, d * KK)
    if rot:
        pv = pivot or (cx, y0, 0)
        m.cube(bone, o, sz, mat, rot=rot, pivot=[v * KK for v in pv], face=face)
    else:
        m.cube(bone, o, sz, mat, face=face)


# рукоять (цука) с навершием, гарда (цуба), муфта (хабаки)
centered(K, "katana", 0, -5.0, 1.6, 0.7, 1.4, "iron", "kashira")
centered(K, "katana", 0, -4.4, 1.4, 6.6, 1.15, "wrap", "tsuka")
centered(K, "katana", 0, 2.2, 3.2, 0.55, 3.2, "iron", "tsuba")
centered(K, "katana", 0.05, 2.75, 1.6, 0.8, 0.8, "gold", "habaki")
# клинок: три отрезка с небольшим изгибом к обуху (−x), острие
BLADE = [(7.5, 0.0), (6.5, 3.5), (5.5, 8.0)]      # (длина, угол изгиба, °)
x, y = 0.0, 2.75
segs = []
for i, (ln, a) in enumerate(BLADE):
    segs.append((x, y, a, ln))
    centered(K, "katana", x, y, 2.1, ln, 0.55, "blade", "blade%d" % i, rot=(0, 0, a) if a else None, pivot=(x, y, 0))
    x -= math.sin(math.radians(a)) * ln
    y += math.cos(math.radians(a)) * ln
centered(K, "katana", x + 0.15, y, 1.5, 2.1, 0.5, "blade", "kissaki", rot=(0, 0, 18), pivot=(x, y, 0))
TIP = y + 2.0
# ножны (сая): чёрный лак, повторяют изгиб; у устья — алый шнур
for i, (sx, sy, a, ln) in enumerate(segs):
    extra = 2.4 if i == len(segs) - 1 else 0.0
    centered(S, "saya", sx, sy, 2.6, ln + extra, 1.05, "saya", "saya%d" % i, rot=(0, 0, a) if a else None, pivot=(sx, sy, 0))
centered(S, "saya", 0, 2.8, 2.4, 0.6, 1.2, "iron", "koiguchi")
centered(S, "saya", 0, 4.5, 2.35, 1.0, 1.15, "cord", "sageo")
K.pack()
S.pack()


def paint(m):
    PT = Painter(128, 128, seed=7)
    for c in m.cubes():
        mat, face = c["mat"], c.get("face")
        for fname, rect in faces(c).items():
            def fn(i, j, w, h, fname=fname, mat=mat, face=face):
                big = fname in ("front", "back")
                if mat == "blade":
                    if fname == "right":                      # +x — лезвие: светлая кромка
                        return EDGE if j % 5 else HAMON
                    if fname == "left":
                        return STEEL_D                         # обух
                    if big:
                        # по ширине: обух (тёмный) → сталь → волнистая линия закалки → кромка
                        i2 = i if fname == "front" else w - 1 - i
                        if i2 == w - 1:
                            return EDGE
                        wave = w - 3.2 + 0.9 * math.sin(j * 0.55) + 0.4 * math.sin(j * 1.7)
                        if i2 >= wave:
                            return HAMON if i2 > wave + 0.6 else mix(HAMON, STEEL, 0.4)
                        if i2 == 0:
                            return STEEL_D
                        if i2 == 1:
                            return mix(STEEL_D, STEEL, 0.5)
                        return STEEL
                    return STEEL
                if mat == "wrap":                              # оплётка ромбами: тёмный шнур поверх светлой кожи
                    d1 = (i + j) % 6
                    d2 = (i - j) % 6
                    if d1 in (0, 1) or d2 in (0, 1):
                        return WRAP
                    return SAME if (i + j) % 2 else mix(SAME, (180, 170, 150), 0.5)
                if mat == "iron":
                    if face in ("tsuba", "koiguchi", "kashira"):
                        rim = i in (0, w - 1) or j in (0, h - 1)
                        return GOLD if rim and fname in ("top", "bottom") else (BLACK_L if (i * 3 + j) % 5 else (70, 62, 66))
                    return BLACK_L
                if mat == "gold":
                    return GOLD if (i + j) % 3 else mix(GOLD, (255, 230, 140), 0.5)
                if mat == "saya":
                    return mix(BLACK, (90, 20, 24), 0.25) if i in (1, 2) and big else BLACK
                if mat == "cord":
                    return RED_L if (i + j) % 3 else RED
                return (255, 0, 255)
            PT.fill(rect, fn)
    return PT


# иконка: катана по диагонали (рукоять внизу слева), тонкий клинок
ICON_PAL = {"o": (24, 22, 30), "E": (255, 255, 255), "s": (200, 206, 216), "d": (120, 128, 142), "g": (196, 160, 70),
            "w": (40, 34, 44), "W": (226, 220, 204), "k": (70, 64, 72), "r": (176, 30, 38)}


def make_icon():
    c = {}
    for y in range(0, 10):                      # клинок 2 px, чуть изогнут к концу
        x = 15 - y + (1 if y < 3 else 0) - 1
        c[(x, y)] = "E"
        if x + 1 <= 15:
            c[(x + 1, y)] = "s" if y > 0 else "d"
    for (x, y) in ((4, 9), (5, 10), (6, 11), (3, 8)):           # цуба
        c[(x, y)] = "g" if (x, y) in ((4, 9), (5, 10)) else "k"
    for i, (x, y) in enumerate(((4, 11), (3, 12), (2, 13))):    # рукоять
        c[(x, y)] = "W" if i % 2 else "w"
    c[(1, 14)] = "k"
    c[(5, 11)] = "r"                            # шнур
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (x, y), ch in c.items():
        px[x, y] = ICON_PAL[ch] + (255,)
    for y in range(16):
        for x in range(16):
            if (x, y) not in c and any((x + dx, y + dy) in c for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = ICON_PAL["o"] + (255,)
    return img


# =====================================================================
# Ключи. 3‑е лицо (px модели игрока, y вниз, вперёд −z):
#   t, хват G, клинок B, нормаль плоскости N (подсказка), левая кисть L, наклон, поворот корпуса, ноги L/R
# 1‑е лицо (камера, блоки): t, хват G, клинок B, нормаль N, направление к правому плечу O
# Удар: 0 — замах, 2.5 — попадание, 4 — проводка, 8 — стойка (меч обнажён)
# =====================================================================
IDLE_B = (0.45, 0.25, 0.86)
TP_IDLE = [(0.0, (0.5, 9.4, -4.2), IDLE_B, (-0.86, 0.0, 0.45), (2.0, 10.5, -2.6), 0.0, 0.0, 0.0, 0.0)]
TP_GUARD = (8.0, (-1.5, 7.5, -7.0), (0.0, -0.55, -0.83), (-1.0, 0.0, 0.0), (0.5, 9.5, -5.0), 0.04, 0.0, -0.15, 0.12)
TP_SWINGS = [
    [   # 1: от правого плеча налево
        (0.0, (-7.0, -1.5, -3.0), (-0.30, -0.50, 0.81), (0.0, 1.0, 0.0), (6.0, 9.0, -3.0), 0.0, 0.40, -0.30, 0.25),
        (1.0, (-6.5, 1.0, -7.0), (-0.50, 0.00, -0.86), (0.0, 1.0, 0.0), (6.0, 9.0, -3.0), 0.05, 0.30, -0.35, 0.28),
        (2.0, (-1.5, 3.0, -9.0), (0.00, 0.05, -1.00), (0.0, 1.0, 0.0), (6.5, 9.0, -1.0), 0.10, 0.00, -0.40, 0.30),
        (3.0, (3.5, 3.5, -7.0), (0.85, 0.05, -0.52), (0.0, 1.0, 0.0), (7.0, 8.5, 1.0), 0.12, -0.30, -0.40, 0.30),
        (4.0, (5.0, 4.5, -4.5), (0.96, 0.10, 0.20), (0.0, 1.0, 0.0), (7.0, 8.5, 2.0), 0.12, -0.40, -0.40, 0.30),
        TP_GUARD,
    ],
    [   # 2: обратно, от левого плеча направо
        (0.0, (3.5, -0.5, -5.0), (0.40, -0.45, 0.80), (0.0, -1.0, 0.0), (6.0, 6.0, -5.0), 0.0, -0.40, -0.10, 0.30),
        (1.0, (2.5, 2.0, -8.0), (0.60, 0.00, -0.80), (0.0, -1.0, 0.0), (6.5, 8.0, -3.0), 0.05, -0.30, -0.25, 0.32),
        (2.0, (-1.5, 3.0, -9.0), (0.00, 0.05, -1.00), (0.0, -1.0, 0.0), (7.0, 9.0, -1.0), 0.10, 0.00, -0.35, 0.32),
        (3.0, (-6.0, 3.5, -6.5), (-0.85, 0.05, -0.52), (0.0, -1.0, 0.0), (7.0, 9.0, 1.0), 0.12, 0.30, -0.35, 0.32),
        (4.0, (-8.0, 4.5, -2.5), (-0.95, 0.10, 0.25), (0.0, -1.0, 0.0), (7.0, 9.0, 2.0), 0.12, 0.40, -0.35, 0.32),
        TP_GUARD,
    ],
    [   # 3: сверху, двумя руками
        (0.0, (-0.5, -5.0, -5.0), (0.00, -0.60, 0.80), (-1.0, 0.0, 0.0), (1.0, -3.5, -5.5), -0.10, 0.10, -0.30, 0.25),
        (1.0, (-0.5, -3.5, -7.5), (0.00, -0.85, -0.52), (-1.0, 0.0, 0.0), (1.0, -1.5, -7.5), -0.02, 0.05, -0.40, 0.30),
        (2.0, (-0.5, 2.0, -9.0), (0.00, 0.10, -1.00), (-1.0, 0.0, 0.0), (1.0, 4.5, -8.0), 0.15, 0.00, -0.50, 0.34),
        (3.0, (-0.5, 7.0, -7.0), (0.00, 0.62, -0.78), (-1.0, 0.0, 0.0), (1.0, 9.5, -5.5), 0.32, 0.00, -0.55, 0.36),
        (4.0, (-0.5, 7.5, -6.5), (0.00, 0.68, -0.73), (-1.0, 0.0, 0.0), (1.0, 10.0, -5.0), 0.34, 0.00, -0.55, 0.36),
        TP_GUARD,
    ],
]
# 1‑е лицо: как обычный меч в руке (руку не рисуем — как у ванильных предметов), клинок к камере плашмя,
# чтобы катана была видна целиком; при ударах лезвие идёт по ходу удара
FP_IDLE = [(0.0, (0.40, -0.50, -0.78), (-0.22, 0.94, -0.26), (0.25, 0.10, 1.0), (0.20, -1.0, 0.20))]
FP_GUARD = (8.0,) + FP_IDLE[0][1:]
FP_SWINGS = [
    [   # 1: справа налево (лезвие смотрит влево)
        (0.0, (0.42, -0.22, -0.78), (0.60, 0.68, 0.42), (0.0, 0.0, -1.0), (0.20, -1.0, 0.20)),
        (1.0, (0.42, -0.26, -0.80), (0.35, 0.72, -0.60), (0.0, 0.0, -1.0), (0.20, -1.0, 0.20)),
        (2.0, (0.06, -0.32, -0.86), (-0.25, 0.62, -0.74), (0.0, 0.0, -1.0), (0.20, -1.0, 0.20)),
        (3.0, (-0.30, -0.36, -0.76), (-0.75, 0.42, -0.51), (0.0, 0.0, -1.0), (0.20, -1.0, 0.20)),
        (4.0, (-0.44, -0.40, -0.66), (-0.90, 0.32, 0.20), (0.0, 0.0, -1.0), (0.20, -1.0, 0.20)),
        FP_GUARD,
    ],
    [   # 2: слева направо
        (0.0, (-0.38, -0.18, -0.70), (-0.55, 0.65, 0.52), (0.0, 0.0, 1.0), (0.20, -1.0, 0.20)),
        (1.0, (-0.24, -0.26, -0.80), (-0.35, 0.72, -0.60), (0.0, 0.0, 1.0), (0.20, -1.0, 0.20)),
        (2.0, (0.10, -0.32, -0.86), (0.25, 0.62, -0.74), (0.0, 0.0, 1.0), (0.20, -1.0, 0.20)),
        (3.0, (0.36, -0.36, -0.80), (0.75, 0.45, -0.49), (0.0, 0.0, 1.0), (0.20, -1.0, 0.20)),
        (4.0, (0.46, -0.40, -0.72), (0.90, 0.38, 0.20), (0.0, 0.0, 1.0), (0.20, -1.0, 0.20)),
        FP_GUARD,
    ],
    [   # 3: сверху вниз
        (0.0, (0.18, -0.02, -0.74), (0.10, 0.90, 0.42), (1.0, 0.0, 0.6), (0.20, -1.0, 0.20)),
        (1.0, (0.14, -0.12, -0.84), (0.05, 0.95, -0.30), (1.0, 0.0, 0.6), (0.20, -1.0, 0.20)),
        (2.0, (0.12, -0.30, -0.90), (0.00, 0.55, -0.83), (1.0, 0.0, 0.6), (0.20, -1.0, 0.20)),
        (3.0, (0.12, -0.48, -0.80), (0.00, -0.05, -1.00), (1.0, 0.0, 0.6), (0.20, -1.0, 0.20)),
        (4.0, (0.12, -0.50, -0.78), (0.00, -0.15, -0.99), (1.0, 0.0, 0.6), (0.20, -1.0, 0.20)),
        FP_GUARD,
    ],
]


# =====================================================================
# Позы (как в Java: KatanaPose)
# =====================================================================
def frame_from(g, b, n):
    b = A.norm(b)
    z = A.norm(A.sub(n, A.scl(b, A.dot(n, b))))
    x = A.cross(b, z)
    return [x, b, z]


def tp_pose(v):
    g, b, n, l = v[0:3], v[3:6], v[6:9], v[9:12]
    lean, twist, leg_l, leg_r = v[12:16]
    R = A.body_R(lean, twist)
    neck = A.add([0, 12, 0], A.mv(R, [0, -12, 0]))
    sh_r = A.add(neck, A.mv(R, [-5, 2, 0]))
    sh_l = A.add(neck, A.mv(R, [5, 2, 0]))
    g = A.add(neck, A.mv(R, g))
    l = A.add(neck, A.mv(R, l))
    xr = A.mv(R, [1, 0, 0])

    def aim(sh, target, hx):
        a = target
        for _ in range(4):
            Rm = A.basis(A.sub(a, sh), xr)
            hand = A.add(sh, A.mv(Rm, [hx, A.ARM_REACH, 0]))
            a = A.add(a, A.sub(target, hand))
        Rm = A.basis(A.sub(a, sh), xr)
        return Rm, A.add(sh, A.mv(Rm, [hx, A.ARM_REACH, 0]))
    Rr, hand_r = aim(sh_r, g, -1.0)
    Rl, hand_l = aim(sh_l, l, 1.0)
    sw = frame_from(g, A.mv(R, b), A.mv(R, n))
    return dict(R=R, neck=neck, sh_r=sh_r, sh_l=sh_l, Rr=Rr, Rl=Rl, grip=hand_r, sword=sw, leg_l=leg_l, leg_r=leg_r)


def saya_tp():
    v = A.flat(TP_IDLE[0])[1:]
    p = tp_pose(v)
    return p["grip"], p["sword"]


def fp_pose(v):
    g, b, n, o = v[0:3], v[3:6], v[6:9], v[9:12]
    return dict(grip=g, sword=frame_from(g, b, n), sh_r=A.add(g, o))


# =====================================================================
# Java
# =====================================================================
def write_java():
    def keys(name, ks):
        rows = ["            {" + ", ".join("%.4ff" % x for x in A.flat(k)) + "}" for k in ks]
        return "    static final float[][] %s = {\n%s\n    };\n" % (name, ",\n".join(rows))
    src = ("package com.lesha_top1972.basedefense.client.weapon;\n\n"
           "/**\n * Ключевые кадры катаны. СОЗДАНО tools/katana_gen.py — правь скрипт, а не этот файл.\n"
           " * 3‑е лицо: {t, хват x,y,z, клинок x,y,z, нормаль плоскости x,y,z, левая кисть x,y,z, наклон, поворот, нога L, нога R}.\n"
           " * 1‑е лицо: {t, хват x,y,z, клинок x,y,z, нормаль x,y,z, направление к плечу x,y,z}.\n */\n"
           "final class KatanaKeys {\n\n"
           "    static final float SCALE_3P = %.3ff;\n    static final float SCALE_1P = %.3ff;\n"
           "    static final float MODEL_K = %.1ff;           // модель построена в K раз крупнее\n\n" % (SCALE_3P, SCALE_1P, KK))
    src += keys("TP_IDLE", TP_IDLE) + "\n" + keys("FP_IDLE", FP_IDLE) + "\n"
    src += "    static final float[][][] TP_SWINGS = {\n" + ",\n".join(
        "        " + keys("X", s).split("= ", 1)[1].rstrip(";\n").replace("\n", "\n    ") for s in TP_SWINGS) + "\n    };\n\n"
    src += "    static final float[][][] FP_SWINGS = {\n" + ",\n".join(
        "        " + keys("X", s).split("= ", 1)[1].rstrip(";\n").replace("\n", "\n    ") for s in FP_SWINGS) + "\n    };\n\n"
    src += "    private KatanaKeys() {\n    }\n}\n"
    with open(JAVA, "w", encoding="utf-8") as f:
        f.write(src)
    print("→", os.path.relpath(JAVA))


# =====================================================================
# Превью
# =====================================================================
def model_quads(m, tf):
    m.bones_out = [{"name": b["name"], "pivot": b["pivot"], "cubes": [
        {k: c[k] for k in ("origin", "size", "uv", "pivot", "rotation") if k in c} for c in b["cubes"]]} for b in m.bones]

    def conv(v):
        return tf([v[0] / KK, (24 - v[1]) / KK, -v[2] / KK])
    return G.java_quads(m, conv)


def preview(tk, ts):
    import mob_preview  # noqa: F401
    skin = Image.open(A.SKIN).convert("RGBA")
    W, H = 170, 230
    shots = [("idle", 0, 0)] + [(i, t, 0) for i in range(3) for t in (0.0, 2.0, 4.0)]

    def side(p):
        return [-p[2], -p[1], p[0]]

    def front(p):
        q = A.rot_y([p[0], -p[1], -p[2]], 0.55)
        return [q[0], q[1], -q[2]]

    rows = []
    sg, ssw = saya_tp()
    for view in (side, front):
        row = []
        for clip, t, _ in shots:
            v = A.flat(TP_IDLE[0])[1:] if clip == "idle" else A.sample(TP_SWINGS[clip], t)
            p = tp_pose(v)
            q = []
            pb, yb, rb = A.euler_zyx(p["R"])
            q += A.part_quads([A.box(0, 0, -4, -8, -4, 8, 8, 8)], A.euler_tf(p["neck"], pb, yb, rb, view))
            q += A.part_quads([A.box(16, 16, -4, 0, -2, 8, 12, 4)], A.euler_tf(p["neck"], pb, yb, rb, view))
            q += A.part_quads([A.box(0, 16, -2, 0, -2, 4, 12, 4)], A.euler_tf([-1.9, 12, 0], p["leg_r"], 0, 0, view))
            q += A.part_quads([A.box(16, 48, -2, 0, -2, 4, 12, 4)], A.euler_tf([1.9, 12, 0], p["leg_l"], 0, 0, view))
            for key, sh, cube in (("Rr", "sh_r", A.box(40, 16, -3, -2, -2, 4, 12, 4)), ("Rl", "sh_l", A.box(32, 48, -1, -2, -2, 4, 12, 4))):
                a, b_, c_ = A.euler_zyx(p[key])
                q += A.part_quads([cube], A.euler_tf(p[sh], a, b_, c_, view))
            kq = model_quads(K, A.matrix_tf(p["grip"], p["sword"], view, SCALE_3P))
            sq = model_quads(S, A.matrix_tf(sg, ssw, view, SCALE_3P))
            img = G.raster([(q, skin, None), (kq, tk, None), (sq, ts, None)], (W, H),
                           lambda pp: (W / 2 + pp[0] * 4.5, H / 2 - 10 - pp[1] * 4.5, pp[2]))
            row.append(img)
        rows.append(row)
    FW, FH = 240, 135
    fy = FH / 2 / math.tan(math.radians(35))
    fp_row = []
    fsg = fp_pose(A.flat(FP_IDLE[0])[1:])
    for clip, t, _ in shots:
        v = A.flat(FP_IDLE[0])[1:] if clip == "idle" else A.sample(FP_SWINGS[clip], t)
        p = fp_pose(v)
        q = []
        kq = model_quads(K, A.matrix_tf(p["grip"], p["sword"], lambda x: x, SCALE_1P / 16))
        sq = []

        def proj(pp):
            if pp[2] > -0.05:
                return None
            return (FW / 2 + pp[0] / -pp[2] * fy, FH / 2 - pp[1] / -pp[2] * fy, -pp[2])
        fp_row.append(G.raster([(q, skin, None), (kq, tk, None), (sq, ts, None)], (FW, FH), proj, bg=(110, 150, 200, 255)))
    n = len(shots)
    sheet = Image.new("RGBA", (max(W * n, 5 * (FW + 6)), H * 2 + 2 * (FH + 6) + 8), (40, 42, 48, 255))
    for r, imgs in enumerate(rows):
        for i, im in enumerate(imgs):
            sheet.alpha_composite(im, (i * W, r * H))
    for i, im in enumerate(fp_row):
        sheet.alpha_composite(im, ((i % 5) * (FW + 6), 2 * H + 8 + (i // 5) * (FH + 6)))
    sheet.save(os.path.join(os.path.dirname(__file__), "mob_preview.png"))
    print("preview → tools/mob_preview.png")


def main():
    tk = paint(K)
    ts = paint(S)
    K.write_geo(ROOT)
    S.write_geo(ROOT)
    tk.img.save(asset_groups.tex("katana.png", ROOT))
    ts.img.save(asset_groups.tex("katana_saya.png", ROOT))
    make_icon().save(os.path.join(ROOT, "textures", "item", "katana.png"))
    write_java()
    if "--preview" in sys.argv:
        preview(tk.img, ts.img)


if __name__ == "__main__":
    main()
