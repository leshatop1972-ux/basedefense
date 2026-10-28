"""
Генератор морозной лучницы (frost_archer): модель Bedrock (.geo.json), анимации (.animation.json),
текстура и светящаяся маска, текстура ледяной стрелы, иконка эффекта «Заморозка» и оверлей экрана.

Запуск:  python tools/frost_archer_gen.py
Проверка без игры:  python tools/mob_preview.py frost_archer attack 0.3,1.0,1.6,2.0

Морозная эльфийка‑лучница: серебристо‑белые длинные волосы, острые уши, ледяная тиара,
корсет и юбка из панелей, сапоги до колен, ледяной наплечник, колчан на правом бедре.
Руки и ноги из двух звеньев. Позы рук считаются обратной кинематикой (mobgen.Model.two_bone):
правая рука достаёт стрелу из колчана, кладёт на тетиву и тянет к щеке, локоть поднят.
Общие инструменты — tools/mobgen.py.
"""
import math
import os
import random

from PIL import Image

from mobgen import (FACE_LIGHT, Model, Painter, add, blend, bake, clamp, ease, ease_out, eulerR, faces,
import asset_groups  # noqa: E402  (папки мобов: geo/animations/textures)
                    lerp, lerp3, mix, mm, mul, rx_m, ry_m, rz_m, scl, sub, write_anims)

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
NAME = "frost_archer"
M = Model(NAME, 128, 128)
bone, cube = M.bone, M.cube

# =====================================================================
# 1. Модель (Bedrock: y вверх, лицо в −z, правая сторона персонажа — −x)
# =====================================================================
UPPER, FORE = 5.5, 5.5          # длины звеньев руки
bone("root", None, (0, 0, 0))

# --- бёдра, пояс ---
bone("body", "root", (0, 11, 0))
cube("body", (-3.5, 9, -2), (7, 3, 4), "legging", face="hips")
cube("body", (-3.8, 11, -2.3), (7.6, 1.4, 4.6), "belt")
cube("body", (-1, 10.8, -2.7), (2, 1.8, 0.6), "silver_gem")
cube("body", (3.3, 8.6, -1.6), (1.4, 2.4, 2.6), "pouch")

# --- колчан на правом бедре ---
bone("quiver", "body", (-4.2, 11, 1.1))
Q_ROT, Q_PIV = (-14, 0, 18), (-4.2, 11, 1.1)
cube("quiver", (-5.3, 4.5, 0.0), (2.2, 7, 2.2), "quiver", rot=Q_ROT, pivot=Q_PIV)
cube("quiver", (-5.45, 9.9, -0.15), (2.5, 0.7, 2.5), "silver", rot=Q_ROT, pivot=Q_PIV)
cube("quiver", (-5.45, 5.6, -0.15), (2.5, 0.7, 2.5), "silver", rot=Q_ROT, pivot=Q_PIV)
cube("quiver", (-5.1, 11.3, 0.3), (0.7, 2.6, 0.7), "fletch", rot=Q_ROT, pivot=Q_PIV)
cube("quiver", (-4.2, 11.1, 1.2), (0.7, 2.4, 0.7), "fletch_w", rot=Q_ROT, pivot=Q_PIV)
cube("quiver", (-4.9, 11.0, 1.4), (0.7, 2.2, 0.7), "fletch", rot=Q_ROT, pivot=Q_PIV)
QUIVER_MOUTH = (-4.6, 12.3, 1.2)   # куда тянется рука за стрелой (Bedrock, до поворота колчана)

# --- юбка из панелей ---
bone("skirt_fr", "body", (-1.95, 10.6, -2.2))
cube("skirt_fr", (-3.9, 4.2, -2.6), (3.8, 6.4, 0.7), "skirt", face="skirt_front")
bone("skirt_fl", "body", (1.95, 10.6, -2.2))
cube("skirt_fl", (0.1, 4.2, -2.6), (3.8, 6.4, 0.7), "skirt", face="skirt_front")
bone("skirt_b", "body", (0, 10.6, 2.2))
cube("skirt_b", (-3.9, 3.6, 1.9), (7.8, 7, 0.7), "skirt", face="skirt_back")
bone("skirt_sr", "body", (-3.7, 10.6, 0))
cube("skirt_sr", (-4.2, 5.0, -1.9), (0.7, 5.6, 3.8), "skirt", face="skirt_side")
bone("skirt_sl", "body", (3.7, 10.6, 0))
cube("skirt_sl", (3.5, 5.0, -1.9), (0.7, 5.6, 3.8), "skirt", face="skirt_side")

# --- ноги: бедро → голень → стопа ---
bone("right_leg", "body", (-1.9, 10, 0))
cube("right_leg", (-3.55, 5, -1.65), (3.3, 5, 3.3), "legging", face="thigh")
cube("right_leg", (-3.7, 7.4, -1.8), (3.6, 0.8, 3.6), "band")
bone("right_shin", "right_leg", (-1.9, 5, 0))
cube("right_shin", (-3.45, 1, -1.55), (3.1, 4, 3.1), "boot", face="boot")
cube("right_shin", (-3.7, 4.0, -1.8), (3.6, 1.4, 3.6), "fur")
cube("right_shin", (-2.9, 3.9, -2.4), (2, 2.2, 0.6), "ice", rot=(-8, 0, 0), pivot=(-1.9, 5, -2))
bone("right_foot", "right_shin", (-1.9, 1, -0.2))
cube("right_foot", (-3.3, -0.3, -2.9), (2.8, 1.4, 3.6), "boot", face="foot")
cube("right_foot", (-2.9, -0.2, -3.3), (2, 1, 0.6), "silver")
M.mirror_x("right_leg", "left_leg")
M.mirror_x("right_shin", "left_shin", parent="left_leg")
M.mirror_x("right_foot", "left_foot", parent="left_shin")

# --- корпус ---
bone("torso", "body", (0, 12, 0))
cube("torso", (-3, 12, -1.75), (6, 5, 3.5), "corset", face="corset")
cube("torso", (-3.5, 17, -2), (7, 5, 4), "bodice", face="bodice")
cube("torso", (-3, 18, -2.9), (6, 3, 1.1), "bodice", face="bust", rot=(-12, 0, 0), pivot=(0, 19.5, -2))
cube("torso", (-4.3, 21.6, -2.6), (8.6, 2, 5.2), "fur", inflate=0.1)
cube("torso", (-1, 23, -1), (2, 1, 2), "skin")

bone("cape", "torso", (0, 23, 2.1))
cube("cape", (-4.2, 14, 2.0), (8.4, 9, 0.8), "cape", face="cape")

