"""
Руки с локтем для 1‑го лица: плечо (за краем экрана) → локоть → кисть. Клинок ЖЁСТКО продолжает предплечье
(запястье почти зафиксировано): направление клинка задают положение кисти (большая дуга рук) + небольшой
излом запястья (cock) и поворот кулака (roll). Так удар делают руки, а не вращение катаны в кулаке.
"""
import math

import numpy as np

import fpview as fv
from fpview import nrm, v3

UPPER = 6.0        # px плеча
FORE = 6.0         # px предплечья (до середины кулака ~4.5)
FIST = 4.5         # от локтя до середины кулака


def ik(S, H, pole, lu, lf):
    """Двухзвенная рука: плечо S, кисть H, локоть — в сторону pole. → локоть E (кисть подтягивается, если далеко)."""
    d = H - S
    L = np.linalg.norm(d)
    L = min(L, lu + lf - 1e-4)
    dn = nrm(d)
    a = (lu * lu - lf * lf + L * L) / (2 * L)
    h = math.sqrt(max(lu * lu - a * a, 0.0))
    p = nrm(pole - dn * np.dot(pole, dn))
    return S + dn * a + p * h


def forearm_frame(E, H, roll_deg, ref):
    """Y — от локтя к кисти; Z (большой палец) — ref, повёрнутый на roll вокруг Y."""
    Y = nrm(H - E)
    z0 = nrm(ref - Y * np.dot(ref, Y))
    Z = fv.rot_axis(Y, math.radians(roll_deg)) @ z0
    X = np.cross(Y, Z)
    return np.stack([X, Y, Z], 1)


def blade_from_forearm(F, cock_deg):
    """Клинок — из кулака в сторону большого пальца (Z), наклонён к продолжению предплечья (Y) на cock градусов.
    Кромка — от предплечья (то, чем рубит рука)."""
    c = math.radians(cock_deg)
    B = nrm(F[:, 2] * math.cos(c) + F[:, 1] * math.sin(c))
    Ed = F[:, 1] - B * np.dot(F[:, 1], B)
    return B, nrm(Ed)


def seg_batch(skin, origin, R, size, uv_side_rows, right, cap_top, cap_bottom, scale):
    """Кусок руки игрока (плечо или предплечье с кулаком): коробка w×h×4 px со своей развёрткой скина.
    origin — верх куска (по оси Y вниз), R — столбцы X,Y,Z."""
    w, h, d = size
    u0, v0 = (40, 16) if right else (32, 48)
    tw, th = skin.size
    r0, r1 = uv_side_rows
    b = fv.Batch(skin)
    x0, x1 = (-w / 2, w / 2)
    z0, z1 = (-d / 2, d / 2)
    P = lambda x, y, z: origin + R @ (v3(x, y, z) * scale / 16)
    # боковые грани: развёртка руки — 4 полосы по 4 px: (x−) d, (z−) w, (x+) d, (z+) w
    sides = [
        ((x0, z1), (x0, z0), u0 + 0),        # WEST (x−)
        ((x0, z0), (x1, z0), u0 + 4),        # NORTH (−z, перед)
        ((x1, z0), (x1, z1), u0 + 8),        # EAST
        ((x1, z1), (x0, z1), u0 + 12),       # SOUTH
    ]
    vt, vb = (v0 + 4 + r0) / th, (v0 + 4 + r1) / th
    for (a, c, uu) in sides:
        ua, ub = uu / tw, (uu + 4) / tw
        b.add([P(a[0], 0, a[1]), P(c[0], 0, c[1]), P(c[0], h, c[1]), P(a[0], h, a[1])],
              [(ua, vt), (ub, vt), (ub, vb), (ua, vb)])
    if cap_bottom:                                     # низ кулака
        ua, ub, va, vb2 = (u0 + 8) / tw, (u0 + 12) / tw, v0 / th, (v0 + 4) / th
        b.add([P(x0, h, z0), P(x1, h, z0), P(x1, h, z1), P(x0, h, z1)], [(ua, va), (ub, va), (ub, vb2), (ua, vb2)])
    if cap_top:
        ua, ub, va, vb2 = (u0 + 4) / tw, (u0 + 8) / tw, v0 / th, (v0 + 4) / th
        b.add([P(x0, 0, z1), P(x1, 0, z1), P(x1, 0, z0), P(x0, 0, z0)], [(ua, va), (ub, va), (ub, vb2), (ua, vb2)])
    return b


def arm(skin, S, E, H, F, right, scale, width=0.75):
    """Плечо S→E и предплечье E→H(кулак). F — рамка предплечья (Z — большой палец)."""
    out = []
    # плечо
    Yu = nrm(E - S)
    Zu = nrm(F[:, 2] - Yu * np.dot(F[:, 2], Yu))
    Ru = np.stack([np.cross(Yu, Zu), Yu, Zu], 1)
    lu = np.linalg.norm(E - S) * 16 / scale
    out.append(seg_batch(skin, S, Ru, (4 * width, lu + 1.0, 4 * width), (0, 6), right, True, False, scale))
    # предплечье до конца кулака (кулак — нижние ~3 px)
    lf = np.linalg.norm(H - E) * 16 / scale + 1.6
    out.append(seg_batch(skin, E, F, (4 * width, lf, 4 * width), (6, 12), right, False, True, scale))
    return out
