"""
Суставная рука игрока (плечо → локоть → запястье → кисть) для оружия с «живыми» руками (тесак Громового тигра).
Рука — один гнутый брусок из скина игрока: в локте и запястье сечения сходятся под углом («на ус»), вдоль
предплечья сечение доворачивается к кисти (как настоящая пронация). Локоть ищется по двум костям (IK) с подсказкой,
куда ему смотреть; кисть жёстко держит рукоять, запястье гнётся не больше WRIST_LIM — дальше кисть доворачивается.
Та же математика — в Java client/weapon/fp/ArmRig.java (правь оба файла вместе).
"""
import math

import numpy as np

CAP, LU, LF, HW, HE = 2.0, 4.6, 3.7, 1.3, 1.1      # px: над плечом, плечо, предплечье, запястье→хват, хват→конец кисти
TOTAL = CAP + LU + LF + HW + HE
ROW_E = 12.0 * (CAP + LU) / TOTAL                   # строка текстуры руки (0..12) у локтя
ROW_W = 12.0 * (CAP + LU + LF) / TOTAL              # у запястья
WRIST_LIM = math.radians(55)
STRETCH_MAX = 1.35
H0 = np.array([0.90, 0.42, 0.0]) / math.hypot(0.90, 0.42)   # ось кисти в системе оружия (костяшки — к кромке и чуть к острию)