# --- руки: плечо → предплечье ---
bone("right_arm", "torso", (-5, 22, 0))
cube("right_arm", (-6.5, 16.5, -1.5), (3, 5.5, 3), "sleeve", face="upper")
cube("right_arm", (-6.8, 19.3, -1.8), (3.6, 3, 3.6), "sleeve", face="puff")
cube("right_arm", (-7.0, 21.0, -2.0), (3.8, 2, 4), "fur", inflate=0.1)
bone("right_fore", "right_arm", (-5, 16.5, 0))
cube("right_fore", (-6.5, 11, -1.5), (3, 5.5, 3), "glove", face="fore")
cube("right_fore", (-6.75, 12.6, -1.75), (3.5, 3, 3.5), "bracer")
cube("right_fore", (-6.3, 10.2, -1.3), (2.6, 1.3, 2.6), "glove", face="hand")
# стрела в руке (видна, пока лучница достаёт её из колчана)
bone("hand_arrow", "right_fore", (-5, 11, 0))
cube("hand_arrow", (-5.25, 0.4, -0.25), (0.5, 10.6, 0.5), "shaft")
cube("hand_arrow", (-5.5, -1.0, -0.5), (1, 1.5, 1), "ice")
cube("hand_arrow", (-5.35, 8.6, -1.1), (0.2, 2, 2.2), "fletch")
cube("hand_arrow", (-6.1, 8.6, -0.1), (2.2, 2, 0.2), "fletch")
# «Ледяной веер»: ещё две стрелы веером (видны только в залпе)
bone("hand_arrow_fan", "right_fore", (-5, 11, 0))
for k in (-1, 1):
    for o, sz, mat in (((-5.25, 0.4, -0.25), (0.5, 10.6, 0.5), "shaft"), ((-5.5, -1.0, -0.5), (1, 1.5, 1), "ice"),
                       ((-5.35, 8.6, -1.1), (0.2, 2, 2.2), "fletch"), ((-6.1, 8.6, -0.1), (2.2, 2, 0.2), "fletch")):
        cube("hand_arrow_fan", o, sz, mat, rot=(0, 0, 13 * k), pivot=(-5, 8.0, 0))

bone("left_arm", "torso", (5, 22, 0))
cube("left_arm", (3.5, 16.5, -1.5), (3, 5.5, 3), "sleeve", face="upper")
cube("left_arm", (3.2, 19.3, -1.8), (3.6, 3, 3.6), "sleeve", face="puff")
cube("left_arm", (2.9, 21.0, -2.3), (4.4, 1.6, 4.6), "ice_armor", rot=(0, 0, 18), pivot=(5, 22, 0))
cube("left_arm", (3.3, 19.9, -2.1), (3.8, 1.2, 4.2), "ice_armor", rot=(0, 0, 28), pivot=(5, 21, 0))
cube("left_arm", (6.0, 22.2, -0.5), (1, 2.4, 1), "ice", rot=(0, 0, -28), pivot=(6.5, 22.2, 0))
bone("left_fore", "left_arm", (5, 16.5, 0))
cube("left_fore", (3.5, 11, -1.5), (3, 5.5, 3), "glove", face="fore")
cube("left_fore", (3.25, 12.6, -1.75), (3.5, 3, 3.5), "bracer")
cube("left_fore", (3.7, 10.2, -1.3), (2.6, 1.3, 2.6), "glove", face="hand")

# --- лук в левой руке ---
P = (5.0, 11.0, 0.0)            # точка хвата
LIMB_PTS = [(1.5, 0.0), (4.0, 0.45), (6.5, 1.5), (8.6, 2.8), (9.8, 2.5)]   # (y, z) от хвата
TIP = LIMB_PTS[-1]
STRING_LEN = TIP[0]
NOCK_Z0 = TIP[1]
DRAW_MAX = 6.0
bone("bow", "left_fore", P)
cube("bow", (P[0] - 0.55, P[1] - 1.5, P[2] - 0.75), (1.1, 3, 1.5), "grip")
cube("bow", (P[0] - 0.75, P[1] - 0.6, P[2] - 1.3), (1.5, 1.2, 0.6), "ice")
for lname, sgn in (("bow_top", 1), ("bow_bot", -1)):
    bone(lname, "bow", (P[0], P[1] + 1.5 * sgn, P[2]))
    pts = [(P[1] + y * sgn, P[2] + z) for y, z in LIMB_PTS]
    for i in range(len(pts) - 1):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        dy, dz = y1 - y0, z1 - z0
        rx = math.degrees(math.atan2(-dz, dy))
        th = 0.9 if i < 2 else 0.7
        cube(lname, (P[0] - 0.5, y0, z0 - th / 2), (1, math.hypot(dy, dz), th), "wood", rot=(rx, 0, 0),
             pivot=(P[0], y0, z0))
    ty, tz = pts[-1]
    cube(lname, (P[0] - 0.5, ty - 0.5, tz - 0.5), (1, 1, 1), "ice")
    cube(lname, (P[0] - 0.3, ty + 0.4 * sgn - 0.3, tz - 0.9), (0.6, 0.6, 0.8), "ice")
    s = lname.replace("bow_", "string_")
    bone(s, lname, (P[0], ty, tz))
    if sgn > 0:
        cube(s, (P[0] - 0.15, ty - STRING_LEN, tz - 0.15), (0.3, STRING_LEN, 0.3), "string")
    else:
        cube(s, (P[0] - 0.15, ty, tz - 0.15), (0.3, STRING_LEN, 0.3), "string")
bone("bow_arrow", "bow", P)
cube("bow_arrow", (P[0] - 0.25, P[1] - 0.25, P[2] - 3.5), (0.5, 0.5, 12), "shaft")
cube("bow_arrow", (P[0] - 0.5, P[1] - 0.5, P[2] - 5), (1, 1, 1.5), "ice")
cube("bow_arrow", (P[0] - 0.1, P[1] - 1, P[2] + 6.4), (0.2, 2, 2), "fletch")
cube("bow_arrow", (P[0] - 1, P[1] - 0.1, P[2] + 6.4), (2, 0.2, 2), "fletch")
bone("bow_arrow_fan", "bow_arrow", P)
for k in (-1, 1):
    for o, sz, mat in (((P[0] - 0.25, P[1] - 0.25, P[2] - 3.5), (0.5, 0.5, 12), "shaft"),
                       ((P[0] - 0.5, P[1] - 0.5, P[2] - 5), (1, 1, 1.5), "ice"),
                       ((P[0] - 0.1, P[1] - 1, P[2] + 6.4), (0.2, 2, 2), "fletch"),
                       ((P[0] - 1, P[1] - 0.1, P[2] + 6.4), (2, 0.2, 2), "fletch")):
        cube("bow_arrow_fan", o, sz, mat, rot=(13 * k, 0, 0), pivot=(P[0], P[1], P[2] + 8.0))

