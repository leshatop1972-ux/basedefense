"""
Скелет игрока с суставами (как в Java client/rig): ванильные части (голова, корпус, руки, ноги) + суставы:
корпус → живот (поясница), рука → предплечье (локоть), нога → голень (колено). Скин игрока делится по высоте
(верхние 6 px и нижние 6 px каждой части), второй слой скина и броня — так же.

Пространство модели — как у ModelPart Minecraft: px, y вниз, вперёд −z, +x — ЛЕВАЯ сторона игрока, ступни на y = 24.
Поворот части — как ModelPart.rotate: R = Rz(roll)·Ry(yaw)·Rx(pitch), точка = pivot + R·p.

Здесь же — авторская поза («таз, поясница, катана в руках, ступни») → углы частей (обратная кинематика рук к
рукояти и ног к земле) и превью: 3‑е лицо и глазами игрока.
"""
import math

import numpy as np

import fpview as fv
from fpview import nrm, v3

# --- развёртка скина 64×64 (u, v) ---
UV = {"head": (0, 0), "hat": (32, 0), "body": (16, 16), "jacket": (16, 32),
      "rarm": (40, 16), "rsleeve": (40, 32), "larm": (32, 48), "lsleeve": (48, 48),
      "rleg": (0, 16), "rpants": (0, 32), "lleg": (16, 48), "lpants": (0, 48)}

UPPER_ARM = 4.5          # от оси плеча до локтя (px)
FIST = 5.0               # от локтя до середины кулака (кулак — в конце руки, как у ванильной)
THIGH = 6.0              # от тазобедренного сустава до колена
SHIN = 6.0               # от колена до подошвы
OVERLAP = 0.6            # нижний кусок заходит в верхний (без щели на сгибе)


def Rzyx(pitch, yaw, roll):
    return fv.rot_axis((0, 0, 1), roll) @ fv.rot_axis((0, 1, 0), yaw) @ fv.rot_axis((1, 0, 0), pitch)


def euler(R):
    """R = Rz·Ry·Rx → (pitch, yaw, roll)."""
    yaw = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    pitch = math.atan2(R[2, 1], R[2, 2])
    roll = math.atan2(R[1, 0], R[0, 0])
    return np.array([pitch, yaw, roll])


# =====================================================================
# Кубики частей (геометрия в px относительно оси части, ряды скина, раздутие)
# =====================================================================
def arm_x(right, slim):
    w = 3 if slim else 4
    x0 = (-2 if slim else -3) if right else -1
    cx = x0 + w / 2.0
    return x0, w, cx


def part_boxes(slim=False):
    """{часть: [(слой, uvключ, (x0,y0,z0,w,h,d), (r0,r1) ряды по высоте исходной части, h_full, раздутие)]}"""
    B = {}
    B["head"] = [("skin", "head", (-4, -8, -4, 8, 8, 8), (0, 8), 8, 0.0), ("skin", "hat", (-4, -8, -4, 8, 8, 8), (0, 8), 8, 0.5)]
    B["body"] = [("skin", "body", (-4, 0, -2, 8, 6, 4), (0, 6), 12, 0.0), ("skin", "jacket", (-4, 0, -2, 8, 6, 4), (0, 6), 12, 0.25)]
    B["belly"] = [("skin", "body", (-4, -OVERLAP, -2, 8, 6 + OVERLAP, 4), (6, 12), 12, 0.0),
                  ("skin", "jacket", (-4, -OVERLAP, -2, 8, 6 + OVERLAP, 4), (6, 12), 12, 0.25)]
    for side, right in (("r", True), ("l", False)):
        x0, w, cx = arm_x(right, slim)
        a, s = side + "arm", side + "sleeve"
        B[side + "arm"] = [("skin", a, (x0, -2, -2, w, 6.5, 4), (0, 6), 12, 0.0), ("skin", s, (x0, -2, -2, w, 6.5, 4), (0, 6), 12, 0.25)]
        B[side + "fore"] = [("skin", a, (x0 - cx, -OVERLAP, -2, w, 6 + OVERLAP, 4), (6, 12), 12, 0.0),
                            ("skin", s, (x0 - cx, -OVERLAP, -2, w, 6 + OVERLAP, 4), (6, 12), 12, 0.25)]
        l, p = side + "leg", ("rpants" if right else "lpants")
        B[side + "leg"] = [("skin", l, (-2, 0, -2, 4, 6, 4), (0, 6), 12, 0.0), ("skin", p, (-2, 0, -2, 4, 6, 4), (0, 6), 12, 0.25)]
        B[side + "shin"] = [("skin", l, (-2, -OVERLAP, -2, 4, 6 + OVERLAP, 4), (6, 12), 12, 0.0),
                            ("skin", p, (-2, -OVERLAP, -2, 4, 6 + OVERLAP, 4), (6, 12), 12, 0.25)]
    return B


