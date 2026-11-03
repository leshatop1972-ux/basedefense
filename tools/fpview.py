"""
fpview — предпросмотр оружия ГЛАЗАМИ ИГРОКА (1‑е лицо), как в игре.

Камера в начале координат смотрит в −z, x — вправо, y — вверх (это пространство, в котором Minecraft рисует
предмет в руке: HeldItemRenderer). Поле зрения руки в Minecraft — всегда 70° по вертикали, экран 16:9.
Рендер — честный z‑буфер с перспективной текстурой и отсечением по ближней плоскости (0.05), свет как у сущностей
(два направленных источника + рассеянный). Аддитивный слой — для следов клинка и свечения.

Руки — модели рук игрока из скина (как ModelPart правой/левой руки), положение считается ТОЙ ЖЕ формулой, что и в
Java (client/weapon/fp/FpArms): кулак в точке hand, рукоять проходит сквозь кулак, клинок — в сторону большого
пальца, рука тянется к «якорю плеча» за краем экрана.

Используется генераторами оружия (tools/katana_gen.py и следующие).
"""
import math
import os

import numpy as np
from PIL import Image

import settler_preview as sp

FOV = 70.0
NEAR = 0.05
SKIN = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures",
                    "entity", "settler", "male_1.png")
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


# =====================================================================
# Векторы
# =====================================================================
def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


def nrm(a):
    a = np.asarray(a, dtype=float)
    n = np.linalg.norm(a)
    return a / n if n > 1e-9 else a


def rot_axis(axis, ang):
    x, y, z = nrm(axis)
    c, s = math.cos(ang), math.sin(ang)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


def cam_matrix(pitch_deg=0.0, yaw_deg=0.0, roll_deg=0.0):
    """Поворот камеры (как в Java: roll по z, потом pitch по x, потом yaw по y) — для фона."""
    r = rot_axis((0, 0, 1), math.radians(roll_deg))
    p = rot_axis((1, 0, 0), math.radians(pitch_deg))
    y = rot_axis((0, 1, 0), math.radians(yaw_deg))
    return r @ p @ y


# =====================================================================
# Сцена: список граней (4 вершины, uv 0..1, нормали) с текстурой
# =====================================================================
class Batch:
    def __init__(self, tex, additive=False, color=(1, 1, 1, 1), lit=True):
        self.tex = None if tex is None else np.asarray(tex.convert("RGBA"), dtype=np.float32) / 255.0
        self.additive = additive
        self.color = np.array(color, dtype=np.float32)
        self.lit = lit
        self.quads = []           # (V 4×3, UV 4×2, N 4×3, C 4×4 | None)

    def add(self, V, UV, N=None, C=None):
        V = np.asarray(V, dtype=float)
        if N is None:
            n = nrm(np.cross(V[1] - V[0], V[2] - V[0]))
            if not np.any(n):
                n = nrm(np.cross(V[2] - V[0], V[3] - V[0]))
            N = np.tile(n, (4, 1))
        self.quads.append((V, np.asarray(UV, dtype=float), np.asarray(N, dtype=float),
                           None if C is None else np.asarray(C, dtype=float)))


def _clip_near(P):
    """Полигон (список словарей с 'p' и атрибутами) отсекается плоскостью z = −NEAR."""
    out = []
    n = len(P)
    for i in range(n):
        a, b = P[i], P[(i + 1) % n]
        ina, inb = a["p"][2] <= -NEAR, b["p"][2] <= -NEAR
        if ina:
            out.append(a)
        if ina != inb:
            t = (-NEAR - a["p"][2]) / (b["p"][2] - a["p"][2])
            out.append({k: a[k] + (b[k] - a[k]) * t for k in a})
    return out