# --- голова ---
bone("head", "torso", (0, 23.5, 0))
cube("head", (-4, 23.5, -4), (8, 8, 8), "head", face="face")
cube("head", (-4, 23.5, -4), (8, 8, 8), "hair", inflate=0.35, face="hair_layer")
cube("head", (-4.75, 18.6, -3.5), (1.3, 7, 1.3), "hair", face="lock")
cube("head", (3.45, 18.6, -3.5), (1.3, 7, 1.3), "hair", face="lock")
cube("head", (-6.0, 26.9, -0.4), (1.9, 1.0, 1.0), "skin", face="ear", rot=(0, 15, 18), pivot=(-4.1, 27.4, 0.1))
cube("head", (4.1, 26.9, -0.4), (1.9, 1.0, 1.0), "skin", face="ear", rot=(0, -15, -18), pivot=(4.1, 27.4, 0.1))
# ледяная тиара
cube("head", (-4.45, 30.1, -4.5), (8.9, 0.6, 0.5), "silver")
cube("head", (-0.6, 29.9, -4.85), (1.2, 1.2, 0.5), "ice")
cube("head", (-0.5, 30.6, -4.7), (1, 3.2, 0.8), "ice", rot=(-8, 0, 0), pivot=(0, 30.6, -4.3))
for sx in (-1, 1):
    cube("head", (1.9 * sx - 0.4, 30.5, -4.6), (0.8, 2.2, 0.7), "ice", rot=(0, 0, -14 * sx), pivot=(1.9 * sx, 30.5, -4.25))
    cube("head", (3.45 * sx - 0.35, 30.4, -4.4), (0.7, 1.6, 0.6), "ice", rot=(0, 0, -28 * sx), pivot=(3.45 * sx, 30.4, -4.1))

# --- длинные волосы по спине (три звена, развеваются) ---
bone("hair_back", "head", (0, 30.5, 4.3))
cube("hair_back", (-4.3, 21, 4.15), (8.6, 9.5, 1.3), "hair", face="hair_long")
bone("hair_mid", "hair_back", (0, 21, 4.8))
cube("hair_mid", (-4, 14, 4.2), (8, 7, 1.2), "hair", face="hair_mid")
bone("hair_tip", "hair_mid", (0, 14, 4.8))
cube("hair_tip", (-3.4, 9, 4.25), (6.8, 5, 1.1), "hair", face="hair_tip")

# --- ледяные осколки ---
bone("shards", "root", (0, 19, 0))
cube("shards", (8, 18, -0.5), (1, 3, 1), "ice", rot=(0, 45, 20), pivot=(8.5, 19.5, 0))
cube("shards", (-9, 21, -0.5), (1, 2, 1), "ice", rot=(0, 45, -25), pivot=(-8.5, 22, 0))
cube("shards", (-0.5, 16, 8.5), (1, 2, 1), "ice", rot=(30, 45, 0), pivot=(0, 17, 9))

M.pack()
M.finalize()

# =====================================================================
# 2. Текстура
# =====================================================================
PT = Painter(M.tex_w, M.tex_h)
R = PT.rnd

HAIR = (226, 236, 249)
HAIR_S = (186, 205, 232)
HAIR_D = (164, 186, 220)
HAIR_H = (252, 253, 255)
SKIN = (250, 235, 229)
SKIN_S = (232, 210, 206)
BLUSH = (247, 190, 199)
LIPS = (230, 142, 162)
LIPS_D = (206, 112, 136)
IRIS = (124, 208, 255)
IRIS_D = (50, 118, 208)
EYE_W = (251, 251, 253)
LASH = (38, 34, 62)
BROW = (132, 152, 196)
WHITE = (238, 243, 249)
SNOW = (247, 250, 253)
PALE = (206, 222, 238)
AZURE = (58, 146, 214)
AZURE_L = (118, 194, 242)
AZURE_D = (32, 92, 160)
NAVY = (24, 52, 98)
SILVER = (202, 214, 228)
SILVER_D = (138, 154, 178)
ICE = (150, 228, 255)
ICE_L = (226, 250, 255)
ICE_D = (72, 172, 228)
WOOD = (224, 230, 238)
WOOD_D = (160, 176, 198)


def light(fname, col, k=1.0):
    return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.03))


def hair_strand(i, j, w, h, fname, dark_bottom=True):
    """Пряди: вертикальные полосы, блики сверху, тень снизу."""
    h_ = (i * 37 + 11) % 5
    col = HAIR if h_ > 1 else (HAIR_S if h_ == 1 else HAIR_D)
    if j < 2 and h_ > 2:
        col = HAIR_H
    if dark_bottom:
        col = mix(col, HAIR_D, 0.35 * j / max(1, h - 1))
    return light(fname, col)


def paint_head_face(c):
    f = faces(c)
    for fname, rect in f.items():
        if fname == "front":
            continue
        if fname == "bottom":
            PT.fill(rect, lambda i, j, w, h: light(fname, SKIN_S) if j < h - 2 else light(fname, HAIR_D))
        else:
            PT.fill(rect, lambda i, j, w, h, fn=fname: hair_strand(i, j, w, h, fn))
    # лицо 8×8
    H, S = "H", "S"
    # как у женских скинов Minecraft: глаза 2×2 (ресницы + тёмный верх радужки, белок + светлая радужка),
    # румянец под глазами, маленькие розовые губы, без носа и бровей
    rows = [
        "HHHHHHHH",
        "HHHHHHHH",
        "HFFFFFFH",
        "HLDSSDLH",
        "HWISSIWH",
        "HRSSSSRH",
        "HSSPpSSH",
        "HTSSSSTH",
    ]
    x0, y0, w, h = f["front"]
    pal = {
        "H": None, "S": SKIN, "B": BROW, "L": LASH, "D": IRIS_D, "W": EYE_W,
        "R": BLUSH, "N": mix(SKIN, SKIN_S, 0.5), "P": LIPS, "p": mix(LIPS, (255, 214, 222), 0.45),
        "T": mix(SKIN, SKIN_S, 0.6), "F": mix(SKIN, SKIN_S, 0.45),
    }
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "H":
                PT.put(x0 + i, y0 + j, hair_strand(i, j, w, h, "front", False))
            elif ch == "I":
                PT.glow_put(x0 + i, y0 + j, IRIS)
            elif ch == "D":
                PT.put(x0 + i, y0 + j, IRIS_D)
            else:
                col = pal[ch]
                if ch == "S" and j >= 6:
                    col = mix(SKIN, SKIN_S, 0.25)
                PT.put(x0 + i, y0 + j, mul(col, PT.noise(0.015)))


def paint_hair_layer(c):
    f = faces(c)
    for fname, rect in f.items():
        if fname == "front":
            fringe = ["HHHHHHHH", "HHHHHHHH", "HH.H..HH", "H......H", "H......H", "H......H", "H......H", "........"]
            PT.fill(rect, lambda i, j, w, h: hair_strand(i, j, w, h, "front", False) if fringe[j][i] == "H" else None)
        elif fname == "bottom":
            PT.fill(rect, lambda i, j, w, h: hair_strand(i, j, w, h, "bottom") if (i in (0, w - 1) or j == h - 1) else None)
        elif fname == "top":
            PT.fill(rect, lambda i, j, w, h: light("top", HAIR_S if i == w // 2 - 1 and j < h - 2 else
                                                     (HAIR_H if (i + j) % 4 == 0 else HAIR)))
        else:
            # бока и затылок: пряди, внизу неровные кончики
            def side(i, j, w, h, fn=fname):
                if j == h - 1 and (i * 3 + len(fn)) % 3 == 0:
                    return None
                return hair_strand(i, j, w, h, fn)
            PT.fill(rect, side)