def child_pivot(name, slim=False):
    if name == "belly":
        return v3(0, 6, 0)
    if name in ("rfore", "lfore"):
        _, _, cx = arm_x(name[0] == "r", slim)
        return v3(cx, UPPER_ARM, 0)
    if name in ("rshin", "lshin"):
        return v3(0, THIGH, 0)


PARENT = {"belly": "body", "rfore": "rarm", "lfore": "larm", "rshin": "rleg", "lshin": "lleg"}
ROOTS = ("head", "body", "rarm", "larm", "rleg", "lleg")


def segment_faces(u, v, w, h_full, d, rows, geo, inflate):
    """Грани куска части (как ModelPart.Cuboid, но с рядами rows исходной высоты h_full).
    → [(4 вершины, 4 uv в px текстуры)]"""
    x0, y0, z0, gw, gh, gd = geo
    e = inflate
    f, g, hh = x0 - e, y0 - e, z0 - e
    i, j, k = x0 + gw + e, y0 + gh + e, z0 + gd + e
    v1, v2, v3_, v4 = (f, g, hh), (i, g, hh), (i, j, hh), (f, j, hh)
    v5, v6, v7, v8 = (f, g, k), (i, g, k), (i, j, k), (f, j, k)
    J, K, L, M, N, O = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    P, Q = v, v + d
    r0, r1 = rows
    Qa, Qb = Q + r0, Q + r1
    faces = []

    def quad(vs, ua, va, ub, vb):
        faces.append((vs, [(ub, va), (ua, va), (ua, vb), (ub, vb)]))
    if r0 == 0:
        quad([v6, v5, v1, v2], K, P, L, Q)            # верх части
    if r1 == h_full:
        quad([v3_, v4, v8, v7], L, Q, M, P)           # низ части
    quad([v1, v5, v8, v4], J, Qa, K, Qb)
    quad([v2, v1, v4, v3_], K, Qa, L, Qb)             # перед (−z)
    quad([v6, v2, v3_, v7], L, Qa, N, Qb)
    quad([v5, v6, v7, v8], N, Qa, O, Qb)
    return faces


def part_quads(name, slim=False):
    """Грани части в её локальных координатах (px) с uv в px скина 64×64."""
    out = []
    for layer, key, geo, rows, hfull, infl in part_boxes(slim)[name]:
        u, v = UV[key]
        w, d = geo[3], geo[5]
        out += segment_faces(u, v, w, hfull, d, rows, geo, infl)
    return out


# =====================================================================
# Поза: корневые части {pivot, rot(pitch,yaw,roll)}, суставы {rot}, катана в предплечье, ножны в животе
# =====================================================================
def fk(pose, slim=False):
    """→ {часть: (P, R)} в пространстве модели."""
    W = {}
    for r in ROOTS:
        W[r] = (v3(pose[r][0]), Rzyx(*pose[r][1]))
    for c, p in PARENT.items():
        P, R = W[p]
        W[c] = (P + R @ child_pivot(c, slim), R @ Rzyx(*pose[c]))
    return W


def ik2(S, T, pole, a, b):
    """Двухзвенная цепочка: S — начало, T — цель, сустав — в сторону pole. → (сустав, достигнутая цель)."""
    d = T - S
    L = np.linalg.norm(d)
    dn = nrm(d)
    if L > a + b - 1e-3:
        L = a + b - 1e-3
        T = S + dn * L
    L = max(L, abs(a - b) + 1e-3)
    x = (a * a - b * b + L * L) / (2 * L)
    h = math.sqrt(max(a * a - x * x, 0.0))
    p = pole - dn * np.dot(pole, dn)
    p = nrm(p) if np.linalg.norm(p) > 1e-6 else nrm(np.cross(dn, v3(1, 0, 0)))
    return S + dn * x + p * h, T