class Renderer:
    def __init__(self, w=640, h=360, fov=FOV):
        self.w, self.h = w, h
        self.f = (h / 2) / math.tan(math.radians(fov / 2))
        ys, xs = np.mgrid[0:h, 0:w]
        self.px = xs + 0.5
        self.py = ys + 0.5

    def background(self, cam=np.eye(3)):
        """Небо и трава с сеткой (видно, как «ходит» камера). cam — поворот камеры."""
        w, h, f = self.w, self.h, self.f
        d = np.stack([(self.px - w / 2) / f, -(self.py - h / 2) / f, -np.ones_like(self.px)], -1)
        d = d @ cam                                    # направление в мире (строки · cam = camᵀ·d)
        d /= np.linalg.norm(d, axis=-1, keepdims=True)
        sky_t = np.clip(d[..., 1], 0, 1)[..., None]
        sky = (1 - sky_t) * np.array([0.72, 0.82, 0.95]) + sky_t * np.array([0.36, 0.55, 0.88])
        with np.errstate(divide="ignore", invalid="ignore"):
            t = -1.62 / d[..., 1]
        gx, gz = d[..., 0] * t, d[..., 2] * t
        ground = d[..., 1] < -1e-3
        chk = ((np.floor(gx) + np.floor(gz)) % 2 == 0)
        grass = np.where(chk[..., None], [0.36, 0.56, 0.24], [0.32, 0.51, 0.21])
        dist = np.clip(np.sqrt(gx ** 2 + gz ** 2) / 40, 0, 1)[..., None]
        grass = grass * (1 - dist) + np.array([0.62, 0.72, 0.82]) * dist
        img = np.where(ground[..., None], grass, sky)
        # «манекен» (цель) в 2.5 блока впереди — для понимания дистанции удара
        return img.astype(np.float32)

    def render(self, batches, cam=np.eye(3), bg=True):
        w, h = self.w, self.h
        col = self.background(cam) if bg else np.full((h, w, 3), 0.2, np.float32)
        zb = np.full((h, w), -1e9, np.float32)          # 1/z (больше — ближе)
        solid = [b for b in batches if not b.additive]
        add = [b for b in batches if b.additive]
        for b in solid + add:
            for V, UV, N, C in b.quads:
                for tri in ((0, 1, 2), (0, 2, 3)):
                    P = [{"p": V[i], "uv": UV[i], "n": N[i],
                          "c": (C[i] if C is not None else np.ones(4))} for i in tri]
                    P = _clip_near(P)
                    for k in range(1, len(P) - 1):
                        self._tri(col, zb, (P[0], P[k], P[k + 1]), b)
        return Image.fromarray(np.clip(col * 255, 0, 255).astype(np.uint8), "RGB")

    def project(self, p):
        return np.array([self.w / 2 + p[0] / -p[2] * self.f, self.h / 2 - p[1] / -p[2] * self.f, -1.0 / p[2]])

    def _tri(self, col, zb, T, b):
        S = [self.project(t["p"]) for t in T]
        xs = [s[0] for s in S]
        ys = [s[1] for s in S]
        x0, x1 = max(int(math.floor(min(xs))), 0), min(int(math.ceil(max(xs))), self.w - 1)
        y0, y1 = max(int(math.floor(min(ys))), 0), min(int(math.ceil(max(ys))), self.h - 1)
        if x0 > x1 or y0 > y1:
            return
        (ax, ay, aw), (bx, by, bw), (cx, cy, cw) = S
        area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if abs(area) < 1e-9:
            return
        px = self.px[y0:y1 + 1, x0:x1 + 1]
        py = self.py[y0:y1 + 1, x0:x1 + 1]
        w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area
        w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            return
        iw = w0 * aw + w1 * bw + w2 * cw                 # 1/z — перспективная интерполяция
        zsl = zb[y0:y1 + 1, x0:x1 + 1]
        if not getattr(b, "nodepth", False):
            m &= iw > zsl
        if not m.any():
            return

        def attr(key):
            a, bb, c = T[0][key], T[1][key], T[2][key]
            num = (w0[..., None] * a * aw + w1[..., None] * bb * bw + w2[..., None] * c * cw)
            return num / iw[..., None]
        uv = attr("uv")
        vc = attr("c")
        if b.tex is not None:
            th, tw = b.tex.shape[:2]
            iu = np.clip((uv[..., 0] * tw).astype(int), 0, tw - 1)
            iv = np.clip((uv[..., 1] * th).astype(int), 0, th - 1)
            tc = b.tex[iv, iu]
        else:
            tc = np.ones(uv.shape[:2] + (4,), np.float32)
        tc = tc * b.color * vc
        if not b.additive:
            m &= tc[..., 3] > 0.1
        if b.lit and not b.additive:
            n = attr("n")
            n /= np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-6)
            lum = 0.4 + 0.6 * (np.maximum(n @ L0, 0) + np.maximum(n @ L1, 0))
            lum = np.minimum(lum, 1.0)
            rgb = tc[..., :3] * lum[..., None]
        else:
            rgb = tc[..., :3]
        csl = col[y0:y1 + 1, x0:x1 + 1]
        if b.additive:
            csl[m] = np.minimum(csl[m] + rgb[m] * tc[..., 3][m][..., None], 1.0)
        else:
            csl[m] = rgb[m]
            zsl[m] = iw[m]


# =====================================================================
# Руки игрока (как в Java FpArms)
# =====================================================================
ARM_GRIP_Y = 8.5          # px от плеча вдоль руки до середины кулака


def arm_frame(hand, blade, anchor):
    """Поворот руки: Y — от плеча к кисти (на якорь), −Z — к клинку (рукоять сквозь кулак). Столбцы X,Y,Z."""
    hand, blade, anchor = v3(hand), v3(blade), v3(anchor)
    Y = nrm(hand - anchor)
    Z = -(blade - Y * np.dot(blade, Y))
    if np.linalg.norm(Z) < 1e-3:
        Z = np.cross(Y, v3(1, 0, 0))
    Z = nrm(Z)
    X = np.cross(Y, Z)
    return np.stack([X, Y, Z], 1)


ARM_TOP = 2.0            # px выше плеча (верх кубика руки)
STRETCH_MAX = 1.8