def paint_hair_part(c, kind):
    for fname, rect in faces(c).items():
        def fn(i, j, w, h, fname=fname):
            if fname in ("front",):
                col = light(fname, HAIR_D)        # сторона к спине — в тени
            elif fname == "top":
                col = light(fname, HAIR)
            else:
                col = hair_strand(i, j, w, h, fname)
            if kind == "hair_mid" and j == 0 and fname not in ("top", "bottom"):
                return ("glow", AZURE_L) if i % 3 == 1 else light(fname, AZURE)    # лента
            if kind in ("hair_tip", "lock") and fname not in ("top", "bottom"):
                teeth = (i * 5 + len(fname)) % 3
                if j >= h - teeth:
                    return None
            if kind == "hair_tip" and fname == "bottom":
                return None
            return col
        PT.fill(rect, fn)


def paint_cloth(c, base, trim=None, trim_rows=1, pattern=None):
    for fname, rect in faces(c).items():
        def fn(i, j, w, h, fname=fname):
            col = base
            if pattern == "weave" and (i + j) % 2 == 0:
                col = mul(col, 0.97)
            if pattern == "diamond" and (i + j) % 4 == 0:
                col = mix(col, AZURE_L, 0.25)
            if trim and fname not in ("top", "bottom") and j >= h - trim_rows:
                col = trim
            k = 1.0 - (0.08 * j / max(1, h - 1) if fname not in ("top", "bottom") else 0)
            if fname not in ("top", "bottom") and i in (0, w - 1):
                k *= 0.93
            return light(fname, col, k)
        PT.fill(rect, fn)


def paint_cube(c):
    mat, face = c["mat"], c.get("face")
    F = faces(c)
    if face == "face":
        paint_head_face(c)
        return
    if face == "hair_layer":
        paint_hair_layer(c)
        return
    if mat == "hair":
        paint_hair_part(c, face)
        return
    if mat == "skin":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, SKIN if face != "ear" or i < w - 1 else SKIN_S))
        return
    if mat == "legging":
        paint_cloth(c, WHITE, pattern="diamond")
        if face == "hips":
            pass
        return
    if mat == "band":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, SILVER if i % 2 == 0 and fn not in ("top", "bottom") else AZURE))
        return
    if mat == "boot":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = AZURE
                if face == "boot" and fname == "front" and i == w // 2 - 1 + (j % 2):
                    col = WHITE                                    # шнуровка
                if face == "boot" and fname not in ("top", "bottom") and j == 0:
                    col = SILVER
                if face == "foot" and fname == "bottom":
                    col = NAVY
                if face == "foot" and fname not in ("top", "bottom") and j == h - 1:
                    col = NAVY
                return light(fname, col, 1.0 - 0.06 * j / max(1, h))
            PT.fill(rect, fn)
        return
    if mat == "fur":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                t = R.random()
                col = SNOW if t > 0.35 else (WHITE if t > 0.1 else PALE)
                if fname not in ("top", "bottom") and j == h - 1 and i % 2 == 0:
                    col = PALE
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "corset":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = WHITE
                if fname in ("left", "right"):
                    col = AZURE if i not in (0, w - 1) else AZURE_D
                elif fname in ("front", "back"):
                    mid = (w - 1) / 2
                    if abs(i - mid) <= 0.5 and (j % 2 == 0):
                        col = AZURE_L                                # шнуровка крест‑накрест
                    elif abs(i - mid) <= 1.5 and (j % 2 == 1):
                        col = AZURE
                    elif i in (0, w - 1):
                        col = AZURE_D                                # косточки корсета
                    if j == 0 or j == h - 1:
                        col = SILVER
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "bodice":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = WHITE
                if face == "bust":
                    col = mix(SNOW, PALE, j / max(1, h - 1) * 0.7)
                    if fname == "front":
                        if j == 0:
                            col = AZURE_L                                   # кайма выреза
                        elif i in (w // 2 - 1, w // 2):
                            col = mix(col, PALE, 0.8)                       # ложбинка
                        if j == h - 1:
                            col = mul(PALE, 0.9)
                    if fname == "top":
                        col = AZURE_L
                    return light(fname, col)
                if fname in ("left", "right"):
                    col = AZURE
                if fname == "front" and (i == 0 or i == w - 1):
                    col = AZURE
                if fname == "back" and i == w // 2:
                    col = AZURE_D
                return light(fname, col)
            PT.fill(rect, fn)
            if face == "bodice" and fname == "front":
                x0, y0, w, h = rect
                PT.glow_put(x0 + w // 2, y0 + 0, ICE_L)              # кулон
        return
    if mat == "cape":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if fname == "front":
                    return light(fname, NAVY)                         # изнанка
                col = AZURE
                if fname not in ("top", "bottom") and j == h - 1:
                    col = WHITE
                if fname == "back" and (i == 0 or i == w - 1):
                    col = AZURE_L
                return light(fname, col, 1.0 - 0.1 * j / max(1, h))
            PT.fill(rect, fn)
            if fname == "back":
                x0, y0, w, h = rect
                cx, cy = x0 + w // 2, y0 + 3
                for dx, dy in ((0, 0), (0, -2), (0, 2), (-2, 0), (2, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
                    PT.put(cx + dx, cy + dy, WHITE)
                PT.glow_put(cx, cy, ICE_L)
        return
    if mat == "sleeve":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = WHITE
                if face == "puff" and fname not in ("top", "bottom") and j == h - 1:
                    col = AZURE
                if face == "puff" and (i + j) % 3 == 0:
                    col = mul(col, 0.95)
                if face == "upper" and fname not in ("top", "bottom") and j == 2:
                    col = AZURE_L
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "glove":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = SNOW
                if face == "hand" and fname == "bottom":
                    col = PALE
                if face == "fore" and fname not in ("top", "bottom") and j == 0:
                    col = AZURE_L
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "bracer":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = AZURE_D
                if fname not in ("top", "bottom") and j in (0, h - 1):
                    col = SILVER
                if fname == "left" or fname == "right":
                    if j == h // 2 and i == w // 2:
                        return ("glow", ICE)
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "skirt":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = AZURE
                outer = (fname == "front" and face != "skirt_back") or (fname == "back" and face == "skirt_back") \
                    or (fname in ("left", "right") and face == "skirt_side")
                if not outer and fname not in ("top", "bottom"):
                    col = AZURE_D
                if fname not in ("top", "bottom") and j >= h - 1:
                    col = WHITE
                if outer and i in (0, w - 1) and w > 2:
                    col = SILVER
                return light(fname, col, 1.0 - 0.08 * j / max(1, h))
            PT.fill(rect, fn)
            x0, y0, w, h = rect
            outer = (fname == "front" and face == "skirt_front") or (fname == "back" and face == "skirt_back")
            if outer:
                # вышитая кайма: тонкая светлая линия по краю и ледяная бусина
                for j in range(1, h - 2):
                    PT.put(x0 + 1, y0 + j, mul(AZURE_L, 0.95))
                    PT.put(x0 + w - 2, y0 + j, mul(AZURE_L, 0.95))
                for i in range(1, w - 1):
                    PT.put(x0 + i, y0 + h - 2, mul(AZURE_L, 0.95))
                PT.glow_put(x0 + w // 2, y0 + 1, ICE_L)
        return
    if mat == "belt":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, SILVER if (i % 3 == 1 and fn in ("front", "back")) else NAVY))
        return
    if mat == "pouch":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, SILVER if j == 0 and fn == "left" else AZURE_D))
        return
    if mat == "quiver":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = AZURE_D if i not in (0, w - 1) else NAVY
                if fname == "top":
                    col = (20, 30, 50)
                return light(fname, col)
            PT.fill(rect, fn)
            if fname in ("left", "right"):
                x0, y0, w, h = rect
                PT.glow_put(x0 + w // 2, y0 + h // 2, ICE)
        return
    if mat == "ice_armor":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                t = (i + j) / max(1, w + h - 2)
                col = mix(ICE_L, ICE_D, t)
                if j == 0 and fname not in ("top", "bottom"):
                    return ("glow", ICE_L)
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "ice":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                t = (i + j) / max(1, w + h - 2)
                col = mix(ICE_L, ICE_D, t)
                if (i == 0 and j == 0) or R.random() < 0.08:
                    col = ICE_L
                return ("glow", mul(col, 0.9 + 0.1 * FACE_LIGHT[fname]))
            PT.fill(rect, fn)
        return
    if mat == "wood":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if fname in ("front", "back") and j % 3 == 1:
                    return ("glow", ICE)
                return light(fname, WOOD_D if (fname in ("front", "back") and j % 3 == 2) else WOOD)
            PT.fill(rect, fn)
        return
    if mat == "grip":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, AZURE_L if (i + j) % 2 == 0 else AZURE_D))
        return
    if mat == "string":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h: ("glow", (240, 250, 255)))
        return
    if mat == "shaft":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, (238, 244, 250)))
        return
    if mat in ("fletch", "fletch_w"):
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, AZURE_L if mat == "fletch" else SNOW))
        return
    if mat in ("silver", "silver_gem"):
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if mat == "silver_gem" and fname == "front" and i == w // 2 and j == h // 2:
                    return ("glow", ICE_L)
                return light(fname, SILVER if (i + j) % 3 else SILVER_D)
            PT.fill(rect, fn)
        return
    for fname, rect in F.items():
        PT.fill(rect, lambda i, j, w, h: (255, 0, 255))