def frame_y_z(Y, Zhint):
    Y = nrm(Y)
    Z = Zhint - Y * np.dot(Zhint, Y)
    if np.linalg.norm(Z) < 1e-5:
        Z = np.cross(Y, v3(1, 0, 0))
    Z = nrm(Z)
    X = np.cross(Y, Z)
    return np.stack([X, Y, Z], 1)


def solve(a, slim=False, katana_scale=0.9, left_grip=3.7):
    """Авторская поза → поза частей.
    a: hips (точка таза), pelvis (pitch,yaw,roll), spine (поясница, отн. таза), head (отн. груди),
       G (кулак правой), B (клинок), E (кромка) — в пространстве модели; L — None (левая на рукояти) или точка,
       feet: (правая, левая) точки подошв; saya: (точка, B, E) в пространстве модели (или None)."""
    Rp = Rzyx(*a["pelvis"])
    hips = v3(a["hips"])
    waist = hips + Rp @ v3(0, -6, 0)
    Rc = Rp @ Rzyx(*a["spine"])
    neck = waist + Rc @ v3(0, -6, 0)
    pose = {"head": (neck, euler(Rc @ Rzyx(*a.get("head", (0, 0, 0))))),
            "body": (neck, euler(Rc)), "belly": euler(Rc.T @ Rp)}
    info = {"clamp": 0.0}
    # --- руки к рукояти ---
    G, B, E = v3(a["G"]), nrm(v3(a["B"])), v3(a["E"])
    Rk = blade_axes(B, E)
    hands = {"r": G}
    if a.get("L") is None:
        hands["l"] = G - Rk[:, 1] * left_grip
        lax = Rk[:, 1]
    else:
        hands["l"] = v3(a["L"][0])
        lax = nrm(v3(a["L"][1]))
    poles = {"r": v3(-0.45, 0.35, 0.82), "l": v3(0.45, 0.35, 0.82)}
    reached = {}
    for side, sgn in (("r", -1), ("l", 1)):
        right = side == "r"
        _, _, cx = arm_x(right, slim)
        piv = neck + Rc @ v3(5 * sgn, 2, 0)
        Sx = piv + Rc @ v3(cx, 0, 0)
        target = hands[side]
        elbow, H = ik2(Sx, target, Rc @ poles[side], UPPER_ARM, FIST)
        info["clamp"] = max(info["clamp"], float(np.linalg.norm(H - target)))
        Yu = nrm(elbow - Sx)
        bend = (H - elbow) - Yu * np.dot(H - elbow, Yu)
        Zu = -bend if np.linalg.norm(bend) > 1e-3 else -(Rc @ poles[side])
        Ru = frame_y_z(Yu, Zu)
        Pu = Sx - Ru @ v3(cx, 0, 0)
        ax = Rk[:, 1] if side == "r" else lax
        Rf = frame_y_z(H - elbow, -ax)                          # рукоять сквозь кулак, клинок — к большому пальцу (−z)
        pose[side + "arm"] = (Pu, euler(Ru))
        pose[side + "fore"] = euler(Ru.T @ Rf)
        reached[side] = (H, Rf)
    # катана в кулаке (по достигнутой точке) — в системе предплечья
    H, Rf = reached["r"]
    pose["katana"] = (v3(0, FIST, 0), Rf.T @ Rk)
    pose["katana_world"] = (H, Rk)
    # --- ноги к ступням ---
    for side, sgn, foot in (("r", -1, a["feet"][0]), ("l", 1, a["feet"][1])):
        piv = hips + Rp @ v3(1.9 * sgn, 0, 0)
        F = v3(foot)
        pole = Rp @ v3(0.15 * sgn, 0, -1)                       # колено вперёд
        knee, Fr = ik2(piv, F, pole, THIGH, SHIN)
        Yt = nrm(knee - piv)
        bend = (Fr - knee) - Yt * np.dot(Fr - knee, Yt)
        Zt = bend if np.linalg.norm(bend) > 1e-3 else -pole
        Rt = frame_y_z(Yt, Zt)
        Rs = frame_y_z(Fr - knee, Rt[:, 2] * 0 + Rp[:, 2])      # стопа смотрит вперёд таза
        pose[side + "leg"] = (piv, euler(Rt))
        pose[side + "shin"] = euler(Rt.T @ Rs)
    if a.get("saya") is not None:
        P, Bs, Es = a["saya"]
        Rs = blade_axes(nrm(v3(Bs)), v3(Es))
        Pb, Rb = fk(pose, slim)["belly"]
        pose["saya"] = (Rb.T @ (v3(P) - Pb), Rb.T @ Rs)
    else:
        pose["saya"] = None
    return pose, info


