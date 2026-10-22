"""
Предпросмотр жителей без запуска игры: рисует модель SettlerEntityModel (те же кубы, UV и
слои, что в Java) спереди, вполоборота и сзади для каждой текстуры. Запуск:
    python tools/settler_preview.py [файл.png]
По умолчанию пишет tools/settler_preview.png (в мод не попадает).
ВАЖНО: список кубов ниже (PARTS) должен совпадать с SettlerEntityModel.getTexturedModelData.
"""
import math
import os
import sys

from PIL import Image

TEX_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "basedefense", "textures", "entity", "settler")

# ---------------------------------------------------------------------------
# Модель: part(name, pivot, rotation(pitch, yaw, roll), cubes, children, tag)
# cube = (u, v, x, y, z, w, h, d, dilation)
# tag — когда часть видна (см. LOOKS): None — всегда.
# ---------------------------------------------------------------------------


def part(name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=(), tag=None):
    return dict(name=name, pivot=pivot, rot=rot, cubes=list(cubes), children=list(children), tag=tag)


O = 0.25   # второй слой одежды
PARTS = [
    part("head", cubes=[(0, 0, -4, -8, -4, 8, 8, 8, 0), (32, 0, -4, -8, -4, 8, 8, 8, 0.5)], children=[
        part("hair_back", (0, -1, 3), cubes=[(64, 0, -4, 0, 0, 8, 7, 1, 0.5)], tag="longHair"),
        part("ponytail", (0, -6, 4), (0.55, 0, 0), cubes=[(82, 0, -1, 0, 0, 2, 5, 2, 0)], tag="ponytail"),
        part("hat_crown", cubes=[(96, 48, -4, -10, -4, 8, 3, 8, 0.6)], tag="hat:brim"),
        part("brim_front", cubes=[(96, 24, -6, -7.2, -6, 12, 1, 2, 0)], tag="hat:brim"),
        part("brim_back", cubes=[(96, 27, -6, -7.2, 4, 12, 1, 2, 0)], tag="hat:brim"),
        part("brim_right", cubes=[(96, 30, -6, -7.2, -4, 2, 1, 8, 0)], tag="hat:brim"),
        part("brim_left", cubes=[(96, 39, 4, -7.2, -4, 2, 1, 8, 0)], tag="hat:brim"),
        part("visor", cubes=[(64, 9, -4, -8.1, -7, 8, 1, 3, 0)], tag="hat:cap"),
    ]),
    part("body", cubes=[(16, 16, -4, 0, -2, 8, 12, 4, 0), (16, 32, -4, 0, -2, 8, 12, 4, O)], children=[
        part("breast", (0, 2.5, -2), cubes=[(64, 48, -3.5, 0, -1, 7, 3, 1, 0), (64, 52, -3.5, 0, -1, 7, 3, 1, O)],
             tag="female"),
        part("skirt", (0, 10, 0), cubes=[(96, 0, -4.5, 0, -2.5, 9, 8, 5, 0), (96, 13, -5, 4, -3, 10, 4, 6, 0)],
             tag="female"),
        part("pouch", (0, 8.4, -2), cubes=[(86, 9, -3.6, 0, -1, 3, 3, 1, 0)], tag="pouch"),
    ]),
    part("right_arm", (-5, 2, 0), (0, 0, 0.05), children=[
        part("wide", cubes=[(40, 16, -3, -2, -2, 4, 12, 4, 0), (40, 32, -3, -2, -2, 4, 12, 4, O)], tag="male"),
        part("slim", cubes=[(64, 16, -2, -2, -2, 3, 12, 4, 0), (64, 32, -2, -2, -2, 3, 12, 4, O)], tag="female"),
    ]),
    part("left_arm", (5, 2, 0), (0, 0, -0.05), children=[
        part("wide", cubes=[(32, 48, -1, -2, -2, 4, 12, 4, 0), (48, 48, -1, -2, -2, 4, 12, 4, O)], tag="male"),
        part("slim", cubes=[(80, 16, -1, -2, -2, 3, 12, 4, 0), (80, 32, -1, -2, -2, 3, 12, 4, O)], tag="female"),
    ]),
    part("right_leg", (-1.9, 12, 0), cubes=[(0, 16, -2, 0, -2, 4, 12, 4, 0), (0, 32, -2, 0, -2, 4, 12, 4, O)]),
    part("left_leg", (1.9, 12, 0), cubes=[(16, 48, -2, 0, -2, 4, 12, 4, 0), (0, 48, -2, 0, -2, 4, 12, 4, O)]),
]