for c in M.cubes():
    paint_cube(c)

# =====================================================================
# 3. Анимации
# =====================================================================
A_BONES = ["root", "body", "torso", "head", "hair_back", "hair_mid", "hair_tip", "cape",
           "right_arm", "right_fore", "left_arm", "left_fore", "bow", "bow_top", "bow_bot",
           "string_top", "string_bot", "bow_arrow", "bow_arrow_fan", "hand_arrow", "hand_arrow_fan",
           "right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot",
           "skirt_fr", "skirt_fl", "skirt_b", "skirt_sr", "skirt_sl", "quiver", "shards"]

# лук у бедра (тетива вверх) и «на боку» при стрельбе — ориентация в пространстве модели
BOW_IDLE = mm(rx_m(math.radians(104)), rz_m(math.radians(8)))
BOW_AIM = mm(ry_m(math.radians(-4)), rz_m(math.radians(-68)))


def bow_R(p):
    """Плавный поворот лука из положения у бедра (p=0) в боевое (p=1)."""
    return mm(rx_m(math.radians(104 * (1 - p))),
              mm(ry_m(math.radians(-4 * p)), rz_m(math.radians(8 * (1 - p) - 68 * p))))


def string_pose(pose, draw, flex):
    """Тетива: нок на draw px за тетивой покоя, плечи лука согнуты на flex°."""
    pose["bow_top"] = {"rot": (-flex, 0, 0)}
    pose["bow_bot"] = {"rot": (flex, 0, 0)}
    for limb, sgn in (("bow_top", 1), ("bow_bot", -1)):
        rx = math.radians(-flex if sgn > 0 else flex)
        Ly = 1.5 * sgn
        ty, tz = TIP[0] * sgn - Ly, TIP[1]
        c, s = math.cos(rx), math.sin(rx)
        wy, wz = Ly + ty * c + tz * s, -ty * s + tz * c
        dy, dz = 0.0 - wy, NOCK_Z0 + draw - wz
        ly, lz = dy * c - dz * s, dy * s + dz * c
        length = math.hypot(ly, lz)
        r = math.degrees(math.atan2(lz, -ly)) if sgn > 0 else math.degrees(math.atan2(-lz, ly))
        pose[limb.replace("bow_", "string_")] = {"rot": (r, 0, 0), "scale": (1, length / STRING_LEN, 1)}
    pose["bow_arrow"] = {"pos": (0, 0, draw - DRAW_MAX)}


def nock_world(pose, draw):
    return M.world("bow", [0, 0, NOCK_Z0 + draw], pose)


def hand_world(pose, side):
    return M.world(side + "_fore", [0, FORE, 0], pose)


def skirt_follow(pose, sway=0.0, flare=0.0):
    """Панели юбки следуют за бёдрами (вперёд — вместе с ногой, назад — задняя панель)."""
    rl = pose.get("right_leg", {}).get("rot", (0, 0, 0))[0]
    ll = pose.get("left_leg", {}).get("rot", (0, 0, 0))[0]
    pose["skirt_fr"] = {"rot": (min(rl, 0) * 0.85 + max(rl, 0) * 0.15 - flare, 0, sway)}
    pose["skirt_fl"] = {"rot": (min(ll, 0) * 0.85 + max(ll, 0) * 0.15 - flare, 0, sway)}
    pose["skirt_b"] = {"rot": (max(rl, ll, 0) * 0.7 + 3 + flare, 0, sway)}
    pose["skirt_sr"] = {"rot": (0, 0, 3 + flare * 1.2 + sway)}
    pose["skirt_sl"] = {"rot": (0, 0, -3 - flare * 1.2 + sway)}


def legs(pose, r, l):
    """r/l = (бедро, голень, стопа) в градусах; положительное — назад/носок вниз."""
    pose["right_leg"] = {"rot": (r[0], 0, r[3] if len(r) > 3 else 2)}
    pose["right_shin"] = {"rot": (r[1], 0, 0)}
    pose["right_foot"] = {"rot": (r[2], 0, 0)}
    pose["left_leg"] = {"rot": (l[0], 0, l[3] if len(l) > 3 else -2)}
    pose["left_shin"] = {"rot": (l[1], 0, 0)}
    pose["left_foot"] = {"rot": (l[2], 0, 0)}