def blade_axes(B, E):
    Y = nrm(B)
    X = E - Y * np.dot(E, Y)
    if np.linalg.norm(X) < 1e-5:
        X = np.cross(Y, v3(0, 0, 1))
    X = nrm(X)
    return np.stack([X, Y, np.cross(X, Y)], 1)


# =====================================================================
# Превью
# =====================================================================
def model_batches(pose, skin, to_view, nview, slim=False, hide=(), katana=None, katana_scale=0.9):
    """to_view: точка модели (px) → камера; nview: нормаль модели → камера."""
    W = fk(pose, slim)
    tw, th = skin.size
    out = []
    for name, (P, R) in W.items():
        if name in hide:
            continue
        b = fv.Batch(skin)
        for verts, uvs in part_quads(name, slim):
            V = [to_view(P + R @ v3(p)) for p in verts]
            UVn = [(uu / tw, vv / th) for uu, vv in uvs]
            n = nrm(np.cross(V[1] - V[0], V[2] - V[0]))
            b.add(V, UVn, [n] * 4)
        out.append(b)
    if katana is not None:
        mesh_katana, mesh_saya, tex, mesh_batch = katana
        Pf, Rf = W["rfore"]
        kp, kR = pose["katana"]
        Pk, Rk = Pf + Rf @ kp, Rf @ kR
        sc = katana_scale
        out.append(mesh_batch(mesh_katana, tex, lambda q: to_view(Pk + Rk @ (v3(q) * sc)), lambda n: nview(Rk @ n)))
        if pose.get("saya") is not None:
            Pb, Rb = W["belly"]
            sp, sR = pose["saya"]
            Ps, Rs = Pb + Rb @ sp, Rb @ sR
            out.append(mesh_batch(mesh_saya, tex, lambda q: to_view(Ps + Rs @ (v3(q) * sc)), lambda n: nview(Rs @ n)))
    return out


MODEL_SCALE = 0.9375


def tp_view(yaw, dist=4.2, height=1.0):
    """Камера 3‑го лица: вокруг игрока на расстоянии dist (блоки), угол yaw (0 — спереди)."""
    def to_view(p):
        w = v3(-p[0], 24 - p[1], p[2]) / 16 * MODEL_SCALE         # мир (как scale(−1,−1,1) у сущностей): y вверх, вперёд −z
        w = fv.rot_axis((0, 1, 0), yaw) @ w
        return w + v3(0, -height, -dist)
    return to_view, lambda n: fv.rot_axis((0, 1, 0), yaw) @ v3(-n[0], -n[1], n[2])


EYE = v3(0, -3.6, 0.0)


def fp_view(pull=0.0, up=0.0, pitch=0.0):
    """Глаза игрока: камера в точке EYE модели, смотрит вперёд (−z). pull — камера рук отодвинута назад (px),
    up — выше (px), pitch — наклон вниз (рад)."""
    Rx = fv.rot_axis((1, 0, 0), pitch)

    def to_view(p):
        q = p - (EYE + v3(0, -up, pull))
        q = Rx @ q
        return v3(-q[0], -q[1], q[2]) / 16 * MODEL_SCALE
    return to_view, lambda n: (lambda m: v3(-m[0], -m[1], m[2]))(Rx @ n)


# =====================================================================
# Ключи авторской позы и клипы
# =====================================================================
class A:
    """Ключ: t (с); hips, pelvis, spine, G, B, E, feet, L — как в solve; cut — от ключа до следующего кромка по ходу;
    hold — остановка; tr — след; dash — множитель рывка (для сервера/клиента — события)."""

    def __init__(self, t, base=None, cut=False, hold=False, tr=0.0, gl=0.0, **kw):
        d = dict(base or {})
        d.update(kw)
        self.t, self.d, self.cut, self.hold, self.tr, self.gl = t, d, cut, hold, tr, gl