def nrm(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def rot_axis(axis, ang):
    x, y, z = nrm(np.asarray(axis, float))
    c, s = math.cos(ang), math.sin(ang)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


def rot_to(a, b):
    """Кратчайший поворот единичного a в единичный b."""
    ax = np.cross(a, b)
    s = np.linalg.norm(ax)
    c = float(np.dot(a, b))
    if s < 1e-6:
        if c > 0:
            return np.eye(3)
        p = np.cross(a, [1.0, 0, 0])
        if np.linalg.norm(p) < 1e-4:
            p = np.cross(a, [0, 1.0, 0])
        return rot_axis(p, math.pi)
    return rot_axis(ax / s, math.atan2(s, c))


def perp(v, u, fallback):
    p = v - u * np.dot(v, u)
    if np.linalg.norm(p) < 1e-4:
        p = fallback - u * np.dot(fallback, u)
        if np.linalg.norm(p) < 1e-4:
            p = np.cross(u, [0.0, 0.0, 1.0])
    return nrm(p)


HANDLE_MIN = math.radians(38)                        # кисть не ложится вдоль рукояти ближе этого угла


def solve(S, G, h, pole, unit, lm=1.0, ty=None):
    """Плечо S, хват G, ось кисти h, подсказка локтю pole, ty — ось рукояти в кулаке → dict(S, E, W, h, k).
    Если запястье пришлось бы выломать сильнее WRIST_LIM — кисть доворачивается к предплечью (рукоять
    проворачивается в кулаке), но поперёк рукояти остаётся."""
    S, G, h, pole = (np.asarray(x, float) for x in (S, G, h, pole))
    h = nrm(h)
    a0, b0 = LU * lm * unit, LF * lm * unit
    E = W = None
    k = 1.0
    for it in range(2):
        W = G - h * (HW * unit)
        d = W - S
        dist = max(1e-5, float(np.linalg.norm(d)))
        k = min(STRETCH_MAX, max(1.0, dist / ((a0 + b0) * 0.995)))
        a, b = a0 * k, b0 * k
        dc = max(abs(a - b) + 1e-4, min(dist, (a + b) * 0.999))
        dn = d / dist
        ca = max(-1.0, min(1.0, (a * a + dc * dc - b * b) / (2 * a * dc)))
        sa = math.sqrt(max(0.0, 1 - ca * ca))
        p = perp(pole, dn, np.array([0.0, -1.0, 0.0]))
        E = S + dn * (a * ca) + p * (a * sa)
        f = nrm(W - E)
        if it == 0:
            ang = math.acos(max(-1.0, min(1.0, float(np.dot(f, h)))))
            if ang > WRIST_LIM:
                ax = np.cross(h, f)
                if np.linalg.norm(ax) > 1e-5:
                    h = nrm(rot_axis(ax, ang - WRIST_LIM) @ h)
                    if ty is not None:
                        ty = nrm(np.asarray(ty, float))
                        c = float(np.dot(h, ty))
                        cm = math.cos(HANDLE_MIN)
                        if abs(c) > cm:                      # слишком вдоль рукояти — отвести
                            pp = perp(h, ty, np.cross(ty, [0.0, 0.0, 1.0]))
                            h = nrm(ty * (cm if c > 0 else -cm) + pp * math.sin(HANDLE_MIN))
                    continue
        break
    return dict(S=S, E=E, W=W, h=h, k=k)


def wrist_bend(sol):
    """Угол запястья (град) — для проверки поз."""
    f = nrm(sol["W"] - sol["E"])
    return math.degrees(math.acos(max(-1.0, min(1.0, float(np.dot(f, sol["h"]))))))


def mesh(sol, bn, out, unit, right, slim=False, wscale=1.0, inflate=0.0, lm=1.0, sleeve=False):
    """→ [(4 вершины, 4 uv в px скина 64×64)]. bn — куда смотрит тыльная сторона кисти, out — «наружу от тела»."""
    S, E, W, h = sol["S"], sol["E"], sol["W"], sol["h"]
    bn, out = np.asarray(bn, float), np.asarray(out, float)
    u1, u2, u3 = nrm(E - S), nrm(W - E), h
    s1 = perp(out, u1, np.array([1.0, 0, 0]))
    hw = ((1.5 if slim else 2.0) * wscale + inflate) * unit
    hd = (2.0 * wscale + inflate) * unit
    s2 = rot_to(u1, u2) @ s1
    s3 = rot_to(u2, u3) @ s2
    sH = perp(bn, u3, s3)
    tau = math.atan2(float(np.dot(np.cross(s3, sH), u3)), float(np.dot(s3, sH)))
    tau = max(-2.6, min(2.6, tau))
    CR = ((1, 1), (1, -1), (-1, -1), (-1, 1))

    def flat(c, u, s):
        t = np.cross(u, s)
        return [c + s * (hw * a) + t * (hd * b) for a, b in CR]

    def mitre(c, uin, uout, s):
        m = uin + uout
        m = nrm(m) if np.linalg.norm(m) > 1e-4 else uin
        den = max(0.60, float(np.dot(uin, m)))
        t = np.cross(uin, s)
        outp = []
        for a, b in CR:
            o = s * (hw * a) + t * (hd * b)
            outp.append(c + o + uin * (-float(np.dot(o, m)) / den))
        return outp
    fl = float(np.linalg.norm(W - E))
    tw = lambda fr: rot_axis(u2, tau * fr) @ s2
    rings = [(flat(S - u1 * (CAP * lm * unit), u1, s1), 0.0),
             (mitre(E, u1, u2, s1), ROW_E),
             (flat(E + u2 * (fl / 3), u2, tw(1 / 3)), ROW_E + (ROW_W - ROW_E) / 3),
             (flat(E + u2 * (fl * 2 / 3), u2, tw(2 / 3)), ROW_E + (ROW_W - ROW_E) * 2 / 3),
             (mitre(W, u2, u3, tw(1.0)), ROW_W),
             (flat(W + u3 * ((HW + HE) * unit), u3, sH), 12.0)]
    w = 3 if slim else 4
    if right:
        bx, by = (40, 32) if sleeve else (40, 16)
    else:
        bx, by = (48, 48) if sleeve else (32, 48)
    col_first, col_front, col_third, col_back = (bx, bx + 4), (bx + 4, bx + 4 + w), (bx + 4 + w, bx + 8 + w), (bx + 8 + w, bx + 8 + 2 * w)
    outer, inner = (col_first, col_third) if right else (col_third, col_first)
    plus_t, minus_t = (col_back, col_front) if right else (col_front, col_back)
    cols = [outer, minus_t, inner, plus_t]              # грани между углами (0,1) (1,2) (2,3) (3,0)
    quads = []
    for j in range(len(rings) - 1):
        (A, ra), (B, rb) = rings[j], rings[j + 1]
        for i in range(4):
            i2 = (i + 1) % 4
            c0, c1 = cols[i]
            quads.append(([A[i], A[i2], B[i2], B[i]], [(c0, by + 4 + ra), (c1, by + 4 + ra), (c1, by + 4 + rb), (c0, by + 4 + rb)]))
    A = rings[0][0]
    quads.append(([A[0], A[3], A[2], A[1]], [(bx + 4, by), (bx + 4 + w, by), (bx + 4 + w, by + 4), (bx + 4, by + 4)]))
    B = rings[-1][0]
    quads.append(([B[0], B[1], B[2], B[3]], [(bx + 4 + w, by), (bx + 4 + 2 * w, by), (bx + 4 + 2 * w, by + 4), (bx + 4 + w, by + 4)]))
    return quads