def base_pose(t=0.0, animate=True):
    """Парение: ноги свободно свисают, носки вытянуты, лук у бедра."""
    w = 2 * math.pi * t / 3.0 if animate else 0.0
    sn = math.sin
    pose = {
        "body": {"rot": (1.5 * sn(w + 1.0), 0, 0), "pos": (0, -0.5 * sn(w), 0)},
        "torso": {"rot": (1.0 * sn(w + 0.6), 0, 0)},
        "head": {"rot": (2 * sn(w + 0.4), 0, 1.5 * sn(w - 0.6))},
        "hair_back": {"rot": (-5 + 2.5 * sn(w - 0.8), 0, 1.5 * sn(w - 0.3))},
        "hair_mid": {"rot": (4 + 3 * sn(w - 1.4), 0, 2 * sn(w - 0.9))},
        "hair_tip": {"rot": (5 + 4 * sn(w - 2.0), 0, 2.5 * sn(w - 1.5))},
        "cape": {"rot": (4 + 2.5 * sn(w - 0.9), 0, 0)},
        "quiver": {"rot": (2 * sn(w - 0.5), 0, 0)},
        "right_arm": {"rot": (3 * sn(w), 0, 9 + 2 * sn(w + 0.4))},
        "right_fore": {"rot": (-12, 0, 0)},
        "left_arm": {"rot": (-4 + 2 * sn(w + 0.7), 0, -10)},
        "left_fore": {"rot": (-22, 0, 0)},
    }
    legs(pose, (-7 + 3 * sn(w), 12 + 3 * sn(w + 0.5), 28 + 3 * sn(w + 1.0)),
         (4 + 3 * sn(w + 1.2), 18 + 3 * sn(w + 1.6), 32 + 3 * sn(w + 2.0)))
    skirt_follow(pose, sway=1.5 * sn(w - 0.7))
    M.set_world_rot(pose, "bow", BOW_IDLE)
    string_pose(pose, 0, 0)
    return pose


def move_pose(t, back=False):
    w = 2 * math.pi * t / 1.2
    sn = math.sin
    pose = base_pose(0, False)
    if not back:
        pose["body"] = {"rot": (8 + 1.5 * sn(2 * w), 0, 2.5 * sn(w)), "pos": (0, -0.35 * sn(2 * w), 0)}
        pose["torso"] = {"rot": (2, 5 * sn(w), 0)}
        pose["head"] = {"rot": (-7, -5 * sn(w), 0)}
        legs(pose, (-4 - 22 * sn(w), 14 + 14 * max(0.0, sn(w + 0.9)), 26 + 6 * sn(w + 1.5)),
             (-4 + 22 * sn(w), 14 + 14 * max(0.0, -sn(w + 0.9)), 26 - 6 * sn(w + 1.5)))
        pose["right_arm"] = {"rot": (14 * sn(w), 0, 9)}
        pose["right_fore"] = {"rot": (-14 - 6 * max(0.0, sn(w)), 0, 0)}
        pose["left_arm"] = {"rot": (-6 - 10 * sn(w), 0, -10)}
        pose["hair_back"] = {"rot": (10 + 3 * sn(w - 0.8), 0, 2 * sn(w - 0.4))}
        pose["hair_mid"] = {"rot": (14 + 4 * sn(w - 1.5), 0, 3 * sn(w - 1.0))}
        pose["hair_tip"] = {"rot": (16 + 5 * sn(w - 2.2), 0, 4 * sn(w - 1.6))}
        pose["cape"] = {"rot": (22 + 5 * sn(w - 0.9), 0, 2 * sn(w - 0.6))}
        skirt_follow(pose, sway=2 * sn(w - 0.8), flare=-4)
    else:
        pose["body"] = {"rot": (-9 + 1.5 * sn(2 * w), 0, 2 * sn(w)), "pos": (0, -0.35 * sn(2 * w), 0)}
        pose["head"] = {"rot": (8, 0, 0)}
        legs(pose, (-20 - 8 * sn(w), 20 + 8 * sn(w + 0.8), 18 + 4 * sn(w + 1.4)),
             (-14 + 8 * sn(w), 24 - 8 * sn(w + 0.8), 20 - 4 * sn(w + 1.4)))
        pose["right_arm"] = {"rot": (-18 + 4 * sn(w), 0, 16)}
        pose["right_fore"] = {"rot": (-20, 0, 0)}
        pose["left_arm"] = {"rot": (-14 + 4 * sn(w + 0.9), 0, -12)}
        pose["hair_back"] = {"rot": (-16 + 3 * sn(w - 0.8), 0, 2 * sn(w - 0.4))}
        pose["hair_mid"] = {"rot": (-10 + 4 * sn(w - 1.5), 0, 3 * sn(w - 1.0))}
        pose["hair_tip"] = {"rot": (-10 + 5 * sn(w - 2.2), 0, 4 * sn(w - 1.6))}
        pose["cape"] = {"rot": (-2 + 3 * sn(w - 0.9), 0, 0)}
        skirt_follow(pose, sway=2 * sn(w - 0.8), flare=3)
    M.set_world_rot(pose, "bow", BOW_IDLE)
    string_pose(pose, 0, 0)
    return pose


# --- атака (секунды); тики в FrostArcherEntity: звук натяжения 17, выстрел 32, всего 50 ---
REACH = 0.35        # рука у колчана — стрела появляется в руке
PULL = 0.6          # стрела вынута, рука у груди
NOCK = 0.85         # стрела на тетиве (в руке скрыта, на луке видна)
DRAW_END = 1.5
RELEASE = 1.6
SPIN_END = 2.25     # после выстрела: отлёт назад с оборотом вокруг себя
ATTACK_LEN = 2.5

GRIP_TARGET = [1.0, -1.0, -10.6]       # куда вытянута левая рука с луком (Java, модель)
GRIP_NOCK = [1.2, -0.6, -7.4]          # лук ближе к себе, пока стрела ложится на тетиву
POLE_LEFT = [1.0, 0.6, 0.2]            # локоть левой руки — вниз и наружу
POLE_DRAW = [-1.0, -0.8, 1.3]          # локоть правой при натяжении — вверх и назад
POLE_REACH = [-1.0, 0.2, 1.0]          # к колчану — наружу и назад


def stance(pose, s, lean=0.0):
    """Стойка лучницы, s = 0..1 — насколько повернулась (левое плечо к цели)."""
    pose["body"] = {"rot": (lerp(0, lean, s), 0, 0), "pos": (0, 0, 0)}
    pose["torso"] = {"rot": (-2 * s, 34 * s, 0)}
    pose["head"] = {"rot": (-3 * s, -30 * s, 2 * s)}
    pose["cape"] = {"rot": (4 + 4 * s, 0, 0)}
    pose["hair_back"] = {"rot": (-5 + 3 * s, -10 * s, 0)}
    pose["hair_mid"] = {"rot": (4, 0, 0)}
    pose["hair_tip"] = {"rot": (5, 0, 0)}
    legs(pose, (lerp(-7, -12, s), lerp(12, 18, s), lerp(28, 30, s), lerp(2, 6, s)),
         (lerp(4, 8, s), lerp(18, 26, s), lerp(32, 34, s), lerp(-2, -4, s)))
    skirt_follow(pose)