def visible(tag, look):
    if tag is None:
        return True
    if tag == "male":
        return not look["female"]
    if tag.startswith("hat:"):
        return look.get("hat") == tag[4:]
    return bool(look.get(tag))


# ---------------------------------------------------------------------------
# Математика
# ---------------------------------------------------------------------------

def rot_x(v, a):
    x, y, z = v
    c, s = math.cos(a), math.sin(a)
    return (x, y * c - z * s, y * s + z * c)


def rot_y(v, a):
    x, y, z = v
    c, s = math.cos(a), math.sin(a)
    return (x * c + z * s, y, -x * s + z * c)


def rot_z(v, a):
    x, y, z = v
    c, s = math.cos(a), math.sin(a)
    return (x * c - y * s, x * s + y * c, z)


def cuboid_quads(cube):
    """Как ModelPart.Cuboid в Minecraft: 6 граней с UV."""
    u, v, x, y, z, w, h, d, e = cube
    f, g, hh = x - e, y - e, z - e
    i, j, k = x + w + e, y + h + e, z + d + e
    v1, v2, v3, v4 = (f, g, hh), (i, g, hh), (i, j, hh), (f, j, hh)
    v5, v6, v7, v8 = (f, g, k), (i, g, k), (i, j, k), (f, j, k)
    J, K, L, M, N, OO = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    P, Q, R = v, v + d, v + d + h
    return [
        ([v6, v5, v1, v2], K, P, L, Q),      # DOWN (верх головы на текстуре)
        ([v3, v4, v8, v7], L, Q, M, P),      # UP
        ([v1, v5, v8, v4], J, Q, K, R),      # WEST
        ([v2, v1, v4, v3], K, Q, L, R),      # NORTH (лицо)
        ([v6, v2, v3, v7], L, Q, N, R),      # EAST
        ([v5, v6, v7, v8], N, Q, OO, R),     # SOUTH
    ]


def collect(parts, look, transform, out):
    for p in parts:
        if not visible(p["tag"], look):
            continue
        pitch, yaw, roll = p["rot"]
        px, py, pz = p["pivot"]

        def tf(v, pitch=pitch, yaw=yaw, roll=roll, px=px, py=py, pz=pz, parent=transform):
            v = rot_x(v, pitch)
            v = rot_y(v, yaw)
            v = rot_z(v, roll)
            return parent((v[0] + px, v[1] + py, v[2] + pz))

        for cube in p["cubes"]:
            for verts, u1, v1, u2, v2 in cuboid_quads(cube):
                out.append(([tf(vv) for vv in verts], u1, v1, u2, v2))
        collect(p["children"], look, tf, out)