def arm_stretch(hand, Y, scale):
    """Во сколько раз вытянуть руку вдоль, чтобы её верх ушёл за z = −0.04 (как в Java FpArms.stretch)."""
    L0 = (ARM_GRIP_Y + ARM_TOP) * scale / 16.0
    if Y[2] >= -1e-3:                     # рука идёт от кисти к камере — Y смотрит в −z? (Y — от плеча к кисти)
        pass
    dz = -Y[2]                            # насколько рука уходит назад (к камере) на единицу длины
    if dz <= 0.05:
        return STRETCH_MAX
    need = (-0.04 - hand[2]) / dz
    return float(min(STRETCH_MAX, max(1.0, need / L0)))


def arm_batches(skin, hand, blade, anchor, right=True, slim=False, scale=1.0, width=0.8):
    """Рука и рукав из скина. hand — середина кулака, blade — направление клинка из кулака."""
    R = arm_frame(hand, blade, anchor)
    if right:
        cube = (40, 16, -2 if slim else -3, -2, -2, 3 if slim else 4, 12, 4, 0)
        sleeve = (40, 32) + cube[2:8] + (0.25,)
        cx = -0.5 if slim else -1.0
    else:
        cube = (32, 48, -1, -2, -2, 3 if slim else 4, 12, 4, 0)
        sleeve = (48, 48) + cube[2:8] + (0.25,)
        cx = 0.5 if slim else 1.0
    # рука тянется до плоскости z = −0.04 (за ближней плоскостью камеры) — конец руки никогда не виден
    k = arm_stretch(hand, R[:, 1], scale)
    S = np.diag([width, k, width]) * scale / 16.0
    origin = v3(hand) - R @ (S @ v3(cx, ARM_GRIP_Y, 0))
    tw, th = skin.size
    out = []
    for cb, layer in ((cube, 0), (sleeve, 1)):
        bt = Batch(skin)
        for verts, u1, v1, u2, v2 in sp.cuboid_quads(cb):
            V = [origin + R @ (S @ v3(p)) for p in verts]
            UV = [(u2 / tw, v1 / th), (u1 / tw, v1 / th), (u1 / tw, v2 / th), (u2 / tw, v2 / th)]
            bt.add(V, UV)
        out.append(bt)
    top = origin + R @ (S @ v3(cx, -2, 0))           # верхний край руки — должен быть за краем экрана
    return out, top


def make_skin():
    """Скин для превью (как у Стива по раскладке 64×64): кожа, короткий рукав рубашки, рукав‑слой."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rng = np.random.default_rng(7)

    def fill(x0, y0, w, h, base, shirt_rows=0, shirt=(36, 150, 160), alpha=255):
        for y in range(h):
            for x in range(w):
                c = shirt if y < shirt_rows else base
                k = 1.0 + rng.uniform(-0.05, 0.05) - (0.08 if x in (0, w - 1) else 0)
                px[x0 + x, y0 + y] = tuple(int(min(255, ch * k)) for ch in c) + (alpha,)
    skin = (196, 142, 110)
    fill(0, 0, 32, 16, skin)                          # голова
    fill(8, 0, 8, 8, (70, 46, 30))                    # волосы сверху
    fill(8, 8, 8, 2, (70, 46, 30))
    for (x, c) in ((9, (250, 250, 250)), (10, (60, 60, 200)), (13, (60, 60, 200)), (14, (250, 250, 250))):
        px[x, 12] = c + (255,)
    for x in range(10, 14):
        px[x, 14] = (140, 80, 70, 255)
    fill(16, 16, 24, 16, (36, 150, 160))              # туловище — рубашка
    fill(0, 16, 16, 16, (60, 60, 150))                # ноги — штаны
    fill(16, 48, 16, 16, (60, 60, 150))
    for (u, v) in ((40, 16), (32, 48)):              # руки: 4×12×4 → развёртка 16×16
        fill(u, v, 16, 16, skin)
        fill(u + 4, v, 8, 4, (36, 150, 160))          # верх (плечо) — рубашка
        for (x0, w) in ((u, 4), (u + 4, 4), (u + 8, 4), (u + 12, 4)):
            fill(x0, v + 4, w, 4, skin, shirt_rows=3)
        fill(u + 8, v, 4, 4, skin)                    # низ (кисть)
        for x in range(u, u + 16):                    # костяшки чуть темнее
            px[x, v + 15] = (176, 122, 92, 255)
    return img


def visible(rend, p, margin=0):
    if p[2] > -NEAR:
        return False
    s = rend.project(p)
    return -margin <= s[0] <= rend.w + margin and -margin <= s[1] <= rend.h + margin


# =====================================================================
# Листы кадров и GIF
# =====================================================================
def sheet(frames, cols, label=None, scale=0.5):
    w, h = frames[0].size
    tw, th = int(w * scale), int(h * scale)
    rows = (len(frames) + cols - 1) // cols
    img = Image.new("RGB", (cols * (tw + 4), rows * (th + 4)), (30, 30, 34))
    from PIL import ImageDraw
    for i, f in enumerate(frames):
        x, y = (i % cols) * (tw + 4), (i // cols) * (th + 4)
        img.paste(f.resize((tw, th), Image.LANCZOS), (x, y))
        if label:
            ImageDraw.Draw(img).text((x + 4, y + 2), label[i], fill=(255, 255, 0))
    return img


def gif(frames, path, fps=30):
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, optimize=False)