def aim_left(pose, s):
    """Левая рука с луком: от бедра (s=0) к положению для накладывания стрелы (s=1)."""
    rest = base_pose(0, False)
    if s <= 0:
        pose["left_arm"], pose["left_fore"] = rest["left_arm"], rest["left_fore"]
    else:
        hand_rest = hand_world(dict(pose, left_arm=rest["left_arm"], left_fore=rest["left_fore"]), "left")
        target = lerp3(hand_rest, GRIP_NOCK, ease(s))
        M.two_bone(pose, "left_arm", "left_fore", target, POLE_LEFT, UPPER, FORE)
    M.set_world_rot(pose, "bow", bow_R(ease(s)))


def quiver_mouth(pose):
    return M.world("quiver", M.local_of("quiver", QUIVER_MOUTH), pose)


def shot_pose(t, d, flex, lean, grip, tremble=0.0):
    pose = base_pose(0, False)
    stance(pose, 1.0, lean=lean)
    M.two_bone(pose, "left_arm", "left_fore", add(grip, [0, tremble * 0.08, 0]), POLE_LEFT, UPPER, FORE)
    M.set_world_rot(pose, "bow", BOW_AIM)
    string_pose(pose, d, flex)
    return pose


def attack_pose(t):
    rest = base_pose(0, False)
    if t < NOCK:
        # достать стрелу из колчана и положить на тетиву, одновременно встать в стойку
        s = ease((t - REACH * 0.6) / (NOCK - REACH * 0.6)) if t > REACH * 0.6 else 0.0
        pose = base_pose(0, False)
        stance(pose, s, lean=-2)
        if t < REACH:
            q = math.sin(math.pi * min(1.0, t / REACH)) if t < REACH else 0.0
            pose["torso"] = {"rot": (2 * q, -12 * q + 34 * s, 4 * q)}     # тянется к колчану
            pose["head"] = {"rot": (6 * q, -10 * q - 30 * s, 0)}
        aim_left(pose, s)
        string_pose(pose, 0, 0)
        hand_rest = hand_world(dict(pose, right_arm=rest["right_arm"], right_fore=rest["right_fore"]), "right")
        mouth = quiver_mouth(pose)
        chest = [-0.5, 6.5, -6.0]
        nock = nock_world(pose, 0)
        if t < REACH:
            target = lerp3(hand_rest, mouth, ease(t / REACH))
            pole = POLE_REACH
        elif t < PULL:
            q = ease_out((t - REACH) / (PULL - REACH))
            up = add(mouth, [0.5, -4.0, -0.5])
            target = lerp3(mouth, up, q) if q < 0.5 else lerp3(up, chest, (q - 0.5) * 2)
            pole = POLE_REACH
        else:
            q = ease((t - PULL) / (NOCK - PULL))
            target = lerp3(chest, nock, q)
            pole = lerp3(POLE_REACH, POLE_DRAW, q)
        M.two_bone(pose, "right_arm", "right_fore", target, pole, UPPER, FORE)
        return pose

    if t < RELEASE:
        # натяжение до щеки (лук выдвигается вперёд), затем лёгкая дрожь перед выстрелом
        push = ease_out(min(1.0, (t - NOCK) / ((DRAW_END - NOCK) * 0.55)))
        grip = lerp3(GRIP_NOCK, GRIP_TARGET, push)
        if t < DRAW_END:
            p = (t - NOCK) / (DRAW_END - NOCK)
            d, flex, tremble = DRAW_MAX * ease_out(p), 8 * ease_out(p), 0.0
        else:
            tremble = math.sin(t * 90)
            d, flex = DRAW_MAX + 0.12 * tremble, 8.0
        pose = shot_pose(t, d, flex, -2 - 2 * (d / DRAW_MAX), grip, tremble)
        M.two_bone(pose, "right_arm", "right_fore", nock_world(pose, d), POLE_DRAW, UPPER, FORE)
        return pose

    # выстрел: тетива щёлкает и дрожит, лук пружинит, правая рука уходит назад;
    # отдача отбрасывает назад — и она делает оборот вокруг себя, волосы и юбка разлетаются
    tp = t - RELEASE
    if tp < 0.025:
        d, flex = DRAW_MAX * (1 - tp / 0.025), 8 * (1 - tp / 0.025)
    else:
        q = tp - 0.025
        d = -1.6 * math.exp(-q * 11) * math.sin(q * 70 + 0.4)
        flex = -3.0 * math.exp(-q * 10) * math.sin(q * 60 + 0.5)
    recoil = math.exp(-tp * 9) * math.sin(min(math.pi / 2, tp * 40))
    pose = shot_pose(t, d, flex, -4 - 10 * recoil, GRIP_TARGET)
    pose["bow"]["rot"] = add(pose["bow"]["rot"], [6 * recoil, 0, 0])
    pose["cape"] = {"rot": (8 + 14 * recoil, 0, 0)}
    nock = nock_world(pose, 0)
    back = add(nock, [-3.0, -1.2, 4.5])
    M.two_bone(pose, "right_arm", "right_fore", lerp3(nock, back, ease_out(min(1.0, tp / 0.12))),
               POLE_DRAW, UPPER, FORE)

    spin_start = RELEASE + 0.1
    if t >= spin_start:
        p = min(1.0, (t - spin_start) / (SPIN_END - spin_start))
        e = ease(p)
        b = math.sin(math.pi * p)
        # к концу оборота поза плавно приходит в покой
        pose = blend(pose, rest, ease(min(1.0, p * 1.3)))
        M.set_world_rot(pose, "bow", bow_R(1 - ease(min(1.0, p * 1.3))))
        string_pose(pose, d * (1 - p), flex * (1 - p))
        pose["root"] = {"rot": (0, 360 * e, 0)}                 # оборот в сторону отдачи

        def addrot(name, dx=0.0, dy=0.0, dz=0.0):
            r = pose.setdefault(name, {}).get("rot", (0, 0, 0))
            pose[name]["rot"] = [r[0] + dx, r[1] + dy, r[2] + dz]

        addrot("body", -6 * b, 0, -8 * b)
        addrot("hair_back", 40 * b, -25 * b, -8 * b)
        addrot("hair_mid", 22 * b, 0, -6 * b)
        addrot("hair_tip", 18 * b, 0, -6 * b)
        addrot("cape", 40 * b, 0, 10 * b)
        addrot("right_arm", 0, 0, 45 * b)
        addrot("left_arm", 0, 0, -30 * b)
        addrot("right_leg", -25 * b)
        addrot("right_shin", 50 * b)
        addrot("right_foot", 8 * b)
        addrot("left_leg", 6 * b)
        addrot("left_shin", 10 * b)
        M.set_world_rot(pose, "bow", bow_R(1 - ease(min(1.0, p * 1.3))))
        skirt_follow(pose, flare=28 * b)
        if t >= SPIN_END:
            pose = blend(pose, rest, ease((t - SPIN_END) / (ATTACK_LEN - SPIN_END)))
            pose["root"] = {"rot": (0, 0, 0)}
            M.set_world_rot(pose, "bow", BOW_IDLE)
            string_pose(pose, 0, 0)
    return pose