def _herm(ts, vals, holds, t):
    import katana_gen as KG
    return KG.hermite(ts, [np.atleast_1d(np.array(v, float)) for v in vals], holds, t)


TIP = 14.0


def sample_auth(keys, t, length):
    ts = [k.t for k in keys]
    holds = [k.hold for k in keys]
    t = min(max(t, 0.0), length)
    n = len(keys)
    i = max([j for j in range(n) if ts[j] <= t] or [0])
    j = min(i + 1, n - 1)
    a = 0.0 if j == i else (t - ts[i]) / (ts[j] - ts[i])
    d = {}
    for f in ("hips", "pelvis", "spine", "head", "G"):
        d[f] = _herm(ts, [k.d.get(f, (0, 0, 0)) for k in keys], holds, t)
    T = _herm(ts, [v3(k.d["G"]) + nrm(v3(k.d["B"])) * TIP for k in keys], holds, t)
    d["B"] = nrm(T - d["G"])
    sm = a * a * (3 - 2 * a)
    import katana_gen as KG
    d["E"] = KG.slerp(v3(keys[i].d["E"]), v3(keys[j].d["E"]), sm)
    fr = _herm(ts, [k.d["feet"][0] for k in keys], holds, t)
    fl = _herm(ts, [k.d["feet"][1] for k in keys], holds, t)
    d["feet"] = (fr, fl)
    Li, Lj = keys[i].d.get("L"), keys[j].d.get("L")
    d["L"] = None if (Li is None and Lj is None) else None if (Li is None or Lj is None) else (
        v3(Li[0]) * (1 - sm) + v3(Lj[0]) * sm, KG.slerp(v3(Li[1]), v3(Lj[1]), sm))
    d["saya"] = keys[i].d.get("saya")
    cut = 0.0
    if keys[i].cut and j != i:
        cut = max(0.0, min(1.0, a / 0.1, (1 - a) / 0.15))
    if cut > 0:
        dt = 1 / 240
        g1, b1 = _gb(keys, max(0, t - dt), length)
        g2, b2 = _gb(keys, min(length, t + dt), length)
        v = (g2 + b2 * TIP * 0.8) - (g1 + b1 * TIP * 0.8)
        v = v - d["B"] * np.dot(v, d["B"])
        if np.linalg.norm(v) > 1e-5:
            d["E"] = KG.slerp(d["E"], nrm(v), cut)
    d["tr"] = keys[i].tr * (1 - a) + keys[j].tr * a
    d["gl"] = keys[i].gl * (1 - a) + keys[j].gl * a
    return d


def _gb(keys, t, length):
    ts = [k.t for k in keys]
    holds = [k.hold for k in keys]
    G = _herm(ts, [k.d["G"] for k in keys], holds, t)
    T = _herm(ts, [v3(k.d["G"]) + nrm(v3(k.d["B"])) * TIP for k in keys], holds, t)
    return G, nrm(T - G)


def rot_pow(R, k):
    """Доля k поворота R (по оси‑углу)."""
    c = max(-1.0, min(1.0, (np.trace(R) - 1) / 2))
    ang = math.acos(c)
    if ang < 1e-6:
        return np.eye(3)
    axis = nrm(v3(R[2, 1] - R[1, 2], R[0, 2] - R[2, 0], R[1, 0] - R[0, 1]))
    return fv.rot_axis(axis, ang * k)


EYE_LOCAL = v3(0, -3.6, -0.8)


def head_camera(pose, follow=0.5, slim=False):
    """Камера 1‑го лица на голове скелета: положение — от головы, поворот — доля follow от поворота корпуса.
    → (to_view, nview, eye, Rcam)"""
    W = fk(pose, slim)
    P, R = W["head"]
    Rcam = rot_pow(R, follow)
    eye = P + R @ EYE_LOCAL * 0 + Rcam @ EYE_LOCAL

    def to_view(p):
        q = Rcam.T @ (p - eye)
        return v3(-q[0], -q[1], q[2]) / 16 * MODEL_SCALE

    def nview(n):
        m = Rcam.T @ n
        return v3(-m[0], -m[1], m[2])
    return to_view, nview, eye, Rcam