def render(tex, look, view_yaw, scale=8, size=(200, 300), baseline=None):
    quads = []
    collect(PARTS, look, lambda v: v, quads)
    W, H = size
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    zbuf = [[-1e9] * W for _ in range(H)]
    px = img.load()
    tw, th = tex.size
    tpx = tex.load()
    light = (0.35, 0.75, 0.55)   # направление света в мире (после поворота)
    ln = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ln for c in light)

    def to_world(v):
        # Мир: x = x, y = -y, z = -z (как сущность, повёрнутая лицом к камере); потом поворот вида
        w = (v[0], -v[1], -v[2])
        return rot_y(w, view_yaw)

    for verts, u1, v1, u2, v2 in quads:
        wv = [to_world(v) for v in verts]
        o, a, b = wv[1], wv[0], wv[2]
        ea = (a[0] - o[0], a[1] - o[1], a[2] - o[2])
        eb = (b[0] - o[0], b[1] - o[1], b[2] - o[2])
        n = (ea[1] * eb[2] - ea[2] * eb[1], ea[2] * eb[0] - ea[0] * eb[2], ea[0] * eb[1] - ea[1] * eb[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1
        n = tuple(c / nl for c in n)
        shade = 0.55 + 0.45 * abs(sum(n[i] * light[i] for i in range(3)))
        # экран: sx = W/2 + x*scale, sy = H - 20 - y*scale (ноги на y=-24 → низ)
        base_y = H - 12 if baseline is None else baseline
        def scr(p):
            return (W / 2 + p[0] * scale, base_y - (p[1] + 24) * scale)
        so, sa, sb = scr(o), scr(a), scr(b)
        A = (sa[0] - so[0], sa[1] - so[1])
        B = (sb[0] - so[0], sb[1] - so[1])
        det = A[0] * B[1] - A[1] * B[0]
        if abs(det) < 1e-6:
            continue
        xs = [so[0], sa[0], sb[0], sa[0] + B[0]]
        ys = [so[1], sa[1], sb[1], sa[1] + B[1]]
        for yy in range(max(0, int(min(ys))), min(H, int(max(ys)) + 1)):
            for xx in range(max(0, int(min(xs))), min(W, int(max(xs)) + 1)):
                dx, dy = xx + 0.5 - so[0], yy + 0.5 - so[1]
                s = (dx * B[1] - dy * B[0]) / det
                t = (A[0] * dy - A[1] * dx) / det
                if not (0 <= s <= 1 and 0 <= t <= 1):
                    continue
                z = o[2] + s * ea[2] + t * eb[2]
                if z <= zbuf[yy][xx]:
                    continue
                tu = u1 + s * (u2 - u1)
                tv = v1 + t * (v2 - v1)
                iu = int(math.floor(min(tu, max(u1, u2) - 1e-4)))
                iv = int(math.floor(min(tv, max(v1, v2) - 1e-4)))
                if not (0 <= iu < tw and 0 <= iv < th):
                    continue
                c = tpx[iu, iv]
                if c[3] < 128:
                    continue
                zbuf[yy][xx] = z
                px[xx, yy] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
    return img


def load_looks():
    """Внешности — как таблица LOOKS в SettlerEntityRenderer."""
    try:
        sys.path.insert(0, os.path.dirname(__file__))
        from settler_textures import CHARACTERS
    except Exception:
        CHARACTERS = {}
    looks = {}
    for name, ch in CHARACTERS.items():
        style = ch.get("hair_style")
        looks[name] = dict(female=ch.get("female", False), longHair=style == "long",
                           ponytail=style == "ponytail", hat=ch.get("hat"), pouch=bool(ch.get("pouch")))
    return looks


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else os.path.join(os.path.dirname(__file__), "settler_preview.png")
    close = "--close" in sys.argv          # крупный план: голова и плечи
    looks = load_looks()
    names = list(looks)
    views = [0.0, math.radians(35), math.radians(-60), math.pi]
    cw, chh = (220, 220) if close else (200, 300)
    scale = 14 if close else 8
    baseline = chh + 19 * scale if close else None
    sheet = Image.new("RGBA", (cw * len(views), chh * len(names)), (58, 64, 72, 255))
    for row, name in enumerate(names):
        tex = Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")
        for col, vy in enumerate(views):
            im = render(tex, looks[name], vy, scale=scale, size=(cw, chh), baseline=baseline)
            sheet.alpha_composite(im, (col * cw, row * chh))
    sheet.save(out)
    print("preview:", out)


if __name__ == "__main__":
    main()
