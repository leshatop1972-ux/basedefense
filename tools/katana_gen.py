"""
Катана: модель (сетка — изогнутый клинок, гарда, рукоять в оплётке, ножны), текстура, иконка,
анимации 1‑го лица (руки + катана + камера) и позы 3‑го лица.

    python tools/katana_gen.py                 — всё: модель, текстура, иконка, анимации, KatanaKeys.java
    python tools/katana_gen.py --model         — превью модели крупно (tools/katana_model.png)
    python tools/katana_gen.py --fp <клип>     — кадры клипа глазами игрока (tools/katana_fp.png) и GIF (tools/katana_<клип>.gif)
    python tools/katana_gen.py --tp            — позы 3‑го лица (tools/katana_tp.png)
    python tools/katana_gen.py --check         — проверка: рука не обрывается на экране, кромка идёт по ходу удара

Система координат катаны (px модели): начало — середина правого кулака на рукояти, +y — к острию,
+x — режущая кромка (ха), −x — обух (мунэ), z — толщина.

1‑е лицо — пространство камеры (блоки): x вправо, y вверх, камера смотрит в −z. Поле зрения 70° (как у руки
в Minecraft). Анимации пекутся в assets/basedefense/fpanim/katana.json по 60 кадров/с; Java (client/weapon/fp)
лишь интерполирует кадры — что видно здесь в превью, то и в игре.
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

TOOLS = os.path.dirname(__file__)
ASSETS = os.path.join(TOOLS, "..", "src", "main", "resources", "assets", "basedefense")
JAVA_DIR = os.path.join(TOOLS, "..", "src", "client", "java", "com", "lesha_top1972", "basedefense", "client", "weapon")

# =====================================================================
# Размеры (px модели)
# =====================================================================
TSUBA_Y0, TSUBA_Y1 = 1.95, 2.40        # гарда
HABAKI_Y1 = 3.15                        # муфта у основания клинка
BLADE_Y0 = 2.40
BLADE_LEN = 13.6
TIP_Y = BLADE_Y0 + BLADE_LEN            # 16.0
KISSAKI = 2.1                           # длина острия (от ёкотэ)
SORI = 0.80                             # изгиб: на сколько острие уходит к обуху
W_BASE, W_TIP = 1.20, 0.86              # ширина клинка у основания и у ёкотэ
T_MUNE, T_SHINOGI = 0.30, 0.38          # толщина по обуху и по ребру
HANDLE_Y0 = -4.35                       # нижний край рукояти (над навершием)
KASHIRA_Y = -4.78
LEFT_GRIP = -2.95                       # середина левого кулака (двуручный хват)
SAYA_END = TIP_Y + 0.9

TEX_W, TEX_H = 128, 128


# =====================================================================
# Текстура
# =====================================================================
def lerpc(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


class Atlas:
    def __init__(self):
        self.a = np.zeros((TEX_H, TEX_W, 4), np.float32)
        self.rng = np.random.default_rng(1972)

    def put(self, x, y, c, a=1.0):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            self.a[y, x] = (c[0] / 255, c[1] / 255, c[2] / 255, a)

    def img(self):
        return Image.fromarray((np.clip(self.a, 0, 1) * 255).astype(np.uint8), "RGBA")


# области атласа (x0, y0, w, h)
R_HIRA = (0, 0, 128, 12)        # плоскость у кромки (линия закалки)
R_SHIN = (0, 12, 128, 6)        # плоскость у обуха (синоги‑дзи)
R_MUNE = (0, 18, 128, 3)        # обух
R_EDGE = (0, 21, 128, 2)        # сама кромка (светлая полоса)
R_TSUKA = (0, 24, 96, 16)       # оплётка рукояти: u — вдоль, v — по кругу
R_TSUBA = (96, 24, 16, 16)      # гарда (лицевые стороны)
R_TSUBA_RIM = (112, 24, 16, 4)  # обод гарды
R_GOLD = (112, 28, 16, 4)       # хабаки/сэппа (латунь)
R_IRON = (112, 32, 16, 4)       # навершие (тёмный металл)
R_SAYA = (0, 44, 128, 16)       # ножны: u — вдоль, v — по кругу
R_HORN = (0, 60, 16, 4)         # устье ножен (рог)
R_CORD = (16, 60, 16, 4)        # шнур
R_GLOW = (32, 60, 8, 4)         # белый (для свечения)


def paint():
    A = Atlas()
    rng = A.rng
    # --- клинок: плоскость у кромки. Строка 0 — кромка, 11 — ребро.
    x0, y0, w, h = R_HIRA
    for x in range(w):
        s = x / (w - 1)                                      # 0 — у гарды, 1 — у острия
        # линия закалки (хамон): волна notare + мелкие «зубцы» gunome; у острия — бо‑си (поворот к обуху)
        wave = 4.3 + 1.1 * math.sin(s * 9.0 + 0.6) + 0.55 * math.sin(s * 23.0 + 1.3) + 0.35 * math.sin(s * 51.0)
        if s > 0.86:
            wave += (s - 0.86) / 0.14 * 2.5
        if s < 0.04:
            wave = 2.0 + s / 0.04 * (wave - 2.0)              # мачи — у основания закалка мельче
        for y in range(h):
            d = y - wave
            if d < -0.6:                                     # закалённая зона: светлая, «изморозь»
                c = lerpc((236, 240, 246), (214, 222, 232), y / max(wave, 1))
                k = 1.0 + rng.uniform(-0.03, 0.03)
            elif d < 0.8:                                    # сама граница — нио (светящиеся крупинки)
                c = (248, 250, 255) if rng.uniform() < 0.55 else (226, 232, 242)
                k = 1.0
            else:                                            # чёрная сталь (дзигане) с древесным узором
                grain = 0.04 * math.sin(x * 0.9 + y * 2.1 + math.sin(x * 0.21) * 3)
                c = lerpc((160, 170, 186), (132, 142, 160), (y - wave) / max(h - wave, 1))
                k = 1.0 + grain + rng.uniform(-0.025, 0.025)
            if 0.30 < s < 0.40 and 6 <= y <= 7:              # полированный блик вдоль клинка
                k += 0.10
            A.put(x0 + x, y0 + y, tuple(min(255, ch * k) for ch in c))
    # --- синоги‑дзи (у обуха): воронёная полировка, темнее
    x0, y0, w, h = R_SHIN
    for x in range(w):
        for y in range(h):
            c = lerpc((122, 130, 146), (98, 106, 124), y / (h - 1))
            k = 1.0 + rng.uniform(-0.02, 0.02) + (0.08 if y == 0 else 0)
            A.put(x0 + x, y0 + y, tuple(min(255, ch * k) for ch in c))
    for x in range(R_MUNE[2]):
        for y in range(R_MUNE[3]):
            A.put(R_MUNE[0] + x, R_MUNE[1] + y, (150, 158, 172) if y == 1 else (118, 126, 142))
    for x in range(R_EDGE[2]):
        for y in range(R_EDGE[3]):
            A.put(R_EDGE[0] + x, R_EDGE[1] + y, (252, 253, 255))
    # --- рукоять: белая кожа ската (самэ) под чёрной шёлковой оплёткой ромбами
    x0, y0, w, h = R_TSUKA
    period = 12.0
    for x in range(w):
        for y in range(h):
            phase = (x % period) / period
            cy = abs((y / h * 2) % 2 - 1)                    # 0..1 по кругу (две стороны)
            a1 = abs(phase - 0.5) * 2                        # 0 в центре витка
            diamond = a1 + cy * 0.9 < 0.62                   # окошко — видна самэ
            if diamond:
                c = (232, 226, 210)
                if rng.uniform() < 0.3:
                    c = (214, 206, 188)
                if abs(a1 + cy * 0.9 - 0.62) < 0.12:
                    c = (150, 144, 132)                      # тень от оплётки
            else:
                c = (30, 26, 34)
                if (x + y) % 3 == 0:
                    c = (46, 40, 52)                         # блеск шёлка
                if abs(a1 - (1 - cy)) < 0.08:
                    c = (58, 50, 64)
            A.put(x0 + x, y0 + y, c)
    # --- гарда: чернёное железо с золотым ободом и узором
    x0, y0, w, h = R_TSUBA
    for x in range(w):
        for y in range(h):
            r = math.hypot(x - 7.5, y - 7.5) / 7.5
            c = (44, 40, 44)
            if r > 0.86:
                c = (176, 140, 64)
            elif 0.55 < r < 0.62:
                c = (120, 96, 50)
            k = 1.0 + rng.uniform(-0.05, 0.05)
            A.put(x0 + x, y0 + y, tuple(min(255, ch * k) for ch in c))
    for (reg, base, hi) in ((R_TSUBA_RIM, (176, 140, 64), (214, 182, 96)), (R_GOLD, (192, 150, 68), (232, 196, 110)),
                            (R_IRON, (40, 38, 44), (72, 68, 76))):
        x0, y0, w, h = reg
        for x in range(w):
            for y in range(h):
                A.put(x0 + x, y0 + y, hi if y == 1 else base)
    # --- ножны: чёрный лак с бордовым отливом, блик по краю
    x0, y0, w, h = R_SAYA
    for x in range(w):
        for y in range(h):
            cy = abs((y / (h - 1)) * 2 - 1)
            c = lerpc((26, 18, 22), (54, 22, 30), 0.5 + 0.5 * math.sin(x * 0.11))
            if cy < 0.12:
                c = lerpc(c, (120, 90, 100), 0.6)            # блик лака
            if x < 3 or x > w - 5:
                c = (36, 32, 38)
            k = 1.0 + rng.uniform(-0.02, 0.02)
            A.put(x0 + x, y0 + y, tuple(min(255, ch * k) for ch in c))
    for x in range(R_HORN[2]):
        for y in range(R_HORN[3]):
            A.put(R_HORN[0] + x, R_HORN[1] + y, (228, 214, 186) if y else (246, 236, 214))
    for x in range(R_CORD[2]):
        for y in range(R_CORD[3]):
            A.put(R_CORD[0] + x, R_CORD[1] + y, (150, 24, 32) if (x + y) % 3 else (196, 46, 50))
    for x in range(R_GLOW[2]):
        for y in range(R_GLOW[3]):
            A.put(R_GLOW[0] + x, R_GLOW[1] + y, (255, 255, 255))
    return A.img()


def uv_of(reg, u, v):
    """u, v — 0..1 внутри области → координаты атласа 0..1 (с отступом полтекселя)."""
    x0, y0, w, h = reg
    return ((x0 + 0.5 + u * (w - 1)) / TEX_W, (y0 + 0.5 + v * (h - 1)) / TEX_H)


# =====================================================================
# Геометрия
# =====================================================================
class Mesh:
    def __init__(self):
        self.q = []          # (V 4×3, UV 4×2, N 4×3)

    def quad(self, V, UV, N=None):
        V = [v3(p) for p in V]
        if N is None:
            n = nrm(np.cross(V[1] - V[0], V[2] - V[0]))
            if np.linalg.norm(n) < 0.5:
                n = nrm(np.cross(V[2] - V[0], V[3] - V[0]))
            N = [n] * 4
        self.q.append((np.array(V), np.array(UV, float), np.array([nrm(n) for n in N])))

    def tri(self, a, b, c, uva, uvb, uvc, N=None):
        self.quad([a, b, c, c], [uva, uvb, uvc, uvc], N)


def blade_center(s):
    """Средняя линия клинка: s — px от основания (0..BLADE_LEN). Изгиб к обуху (−x) ~ s²."""
    t = s / BLADE_LEN
    return v3(-SORI * t * t, BLADE_Y0 + s, 0.0)


def blade_tangent(s):
    t = s / BLADE_LEN
    return nrm(v3(-2 * SORI * t / BLADE_LEN, 1.0, 0.0))


def blade_section(s):
    """Сечение клинка в точке s: [кромка, ребро+, обух+, обух−, ребро−] (x, z) относительно средней линии
    в плоскости, перпендикулярной касательной."""
    t = s / BLADE_LEN
    yokote = BLADE_LEN - KISSAKI
    w = W_BASE + (W_TIP - W_BASE) * min(1.0, s / yokote)
    th = 1.0 - 0.35 * t
    tm, ts = T_MUNE * th / 2, T_SHINOGI * th / 2
    mune = -0.45 * w
    edge = 0.55 * w
    shin = mune + 0.32 * w
    if s > yokote:                                         # острие: кромка закругляется к обуху (фукура)
        u = (s - yokote) / KISSAKI
        k = math.sqrt(max(0.0, 1 - u ** 1.6))
        edge = mune + (edge - mune) * k
        shin = mune + (shin - mune) * k
        tm *= k
        ts *= max(k, 0.0)
        mune = mune + 0.0 * u
    return [(edge, 0.0), (shin, ts), (mune, tm), (mune, -tm), (shin, -ts)]


def build_blade(m):
    yokote = BLADE_LEN - KISSAKI
    ss = list(np.linspace(0, yokote, 30)) + list(np.linspace(yokote, BLADE_LEN, 12))[1:]
    rings = []
    for s in ss:
        c = blade_center(s)
        tg = blade_tangent(s)
        xa = nrm(np.cross(tg, v3(0, 0, 1)))                 # перпендикуляр к касательной в плоскости x‑y (≈ +x)
        if xa[0] < 0:
            xa = -xa
        pts = [c + xa * x + v3(0, 0, z) for (x, z) in blade_section(s)]
        rings.append((s, pts))
    # грани: 0 кромка→ребро+ (хира), 1 ребро+→обух+ (синоги‑дзи), 2 обух (мунэ), 3 обух−→ребро−, 4 ребро−→кромка
    regs = [R_HIRA, R_SHIN, R_MUNE, R_SHIN, R_HIRA]
    for i in range(len(rings) - 1):
        s0, A = rings[i]
        s1, B = rings[i + 1]
        u0, u1 = s0 / BLADE_LEN, s1 / BLADE_LEN
        for f in range(5):
            a0, a1 = A[f], A[(f + 1) % 5]
            b0, b1 = B[f], B[(f + 1) % 5]
            reg = regs[f]
            if f in (0,):          # кромка (v=0) → ребро (v=1)
                va, vb = 0.0, 1.0
            elif f == 4:           # ребро− (v=1) → кромка (v=0)
                va, vb = 1.0, 0.0
            elif f == 1:
                va, vb = 0.0, 1.0
            elif f == 3:
                va, vb = 1.0, 0.0
            else:
                va, vb = 0.0, 1.0
            m.quad([a0, b0, b1, a1], [uv_of(reg, u0, va), uv_of(reg, u1, va), uv_of(reg, u1, vb), uv_of(reg, u0, vb)])
    # светлая полоска по самой кромке — тонкий «лист» (видно при взгляде на кромку)
    # основание клинка закрыто хабаки — торец не нужен; кончик сходится в точку (последнее кольцо вырождено)


def ellipse_ring(cx, y, cz, rx, rz, n, phase=0.0):
    return [v3(cx + rx * math.cos(phase + 2 * math.pi * i / n), y, cz + rz * math.sin(phase + 2 * math.pi * i / n))
            for i in range(n)]


def build_tube(m, ys, radii, reg, n=10, smooth=True, cx=0.0, u_range=(0, 1), cap0=None, cap1=None, cap_reg=None):
    """Труба вдоль y (рукоять, навершие). radii(y) → (rx, rz)."""
    rings = [ellipse_ring(cx, y, 0.0, *radii(y), n, math.pi / n) for y in ys]
    for i in range(len(ys) - 1):
        u0 = u_range[0] + (u_range[1] - u_range[0]) * i / (len(ys) - 1)
        u1 = u_range[0] + (u_range[1] - u_range[0]) * (i + 1) / (len(ys) - 1)
        for k in range(n):
            a0, a1 = rings[i][k], rings[i][(k + 1) % n]
            b0, b1 = rings[i + 1][k], rings[i + 1][(k + 1) % n]
            va, vb = k / n, (k + 1) / n
            N = None
            if smooth:
                def nn(p, y):
                    rx, rz = radii(y)
                    return nrm(v3((p[0] - cx) / (rx * rx), 0, p[2] / (rz * rz)))
                N = [nn(a0, ys[i]), nn(b0, ys[i + 1]), nn(b1, ys[i + 1]), nn(a1, ys[i])]
            m.quad([a0, b0, b1, a1], [uv_of(reg, u0, va), uv_of(reg, u1, va), uv_of(reg, u1, vb), uv_of(reg, u0, vb)], N)
    for cap, ring, sign in ((cap0, rings[0], -1), (cap1, rings[-1], 1)):
        if cap is None:
            continue
        c = v3(cx, ring[0][1] + sign * cap, 0)
        for k in range(n):
            a, b = ring[k], ring[(k + 1) % n]
            if sign > 0:
                m.tri(a, b, c, uv_of(cap_reg, 0, 0), uv_of(cap_reg, 1, 0), uv_of(cap_reg, 0.5, 1))
            else:
                m.tri(b, a, c, uv_of(cap_reg, 0, 0), uv_of(cap_reg, 1, 0), uv_of(cap_reg, 0.5, 1))


def build_disc(m, y0, y1, rx, rz, n, face_reg, rim_reg, hole=None):
    """Плоский диск (гарда, шайбы) поперёк y: рёбра — обод, торцы — веер из центра."""
    r0 = ellipse_ring(0, y0, 0, rx, rz, n, math.pi / n)
    r1 = ellipse_ring(0, y1, 0, rx, rz, n, math.pi / n)
    for k in range(n):
        a0, a1, b0, b1 = r0[k], r0[(k + 1) % n], r1[k], r1[(k + 1) % n]
        m.quad([a0, b0, b1, a1], [uv_of(rim_reg, k / n, 0), uv_of(rim_reg, k / n, 1), uv_of(rim_reg, (k + 1) / n, 1),
                                  uv_of(rim_reg, (k + 1) / n, 0)])
    for ring, y, up in ((r0, y0, False), (r1, y1, True)):
        c = v3(0, y, 0)
        for k in range(n):
            a, b = ring[k], ring[(k + 1) % n]
            ua = (0.5 + 0.5 * (a[0] / rx), 0.5 + 0.5 * (a[2] / rz))
            ub = (0.5 + 0.5 * (b[0] / rx), 0.5 + 0.5 * (b[2] / rz))
            uvs = [uv_of(face_reg, *ua), uv_of(face_reg, *ub), uv_of(face_reg, 0.5, 0.5)]
            if up:
                m.tri(a, b, c, *uvs)
            else:
                m.tri(b, a, c, uvs[1], uvs[0], uvs[2])


def build_box(m, x0, x1, y0, y1, z0, z1, reg):
    P = [v3(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]

    def q(a, b, c, d):
        m.quad([P[a], P[b], P[c], P[d]], [uv_of(reg, 0, 0), uv_of(reg, 0, 1), uv_of(reg, 1, 1), uv_of(reg, 1, 0)])
    q(0, 1, 3, 2)   # x0
    q(4, 6, 7, 5)   # x1
    q(0, 4, 5, 1)   # z0
    q(2, 3, 7, 6)   # z1
    q(1, 5, 7, 3)   # y1
    q(0, 2, 6, 4)   # y0


def build_katana():
    m = Mesh()
    build_blade(m)
    # хабаки — латунная муфта у основания клинка (по сечению клинка, чуть шире)
    sec = blade_section(0.0)
    e, mu = sec[0][0] + 0.10, sec[2][0] - 0.10
    build_box(m, mu, e, TSUBA_Y1, HABAKI_Y1, -0.25, 0.25, R_GOLD)
    # гарда (цуба) и шайбы (сэппа)
    build_disc(m, TSUBA_Y0 + 0.06, TSUBA_Y1 - 0.06, 1.62, 1.38, 14, R_TSUBA, R_TSUBA_RIM)
    build_disc(m, TSUBA_Y1 - 0.06, TSUBA_Y1, 0.86, 0.56, 10, R_GOLD, R_GOLD)
    build_disc(m, TSUBA_Y0, TSUBA_Y0 + 0.06, 0.86, 0.56, 10, R_GOLD, R_GOLD)
    # рукоять: к середине чуть тоньше; фути (латунное кольцо) у гарды
    build_box(m, -0.66, 0.66, TSUBA_Y0 - 0.35, TSUBA_Y0, -0.50, 0.50, R_IRON)

    def tr(y):
        t = (y - HANDLE_Y0) / (TSUBA_Y0 - 0.35 - HANDLE_Y0)
        k = 1.0 - 0.07 * math.sin(t * math.pi)
        return 0.60 * k, 0.45 * k
    ys = list(np.linspace(HANDLE_Y0, TSUBA_Y0 - 0.35, 14))
    build_tube(m, ys, tr, R_TSUKA, n=10)
    # навершие (касира)
    build_tube(m, [KASHIRA_Y, HANDLE_Y0], lambda y: (0.66, 0.50), R_IRON, n=10, cap0=0.12, cap_reg=R_IRON)
    return m


def saya_center(y):
    s = y - BLADE_Y0
    return blade_center(min(max(s, 0), BLADE_LEN))


def build_saya():
    m = Mesh()
    ys = list(np.linspace(BLADE_Y0 + 0.02, SAYA_END, 26))
    n = 12
    rings = []
    for y in ys:
        s = min(max(y - BLADE_Y0, 0), BLADE_LEN)
        c = blade_center(s)
        w = W_BASE + (W_TIP - W_BASE) * min(1.0, s / (BLADE_LEN - KISSAKI))
        rx, rz = w * 0.5 + 0.30, 0.42
        if y > SAYA_END - 0.8:                       # кодзири — скруглённый конец
            k = (y - (SAYA_END - 0.8)) / 0.8
            rx *= math.sqrt(max(0.05, 1 - k * k * 0.85))
            rz *= math.sqrt(max(0.05, 1 - k * k * 0.85))
        cx = c[0] + 0.05 * w
        rings.append([v3(cx + rx * math.cos(2 * math.pi * i / n), y, rz * math.sin(2 * math.pi * i / n)) for i in range(n)])
    for i in range(len(ys) - 1):
        reg = R_HORN if i == 0 else R_SAYA
        u0, u1 = i / (len(ys) - 1), (i + 1) / (len(ys) - 1)
        for k in range(n):
            a0, a1, b0, b1 = rings[i][k], rings[i][(k + 1) % n], rings[i + 1][k], rings[i + 1][(k + 1) % n]
            cxa = sum(p[0] for p in rings[i]) / n
            cxb = sum(p[0] for p in rings[i + 1]) / n
            N = [nrm(a0 - v3(cxa, a0[1], 0)), nrm(b0 - v3(cxb, b0[1], 0)), nrm(b1 - v3(cxb, b1[1], 0)),
                 nrm(a1 - v3(cxa, a1[1], 0))]
            m.quad([a0, b0, b1, a1], [uv_of(reg, u0, k / n), uv_of(reg, u1, k / n), uv_of(reg, u1, (k + 1) / n),
                                      uv_of(reg, u0, (k + 1) / n)], N)
    # торцы: устье (тёмное отверстие) и конец
    for ring, up in ((rings[0], False), (rings[-1], True)):
        c = sum(ring) / n
        for k in range(n):
            a, b = ring[k], ring[(k + 1) % n]
            uvs = [uv_of(R_IRON, 0, 0), uv_of(R_IRON, 1, 0), uv_of(R_IRON, 0.5, 1)]
            if up:
                m.tri(a, b, c, *uvs)
            else:
                m.tri(b, a, c, *uvs)
    # курикатa и шнур (сагэо) — на стороне −z у устья
    build_box(m, -0.25, 0.25, 3.6, 4.4, -0.42 - 0.35, -0.38, R_HORN)
    build_box(m, -0.18, 0.18, 4.4, 6.8, -0.70, -0.52, R_CORD)
    return m


def make_icon():
    """Иконка 32×32: изогнутый клинок по диагонали, гарда, оплётка."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    d = {}
    # клинок: от (9,22) к (30,1) с изгибом
    for i in range(0, 25):
        t = i / 24
        x = 9.5 + t * 20.5
        y = 21.5 - t * 20.5 - 1.6 * math.sin(t * math.pi) * 0 - 1.3 * t * t
        bx, by = int(round(x)), int(round(y))
        d[(bx, by)] = (236, 240, 246)                 # кромка (сверху‑слева)
        d[(bx + 1, by + 1)] = (150, 160, 178)         # обух
        d[(bx, by + 1)] = (200, 208, 220)
        if i % 3 == 0:
            d[(bx + 1, by)] = (214, 220, 230)
    for (x, y) in ((7, 21), (8, 22), (9, 23), (10, 24), (8, 20), (11, 25), (6, 20)):
        d[(x, y)] = (176, 140, 64) if (x, y) in ((8, 22), (9, 23)) else (52, 48, 52)
    for i in range(6):
        x, y = 7 - i, 24 + i
        d[(x, y)] = (30, 26, 34) if i % 2 == 0 else (232, 226, 210)
        d[(x + 1, y)] = (46, 40, 52)
        d[(x, y - 1)] = (40, 36, 44)
    d[(1, 30)] = (60, 58, 66)
    d[(2, 30)] = (60, 58, 66)
    d[(1, 29)] = (60, 58, 66)
    for (x, y), c in d.items():
        if 0 <= x < 32 and 0 <= y < 32:
            px[x, y] = c + (255,)
    out = img.copy()
    po = out.load()
    for y in range(32):
        for x in range(32):
            if px[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and px[x + dx, y + dy][3]
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = (20, 18, 24, 255)
    return out


def mesh_json(m):
    out = []
    for V, UV, N in m.q:
        for i in range(4):
            out += [round(float(V[i][0]), 4), round(float(V[i][1]), 4), round(float(V[i][2]), 4),
                    round(float(UV[i][0]), 5), round(float(UV[i][1]), 5),
                    round(float(N[i][0]), 3), round(float(N[i][1]), 3), round(float(N[i][2]), 3)]
    return out


def mesh_batch(m, tex, tf, ntf, color=(1, 1, 1, 1)):
    b = fv.Batch(tex, color=color)
    for V, UV, N in m.q:
        b.add([tf(p) for p in V], UV, [ntf(n) for n in N])
    return b


KATANA = build_katana()
SAYA = build_saya()


# =====================================================================
# 1‑е лицо: ключи → кадры
# =====================================================================
S1 = 1.00                    # масштаб катаны и рук от 1‑го лица (px → блоки: S1/16)
ARM_W = 0.62                 # толщина рук (доля от обычной) — кулак вровень с рукоятью
FPS = 60
TIPD = TIP_Y * S1 / 16       # от кулака до острия, блоки
SHOULDER_R = v3(0.40, -0.38, 0.10)
SHOULDER_L = v3(-0.20, -0.48, 0.10)


def frame_q(R):
    """Матрица (столбцы X,Y,Z) → кватернион (x,y,z,w)."""
    m = R
    tr = m[0, 0] + m[1, 1] + m[2, 2]
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        w, x, y, z = 0.25 * s, (m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w, x, y, z = (m[2, 1] - m[1, 2]) / s, 0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w, x, y, z = (m[0, 2] - m[2, 0]) / s, (m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s
    else:
        s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w, x, y, z = (m[1, 0] - m[0, 1]) / s, (m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s
    q = np.array([x, y, z, w])
    return q / np.linalg.norm(q)


def q_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def blade_frame(B, E):
    Y = nrm(B)
    X = E - Y * np.dot(E, Y)
    if np.linalg.norm(X) < 1e-4:
        X = np.cross(Y, v3(0, 0, 1))
    X = nrm(X)
    Z = np.cross(X, Y)
    return np.stack([X, Y, Z], 1)


def slerp(a, b, t):
    a, b = nrm(a), nrm(b)
    d = float(np.clip(np.dot(a, b), -1, 1))
    if d > 0.9995:
        return nrm(a + (b - a) * t)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


class K:
    """Ключ 1‑го лица. G — середина правого кулака; T — куда смотрит клинок (точка на продолжении);
    E — куда смотрит кромка (подсказка); cut — от этого ключа до следующего кромка ведёт удар (ставится по ходу);
    hold — ключ‑остановка (нулевая скорость); L — левая рука: None — на рукояти, иначе (точка, ось кулака);
    lw — вес свободной левой руки 0..1; tw — поворот корпуса (град), cr — присед (блоки);
    cam — (pitch, yaw, roll) градусы; fov — множитель обзора; tr — след клинка 0..1; gl — свечение клинка;
    sheath — (d) катана в ножнах, вынута на d px (позиция берётся от ножен клипа)."""

    def __init__(self, t, G=None, T=None, E=None, cut=False, hold=False, L=None, lw=0.0, tw=0.0, cr=0.0,
                 cam=(0, 0, 0), fov=1.0, tr=0.0, gl=0.0, sheath=None, ease=None):
        self.t = t
        self.G = None if G is None else v3(G)
        self.T = None if T is None else v3(T)
        self.E = None if E is None else v3(E)
        self.cut, self.hold = cut, hold
        self.L, self.lw, self.tw, self.cr = L, lw, tw, cr
        self.cam, self.fov, self.tr, self.gl = v3(cam), fov, tr, gl
        self.sheath = sheath
        self.ease = ease


def hermite(ts, ps, holds, t, loop_len=None):
    """Сплайн Катмулла–Рома по времени (неравномерные ключи), ключи hold — с нулевой скоростью."""
    n = len(ts)
    if n == 1:
        return ps[0]
    if t <= ts[0]:
        return ps[0]
    if t >= ts[-1]:
        return ps[-1]
    i = max(k for k in range(n - 1) if ts[k] <= t)
    t0, t1 = ts[i], ts[i + 1]

    def tangent(k):
        if holds[k]:
            return ps[k] * 0
        if 0 < k < n - 1:
            return (ps[k + 1] - ps[k - 1]) / (ts[k + 1] - ts[k - 1])
        if loop_len is not None:
            if k == 0:
                return (ps[1] - ps[n - 2]) / (ts[1] + loop_len - ts[n - 2])
            return (ps[1] - ps[n - 2]) / (ts[1] + loop_len - ts[n - 2])
        return ps[k] * 0
    h = t1 - t0
    s = (t - t0) / h
    m0, m1 = tangent(i) * h, tangent(i + 1) * h
    s2, s3 = s * s, s * s * s
    return (2 * s3 - 3 * s2 + 1) * ps[i] + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * ps[i + 1] + (s3 - s2) * m1


class Clip:
    def __init__(self, name, length, keys, loop=False, saya=None, events=()):
        self.name, self.length, self.keys, self.loop = name, length, keys, loop
        self.saya = saya          # (точка устья‑начала, направление клинка в ножнах, кромка) — если ножны видны
        self.events = list(events)
        self.resolve()

    def resolve(self):
        prev = None
        for k in self.keys:
            if k.sheath is not None:
                p, b, e = self.saya
                k.G = v3(p) - nrm(b) * (k.sheath * S1 / 16)
                k.T = k.G + nrm(b) * TIPD
                k.E = v3(e)
            if k.G is None:
                k.G, k.T, k.E = prev.G, prev.T, prev.E
            if k.E is None:
                k.E = prev.E
            prev = k

    def _arrays(self):
        ts = [k.t for k in self.keys]
        holds = [k.hold for k in self.keys]
        return ts, holds

    def raw(self, t):
        """Положение без кромки по ходу (кромка — из ключей)."""
        ts, holds = self._arrays()
        ll = self.length if self.loop else None
        if self.loop:
            t = t % self.length
        G = hermite(ts, [k.G for k in self.keys], holds, t, ll)
        T = hermite(ts, [k.T for k in self.keys], holds, t, ll)
        B = nrm(T - G)
        n = len(ts)
        i = max([k for k in range(n) if ts[k] <= t] or [0])
        j = min(i + 1, n - 1)
        a = 0.0 if j == i else (t - ts[i]) / (ts[j] - ts[i])
        a = a * a * (3 - 2 * a)
        E = slerp(self.keys[i].E, self.keys[j].E, a)
        sc = lambda f: hermite(ts, [np.atleast_1d(np.array(f(k), float)) for k in self.keys], holds, t, ll)
        lin = lambda f: (1 - a) * f(self.keys[i]) + a * f(self.keys[j])
        cut_w = 0.0
        if self.keys[i].cut:
            cut_w = min(1.0, a / 0.08, (1 - a) / 0.12) if j != i else 0.0
            cut_w = max(0.0, cut_w)
        return dict(G=G, B=B, E=E, i=i, a=a, cut=cut_w,
                    tw=float(sc(lambda k: k.tw)[0]), cr=float(sc(lambda k: k.cr)[0]),
                    cam=sc(lambda k: k.cam), fov=float(sc(lambda k: k.fov)[0]),
                    tr=float(lin(lambda k: k.tr)), gl=float(lin(lambda k: k.gl)),
                    lw=float(sc(lambda k: k.lw)[0]), L=self._left(t, i, j, a))

    def _left(self, t, i, j, a):
        ki, kj = self.keys[i], self.keys[j]
        li = ki.L if ki.L is not None else (kj.L if kj.L is not None else None)
        lj = kj.L if kj.L is not None else li
        if li is None:
            return None
        p = (1 - a) * v3(li[0]) + a * v3(lj[0])
        ax = slerp(v3(li[1]), v3(lj[1]), a)
        return p, ax

    def pose(self, t):
        r = self.raw(t)
        G, B, E = r["G"], r["B"], r["E"]
        if r["cut"] > 0:                                     # кромка — по ходу движения острия
            dt = 1 / 240
            p1 = self.raw(max(0, t - dt))
            p2 = self.raw(min(self.length, t + dt))
            tip1 = p1["G"] + p1["B"] * TIPD * 0.75
            tip2 = p2["G"] + p2["B"] * TIPD * 0.75
            v = tip2 - tip1
            v = v - B * np.dot(v, B)
            if np.linalg.norm(v) > 1e-5:
                E = slerp(E, nrm(v), r["cut"])
        R = blade_frame(B, E)
        r["R"] = R
        # левая рука: на рукояти (по оси клинка у основания — ось Y катаны) или свободная
        grip_l = G + R[:, 1] * (LEFT_GRIP * S1 / 16)
        if r["L"] is not None and r["lw"] > 0:
            p, ax = r["L"]
            r["Lp"] = grip_l * (1 - r["lw"]) + v3(p) * r["lw"]
            r["La"] = slerp(R[:, 1], ax, r["lw"])
        else:
            r["Lp"], r["La"] = grip_l, R[:, 1]
        tw = math.radians(r["tw"])
        rot = fv.rot_axis((0, 1, 0), tw)
        piv = v3(0, 0, 0.15)
        r["AR"] = rot @ (SHOULDER_R - piv) + piv + v3(0, -r["cr"], 0)
        r["AL"] = rot @ (SHOULDER_L - piv) + piv + v3(0, -r["cr"], 0)
        return r


CLIPS = {}


def clip(c):
    CLIPS[c.name] = c
    return c


SAYA_HIDE = v3(-0.30, -0.45, 0.20)          # куда уезжают ножны за край экрана


def saya_pose(c, t):
    """Ножны: (точка, матрица) или None. Видимость sv — канал ключей (0 — за краем экрана)."""
    if c.saya is None:
        return None, 0.0
    ts, holds = c._arrays()
    sv = float(hermite(ts, [np.atleast_1d(float(getattr(k, "sv", 0.0))) for k in c.keys], holds,
                       t % c.length if c.loop else t)[0])
    if sv <= 0.001:
        return None, 0.0
    p, b, e = c.saya
    pos = v3(p) + SAYA_HIDE * (1 - sv)
    return (pos, blade_frame(v3(b), v3(e))), sv


def fp_batches(c, t, tex, skin, trail=True):
    r = c.pose(t)
    M = fv.cam_matrix(*r["cam"])                       # камера (наклон/поворот/крен) — как в Java
    s = S1 / 16
    G, R = r["G"], r["R"]
    tf = lambda p: M @ (G + R @ (v3(p) * s))
    ntf = lambda n: M @ (R @ n)
    bs = [mesh_batch(KATANA, tex, tf, ntf)]
    sp, sv = saya_pose(c, t)
    if sp is not None:
        P, RS = sp
        bs.append(mesh_batch(SAYA, tex, lambda p: M @ (P + RS @ (v3(p) * s)), lambda n: M @ (RS @ n)))
    arms_r, top_r = fv.arm_batches(skin, M @ G, M @ R[:, 1], M @ r["AR"], True, False, S1, ARM_W)
    arms_l, top_l = fv.arm_batches(skin, M @ r["Lp"], M @ r["La"], M @ r["AL"], False, False, S1, ARM_W)
    bs += arms_r + arms_l
    if trail and r["tr"] > 0.01:
        bs.append(trail_batch(c, t, M))
    if r["gl"] > 0.01:
        bs.append(glow_batch(r, M))
        bs.append(flare_batch(M @ (G + R @ (v3(0, TSUBA_Y1 + 0.3, 0) * s)), r["gl"]))
    return bs, r, M, (top_r, top_l)


TRAIL_LEN = 0.11      # секунды «хвоста»
TRAIL_N = 14


def blade_points(r, y):
    return r["G"] + r["R"] @ (v3(0.0, y, 0.0) * (S1 / 16)) + r["R"] @ (blade_center(y - BLADE_Y0) * 0 + v3(blade_center(y - BLADE_Y0)[0], 0, 0) * (S1 / 16))


def trail_batch(c, t, M):
    """След клинка: лента между точкой у основания и острием за последние TRAIL_LEN секунд (как в Java FpTrail)."""
    b = fv.Batch(None, additive=True, lit=False)
    pts = []
    for k in range(TRAIL_N + 1):
        tt = max(0.0, t - TRAIL_LEN * k / TRAIL_N)
        rr = c.pose(tt)
        pts.append((M @ blade_points(rr, BLADE_Y0 + 3.5), M @ blade_points(rr, TIP_Y - 0.2), rr["tr"]))
    for k in range(TRAIL_N):
        (a0, a1, ia), (b0, b1, ib) = pts[k], pts[k + 1]
        fa = (1 - k / TRAIL_N) ** 1.5 * ia
        fb = (1 - (k + 1) / TRAIL_N) ** 1.5 * ib
        col = lambda f, edge: (0.75 * f, 0.85 * f, 1.0 * f, 0.55 if edge else 0.18)
        b.add([a0, a1, b1, b0], [(0, 0)] * 4, None, [col(fa, False), col(fa, True), col(fb, True), col(fb, False)])
    return b


def glow_batch(r, M):
    """Свечение кромки (зарядка): тонкая светлая лента вдоль кромки, ярче к острию."""
    b = fv.Batch(None, additive=True, lit=False)
    g = r["gl"]
    ys = np.linspace(BLADE_Y0 + 0.5, TIP_Y - 0.1, 12)
    s = S1 / 16
    for i in range(len(ys) - 1):
        y0, y1 = ys[i], ys[i + 1]
        e0 = blade_section(y0 - BLADE_Y0)[0][0] + blade_center(y0 - BLADE_Y0)[0]
        e1 = blade_section(y1 - BLADE_Y0)[0][0] + blade_center(y1 - BLADE_Y0)[0]
        P = [r["G"] + r["R"] @ (v3(e0 - 0.25, y0, 0) * s), r["G"] + r["R"] @ (v3(e0 + 0.35, y0, 0) * s),
             r["G"] + r["R"] @ (v3(e1 + 0.35, y1, 0) * s), r["G"] + r["R"] @ (v3(e1 - 0.25, y1, 0) * s)]
        k0, k1 = g * (0.4 + 0.6 * i / 11), g * (0.4 + 0.6 * (i + 1) / 11)
        b.add([M @ p for p in P], [(0, 0)] * 4, None,
              [(0.6 * k0, 0.8 * k0, 1.0 * k0, 0.3), (0.6 * k0, 0.8 * k0, 1.0 * k0, 0.9),
               (0.6 * k1, 0.8 * k1, 1.0 * k1, 0.9), (0.6 * k1, 0.8 * k1, 1.0 * k1, 0.3)])
    return b


def flare_batch(p, g):
    """Блик‑звезда (готовность удара) в точке p (уже в камере): два луча и ореол, лицом к камере."""
    b = fv.Batch(None, additive=True, lit=False)
    b.nodepth = True
    d = -p[2]
    size = 0.13 * g * d
    for (ax, ay, w) in ((1, 0, 0.10), (0, 1, 0.10), (0.7, 0.7, 0.05), (0.7, -0.7, 0.05)):
        u = v3(ax, ay, 0) * size
        v = v3(-ay, ax, 0) * size * w
        c0, c1 = (0.7 * g, 0.85 * g, 1.0 * g, 0.9), (0.0, 0.0, 0.0, 0.0)
        b.add([p - u, p - v, p + u, p + v], [(0, 0)] * 4, None, [c1, c0, c1, c0])
    h = v3(1, 0, 0) * size * 0.35
    k = v3(0, 1, 0) * size * 0.35
    c = (0.5 * g, 0.6 * g, 0.8 * g, 0.6)
    b.add([p - h - k, p + h - k, p + h + k, p - h + k], [(0, 0)] * 4, None, [c] * 4)
    return b


def render_fp(c, times, tex, skin, size=(640, 360), labels=True):
    rend = fv.Renderer(*size)
    out = []
    warn = []
    for t in times:
        bs, r, M, tops = fp_batches(c, t, tex, skin)
        img = rend.render(bs, cam=M)
        for nm, tp in zip(("правая", "левая"), tops):
            if fv.visible(rend, tp, -10):
                warn.append("%s: %.2f с — край руки (%s) на экране" % (c.name, t, nm))
        if labels:
            d = ImageDraw.Draw(img)
            d.line((size[0] / 2 - 6, size[1] / 2, size[0] / 2 + 6, size[1] / 2), fill=(255, 255, 255))
            d.line((size[0] / 2, size[1] / 2 - 6, size[0] / 2, size[1] / 2 + 6), fill=(255, 255, 255))
        out.append(img)
    return out, warn


def P(t, G, B, E, **kw):
    """Ключ через направление клинка B (вместо точки T)."""
    G = v3(G)
    return K(t, G=G, T=G + nrm(v3(B)), E=E, **kw)


# --- стойка (тюдан справа): клинок по диагонали вверх‑влево, видна плоскость, кромка вперёд‑вниз ---
GUARD_E = (-0.75, 0.0, -0.66)


def scr_ray(sx, sy):
    """Точка экрана (0..1 слева‑направо, сверху‑вниз; 16:9, поле зрения руки 70°) → луч из глаза."""
    k = math.tan(math.radians(35))
    return v3((sx - 0.5) * 2 * k * 16 / 9, -(sy - 0.5) * 2 * k, -1.0)


def scr_key(t, H, T, z, E, **kw):
    """Ключ по экрану: H — где кулак, T — где острие (точки экрана), z — глубина кулака (блоки)."""
    G = scr_ray(*H) * z
    r = nrm(scr_ray(*T))
    L = TIPD
    b = np.dot(r, G)
    disc = b * b - (np.dot(G, G) - L * L)
    tt = b + math.sqrt(max(disc, 0.0))
    tip = r * tt
    B = nrm(tip - G)
    if E is None:
        E = np.cross(B, v3(0, 0, 1))
    return K(t, G=G, T=G + B, E=E, **kw)


_g = scr_key(0, (0.75, 0.92), (0.86, -0.05), 0.62, GUARD_E)
GUARD_G, GUARD_B = tuple(_g.G), tuple(nrm(_g.T - _g.G))


def guard(t, **kw):
    return P(t, GUARD_G, GUARD_B, GUARD_E, **kw)


# Ножны у левого бедра (видны при зарядке и выхвате): начало — где кулак на рукояти у вложенной катаны.
SAYA_POSE = ((0.00, -0.24, -0.62), (-0.86, -0.22, 0.46), (0.05, 1.0, 0.25))
KOIGUCHI_L = 0.32 * S1 / 16 * 16 / 16      # не используется


# В покое — ножны у таза справа, ровно (вдоль взгляда), почти за нижним краем экрана; на рукояти — правая рука.
SAYA_IDLE = ((0.22, -0.46, -0.44), (0.0, -0.06, 1.0), (0.0, 1.0, 0.06))      # справа, ровно назад, кромка вверх
L_DOWN = (v3(-0.26, -0.80, -0.30), v3(-0.2, 0.3, -0.93))


def L_saya(d=0.30):
    """Левая рука на устье ножен (большой палец на гарде): точка и ось кулака (по ножнам)."""
    p, b, e = SAYA_POSE
    return (v3(p) + nrm(v3(b)) * (TSUBA_Y1 + 1.6) * S1 / 16, nrm(v3(b)))


def build_clips():
    CLIPS.clear()
    # ---------------- стойка: дыхание (петля 4 с) ----------------
    g, b = v3(GUARD_G), v3(GUARD_B)
    clip(Clip("idle", 4.0, [
        guard(0.0),
        P(1.0, g + v3(0.002, 0.007, 0.0), b + v3(0.012, 0.0, 0.0), GUARD_E, cam=(-0.15, 0, 0)),
        P(2.0, g + v3(0.0, 0.010, 0.003), b + v3(0.0, 0.0, 0.02), GUARD_E, cam=(-0.25, 0, 0.1)),
        P(3.0, g + v3(-0.002, 0.004, 0.0), b + v3(-0.01, 0.0, 0.0), GUARD_E, cam=(-0.1, 0, 0)),
        guard(4.0),
    ], loop=True))
    # ---------------- достать (смена предмета) 0.5 с ----------------
    clip(Clip("draw", 0.55, [
        P(0.00, (0.40, -0.52, -0.52), (0.30, -0.45, 0.84), (0.9, -0.2, 0.3), hold=True),
        P(0.10, (0.36, -0.36, -0.56), (0.45, 0.35, 0.82), (0.6, 0.6, -0.4), tr=0.0),
        P(0.26, (0.28, -0.18, -0.62), (-0.20, 0.95, 0.10), (-0.4, 0.0, -0.9), tr=0.5),
        P(0.36, (0.25, -0.20, -0.67), (-0.70, 0.70, -0.16), (-0.5, -0.4, -0.8), tr=0.2, cam=(0.3, 0, -0.6)),
        guard(0.55, hold=True),
    ], events=[(0.0, "draw")]))
    # Удары сняты с видео‑образца (First Person katana, UE4) по кадрам: точки «руки» и «острие» на экране → 3D (scr_key).
    # ---------------- удар 1: справа‑сверху до центра (замирает на 3 кадра), уходит вниз‑влево ----------------
    clip(Clip("slash1", 0.62, [
        scr_key(0.00, (0.75, 0.92), (0.86, -0.05), 0.62, GUARD_E),
        scr_key(0.07, (0.88, 1.12), (1.02, 0.30), 0.60, (-0.6, 0.0, -0.8), tw=4),
        scr_key(0.12, (0.84, 0.98), (1.00, 0.12), 0.58, (-0.5, 0.2, -0.85), tw=8, cam=(-0.6, 0.8, 1.2)),
        scr_key(0.16, (0.68, 0.70), (0.88, 0.05), 0.55, (-0.6, 0.3, -0.75), tw=10, cam=(-0.9, 1.0, 1.6), fov=0.98, hold=True),
        scr_key(0.20, (0.33, 1.05), (0.50, 0.43), 0.60, None, cut=True, tr=1.0, tw=-10, cam=(1.0, -0.6, -2.4), fov=1.03),
        scr_key(0.26, (0.31, 1.07), (0.47, 0.46), 0.60, None, tr=0.4, tw=-11, cam=(1.1, -0.7, -2.6), fov=1.03),
        scr_key(0.33, (0.22, 1.40), (0.28, 0.90), 0.55, None, tw=-16, cam=(1.2, -0.8, -2.2), hold=True),
        scr_key(0.62, (0.75, 0.92), (0.86, -0.05), 0.62, GUARD_E, hold=True),
    ], events=[(0.0, "swing"), (0.18, "hit")]))
    # ---------------- удар 2: слева (клинок влево) вниз‑вправо ----------------
    clip(Clip("slash2", 0.62, [
        scr_key(0.00, (0.22, 1.40), (0.28, 0.90), 0.55, (-0.3, -0.8, 0.5), tw=-16),
        scr_key(0.10, (0.40, 0.85), (-0.05, 0.58), 0.58, (0.0, 0.5, -0.85), tw=-14, cam=(-0.4, -1.0, -1.4), fov=0.98, hold=True),
        scr_key(0.135, (0.52, 0.72), (0.14, 0.30), 0.60, None, cut=True, tr=1.0, tw=-4, cam=(0.2, -0.3, -0.4)),
        scr_key(0.17, (0.72, 0.97), (0.35, 0.33), 0.60, None, cut=True, tr=1.0, tw=8, cam=(1.0, 0.6, 1.8), fov=1.03),
        scr_key(0.20, (0.85, 1.15), (0.62, 0.68), 0.58, None, tr=0.5, tw=12, cam=(1.3, 0.9, 2.4), fov=1.02),
        scr_key(0.27, (0.95, 1.32), (0.82, 0.98), 0.55, None, tw=14, cam=(1.0, 0.8, 2.0), hold=True),
        scr_key(0.62, (0.75, 0.92), (0.86, -0.05), 0.62, GUARD_E, hold=True),
    ], events=[(0.0, "swing"), (0.15, "hit")]))
    # ---------------- удар 3: горизонтально справа налево + восходящий разрез, поза «острие в центр» ----------------
    clip(Clip("slash3", 1.30, [
        scr_key(0.00, (0.95, 1.32), (0.82, 0.98), 0.55, (0.3, -0.8, -0.5), tw=14),
        scr_key(0.10, (0.78, 0.95), (1.15, 0.30), 0.58, (-0.6, 0.0, -0.8), tw=16, cam=(-0.3, 1.2, 1.4), fov=0.97, hold=True),
        scr_key(0.14, (0.48, 0.80), (1.00, 0.33), 0.62, None, cut=True, tr=1.0, tw=8, cam=(0.2, 0.6, 0.5)),
        scr_key(0.17, (0.12, 0.80), (0.85, 0.38), 0.62, None, cut=True, tr=1.0, tw=-4, cam=(0.6, -0.6, -1.4), fov=1.04),
        scr_key(0.20, (0.02, 0.88), (0.68, 0.42), 0.62, None, cut=True, tr=1.0, tw=-10, cam=(0.8, -1.0, -2.2), fov=1.04),
        scr_key(0.235, (-0.10, 0.80), (0.40, 0.50), 0.60, None, tr=0.6, tw=-14, cam=(0.8, -1.2, -2.6)),
        scr_key(0.29, (-0.28, 0.88), (0.08, 0.72), 0.58, None, tw=-18, cam=(0.7, -1.2, -2.4), hold=True),
        scr_key(0.47, (0.12, 0.92), (0.42, 0.66), 0.60, (0.2, 0.9, -0.3), tw=-10, cam=(0.6, -0.6, -1.0)),
        scr_key(0.52, (0.45, 0.66), (0.72, 0.40), 0.62, None, cut=True, tr=1.0, tw=2, cam=(-0.6, 0.4, 1.0), fov=1.04),
        scr_key(0.545, (0.60, 0.55), (0.66, 0.46), 0.62, None, cut=True, tr=1.0, tw=8, cam=(-1.0, 0.8, 2.0), fov=1.05),
        scr_key(0.57, (0.72, 0.45), (0.40, 0.80), 0.60, None, tr=0.5, tw=12, cam=(-1.0, 1.0, 2.4), fov=1.04),
        scr_key(0.60, (0.85, 0.27), (0.50, 0.48), 0.58, None, tw=14, cam=(-0.8, 1.0, 2.2), fov=1.02),
        scr_key(1.00, (0.85, 0.28), (0.50, 0.49), 0.58, None, tw=14, cam=(-0.6, 0.8, 1.8), hold=True),
        scr_key(1.30, (0.75, 0.92), (0.86, -0.05), 0.62, GUARD_E, hold=True),
    ], events=[(0.0, "swing"), (0.17, "hit"), (0.40, "swing_heavy"), (0.53, "hit")]))
    # ---------------- в ножнах на поясе (когда не бьёт): правая рука на рукояти, левая — на устье ----------------
    sp, sb, se = SAYA_POSE
    sh = lambda t, d, **kw: K(t, sheath=d, **kw)
    SH = dict(tw=-4, cr=0.0, L=L_DOWN, lw=1.0)
    shd = Clip("sheathed", 4.0, [
        sh(0.0, 0.0, **SH),
        sh(1.3, 0.0, **dict(SH, cr=-0.006)),
        sh(2.6, 0.0, **dict(SH, cr=0.003)),
        sh(4.0, 0.0, **SH),
    ], loop=True, saya=SAYA_IDLE)
    for k in shd.keys:
        k.sv = 1.0
    clip(shd)
    # ---------------- вложить в ножны (после боя): клинок к левому бедру, в устье, до щелчка ----------------
    sht = Clip("sheathe", 0.80, [
        guard(0.00),
        P(0.16, (0.26, -0.34, -0.60), (0.30, 0.10, 0.95), (0.0, 1.0, -0.1), tw=4, cam=(0.6, 0.3, 0.4)),
        sh(0.32, 9.0, cam=(0.6, 0.3, 0.4), **SH),
        sh(0.50, 0.0, cam=(0.4, 0.2, 0.3), **dict(SH, hold=True)),
        sh(0.80, 0.0, **dict(SH, hold=True)),
    ], saya=SAYA_IDLE, events=[(0.46, "sheathe")])
    for k, sv in zip(sht.keys, (0, 0.6, 1, 1, 1)):
        k.sv = sv
    clip(sht)
    # ---------------- зарядка из ножен: присесть, рука крепче на рукояти, клинок светится ----------------
    chs = Clip("charge", 1.5, [
        sh(0.00, 0.0, tw=-10, cr=0.04, cam=(1.5, -0.6, -0.8), L=L_saya(), lw=1.0),
        sh(0.40, 0.0, tw=-16, cr=0.10, cam=(3.0, -1.0, -1.4), L=L_saya(), lw=1.0, hold=True),
        sh(1.10, 0.0, tw=-17, cr=0.11, cam=(3.6, -1.0, -1.5), L=L_saya(), lw=1.0, fov=0.96, gl=0.4),
        sh(1.38, -0.15, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=0.8),
        sh(1.50, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0, hold=True),
    ], saya=SAYA_POSE, events=[(0.0, "charge"), (1.5, "ready")])
    for k in chs.keys:
        k.sv = 1.0
    clip(chs)
    # ---------------- зарядка, если катана была обнажена: сначала в ножны ----------------
    ch = Clip("charge_drawn", 1.5, [
        guard(0.00),
        P(0.16, (0.04, -0.30, -0.56), (-0.66, 0.30, 0.69), (0.2, 0.95, 0.1), tw=-10, cr=0.04, cam=(1.0, -0.6, -1.0)),
        sh(0.30, 9.0, tw=-14, cr=0.08, cam=(1.6, -0.8, -1.2), L=L_saya(), lw=1.0),
        sh(0.46, 0.0, tw=-16, cr=0.10, cam=(3.0, -1.0, -1.4), L=L_saya(), lw=1.0, hold=True),
        sh(1.10, 0.0, tw=-17, cr=0.11, cam=(3.6, -1.0, -1.5), L=L_saya(), lw=1.0, fov=0.96, gl=0.4),
        sh(1.38, -0.15, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=0.8),
        sh(1.50, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0, hold=True),
    ], saya=SAYA_POSE, events=[(0.0, "charge"), (0.42, "sheathe"), (1.5, "ready")])
    for k, sv in zip(ch.keys, (0, 0.6, 1, 1, 1, 1, 1)):
        k.sv = sv
    clip(ch)
    chd = Clip("charged", 0.6, [
        sh(0.00, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0),
        sh(0.15, 0.7, tw=-18, cr=0.125, cam=(4.05, -1.0, -1.65), L=L_saya(), lw=1.0, fov=0.94, gl=0.75),
        sh(0.30, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.05, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0),
        sh(0.45, 0.65, tw=-18, cr=0.118, cam=(4.0, -1.0, -1.58), L=L_saya(), lw=1.0, fov=0.94, gl=0.8),
        sh(0.60, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0),
    ], loop=True, saya=SAYA_POSE)
    for k in chd.keys:
        k.sv = 1.0
    clip(chd)
    # ---------------- особая атака: выхват‑разрез на рывке (нукицукэ), пауза, стряхнуть кровь (тибури) ----------------
    sp_ = Clip("special", 1.25, [
        sh(0.00, 0.6, tw=-18, cr=0.12, cam=(4.0, -1.0, -1.6), L=L_saya(), lw=1.0, fov=0.94, gl=1.0),
        sh(0.035, 8.0, tw=-14, cr=0.10, cam=(1.5, -1.2, -3.0), L=L_saya(), lw=1.0, fov=1.10, gl=1.0, tr=0.6),
        P(0.08, (-0.10, -0.24, -0.66), (-0.55, 0.08, -0.83), (0.75, 0.1, -0.65), tw=-6, cr=0.06, cut=True, tr=1.0,
          cam=(0.6, -0.6, -4.0), fov=1.22, gl=0.8, L=L_saya(0.4), lw=1.0),
        P(0.12, (0.10, -0.22, -0.80), (0.30, 0.12, -0.95), None, tw=8, cr=0.03, cut=True, tr=1.0,
          cam=(0.2, 0.6, 1.0), fov=1.25, gl=0.6, L=L_saya(0.4), lw=1.0),
        P(0.17, (0.24, -0.16, -0.80), (0.75, 0.40, -0.53), None, tw=20, cr=0.02, cut=True, tr=1.0,
          cam=(-0.2, 1.8, 5.0), fov=1.20, gl=0.4, L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0),
        P(0.24, (0.27, -0.13, -0.80), (0.60, 0.62, -0.50), (0.75, -0.35, 0.55), tw=24, cr=0.02, tr=0.3,
          cam=(-0.4, 2.0, 5.5), fov=1.10, L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0),
        P(0.52, (0.275, -0.13, -0.79), (0.58, 0.65, -0.49), (0.75, -0.35, 0.55), tw=25, cr=0.02,
          cam=(-0.3, 1.9, 5.2), fov=1.04, L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0, hold=True),
        # тибури: поднять и резко стряхнуть вниз‑вправо
        P(0.64, (0.22, -0.02, -0.72), (-0.15, 0.97, -0.18), (0.9, 0.2, 0.3), tw=12, cam=(-0.8, 0.8, 2.0), L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0, hold=True),
        P(0.70, (0.17, -0.12, -0.76), (0.92, 0.04, -0.38), None, tw=16, cut=True, tr=0.8, cam=(2.0, 1.0, 2.5), L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0),
        P(0.76, (0.17, -0.14, -0.75), (0.92, -0.04, -0.38), (0.2, -0.95, 0.2), tw=16, tr=0.0, cam=(2.4, 1.0, 2.6), L=(v3(-0.30, -0.62, -0.30), v3(-0.3, 0.2, -0.9)), lw=1.0, hold=True),
        guard(1.25, hold=True),
    ], saya=SAYA_POSE, events=[(0.0, "special"), (0.68, "flick")])
    for k, sv in zip(sp_.keys, (1, 1, 1, 0.9, 0.0, 0, 0, 0, 0, 0, 0)):
        k.sv = sv
    clip(sp_)


build_clips()


# =====================================================================
# Запекание 1‑го лица → assets/basedefense/fpanim/katana.json (читает Java client/weapon/fp/FpAnim)
# Кадр: G(3) q(4) Lp(3) La(3) AR(3) AL(3) cam(3) fov tr gl sv sayaP(3) sayaQ(4) — 33 числа
# =====================================================================
CHANNELS = 33


def bake_frame(c, t):
    r = c.pose(t)
    out = list(r["G"]) + list(frame_q(r["R"])) + list(r["Lp"]) + list(nrm(r["La"])) + list(r["AR"]) + list(r["AL"])
    out += list(r["cam"]) + [r["fov"], r["tr"], r["gl"]]
    sp, sv = saya_pose(c, t)
    if sp is None:
        out += [0.0] + [0.0] * 3 + [0.0, 0.0, 0.0, 1.0]
    else:
        out += [sv] + list(sp[0]) + list(frame_q(sp[1]))
    assert len(out) == CHANNELS
    return [round(float(x), 4) for x in out]


def export_fp():
    data = {"fps": FPS, "scale": S1, "armWidth": ARM_W, "leftGrip": LEFT_GRIP,
            "trail": {"base": [float(blade_center(3.5)[0]), BLADE_Y0 + 3.5],
                      "tip": [float(blade_center(BLADE_LEN - 0.2)[0]), TIP_Y - 0.2], "length": TRAIL_LEN},
            "flare": [0.0, TSUBA_Y1 + 0.3, 0.0], "sayaHide": [float(x) for x in SAYA_HIDE], "clips": {}}
    for name, c in CLIPS.items():
        n = int(round(c.length * FPS))
        frames = [bake_frame(c, min(c.length, i / FPS)) for i in range(n + 1)]
        data["clips"][name] = {"length": c.length, "loop": c.loop,
                               "events": [[round(t, 3), e] for t, e in c.events], "frames": frames}
    path = os.path.join(ASSETS, "fpanim", "katana.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    print("→", os.path.relpath(path), "%.0f КБ" % (os.path.getsize(path) / 1024))


def export_mesh():
    data = {"texture": [TEX_W, TEX_H], "parts": {"katana": mesh_json(KATANA), "saya": mesh_json(SAYA)}}
    path = os.path.join(ASSETS, "mesh", "katana.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))
    print("→", os.path.relpath(path), "%.0f КБ" % (os.path.getsize(path) / 1024))


def check_fp():
    """Кромка ведёт удар (угол между кромкой и движением острия < 35°) и края рук за экраном."""
    rend = fv.Renderer(640, 360)
    bad = 0
    for name, c in CLIPS.items():
        n = int(c.length * FPS)
        for i in range(1, n):
            t = i / FPS
            r = c.pose(t)
            if r["cut"] > 0.9:
                p1, p2 = c.pose(t - 1 / 240), c.pose(t + 1 / 240)
                v = (p2["G"] + p2["B"] * TIPD) - (p1["G"] + p1["B"] * TIPD)
                v = v - r["R"][:, 1] * np.dot(v, r["R"][:, 1])
                if np.linalg.norm(v) * 120 > 1.0:
                    cosang = np.dot(nrm(v), r["R"][:, 0])
                    if cosang < math.cos(math.radians(35)):
                        print("  %s %.3f: кромка не по ходу удара (%.0f°)" % (name, t, math.degrees(math.acos(min(1, cosang)))))
                        bad += 1
            for nm, a, h, ax in (("R", r["AR"], r["G"], r["R"][:, 1]), ("L", r["AL"], r["Lp"], r["La"])):
                R = fv.arm_frame(h, ax, a)
                k = fv.arm_stretch(h, R[:, 1], S1)
                top = h - R[:, 1] * ((fv.ARM_GRIP_Y + fv.ARM_TOP) * k * S1 / 16)
                if fv.visible(rend, top, -4):
                    print("  %s %.3f: верх руки %s на экране" % (name, t, nm))
                    bad += 1
    print("проверка 1‑го лица:", "ок" if bad == 0 else "%d замечаний" % bad)


# =====================================================================
# 3‑е лицо (px модели игрока от шеи, y вниз, вперёд −z, +x — левая сторона игрока). Как в Java KatanaClient.applyModel:
# ключ {t (тики), хват G, клинок B, нормаль N, левая кисть L, наклон, поворот, нога L, нога R, в ножнах 0..1}
# =====================================================================
import bt_sword_anim as A  # noqa: E402

SCALE_3P = 0.85
TP_IDLE = (0.0, (0.5, 9.4, -4.2), (0.45, 0.25, 0.86), (-0.86, 0.0, 0.45), (2.0, 10.5, -2.6), 0.0, 0.0, 0.0, 0.0, 1.0)


class TK:
    """Ключ 3‑го лица: E — кромка (нормаль считается сама), L=None — левая рука на рукояти, cut — кромка по ходу."""

    def __init__(self, t, G, B, E, L=None, lean=0.0, tw=0.0, ll=0.0, lr=0.0, sh=0.0, cut=False):
        self.t, self.G, self.B, self.E, self.L = t, v3(G), nrm(v3(B)), v3(E), L
        self.lean, self.tw, self.ll, self.lr, self.sh, self.cut = lean, tw, ll, lr, sh, cut


def tk_idle(t, **kw):
    _, g, b, n, l, lean, tw, ll, lr, sh = TP_IDLE
    e = np.cross(v3(b), v3(n))
    d = dict(L=l, lean=lean, tw=tw, ll=ll, lr=lr, sh=1.0)
    d.update(kw)
    return TK(t, g, b, e, **d)


TP_GUARD = dict(G=(-1.5, 7.5, -7.0), B=(0.0, -0.55, -0.83), E=(0.0, 0.83, -0.55))


def tk_guard(t, **kw):
    d = dict(lean=0.04, ll=-0.15, lr=0.12)
    d.update(kw)
    return TK(t, TP_GUARD["G"], TP_GUARD["B"], TP_GUARD["E"], **d)


TP_CHARGE = tk_idle(0, lean=0.30, tw=-0.25, ll=-0.45, lr=0.50)


def tp_clips():
    s1 = [tk_guard(0),
          TK(1.5, (-6, -3, -1), (0.25, -0.55, 0.80), (-0.3, -0.75, -0.6), lean=-0.05, tw=0.35, ll=-0.2, lr=0.2),
          TK(3.0, (-1, 4, -9), (0.55, -0.45, -0.70), (0, 0, 0), lean=0.10, tw=0.05, ll=-0.3, lr=0.25, cut=True),
          TK(4.5, (3, 8, -6), (0.75, 0.45, -0.48), (0, 0, 0), lean=0.18, tw=-0.30, ll=-0.35, lr=0.3, cut=True),
          TK(6.0, (4, 9.5, -4), (0.60, 0.70, -0.35), (0.3, -0.5, 0.8), lean=0.15, tw=-0.35, ll=-0.35, lr=0.3),
          tk_guard(12)]
    s2 = [TK(0, (4, 9.5, -4), (0.60, 0.70, -0.35), (0.3, -0.5, 0.8), lean=0.15, tw=-0.35, ll=-0.35, lr=0.3),
          TK(1.5, (4, 9, -3), (0.60, 0.55, 0.55), (0.2, 0.6, -0.75), lean=0.12, tw=-0.40, ll=-0.3, lr=0.3),
          TK(3.0, (-1, 6, -9), (0.10, -0.45, -0.89), (0, 0, 0), lean=0.08, tw=0.0, ll=-0.3, lr=0.3, cut=True),
          TK(4.5, (-5, 1, -7), (-0.55, -0.75, -0.35), (0, 0, 0), lean=0.0, tw=0.30, ll=-0.25, lr=0.3, cut=True),
          TK(6.0, (-6, -1, -5), (-0.45, -0.85, -0.25), (-0.6, 0.5, -0.3), lean=-0.02, tw=0.35, ll=-0.25, lr=0.3),
          tk_guard(12)]
    s3 = [TK(0, (-6, -1, -5), (-0.45, -0.85, -0.25), (-0.6, 0.5, -0.3), lean=-0.02, tw=0.35, ll=-0.25, lr=0.3),
          TK(3.0, (-0.5, -6.5, -3), (0.0, -0.45, 0.89), (0, -0.89, -0.45), lean=-0.15, tw=0.05, ll=-0.1, lr=0.1),
          TK(4.5, (-0.5, -1, -9), (0.0, -0.75, -0.66), (0, 0, 0), lean=0.05, ll=-0.3, lr=0.3, cut=True),
          TK(6.0, (-0.5, 7, -9), (0.0, 0.45, -0.89), (0, 0, 0), lean=0.30, ll=-0.55, lr=0.42, cut=True),
          TK(8.5, (-0.5, 8, -8), (0.0, 0.60, -0.80), (0, 0.8, 0.6), lean=0.32, ll=-0.55, lr=0.42),
          tk_guard(16)]
    LH = (2.0, 10.5, -2.6)
    sp = [tk_idle(0, lean=0.30, tw=-0.25, ll=-0.45, lr=0.50),
          TK(1.0, (-1, 7, -9), (0.55, 0.05, -0.83), (0, 0, 0), L=LH, lean=0.22, tw=0.0, ll=-0.5, lr=0.5, cut=True),
          TK(2.0, (-5, 5, -8), (-0.45, 0.05, -0.89), (0, 0, 0), L=LH, lean=0.20, tw=0.30, ll=-0.5, lr=0.5, cut=True),
          TK(3.0, (-8, 4, -4), (-0.95, 0.05, 0.20), (-0.2, 0, -0.95), L=LH, lean=0.18, tw=0.45, ll=-0.5, lr=0.5),
          TK(10.0, (-8, 4, -4), (-0.94, 0.08, 0.22), (-0.2, 0, -0.95), L=LH, lean=0.18, tw=0.45, ll=-0.5, lr=0.5),
          TK(12.5, (-6, -1, -5), (-0.30, -0.90, -0.30), (-0.9, 0.3, 0.2), L=LH, lean=0.08, tw=0.25, ll=-0.3, lr=0.3),
          TK(14.0, (-7, 6, -5), (-0.90, 0.35, -0.25), (0, 0, 0), L=LH, lean=0.10, tw=0.30, ll=-0.3, lr=0.3, cut=True),
          TK(17.0, (-6, 7, -5), (-0.85, 0.45, -0.25), (0.3, 0.8, 0.5), L=LH, lean=0.08, tw=0.25, ll=-0.2, lr=0.2),
          tk_idle(23, lean=0.05),
          tk_idle(25)]
    return [s1, s2, s3, sp]


def tp_bake(keys):
    """Ключи → кадры каждый тик: Катмулл–Ром по G, острию, L; кромка — по ходу (cut) или из ключей."""
    ts = [k.t for k in keys]
    holds = [False] * len(keys)
    tip = lambda k: k.G + k.B * 14.0
    out = []
    end = int(round(ts[-1]))
    for f in range(end + 1):
        t = float(f)
        G = hermite(ts, [k.G for k in keys], holds, t)
        T = hermite(ts, [tip(k) for k in keys], holds, t)
        B = nrm(T - G)
        i = max(k for k in range(len(ts)) if ts[k] <= t) if t < ts[-1] else len(ts) - 2
        j = i + 1
        a = min(1.0, max(0.0, (t - ts[i]) / (ts[j] - ts[i])))
        ki, kj = keys[i], keys[j]
        if ki.cut:
            G1 = hermite(ts, [k.G for k in keys], holds, t - 0.25)
            T1 = hermite(ts, [tip(k) for k in keys], holds, t - 0.25)
            G2 = hermite(ts, [k.G for k in keys], holds, t + 0.25)
            T2 = hermite(ts, [tip(k) for k in keys], holds, t + 0.25)
            v = (T2 - T1) - (G2 - G1) * 0.0
            E = v
        else:
            Ei = ki.E if np.linalg.norm(ki.E) > 0 else None
            Ej = kj.E if np.linalg.norm(kj.E) > 0 else None
            E = slerp(Ei if Ei is not None else Ej, Ej if Ej is not None else Ei, a * a * (3 - 2 * a))
        E = nrm(E - B * np.dot(E, B))
        N = np.cross(E, B)
        lin = lambda f_: f_(ki) + (f_(kj) - f_(ki)) * a
        if ki.L is None and kj.L is None:
            L = None
        else:
            li = v3(ki.L) if ki.L is not None else None
            lj = v3(kj.L) if kj.L is not None else None
            L = li if lj is None else (lj if li is None else li + (lj - li) * a)
        if L is None:                                       # левая на рукояти: ниже правой по оси клинка
            L = G - B * (-LEFT_GRIP * SCALE_3P)
        elif ki.L is None or kj.L is None:
            grip = G - B * (-LEFT_GRIP * SCALE_3P)
            w = a if ki.L is None else 1 - a
            L = grip * (1 - w) + L * w
        row = [t] + list(G) + list(B) + list(N) + list(L) + [lin(lambda k: k.lean), lin(lambda k: k.tw),
                                                             lin(lambda k: k.ll), lin(lambda k: k.lr), lin(lambda k: k.sh)]
        out.append([round(float(x), 4) for x in row])
    return out


def write_java():
    def arr(rows, ind="            "):
        return ",\n".join(ind + "{" + ", ".join("%.4ff" % x for x in r) + "}" for r in rows)
    clips = [tp_bake(k) for k in tp_clips()]
    idle = [0.0] + list(TP_IDLE[1]) + list(TP_IDLE[2]) + list(TP_IDLE[3]) + list(TP_IDLE[4]) + list(TP_IDLE[5:10])
    ch = TP_CHARGE
    charge = [0.0] + list(ch.G) + list(ch.B) + list(np.cross(ch.E, ch.B)) + list(ch.L) + [ch.lean, ch.tw, ch.ll, ch.lr, ch.sh]
    src = ("package com.lesha_top1972.basedefense.client.weapon;\n\n"
           "/**\n * Позы катаны от 3‑го лица. СОЗДАНО tools/katana_gen.py — правь скрипт, а не этот файл.\n"
           " * Кадр: {t (тики), хват x,y,z, клинок x,y,z, нормаль x,y,z, левая кисть x,y,z, наклон, поворот, нога L, нога R,\n"
           " * в ножнах 0..1}. px модели игрока от шеи, y вниз, вперёд −z. Кадры — каждый тик.\n */\n"
           "final class KatanaKeys {\n\n"
           "    static final float SCALE_3P = %.3ff;\n\n" % SCALE_3P)
    src += "    static final float[][] TP_IDLE = {\n" + arr([idle]) + "\n    };\n\n"
    src += "    static final float[][] TP_CHARGE = {\n" + arr([charge]) + "\n    };\n\n"
    src += "    /** 0–2 — удары серии, 3 — особая атака (выхват на рывке, тибури, в ножны). */\n"
    src += "    static final float[][][] TP_SWINGS = {\n" + ",\n".join(
        "        {\n" + arr(c) + "\n        }" for c in clips) + "\n    };\n\n"
    src += "    private KatanaKeys() {\n    }\n}\n"
    path = os.path.join(JAVA_DIR, "KatanaKeys.java")
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("→", os.path.relpath(path))
    return clips


# --- превью 3‑го лица (тем же рендером) ---
def tp_pose(v):
    """Как Java KatanaClient.applyModel: v = кадр без t."""
    g, b, n, l = v[0:3], v[3:6], v[6:9], v[9:12]
    lean, twist, leg_l, leg_r, sh = v[12:17]
    R = A.body_R(lean, twist)
    neck = A.add([0, 12, 0], A.mv(R, [0, -12, 0]))
    sh_r = A.add(neck, A.mv(R, [-5, 2, 0]))
    sh_l = A.add(neck, A.mv(R, [5, 2, 0]))
    sg, ssw = saya_tp()
    gk = A.add(neck, A.mv(R, g))
    sw = frame_np(A.mv(R, b), A.mv(R, n))
    if sh > 0:                                    # в ножнах: катана — по ножнам (у бедра, без наклона корпуса)
        gk = list(v3(gk) * (1 - sh) + v3(sg) * sh)
        sw = frame_np(list(nrm(sw[:, 1] * (1 - sh) + ssw[:, 1] * sh)), list(nrm(sw[:, 2] * (1 - sh) + ssw[:, 2] * sh)))
    l = A.add(neck, A.mv(R, l))
    xr = A.mv(R, [1, 0, 0])

    def aim(shp, target, hx):
        a = target
        for _ in range(4):
            Rm = A.basis(A.sub(a, shp), xr)
            hand = A.add(shp, A.mv(Rm, [hx, A.ARM_REACH, 0]))
            a = A.add(a, A.sub(target, hand))
        Rm = A.basis(A.sub(a, shp), xr)
        return Rm, A.add(shp, A.mv(Rm, [hx, A.ARM_REACH, 0]))
    Rr, hand_r = aim(sh_r, list(gk), -1.0)
    Rl, hand_l = aim(sh_l, l, 1.0)
    return dict(R=R, neck=neck, sh_r=sh_r, sh_l=sh_l, Rr=Rr, Rl=Rl, grip=v3(hand_r), sword=sw, leg_l=leg_l, leg_r=leg_r,
                saya=(v3(sg), ssw))


def frame_np(b, n):
    b = nrm(v3(b))
    z = nrm(v3(n) - b * np.dot(v3(n), b))
    x = np.cross(b, z)
    return np.stack([x, b, z], 1)


def saya_tp():
    """Ножны у левого бедра: поза TP_IDLE без наклона корпуса (как в Java)."""
    _, g, b, n, l, *_ = TP_IDLE
    neck0 = [0, 0, 0]
    sh_r = A.add(neck0, [-5, 2, 0])
    a = list(g)
    for _ in range(4):
        Rm = A.basis(A.sub(a, sh_r), [1, 0, 0])
        hand = A.add(sh_r, A.mv(Rm, [-1.0, A.ARM_REACH, 0]))
        a = A.add(a, A.sub(list(g), hand))
    Rm = A.basis(A.sub(a, sh_r), [1, 0, 0])
    hand = A.add(sh_r, A.mv(Rm, [-1.0, A.ARM_REACH, 0]))
    return v3(hand), frame_np(b, n)


def render_tp(frames, tex, skin, views=((0.6, "спереди‑сбоку"), (math.pi / 2, "сбоку"))):
    rend = fv.Renderer(260, 300)
    rend.f = 300 / 2 / math.tan(math.radians(14))
    out = []
    for yaw, _ in views:
        Ry = fv.rot_axis((0, 1, 0), yaw)

        def W(p):
            p = v3(p)
            q = v3(p[0], -p[1], -p[2]) / 16.0         # модель (y вниз, вперёд −z) → мир (y вверх, вперёд +z)
            q = Ry @ q
            return q + v3(0, -0.15, -5.2)
        for v in frames:
            p = tp_pose(v[1:])
            bs = []

            def part(cubes, origin, Rm, skin_=skin):
                b = fv.Batch(skin_)
                tw_, th_ = skin_.size
                for cb in cubes:
                    for verts, u1, v1, u2, v2 in fv.sp.cuboid_quads(cb):
                        V = [W(v3(origin) + np.stack([v3(c) for c in Rm], 1) @ v3(vv)) for vv in verts]
                        b.add(V, [(u2 / tw_, v1 / th_), (u1 / tw_, v1 / th_), (u1 / tw_, v2 / th_), (u2 / tw_, v2 / th_)])
                bs.append(b)
            Rb = p["R"]
            part([(0, 0, -4, -8, -4, 8, 8, 8, 0)], p["neck"], Rb)
            part([(16, 16, -4, 0, -2, 8, 12, 4, 0)], p["neck"], Rb)
            for (u, vv, px_, ang) in ((0, 16, -1.9, p["leg_r"]), (16, 48, 1.9, p["leg_l"])):
                c, s_ = math.cos(ang), math.sin(ang)
                Rl_ = [[1, 0, 0], [0, c, s_], [0, -s_, c]]
                part([(u, vv, -2, 0, -2, 4, 12, 4, 0)], [px_, 12, 0], Rl_)
            part([(40, 16, -3, -2, -2, 4, 12, 4, 0)], p["sh_r"], p["Rr"])
            part([(32, 48, -1, -2, -2, 4, 12, 4, 0)], p["sh_l"], p["Rl"])
            sc = SCALE_3P
            gp, sw = p["grip"], p["sword"]
            bs.append(mesh_batch(KATANA, tex, lambda q: W(gp + sw @ (v3(q) * sc)), lambda n: Ry @ (v3(n[0], -n[1], -n[2]) * 0 + v3((sw @ n)[0], -(sw @ n)[1], -(sw @ n)[2]))))
            sgp, ssw = p["saya"]
            bs.append(mesh_batch(SAYA, tex, lambda q: W(sgp + ssw @ (v3(q) * sc)), lambda n: Ry @ v3((ssw @ n)[0], -(ssw @ n)[1], -(ssw @ n)[2])))
            out.append(rend.render(bs, bg=False))
    return out


def gif_clip(name, tex, skin, path, size=(480, 270)):
    c = CLIPS[name]
    n = int(c.length * 30)
    times = [i / 30 for i in range(n + 1)]
    frames, _ = render_fp(c, times, tex, skin, size=size)
    frames += [frames[-1]] * 6
    fv.gif(frames, path, fps=30)


def main():
    tex = paint()
    skin = fv.make_skin()
    tex.save(os.path.join(ASSETS, "textures", "entity", "weapon", "katana.png"))
    make_icon().save(os.path.join(ASSETS, "textures", "item", "katana.png"))
    export_mesh()
    export_fp()
    write_java()
    if "--check" in sys.argv:
        check_fp()
    if "--fp" in sys.argv:
        name = sys.argv[sys.argv.index("--fp") + 1]
        c = CLIPS[name]
        times = list(np.linspace(0, c.length, 12))
        frames, warn = render_fp(c, times, tex, skin)
        fv.sheet(frames, 4, label=["%.2f" % t for t in times]).save(os.path.join(TOOLS, "katana_fp.png"))
        gif_clip(name, tex, skin, os.path.join(TOOLS, "katana_%s.gif" % name))
        print("\n".join(warn) or "превью → tools/katana_fp.png, tools/katana_%s.gif" % name)
    if "--tp" in sys.argv:
        clips = [tp_bake(k) for k in tp_clips()]
        rows = []
        for c in clips:
            idx = list(range(0, len(c), max(1, len(c) // 8)))[:8]
            rows += render_tp([c[i] for i in idx], tex, skin, views=((0.6, ""),))
        fv.sheet(rows, 8, scale=1.0).save(os.path.join(TOOLS, "katana_tp.png"))
        print("превью → tools/katana_tp.png")


if __name__ == "__main__":
    main()
