"""
Меч кровожада (предмет игрока): модель в руке, текстура со светящейся маской, иконка 16×16.

    python tools/bt_sword_gen.py            — записать geo/blood_greatsword.geo.json,
                                              textures/entity/blood_greatsword(.png/_glowmask.png),
                                              textures/item/bloodthirster_sword.png
    python tools/bt_sword_gen.py --preview  — ещё и tools/mob_preview.png: меч с разных сторон и иконка

Модель — Bedrock (y вверх), ось клинка +y, точка хвата (правая кисть) — в начале координат.
Всё построено в 2 раза крупнее (K = 2) — так на текстуре вдвое больше пикселей на грань;
в игре модель рисуется с масштабом 1/K (client/weapon/BloodSwordRender).
Позы рук и меча (замах, удар) — tools/bt_sword_anim.py.
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import Model, Painter, faces, mix, mul  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
NAME = "blood_greatsword"
K = 2.0                     # детализация: 1 px модели в игре = K px здесь

# --- палитра: кровь, чёрная сталь, кость ---
BLACK = (20, 7, 11)
BLACK_L = (42, 15, 21)
IRON = (64, 30, 36)
IRON_L = (98, 48, 54)
BORDEAUX_D = (62, 6, 18)
BORDEAUX = (96, 10, 26)
CRIMSON = (146, 14, 32)
RED = (192, 28, 40)
SCARLET = (230, 54, 56)
GLOW = (255, 64, 58)
GLOW_HOT = (255, 168, 146)
BONE = (228, 212, 196)
BONE_S = (176, 150, 138)

# =====================================================================
# Модель (единицы — px игры, при записи умножаются на K)
# =====================================================================
M = Model(NAME, 128, 128)
M.bone("sword", None, (0, 0, 0))

# точки хвата (px игры, вдоль оси клинка): правая кисть — у гарды, левая — у навершия
GRIP_RIGHT = 1.6
GRIP_LEFT = -2.6
BLADE_BASE = 5.8
TIP = 26.6


def cube(origin, size, mat, face, rot=None, pivot=None):
    o = [v * K for v in origin]
    s = [v * K for v in size]
    if rot:
        M.cube("sword", o, s, mat, rot=rot, pivot=[v * K for v in pivot], face=face)
    else:
        M.cube("sword", o, s, mat, face=face)


def centered(cx, y0, cz, w, h, d, mat, face, rot=None):
    """Куб с центром (cx, ·, cz) по x/z, от y0 вверх на h; поворот — вокруг центра куба."""
    o = (cx - w / 2, y0, cz - d / 2)
    piv = (cx, y0 + h / 2, cz)
    cube(o, (w, h, d), mat, face, rot=rot, pivot=piv if rot else None)


# навершие: ромбовидный шип, тяжёлая головка с рубином, кольцо
centered(0, -7.7, 0, 1.0, 1.5, 1.0, "iron", "spike", rot=(0, 45, 0))
centered(0, -6.5, 0, 2.6, 1.9, 2.6, "iron", "pommel")
centered(0, -6.05, 0, 1.2, 1.0, 3.0, "gem", "pommel_gem")
centered(0, -4.75, 0, 1.9, 0.6, 1.9, "iron", "ferrule")
# рукоять (двуручная) — обмотка кожей с алым шнуром
centered(0, -4.4, 0, 1.5, 8.0, 1.5, "grip", "grip")
centered(0, 3.55, 0, 2.0, 0.7, 2.0, "iron", "ferrule")
# гарда: центр с рубином, крылья, загнутые вверх концы
centered(0, 4.2, 0, 3.4, 1.7, 2.2, "iron", "guard_c")
centered(0, 4.45, 0, 1.3, 1.3, 2.7, "gem", "guard_gem", rot=(0, 0, 45))
for sgn in (1, -1):
    cube((1.6 if sgn > 0 else -4.8, 4.55, -0.7), (3.2, 1.0, 1.4), "iron", "guard_arm")
    centered(sgn * 5.3, 4.75, 0, 2.4, 0.8, 1.0, "iron", "guard_tip", rot=(0, 0, sgn * 38))
    # костяной коготь на конце крыла
    centered(sgn * 6.05, 5.3, 0, 0.7, 1.5, 0.7, "bone", "guard_fang", rot=(0, 0, sgn * -20))
    # «челюсти» у основания клинка — костяные клыки обхватывают клинок
    centered(sgn * 1.75, 5.4, 0, 0.6, 1.9, 1.2, "bone", "jaw", rot=(0, 0, sgn * 12))

# клинок: прямой, у острия сужается; у гарды — чуть шире (рикассо с зубьями)
BLADE = [  # (y0, y1, ширина, толщина)
    (BLADE_BASE, 7.6, 3.8, 0.95),
    (7.6, 20.4, 3.4, 0.9),
    (20.4, 22.6, 3.0, 0.85),
    (22.6, 24.2, 2.4, 0.8),
    (24.2, 25.4, 1.6, 0.7),
    (25.4, 26.2, 0.9, 0.6),
    (26.2, TIP, 0.4, 0.5),
]
for i, (y0, y1, w, d) in enumerate(BLADE):
    centered(0, y0, 0, w, y1 - y0, d, "blade", "blade%d" % i)
# рубин в основании клинка (насквозь)
centered(0, 6.2, 0, 1.2, 1.5, 1.25, "gem", "blade_gem")
# зубья у основания по обеим кромкам («жажда крови»)
for y in (7.9, 9.5):
    for sgn in (1, -1):
        centered(sgn * 1.75, y, 0, 0.8, 0.8, 0.6, "blade", "tooth", rot=(0, 0, 45))

M.pack()


# =====================================================================
# Текстура
# =====================================================================
def lit(fname, col):
    k = {"top": 1.06, "bottom": 0.72, "front": 1.0, "back": 1.0, "right": 0.9, "left": 0.9}[fname]
    return mul(col, k)


def make_painter():
    PT = Painter(M.tex_w, M.tex_h, seed=1972)
    for c in M.cubes():
        mat, face = c["mat"], c.get("face")
        for fname, rect in faces(c).items():
            def fn(i, j, w, h, fname=fname, mat=mat, face=face):
                big = fname in ("front", "back")
                if mat == "blade":
                    if face == "tooth":
                        return lit(fname, mix(SCARLET, GLOW_HOT, 0.3)) if fname in ("top", "right", "left") else lit(fname, CRIMSON)
                    idx = int(face[5:])
                    y0, y1 = BLADE[idx][0], BLADE[idx][1]
                    yy = y1 - (j + 0.5) / h * (y1 - y0)            # высота точки (px игры)
                    t = (yy - BLADE_BASE) / (TIP - BLADE_BASE)       # 0 у гарды … 1 у острия
                    if big:
                        # чёрно‑багровая сталь с узором «дамаск», алые фаски, светлая кромка,
                        # по центру — дол с кровяной жилой (светится)
                        edge = min(i, w - 1 - i)
                        mid = (w - 1) / 2
                        if edge == 0 and w >= 2:
                            return mix(SCARLET, GLOW_HOT, 0.15 + 0.3 * t)
                        if edge == 1 and w >= 5:
                            return mix(RED, SCARLET, 0.2 + 0.5 * t)
                        steel = mix(BLACK_L, BORDEAUX, 0.45 + 0.35 * t)
                        wave = math.sin(yy * 2.3 + abs(i - mid) * 1.9)
                        if wave > 0.55:
                            steel = mix(steel, CRIMSON, 0.35)
                        elif wave < -0.7:
                            steel = mix(steel, BLACK, 0.4)
                        if w >= 5 and abs(i - mid) < 0.6 and t < 0.82:
                            pulse = math.sin(yy * 1.15)
                            if pulse > -0.35:
                                return ("glow", GLOW if pulse > 0.4 else mix(GLOW, CRIMSON, 0.45))
                            return mix(BLACK, BORDEAUX_D, 0.5)
                        return steel
                    if fname in ("left", "right"):
                        return mix(SCARLET, GLOW_HOT, 0.35) if (j % 3) else GLOW_HOT       # кромка
                    return lit(fname, SCARLET)
                if mat == "gem":
                    if big or face == "guard_gem":
                        if 0 < i < w - 1 and 0 < j < h - 1:
                            return ("glow", GLOW_HOT)
                        return ("glow", GLOW)
                    return lit(fname, CRIMSON)
                if mat == "bone":
                    t = j / max(1, h - 1)
                    col = mix(BONE, BONE_S, 0.15 + 0.5 * t)
                    if face == "jaw" and j <= 1:
                        return mix(SCARLET, BONE, 0.25)                 # окровавленные кончики
                    if face == "guard_fang" and j <= 1:
                        return mix(SCARLET, BONE, 0.35)
                    return lit(fname, mix(col, BORDEAUX, 0.15) if (i + j) % 4 == 0 else col)
                if mat == "grip":
                    # обмотка: косые полосы кожи + алый шнур
                    band = (j + (i if fname in ("front", "back") else i + 1)) % 5
                    if band == 0:
                        return lit(fname, RED)
                    if band == 1:
                        return lit(fname, BORDEAUX)
                    return lit(fname, BLACK_L if band % 2 else mix(BLACK_L, BORDEAUX_D, 0.5))
                if mat == "iron":
                    rim = i in (0, w - 1) or j in (0, h - 1)
                    if face in ("guard_c", "guard_arm") and big and rim:
                        return lit(fname, mix(IRON_L, RED, 0.45))
                    if face == "guard_tip":
                        return lit(fname, mix(IRON, CRIMSON, 0.25 + 0.5 * (i / max(1, w - 1))))
                    if face == "ferrule":
                        return lit(fname, mix(IRON_L, SCARLET, 0.25) if j == 0 else IRON)
                    if face == "spike":
                        return lit(fname, mix(IRON_L, CRIMSON, 0.3))
                    col = IRON if (i * 3 + j * 5) % 7 else IRON_L
                    return lit(fname, mix(col, BLACK, 0.25) if rim else col)
                return (255, 0, 255)
            PT.fill(rect, fn)
    return PT


# =====================================================================
# Иконка 16×16 — меч по диагонали (рукоять внизу слева, острие вверху справа)
# =====================================================================
ICON = [
    "..............oo",
    ".............oHo",
    "............oHro",
    "...........oHvdo",
    "..........oHvdo.",
    ".........oHvdo..",
    "........oHvdo...",
    ".......oHvdo....",
    "..ob..oHrdo.....",
    "..obgoHrdo......",
    "...okgGkdo......",
    "....owGkko......",
    "...owWookbo.....",
    "..owWo..obo.....",
    ".oGPo....o......",
    ".oPo............",
]
ICON_PAL = {
    "o": (30, 5, 12),          # контур
    "H": (250, 112, 100),      # светлая кромка
    "r": (168, 20, 36),        # клинок
    "v": (255, 46, 46),        # кровяная жила в доле
    "d": (100, 8, 24),         # тёмная кромка
    "b": (232, 216, 200),      # кость (когти гарды)
    "g": (124, 42, 48),        # гарда
    "k": (70, 20, 28),         # гарда в тени
    "G": (255, 84, 66),        # рубин
    "w": (150, 20, 36),        # обмотка (алый шнур)
    "W": (58, 12, 22),         # обмотка (тёмная кожа)
    "P": (88, 34, 40),         # навершие
}


def make_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = ICON_PAL[ch] + (255,)
    return img


# =====================================================================
# Простой растеризатор для превью (перспектива или ортография)
# =====================================================================
def cuboid_quads(cube_):
    import settler_preview as sp
    return sp.cuboid_quads(cube_)


def java_quads(model, tf):
    """Все грани модели меча → [(verts, u1, v1, u2, v2)], tf: Java‑точка → мир."""
    import mob_preview as mp
    parts = mp.build_parts({"bones": model.bones_out})
    out = []

    def walk(ps, parent):
        for p in ps:
            pitch, yaw, roll = p["rot"]
            px_, py_, pz_ = p["pivot"]

            def f(v, pitch=pitch, yaw=yaw, roll=roll, px_=px_, py_=py_, pz_=pz_, parent=parent):
                import settler_preview as sp
                v = sp.rot_x(v, pitch)
                v = sp.rot_y(v, yaw)
                v = sp.rot_z(v, roll)
                return parent((v[0] + px_, v[1] + py_, v[2] + pz_))
            for cb in p["cubes"]:
                for verts, u1, v1, u2, v2 in cuboid_quads(cb):
                    out.append(([f(vv) for vv in verts], u1, v1, u2, v2))
            walk(p["children"], f)
    walk(parts, tf)
    return out


def raster(layers, size, project, bg=(52, 56, 64, 255), light=(0.3, 0.8, 0.5)):
    """layers: [(quads, tex, glow)] — quads в мире камеры; project(p) → (sx, sy, depth) | None."""
    W, H = size
    img = Image.new("RGBA", size, bg)
    px = img.load()
    zb = [[1e9] * W for _ in range(H)]
    ln = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ln for c in light)
    for quads, tex, glow in layers:
        tw, th = tex.size
        tp = tex.load()
        gp = glow.load() if glow else None
        for verts, u1, v1, u2, v2 in quads:
            o, a, b = verts[1], verts[0], verts[2]
            ea = [a[i] - o[i] for i in range(3)]
            eb = [b[i] - o[i] for i in range(3)]
            n = (ea[1] * eb[2] - ea[2] * eb[1], ea[2] * eb[0] - ea[0] * eb[2], ea[0] * eb[1] - ea[1] * eb[0])
            nl = math.sqrt(sum(c * c for c in n)) or 1
            shade = 0.5 + 0.5 * abs(sum(n[i] / nl * light[i] for i in range(3)))
            # разбить грань на сетку точек и рисовать маленькими «пятнами» — просто и без дыр
            pa, pb, po = project(a), project(b), project(o)
            if not (pa and pb and po):
                continue
            span = max(abs(pa[0] - po[0]) + abs(pa[1] - po[1]), abs(pb[0] - po[0]) + abs(pb[1] - po[1]))
            ns = max(2, int(span * 1.6) + 1)
            us = max(1, abs(int(round(u2 - u1))))
            vs = max(1, abs(int(round(v2 - v1))))
            na, nb_ = max(ns, us * 2), max(ns, vs * 2)
            for ii in range(na):
                s = (ii + 0.5) / na
                for jj in range(nb_):
                    t = (jj + 0.5) / nb_
                    p = [o[k] + s * ea[k] + t * eb[k] for k in range(3)]
                    pr = project(p)
                    if not pr:
                        continue
                    sx, sy, dep = pr
                    tu = u1 + s * (u2 - u1)
                    tv = v1 + t * (v2 - v1)
                    iu, iv = int(math.floor(tu)), int(math.floor(tv))
                    iu = min(max(iu, min(u1, u2)), max(u1, u2) - 1)
                    iv = min(max(iv, min(v1, v2)), max(v1, v2) - 1)
                    if not (0 <= iu < tw and 0 <= iv < th):
                        continue
                    c = tp[iu, iv]
                    if c[3] < 128:
                        continue
                    g = gp[iu, iv] if gp else (0, 0, 0, 0)
                    k = 1.0 if g[3] > 0 else shade
                    col = (min(255, int(c[0] * k)), min(255, int(c[1] * k)), min(255, int(c[2] * k)), 255)
                    rad = max(1, int(span / na + 1))
                    for yy in range(int(sy), int(sy) + rad):
                        for xx in range(int(sx), int(sx) + rad):
                            if 0 <= xx < W and 0 <= yy < H and dep < zb[yy][xx]:
                                zb[yy][xx] = dep
                                px[xx, yy] = col
    return img


def finalize_bones():
    """Кубы с uv для превью (как в geo)."""
    out = []
    for b in M.bones:
        ob = {"name": b["name"], "pivot": b["pivot"], "cubes": []}
        for c in b["cubes"]:
            oc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
            for k in ("inflate", "pivot", "rotation"):
                if k in c:
                    oc[k] = c[k]
            ob["cubes"].append(oc)
        out.append(ob)
    M.bones_out = out


def sword_preview(tex, glow, icon):
    finalize_bones()
    frames = []
    for yaw, pitch in ((0.0, 0.0), (math.pi / 2, 0.0), (0.7, 0.35), (2.4, -0.3)):
        def tf(v, yaw=yaw, pitch=pitch):
            # Java (y вниз) → мир (y вверх); масштаб 1/K
            x, y, z = v[0] / K, (24 - v[1]) / K, v[2] / K
            c, s = math.cos(yaw), math.sin(yaw)
            x, z = x * c + z * s, -x * s + z * c
            c, s = math.cos(pitch), math.sin(pitch)
            y, z = y * c - z * s, y * s + z * c
            return (x, y, z)
        q = java_quads(M, tf)
        W, H = 220, 560
        sc = 14.5

        def proj(p):
            return (W / 2 + p[0] * sc, H / 2 - (p[1] - 9.5) * sc, -p[2])
        frames.append(raster([(q, tex, glow)], (W, H), proj))
    ic = icon.resize((256, 256), Image.NEAREST)
    sheet = Image.new("RGBA", (220 * 4 + 280, 560), (52, 56, 64, 255))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, (i * 220, 0))
    sheet.alpha_composite(ic, (220 * 4 + 12, 150))
    sheet.save(os.path.join(os.path.dirname(__file__), "mob_preview.png"))
    print("preview → tools/mob_preview.png")


def main():
    PT = make_painter()
    M.write_geo(ROOT)
    tex_dir = os.path.join(ROOT, "textures", "entity")
    PT.img.save(os.path.join(tex_dir, NAME + ".png"))
    PT.glow.save(os.path.join(tex_dir, NAME + "_glowmask.png"))
    icon = make_icon()
    icon.save(os.path.join(ROOT, "textures", "item", "bloodthirster_sword.png"))
    print("ok:", NAME, "кубов", len(M.cubes()))
    if "--preview" in sys.argv:
        sword_preview(PT.img, PT.glow, icon)


if __name__ == "__main__":
    main()