# «Ледяной веер»: та же атака, но три стрелы и натяжение дольше (тики в FrostArcherEntity: выстрел 46, всего 64)
VOLLEY_LEN = 3.2
VOLLEY_RELEASE = 2.3


def volley_pose(t):
    if t < NOCK:
        ta = t
    elif t < VOLLEY_RELEASE:
        ta = NOCK + (t - NOCK) * (RELEASE - NOCK) / (VOLLEY_RELEASE - NOCK)
    else:
        ta = RELEASE + (t - VOLLEY_RELEASE) * (ATTACK_LEN - RELEASE) / (VOLLEY_LEN - VOLLEY_RELEASE)
    return attack_pose(ta)


def shards(pose, t):
    pose["shards"] = {"rot": (0, -360 * (t % 3.0) / 3.0, 0), "pos": (0, -0.6 * math.sin(2 * math.pi * t / 1.5), 0)}


CLIPS = [
    ("idle", lambda t: base_pose(t), 3.0, 20, True),
    ("move", lambda t: move_pose(t), 1.2, 20, True),
    ("move_back", lambda t: move_pose(t, True), 1.2, 20, True),
    ("attack", attack_pose, ATTACK_LEN, 40, False),
    ("volley", volley_pose, VOLLEY_LEN, 40, False),
]
animations = {"animation.%s.%s" % (NAME, n): bake(fn, ln, fps, lp, A_BONES, shards if n == "idle" else None)
              for n, fn, ln, fps, lp in CLIPS}

# =====================================================================
# 4. Прочие текстуры: стрела, иконка эффекта, иней на экране
# =====================================================================

def arrow_texture():
    """Текстура ледяной стрелы в раскладке ванильной стрелы 32×32 (бок 16×5, торец 5×5)."""
    a = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = a.load()
    for x in range(16):
        # древко
        p[x, 2] = (236, 246, 255, 255) if x % 3 else (190, 225, 250, 255)
    for x in range(1, 5):                     # оперение (хвост, слева)
        for y in (0, 1, 3, 4):
            if (x + y) % 2 == 0 or y in (1, 3):
                p[x, y] = (60, 150, 220, 255) if y in (1, 3) else (120, 200, 245, 255)
    p[0, 2] = (40, 110, 180, 255)
    # ледяной наконечник (справа)
    for x, ys, col in ((12, (1, 2, 3), (110, 210, 250)), (13, (0, 1, 2, 3, 4), (160, 235, 255)),
                       (14, (1, 2, 3), (210, 248, 255)), (15, (2,), (255, 255, 255))):
        for y in ys:
            p[x, y] = col + (255,)
    # торец
    for x in range(5):
        for y in range(5, 10):
            if x in (1, 3) or y in (6, 8):
                p[x, y] = (90, 180, 235, 255)
    p[2, 7] = (220, 248, 255, 255)
    a.save(asset_groups.tex("frost_arrow.png", ROOT))


def effect_icon():
    """Иконка эффекта 18×18: шестилучевая снежинка с веточками."""
    a = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    p = a.load()
    c = 8.5

    def dot(x, y, col):
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < 18 and 0 <= yi < 18:
            p[xi, yi] = col + (255,)

    for k in range(6):
        ang = math.pi / 2 + k * math.pi / 3
        dx, dy = math.cos(ang), -math.sin(ang)
        for r10 in range(0, 76):
            r = r10 / 10
            dot(c + dx * r, c + dy * r, (235, 250, 255) if r < 5 else (150, 222, 255))
        for br in (3.5, 5.5):
            for side in (-1, 1):
                ba = ang + side * math.pi / 4
                for r10 in range(0, 18):
                    r = r10 / 10
                    dot(c + dx * br + math.cos(ba) * r, c + dy * br - math.sin(ba) * r, (110, 200, 245))
    for x, y in ((8, 8), (9, 8), (8, 9), (9, 9)):
        p[x, y] = (255, 255, 255, 255)
    os.makedirs(os.path.join(ROOT, "textures", "mob_effect"), exist_ok=True)
    a.save(os.path.join(ROOT, "textures", "mob_effect", "freeze.png"))


def overlay():
    """Морозная рамка экрана 256×256: иней по краям, к центру прозрачно."""
    s = 256
    a = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = a.load()
    r = random.Random(5)
    for y in range(s):
        for x in range(s):
            dx, dy = (x - s / 2) / (s / 2), (y - s / 2) / (s / 2)
            d = max(abs(dx), abs(dy)) * 0.65 + math.hypot(dx, dy) * 0.35
            t = max(0.0, (d - 0.55) / 0.45)
            t = t ** 1.6
            n = r.random()
            alpha = min(1.0, t * (0.75 + 0.5 * n))
            if alpha <= 0.02:
                continue
            col = mix((175, 222, 250), (250, 254, 255), min(1.0, t * 1.2 + n * 0.2))
            p[x, y] = (col[0], col[1], col[2], clamp(alpha * 235))
    # кристаллы инея — лучи от краёв
    for _ in range(90):
        side = r.randrange(4)
        pos = r.uniform(0, s)
        x, y = [(pos, 0), (pos, s - 1), (0, pos), (s - 1, pos)][side]
        ang = [math.pi / 2, -math.pi / 2, 0, math.pi][side] + r.uniform(-0.7, 0.7)
        length = r.uniform(12, 48)
        for k in range(int(length)):
            xx, yy = int(x + math.cos(ang) * k), int(y + math.sin(ang) * k)
            if 0 <= xx < s and 0 <= yy < s:
                fade = 1 - k / length
                cur = p[xx, yy]
                p[xx, yy] = (255, 255, 255, max(cur[3], clamp(200 * fade)))
    os.makedirs(os.path.join(ROOT, "textures", "misc"), exist_ok=True)
    a.save(os.path.join(ROOT, "textures", "misc", "freeze_overlay.png"))


if __name__ == "__main__":
    # лук (с тетивой и стрелами на нём) — отдельной моделью frost_archer_bow с дорожкой поворота
    import weaponize
    write_anims(ROOT, NAME, weaponize.export(ROOT, M, animations, CLIPS, "bow", NAME + "_bow"))
    PT.img.save(asset_groups.tex(NAME + ".png", ROOT))
    PT.glow.save(asset_groups.tex(NAME + "_glowmask.png", ROOT))
    arrow_texture()
    effect_icon()
    print("готово:", NAME, "кубов:", len(M.cubes()))
