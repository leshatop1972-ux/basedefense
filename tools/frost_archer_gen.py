"""
Генератор морозной лучницы (frost_archer): модель Bedrock (.geo.json), анимации (.animation.json),
текстура и светящаяся маска, текстура ледяной стрелы, иконка эффекта «Заморозка» и оверлей экрана.

Запуск:  python tools/frost_archer_gen.py
Проверка без игры:  python tools/mob_preview.py frost_archer attack

Модель описана здесь же (кости и кубы в координатах Bedrock, y вверх, лицо смотрит в −z).
Анимации считаются кодом: позы рук и лука вычисляются обратной кинематикой, чтобы правая рука
держала тетиву, а лук — левая. Повороты костей в Bedrock и в Java совпадают (см. BedrockModelLoader),
поэтому математика ведётся в Java-пространстве (x, 24 − y, z).
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
NAME = "frost_archer"
TEX_W, TEX_H = 128, 128
RND = random.Random(1972)

# =====================================================================
# 1. Модель
# =====================================================================
BONES = []          # по порядку: родитель раньше ребёнка
BONE = {}


def bone(name, parent, pivot):
    b = {"name": name, "pivot": list(pivot), "cubes": []}
    if parent:
        b["parent"] = parent
    BONES.append(b)
    BONE[name] = b
    return b


def cube(bname, origin, size, mat, inflate=0.0, rot=None, pivot=None, face=None):
    c = {"origin": [round(v, 4) for v in origin], "size": [round(v, 4) for v in size], "mat": mat}
    if inflate:
        c["inflate"] = inflate
    if rot:
        c["rotation"] = [round(v, 3) for v in rot]
        c["pivot"] = [round(v, 4) for v in pivot]
    if face:
        c["face"] = face
    BONE[bname]["cubes"].append(c)
    return c


# --- туловище и подол (вместо ног — парящий рваный подол) ---
bone("root", None, (0, 0, 0))
bone("body", "root", (0, 12, 0))
cube("body", (-4, 11, -2.5), (8, 5, 5), "robe")
cube("body", (-4.5, 15, -3), (9, 2, 6), "leather", face="belt")
cube("body", (-1, 15, -3.4), (2, 2, 1), "silver")

bone("robe1", "body", (0, 11, 0))
cube("robe1", (-4.5, 7, -3), (9, 4, 6), "robe", face="hem_trim")
cube("robe1", (-2, 5, -3.5), (4, 6, 1), "azure", face="tabard")
bone("robe2", "robe1", (0, 7, 0))
cube("robe2", (-4, 3, -2.5), (8, 4, 5), "robe", face="hem_trim")
bone("robe3", "robe2", (0, 3, 0))
cube("robe3", (-3.5, -1, -2), (7, 4, 4), "robe_rag", face="frayed")

bone("torso", "body", (0, 17, 0))
cube("torso", (-4, 17, -2.5), (8, 7, 5), "robe", face="chest")
cube("torso", (-5, 21, -3.5), (10, 3, 7), "fur", inflate=0.2)

bone("cape", "torso", (0, 23, 2.6))
cube("cape", (-4.5, 11, 2.6), (9, 12, 1), "azure", face="cape")

bone("quiver", "torso", (2.5, 20, 3.8))
cube("quiver", (1, 13, 3.8), (3, 9, 3), "leather", face="quiver", rot=(0, 0, -18), pivot=(2.5, 17.5, 5.3))
cube("quiver", (1.4, 21.5, 4.3), (1, 3, 1), "azure", rot=(0, 0, -18), pivot=(2.5, 17.5, 5.3))
cube("quiver", (2.6, 21.0, 5.0), (1, 3, 1), "white", rot=(0, 0, -18), pivot=(2.5, 17.5, 5.3))

# --- голова под капюшоном ---
bone("head", "torso", (0, 24, 0))
cube("head", (-3.5, 24, -3.5), (7, 7, 7), "skin", face="face")
cube("head", (-4, 23.5, -4), (8, 2, 8), "fur", inflate=0.1)
cube("head", (-4.5, 30.5, -4.5), (9, 2, 9), "hood")
cube("head", (-4.5, 24, 2.5), (9, 7, 2), "hood")
cube("head", (3.5, 24, -4.5), (1, 7, 7), "hood")
cube("head", (-4.5, 24, -4.5), (1, 7, 7), "hood")
cube("head", (-4.5, 29.5, -5), (9, 1, 1), "azure", face="brim")
cube("head", (-1.5, 27, 4.5), (3, 4, 2), "hood", rot=(20, 0, 0), pivot=(0, 30, 4.5))
# ледяная корона на капюшоне
cube("head", (-0.5, 31.5, -5), (1, 3, 1), "ice", rot=(-12, 0, 0), pivot=(0, 31.5, -4.5))
cube("head", (2, 31, -4.8), (1, 2, 1), "ice", rot=(0, 0, -15), pivot=(2.5, 31, -4.3))
cube("head", (-3, 31, -4.8), (1, 2, 1), "ice", rot=(0, 0, 15), pivot=(-2.5, 31, -4.3))
cube("head", (3.8, 30, -2), (1, 2, 1), "ice", rot=(0, 0, -32), pivot=(4.3, 30, -1.5))
cube("head", (-4.8, 30, -2), (1, 2, 1), "ice", rot=(0, 0, 32), pivot=(-4.3, 30, -1.5))

# --- руки ---
for side, sx in (("right", -1), ("left", 1)):
    b = side + "_arm"
    bone(b, "torso", (5 * sx, 23, 0))
    x0 = 4 if sx > 0 else -7
    cube(b, (x0, 14, -1.5), (3, 9, 3), "robe", face="sleeve")
    cube(b, (x0 - 0.5, 21, -2), (4, 3, 4), "fur", inflate=0.1)
    cube(b, (x0 - 0.25, 14.5, -1.75), (3.5, 3, 3.5), "leather", face="bracer")
    cube(b, (x0 + 0.25, 13, -1.25), (2.5, 1.5, 2.5), "glove")

# --- лук (в левой руке) ---
P = (5.5, 13.5, 0.0)            # точка хвата
LIMB_PTS = [(1.5, 0.0), (4.0, 0.4), (6.5, 1.4), (8.6, 2.7), (9.8, 2.4)]   # (y, z) от хвата
TIP = LIMB_PTS[-1]
STRING_LEN = TIP[0]             # половина тетивы: от кончика до уровня хвата
NOCK_Z0 = TIP[1]                # тетива в покое
DRAW_MAX = 6.0                  # насколько оттягивается тетива (px)
ARROW_BACK = 8.4                # хвост стрелы при полном натяжении (= NOCK_Z0 + DRAW_MAX)

bone("bow", "left_arm", P)
cube("bow", (P[0] - 0.5, P[1] - 1.5, P[2] - 0.75), (1, 3, 1.5), "glove", face="grip")
cube("bow", (P[0] - 0.75, P[1] - 0.5, P[2] - 1.25), (1.5, 1, 0.5), "ice")

for lname, sgn in (("bow_top", 1), ("bow_bot", -1)):
    bone(lname, "bow", (P[0], P[1] + 1.5 * sgn, P[2]))
    pts = [(P[1] + y * sgn, P[2] + z) for y, z in LIMB_PTS]
    for i in range(len(pts) - 1):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        dy, dz = y1 - y0, z1 - z0
        length = math.hypot(dy, dz)
        rx = math.degrees(math.atan2(-dz, dy))          # локальная +y → (dy, dz)
        th = 0.9 if i < 2 else 0.7
        cube(lname, (P[0] - 0.5, y0, z0 - th / 2), (1, length, th), "wood", rot=(rx, 0, 0), pivot=(P[0], y0, z0))
    ty, tz = pts[-1]
    cube(lname, (P[0] - 0.5, ty - 0.5, tz - 0.5), (1, 1, 1), "ice")
    s = lname.replace("bow_", "string_")
    bone(s, lname, (P[0], ty, tz))
    if sgn > 0:
        cube(s, (P[0] - 0.15, ty - STRING_LEN, tz - 0.15), (0.3, STRING_LEN, 0.3), "string")
    else:
        cube(s, (P[0] - 0.15, ty, tz - 0.15), (0.3, STRING_LEN, 0.3), "string")

bone("bow_arrow", "bow", P)
cube("bow_arrow", (P[0] - 0.25, P[1] - 0.25, P[2] - 3.5), (0.5, 0.5, 12), "shaft")
cube("bow_arrow", (P[0] - 0.5, P[1] - 0.5, P[2] - 5), (1, 1, 1.5), "ice")
cube("bow_arrow", (P[0] - 0.1, P[1] - 1, P[2] + 6.4), (0.2, 2, 2), "azure")
cube("bow_arrow", (P[0] - 1, P[1] - 0.1, P[2] + 6.4), (2, 0.2, 2), "azure")

# --- ледяные осколки, кружат вокруг ---
bone("shards", "root", (0, 19, 0))
cube("shards", (8, 18, -0.5), (1, 3, 1), "ice", rot=(0, 45, 20), pivot=(8.5, 19.5, 0))
cube("shards", (-9, 21, -0.5), (1, 2, 1), "ice", rot=(0, 45, -25), pivot=(-8.5, 22, 0))
cube("shards", (-0.5, 16, 8), (1, 2, 1), "ice", rot=(30, 45, 0), pivot=(0, 17, 8.5))

# =====================================================================
# 2. Развёртка UV (box UV, полки)
# =====================================================================


def footprint(c):
    w, h, d = (max(1, math.ceil(s - 1e-6)) for s in c["size"])
    return w, h, d, 2 * (w + d), h + d


def pack():
    cubes = [c for b in BONES for c in b["cubes"]]
    order = sorted(cubes, key=lambda c: (-footprint(c)[4], -footprint(c)[3]))
    x = y = row_h = 0
    for c in order:
        fw, fh = footprint(c)[3:5]
        if x + fw > TEX_W:
            x, y, row_h = 0, y + row_h, 0
        c["uv"] = [x, y]
        x += fw
        row_h = max(row_h, fh)
    assert y + row_h <= TEX_H, "текстура мала: %d" % (y + row_h)


pack()

# =====================================================================
# 3. Текстура
# =====================================================================
img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
glow = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
PX, GL = img.load(), glow.load()


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(ch * k) for ch in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def put(x, y, col, a=255):
    if 0 <= x < TEX_W and 0 <= y < TEX_H:
        PX[x, y] = (col[0], col[1], col[2], a)


def glow_put(x, y, col):
    put(x, y, col)
    GL[x, y] = (col[0], col[1], col[2], 255)


WHITE = (236, 242, 248)
SNOW = (246, 249, 252)
PALE = (205, 222, 236)
SHADOW = (150, 176, 204)
AZURE = (58, 146, 214)
AZURE_L = (112, 190, 240)
AZURE_D = (30, 86, 150)
NAVY = (22, 44, 78)
ICE = (150, 228, 255)
ICE_L = (225, 250, 255)
ICE_D = (70, 170, 225)
SILVER = (196, 208, 222)
SKIN = (196, 220, 236)
WOOD = (222, 228, 236)
WOOD_D = (160, 176, 196)

FACE_LIGHT = {"top": 1.07, "bottom": 0.72, "front": 1.0, "back": 0.86, "right": 0.92, "left": 0.92}


def faces(c):
    w, h, d = footprint(c)[:3]
    u, v = c["uv"]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def noise(k=0.06):
    return 1 + (RND.random() - 0.5) * 2 * k


def paint_cloth(rect, fname, base, trim=None, trim_rows=0, weave=True, edge=0.9):
    x0, y0, w, h = rect
    L = FACE_LIGHT[fname]
    for j in range(h):
        for i in range(w):
            col = base
            if weave and (i + j) % 2 == 0:
                col = mul(col, 0.97)
            g = 1.0 - 0.10 * (j / max(1, h - 1)) if fname not in ("top", "bottom") else 1.0
            if trim and fname not in ("top", "bottom") and j >= h - trim_rows:
                col = trim if j < h - 1 else mul(trim, 0.8)
            k = L * g * noise(0.035)
            if i in (0, w - 1) and fname not in ("top", "bottom"):
                k *= edge
            put(x0 + i, y0 + j, mul(col, k))


def paint_fur(rect, fname):
    x0, y0, w, h = rect
    L = FACE_LIGHT[fname]
    for j in range(h):
        for i in range(w):
            t = RND.random()
            col = SNOW if t > 0.35 else (WHITE if t > 0.1 else PALE)
            if fname not in ("top", "bottom") and j == h - 1 and (i % 2 == 0):
                col = PALE
            put(x0 + i, y0 + j, mul(col, L * (1.0 - 0.06 * j / max(1, h))))


def paint_leather(rect, fname, stitch=True):
    x0, y0, w, h = rect
    L = FACE_LIGHT[fname]
    for j in range(h):
        for i in range(w):
            col = AZURE_D
            if stitch and fname not in ("top", "bottom") and (j == 0 or j == h - 1) and i % 2 == 0 and h > 2:
                col = AZURE_L
            put(x0 + i, y0 + j, mul(col, L * noise(0.05)))


def paint_ice(rect, fname):
    x0, y0, w, h = rect
    for j in range(h):
        for i in range(w):
            t = (i + j) / max(1, w + h - 2)
            col = mix(ICE_L, ICE_D, t)
            if (i == 0 and j == 0) or RND.random() < 0.08:
                col = ICE_L
            glow_put(x0 + i, y0 + j, mul(col, 0.9 + 0.1 * FACE_LIGHT[fname]))


def paint_solid(rect, fname, col, glowing=False):
    x0, y0, w, h = rect
    for j in range(h):
        for i in range(w):
            c = mul(col, FACE_LIGHT[fname] * noise(0.03))
            (glow_put if glowing else put)(x0 + i, y0 + j, c)


def paint_face(c):
    f = faces(c)
    for fname, rect in f.items():
        x0, y0, w, h = rect
        if fname != "front":
            # под капюшоном: тёмные бока и затылок (почти не видны)
            paint_solid(rect, fname, (40, 62, 92))
            continue
        for j in range(h):
            for i in range(w):
                # тень от капюшона сверху, бледно‑голубая кожа ниже
                t = j / (h - 1)
                col = mix((34, 52, 84), SKIN, min(1.0, max(0.0, (t - 0.05) / 0.45)))
                if j >= h - 2:                       # шарф‑маска на нижней части лица
                    col = AZURE if j == h - 2 else AZURE_D
                    if i % 2 == 0:
                        col = mul(col, 1.08)
                put(x0 + i, y0 + j, mul(col, noise(0.02)))
        # глаза: светящиеся, 2×1 с бликом, на уровне тени
        ey = y0 + 3
        for ex in (x0 + 1, x0 + w - 3):
            glow_put(ex, ey, (160, 236, 255))
            glow_put(ex + 1, ey, (232, 252, 255))
            put(ex, ey - 1, (26, 40, 66))
            put(ex + 1, ey - 1, (26, 40, 66))
        # тень носа и скул
        put(x0 + w // 2, y0 + 4, mul(SKIN, 0.82))
        put(x0, y0 + 4, mul(SKIN, 0.85))
        put(x0 + w - 1, y0 + 4, mul(SKIN, 0.85))


def paint_cube(c):
    mat, face = c["mat"], c.get("face")
    if face == "face":
        paint_face(c)
        return
    for fname, rect in faces(c).items():
        x0, y0, w, h = rect
        if mat == "robe":
            trim_rows = 0
            trim = None
            if face in ("hem_trim", "sleeve"):
                trim, trim_rows = AZURE, 1
            paint_cloth(rect, fname, WHITE, trim, trim_rows)
            if face == "chest" and fname == "front":
                # V‑образная лазурная оторочка и руна в центре
                for j in range(h):
                    for i in range(w):
                        if abs(i - (w - 1) / 2) == round(3 - j * 0.6) and j < 5:
                            put(x0 + i, y0 + j, AZURE)
                glow_put(x0 + w // 2 - 1, y0 + 4, ICE_L)
                glow_put(x0 + w // 2, y0 + 4, ICE)
                glow_put(x0 + w // 2 - 1, y0 + 5, ICE)
                glow_put(x0 + w // 2, y0 + 5, ICE_L)
            if face == "sleeve" and fname in ("front", "left", "right", "back"):
                put(x0, y0 + 2, PALE)
        elif mat == "robe_rag":
            paint_cloth(rect, fname, WHITE)
            if fname not in ("top", "bottom"):
                # рваный край: нижние пиксели прозрачны зубцами, лазурная полоса выше
                for i in range(w):
                    cut = (i * 7 + len(fname) * 3) % 3
                    for j in range(h - cut, h):
                        PX[x0 + i, y0 + j] = (0, 0, 0, 0)
                    if h - cut - 1 >= 0:
                        put(x0 + i, y0 + h - cut - 1, mul(AZURE_L, FACE_LIGHT[fname]))
                    put(x0 + i, y0 + 1, mul(AZURE, FACE_LIGHT[fname]))
        elif mat == "azure":
            paint_cloth(rect, fname, AZURE, mul(WHITE, 0.98), 1 if face in ("cape", "tabard") else 0)
            if face == "cape" and fname == "back":
                # снежинка‑узор на плаще
                cx, cy = x0 + w // 2, y0 + 4
                for dx, dy in ((0, 0), (0, -2), (0, 2), (-2, 0), (2, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
                    put(cx + dx, cy + dy, WHITE)
                glow_put(cx, cy, ICE_L)
            if face == "brim":
                for i in range(w):
                    if i % 2 == 0:
                        put(x0 + i, y0, AZURE_L)
        elif mat == "fur":
            paint_fur(rect, fname)
        elif mat == "hood":
            paint_fur(rect, fname)
            if fname in ("back", "right", "left") and c["size"][0] <= 1.01:
                pass
        elif mat == "leather":
            paint_leather(rect, fname, stitch=face != "quiver")
            if face == "quiver" and fname in ("front", "back", "left", "right"):
                for j in (1, h - 2):
                    for i in range(w):
                        put(x0 + i, y0 + j, mul(SILVER, FACE_LIGHT[fname]))
            if face == "belt" and fname == "front":
                for i in range(w):
                    put(x0 + i, y0, mul(AZURE_L, 0.95))
        elif mat == "glove":
            paint_leather(rect, fname, stitch=False)
            if face == "grip" and fname in ("front", "back", "left", "right"):
                for j in range(h):
                    put(x0 + (j % max(1, w)), y0 + j, AZURE_L)
        elif mat == "silver":
            paint_solid(rect, fname, SILVER)
            if fname == "front":
                glow_put(x0 + w // 2, y0 + h // 2, ICE_L)
        elif mat == "ice":
            paint_ice(rect, fname)
        elif mat == "wood":
            paint_solid(rect, fname, WOOD)
            if fname in ("front", "back"):
                for j in range(h):
                    if j % 3 == 1:
                        glow_put(x0, y0 + j, ICE)
                    elif j % 3 == 2:
                        put(x0, y0 + j, WOOD_D)
        elif mat == "string":
            paint_solid(rect, fname, (240, 250, 255), glowing=True)
        elif mat == "shaft":
            paint_solid(rect, fname, (238, 244, 250))
        elif mat == "white":
            paint_solid(rect, fname, SNOW)
        elif mat == "skin":
            paint_solid(rect, fname, SKIN)
        else:
            paint_solid(rect, fname, (255, 0, 255))


for b in BONES:
    for c in b["cubes"]:
        paint_cube(c)

# =====================================================================
# 4. Анимации (обратная кинематика в Java‑пространстве)
# =====================================================================


def rx_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def tr(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def euler(deg):
    x, y, z = (math.radians(d) for d in deg)
    return mm(rz_m(z), mm(ry_m(y), rx_m(x)))


def to_euler(R):
    """R = Rz·Ry·Rx → (x, y, z) в градусах."""
    sy = max(-1.0, min(1.0, -R[2][0]))
    b = math.asin(sy)
    a = math.atan2(R[2][1], R[2][2])
    c = math.atan2(R[1][0], R[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def norm(v):
    l = math.sqrt(sum(x * x for x in v)) or 1
    return [x / l for x in v]


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def scl(a, k):
    return [x * k for x in a]


def jpos(p):
    return [p[0], 24 - p[1], p[2]]


PIV = {b["name"]: jpos(b["pivot"]) for b in BONES}
PARENT = {b["name"]: b.get("parent") for b in BONES}


def rel_pivot(name):
    p = PARENT[name]
    return sub(PIV[name], PIV[p]) if p else PIV[name]


def chain_R(name, pose):
    """Полный поворот кости в пространстве модели."""
    R = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    path = []
    n = name
    while n:
        path.append(n)
        n = PARENT[n]
    for n in reversed(path):
        R = mm(R, euler(pose.get(n, {}).get("rot", (0, 0, 0))))
    return R


def world(name, local, pose):
    """Точка local (Java, относительно опоры кости) → пространство модели."""
    p = list(local)
    n = name
    while n:
        st = pose.get(n, {})
        p = mv(euler(st.get("rot", (0, 0, 0))), p)
        off = st.get("pos", (0, 0, 0))            # Java‑смещение
        p = add(add(p, rel_pivot(n)), off)
        n = PARENT[n]
    return p


def arm_euler(d):
    """Повороты руки, чтобы её ось (+y локально, вниз) смотрела в направлении d (в системе родителя)."""
    d = norm(d)
    a = -math.acos(max(-1, min(1, d[1])))
    b = math.atan2(-d[0], -d[2]) if abs(math.sin(a)) > 1e-4 else 0.0
    return [math.degrees(a), math.degrees(b), 0.0]


HAND = {"left_arm": [0.5, 9.5, 0.0], "right_arm": [-0.5, 9.5, 0.0]}


def point_arm(pose, arm, target_world):
    """Направить руку на точку (в пространстве модели)."""
    parent = PARENT[arm]
    shoulder = world(arm, [0, 0, 0], pose)
    Rp = chain_R(parent, pose)
    d_local = mv(tr(Rp), sub(target_world, shoulder))
    pose.setdefault(arm, {})["rot"] = arm_euler(d_local)


def set_bow(pose, R_world):
    """Повернуть лук так, чтобы его итоговая ориентация в модели была R_world."""
    Rp = chain_R("left_arm", pose)
    pose.setdefault("bow", {})["rot"] = to_euler(mm(tr(Rp), R_world))


def string_pose(pose, draw, flex):
    """Тетива: кончики плеч + точка натяжения (нок) на draw px за тетивой покоя; flex — изгиб плеч (°)."""
    pose["bow_top"] = {"rot": (-flex, 0, 0)}
    pose["bow_bot"] = {"rot": (flex, 0, 0)}
    nock = (0.0, NOCK_Z0 + draw)                       # (y, z) Bedrock, от хвата
    for limb, sgn in (("bow_top", 1), ("bow_bot", -1)):
        rx = math.radians(-flex if sgn > 0 else flex)
        Ly, Lz = 1.5 * sgn, 0.0
        ty, tz = TIP[0] * sgn - Ly, TIP[1] - Lz       # кончик относительно опоры плеча
        c, s = math.cos(rx), math.sin(rx)
        # Bedrock rx: (y, z) → (y·c + z·s, −y·s + z·c)
        wy, wz = Ly + ty * c + tz * s, Lz - ty * s + tz * c
        dy, dz = nock[0] - wy, nock[1] - wz             # в системе лука
        ly, lz = dy * c - dz * s, dy * s + dz * c       # обратно в систему плеча
        length = math.hypot(ly, lz)
        if sgn > 0:
            r = math.degrees(math.atan2(lz, -ly))      # локальная −y → направление
        else:
            r = math.degrees(math.atan2(-lz, ly))      # локальная +y → направление
        name = limb.replace("bow_", "string_")
        pose[name] = {"rot": (r, 0, 0), "scale": (1, length / STRING_LEN, 1)}
    pose["bow_arrow"] = {"pos": (0, 0, draw - DRAW_MAX)}


def nock_world(pose, draw):
    # точка тетивы в системе лука (Java): y = 0, z = NOCK_Z0 + draw
    return world("bow", [0, 0, NOCK_Z0 + draw], pose)


# --- позы ---
BOW_IDLE = mm(rx_m(math.radians(104)), rz_m(math.radians(8)))     # лук горизонтально у бедра, тетива вверх
BOW_AIM = mm(ry_m(math.radians(-4)), rz_m(math.radians(-68)))     # лук «на боку», почти горизонтально


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def lerp(a, b, t):
    return a + (b - a) * t


def lerp3(a, b, t):
    return [lerp(a[i], b[i], t) for i in range(3)]


def base_pose(t=0.0, idle_phase=True):
    """Поза покоя: руки чуть в стороны, лук у бедра."""
    pose = {}
    w = 2 * math.pi * t / 3.0 if idle_phase else 0.0
    pose["body"] = {"rot": (2 * math.sin(w + 1.0), 0, 0), "pos": (0, -0.5 * math.sin(w), 0)}
    pose["robe1"] = {"rot": (3 * math.sin(w - 0.6), 0, 1.5 * math.sin(w * 1 + 0.3))}
    pose["robe2"] = {"rot": (4 * math.sin(w - 1.2), 0, 2 * math.sin(w - 0.4))}
    pose["robe3"] = {"rot": (5 * math.sin(w - 1.8), 0, 3 * math.sin(w - 1.1))}
    pose["cape"] = {"rot": (4 + 3 * math.sin(w - 0.8), 0, 0)}
    pose["head"] = {"rot": (2 * math.sin(w + 0.5), 0, 1.5 * math.sin(w * 1 - 0.7))}
    pose["right_arm"] = {"rot": (2 * math.sin(w), 0, 10 + 2 * math.sin(w + 0.4))}
    pose["left_arm"] = {"rot": (-12 + 2 * math.sin(w + 0.7), 0, -12 - 2 * math.sin(w + 0.2))}
    set_bow(pose, BOW_IDLE)
    string_pose(pose, 0.0, 0.0)
    return pose


def aim_pose(draw, flex, lean=0.0, tremble=0.0):
    """Стойка с луком «на боку»: левое плечо вперёд, лук на уровне подбородка, правая рука у тетивы."""
    pose = base_pose(0.0, False)
    pose["body"] = {"rot": (lean, 0, 0), "pos": (0, 0, 0)}
    pose["torso"] = {"rot": (-2, 38, 0)}
    pose["head"] = {"rot": (-4, -34, 2)}
    pose["cape"] = {"rot": (6, 0, 0)}
    grip_target = [1.2 + tremble * 0.1, -1.6 + tremble * 0.12, -9.2]
    point_arm(pose, "left_arm", grip_target)
    set_bow(pose, BOW_AIM)
    string_pose(pose, draw, flex)
    point_arm(pose, "right_arm", nock_world(pose, draw))
    return pose


def blend(p1, p2, t):
    out = {}
    for n in set(p1) | set(p2):
        a, b = p1.get(n, {}), p2.get(n, {})
        st = {}
        for k, dflt in (("rot", (0, 0, 0)), ("pos", (0, 0, 0)), ("scale", (1, 1, 1))):
            if k in a or k in b:
                st[k] = lerp3(a.get(k, dflt), b.get(k, dflt), t)
        out[n] = st
    return out


# --- тайминги атаки (секунды); должны совпадать с FrostArcherEntity ---
SPIN_END = 0.75
RAISE_END = 1.25
DRAW_END = 1.85
RELEASE = 1.95
FOLLOW_END = 2.15
ATTACK_LEN = 2.5


def attack_pose(t):
    idle0 = base_pose(0.0, False)
    if t < SPIN_END:
        p = t / SPIN_END
        e = ease(p)
        pose = base_pose(0.0, False)
        bump = math.sin(math.pi * p)
        pose["root"] = {"rot": (0, -360 * e, 0)}
        pose["body"] = {"rot": (4 * bump, 0, 10 * bump), "pos": (0, -1.2 * bump, 0)}
        pose["robe1"] = {"rot": (8 * bump, 22 * bump, 6 * bump)}
        pose["robe2"] = {"rot": (10 * bump, 28 * bump, 8 * bump)}
        pose["robe3"] = {"rot": (12 * bump, 34 * bump, 10 * bump)}
        pose["cape"] = {"rot": (4 + 40 * bump, 0, -10 * bump)}
        pose["right_arm"] = {"rot": (0, 0, 10 + 45 * bump)}
        pose["left_arm"] = {"rot": (-12, 0, -12 - 40 * bump)}
        pose["head"] = {"rot": (-6 * bump, 0, -6 * bump)}
        set_bow(pose, BOW_IDLE)
        string_pose(pose, 0, 0)
        return pose
    if t < RAISE_END:
        p = ease((t - SPIN_END) / (RAISE_END - SPIN_END))
        aim = aim_pose(0.0, 0.0)
        pose = blend(idle0, aim, p)
        # лук поворачивается из положения у бедра в положение «на боку» — пересчитываем точно
        R = mm(rx_m(math.radians(104 * (1 - p))), mm(ry_m(math.radians(-4 * p)), rz_m(math.radians(8 * (1 - p) - 68 * p))))
        set_bow(pose, R)
        string_pose(pose, 0, 0)
        if p > 0.55:
            q = (p - 0.55) / 0.45
            pose["right_arm"]["rot"] = lerp3(pose["right_arm"]["rot"], aim_pose(0, 0)["right_arm"]["rot"], ease(q))
        return pose
    if t < DRAW_END:
        p = (t - RAISE_END) / (DRAW_END - RAISE_END)
        d = DRAW_MAX * ease_out(p)
        return aim_pose(d, 8 * ease_out(p), lean=-3 * p)
    if t < RELEASE:
        tremble = math.sin(t * 90)
        return aim_pose(DRAW_MAX + 0.12 * tremble, 8, lean=-3, tremble=tremble)
    # выстрел: тетива щёлкает и дрожит, плечи лука пружинят, правая рука уходит назад, отдача корпусом
    tp = t - RELEASE
    if tp < 0.025:
        d = DRAW_MAX * (1 - tp / 0.025)
        flex = 8 * (1 - tp / 0.025)
    else:
        q = tp - 0.025
        d = -1.6 * math.exp(-q * 11) * math.sin(q * 70 + 0.4)
        flex = -3.0 * math.exp(-q * 10) * math.sin(q * 60 + 0.5)
    recoil = math.exp(-tp * 9) * math.sin(min(math.pi / 2, tp * 40))
    pose = aim_pose(0.0, 0.0, lean=-3 - 10 * recoil)
    string_pose(pose, d, flex)
    pose["bow"]["rot"] = add(pose["bow"]["rot"], [6 * recoil, 0, 0])
    nock = nock_world(pose, 0)
    back = add(nock, [-3.5, -1.5, 5.0])
    follow = ease_out(min(1.0, tp / 0.12))
    point_arm(pose, "right_arm", lerp3(nock, back, follow))
    pose["head"]["rot"] = add(pose["head"]["rot"], [-4 * recoil, 0, 0])
    pose["cape"] = {"rot": (6 + 14 * recoil, 0, 0)}
    pose["robe1"] = {"rot": (-6 * recoil, 0, 0)}
    pose["robe2"] = {"rot": (-8 * recoil, 0, 0)}
    pose["robe3"] = {"rot": (-10 * recoil, 0, 0)}
    if t >= FOLLOW_END:
        p = ease((t - FOLLOW_END) / (ATTACK_LEN - FOLLOW_END))
        pose = blend(pose, idle0, p)
        R = mm(rx_m(math.radians(104 * p)), mm(ry_m(math.radians(-4 * (1 - p))), rz_m(math.radians(8 * p - 68 * (1 - p)))))
        set_bow(pose, R)
        string_pose(pose, d * (1 - p), flex * (1 - p))
    return pose


def move_pose(t, back=False):
    w = 2 * math.pi * t / 1.2
    pose = base_pose(0.0, False)
    if not back:
        pose["body"] = {"rot": (9 + 2 * math.sin(w), 0, 3 * math.sin(w)), "pos": (0, -0.4 * math.sin(2 * w), 0)}
        pose["robe1"] = {"rot": (8 + 4 * math.sin(w - 0.7), 0, 3 * math.sin(w - 0.5))}
        pose["robe2"] = {"rot": (7 + 5 * math.sin(w - 1.4), 0, 4 * math.sin(w - 1.0))}
        pose["robe3"] = {"rot": (9 + 6 * math.sin(w - 2.1), 0, 5 * math.sin(w - 1.5))}
        pose["cape"] = {"rot": (20 + 6 * math.sin(w - 0.9), 0, 3 * math.sin(w - 0.6))}
        pose["head"] = {"rot": (-12, 0, -2 * math.sin(w))}
        pose["right_arm"] = {"rot": (20 + 6 * math.sin(w + 0.5), 0, 12)}
        pose["left_arm"] = {"rot": (6 + 6 * math.sin(w + 0.9), 0, -14)}
    else:
        pose["body"] = {"rot": (-9 + 2 * math.sin(w), 0, 3 * math.sin(w)), "pos": (0, -0.4 * math.sin(2 * w), 0)}
        pose["robe1"] = {"rot": (-7 + 4 * math.sin(w - 0.7), 0, 3 * math.sin(w - 0.5))}
        pose["robe2"] = {"rot": (-7 + 5 * math.sin(w - 1.4), 0, 4 * math.sin(w - 1.0))}
        pose["robe3"] = {"rot": (-9 + 6 * math.sin(w - 2.1), 0, 5 * math.sin(w - 1.5))}
        pose["cape"] = {"rot": (2 + 4 * math.sin(w - 0.9), 0, 0)}
        pose["head"] = {"rot": (10, 0, -2 * math.sin(w))}
        pose["right_arm"] = {"rot": (-18 + 5 * math.sin(w + 0.5), 0, 16)}
        pose["left_arm"] = {"rot": (-30 + 5 * math.sin(w + 0.9), 0, -14)}
    set_bow(pose, BOW_IDLE)
    string_pose(pose, 0, 0)
    return pose


ANIM_BONES = ["root", "body", "robe1", "robe2", "robe3", "torso", "cape", "head", "right_arm", "left_arm",
              "bow", "bow_top", "bow_bot", "string_top", "string_bot", "bow_arrow", "shards"]


def bake(fn, length, fps, loop, extra=None):
    bones = {}
    n = int(round(length * fps))
    for k in range(n + 1):
        t = k / fps
        if t > length:
            t = length
        pose = fn(t)
        if extra:
            extra(pose, t)
        key = ("%.4f" % t).rstrip("0").rstrip(".") or "0"
        for name in ANIM_BONES:
            st = pose.get(name, {})
            out_name = name
            ch = bones.setdefault(out_name, {})
            r = st.get("rot", (0, 0, 0))
            ch.setdefault("rotation", {})[key] = [round(v, 2) for v in r]
            if "pos" in st:
                px, py, pz = st["pos"]
                ch.setdefault("position", {})[key] = [round(px, 3), round(-py, 3), round(pz, 3)]
            if "scale" in st:
                ch.setdefault("scale", {})[key] = [round(v, 4) for v in st["scale"]]
    # скачок поворота больше 180° между кадрами (конец оборота −360° → 0°) — делаем мгновенным
    # (pre/post), иначе линейная интерполяция провернёт модель обратно
    for name, chans in bones.items():
        rot = chans.get("rotation")
        if not rot:
            continue
        keys = list(rot.keys())
        for i in range(1, len(keys)):
            prev, cur = rot[keys[i - 1]], rot[keys[i]]
            prev = prev["post"] if isinstance(prev, dict) else prev
            if any(abs(cur[j] - prev[j]) > 180 for j in range(3)):
                pre = [cur[j] + round((prev[j] - cur[j]) / 360.0) * 360 for j in range(3)]
                rot[keys[i]] = {"pre": pre, "post": cur}
    # убрать каналы, которые не меняются и равны нулю/единице
    for name in list(bones):
        for cname in list(bones[name]):
            vals = [v if isinstance(v, list) else v["post"] for v in bones[name][cname].values()]
            neutral = [1, 1, 1] if cname == "scale" else [0, 0, 0]
            if all(v == neutral for v in vals):
                del bones[name][cname]
        if not bones[name]:
            del bones[name]
    return {"loop": loop, "animation_length": length, "bones": bones}


def shards(pose, t):
    pose["shards"] = {"rot": (0, -360 * (t % 3.0) / 3.0, 0), "pos": (0, -0.6 * math.sin(2 * math.pi * t / 1.5), 0)}


animations = {
    "animation.%s.idle" % NAME: bake(lambda t: base_pose(t), 3.0, 20, True, shards),
    "animation.%s.move" % NAME: bake(lambda t: move_pose(t), 1.2, 20, True),
    "animation.%s.move_back" % NAME: bake(lambda t: move_pose(t, True), 1.2, 20, True),
    "animation.%s.attack" % NAME: bake(attack_pose, ATTACK_LEN, 40, False),
}

# =====================================================================
# 5. Запись файлов
# =====================================================================


def write_geo():
    out_bones = []
    for b in BONES:
        ob = {"name": b["name"], "pivot": b["pivot"]}
        if "parent" in b:
            ob["parent"] = b["parent"]
        cubes = []
        for c in b["cubes"]:
            oc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
            for k in ("inflate", "pivot", "rotation"):
                if k in c:
                    oc[k] = c[k]
            cubes.append(oc)
        if cubes:
            ob["cubes"] = cubes
        out_bones.append(ob)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": TEX_H,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out_bones}]}
    with open(os.path.join(ROOT, "geo", NAME + ".geo.json"), "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=1)


def write_anims():
    with open(os.path.join(ROOT, "animations", NAME + ".animation.json"), "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": animations}, f, separators=(",", ":"))


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
    a.save(os.path.join(ROOT, "textures", "entity", "frost_arrow.png"))


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
    write_geo()
    write_anims()
    img.save(os.path.join(ROOT, "textures", "entity", NAME + ".png"))
    glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_glowmask.png"))
    arrow_texture()
    effect_icon()
    overlay()
    print("готово:", NAME, "кубов:", sum(len(b["cubes"]) for b in BONES))
