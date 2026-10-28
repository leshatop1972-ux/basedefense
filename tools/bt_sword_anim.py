"""
Меч кровожада: позы замаха и удара для 1‑го и 3‑го лица + превью без игры.

    python tools/bt_sword_anim.py            — записать ключевые кадры в
                                               client/weapon/BloodSwordKeys.java
    python tools/bt_sword_anim.py --preview  — ещё и tools/mob_preview.png: кадры от 3‑го лица (сбоку и спереди)
                                               и от 1‑го лица (как видит игрок)

Та же математика, что в Java (client/weapon/BloodSwordPose): ключи → сплайн Катмулла–Рома → поза;
руки — обратная кинематика (кисть в точке хвата), меч — в правой кисти, левая кисть — на рукояти.

3‑е лицо — пространство модели игрока (px, y вниз, вперёд — −z), опора тела — шея (0, 0, 0), бёдра (0, 12, 0).
1‑е лицо — пространство камеры (блоки, y вверх, вперёд — −z).
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import bt_sword_gen as G  # noqa: E402

JAVA = os.path.join(os.path.dirname(__file__), "..", "src", "client", "java", "com", "lesha_top1972", "basedefense",
                    "client", "weapon", "BloodSwordKeys.java")

# --- общие числа (совпадают с Java) ---
SCALE_3P = 0.70          # размер меча от 3‑го лица (1 px модели = 0.70 px игрока)
SCALE_1P = 0.50          # от 1‑го лица
HAND_GAP = (G.GRIP_RIGHT - G.GRIP_LEFT)    # px модели меча между кистями
ARM_REACH = 9.5          # px от плеча до середины кулака
SHRUG = 1.4              # плечи приподняты, когда руки над головой (px)

# =====================================================================
# Ключевые кадры
# =====================================================================
# 3‑е лицо: t, хват G (px модели), клинок B, наклон корпуса (рад, + вперёд), поворот корпуса,
#           левая нога, правая нога (питч, рад), «пожатие плеч» 0..1
TP_CHARGE = [
    (0.00, (-1.0, 9.6, -6.2), (0.0, -0.40, -0.92), 0.04, 0.00, 0.00, 0.00, 0.0),
    (0.30, (-0.6, 4.6, -8.0), (0.0, -0.93, -0.36), 0.00, -0.06, -0.10, 0.08, 0.2),
    (0.65, (-0.4, -1.6, -7.6), (0.0, -0.99, 0.10), -0.06, 0.08, -0.20, 0.16, 0.7),
    (1.00, (-0.4, -4.6, -6.0), (0.0, -0.72, 0.69), -0.12, 0.14, -0.28, 0.22, 1.0),
]
# удар: время в тиках (0 — отпустил ЛКМ, 3 — попадание)
TP_SLAM = [
    (0.0, (-0.4, -4.6, -6.0), (0.0, -0.72, 0.69), -0.12, 0.14, -0.28, 0.22, 1.0),
    (1.0, (-0.4, -3.2, -7.4), (0.0, -0.80, -0.60), -0.02, 0.06, -0.36, 0.26, 0.8),
    (2.0, (-0.4, 2.4, -8.6), (0.0, 0.10, -0.99), 0.18, -0.04, -0.46, 0.30, 0.3),
    (3.0, (-0.4, 7.6, -6.9), (0.0, 0.62, -0.78), 0.36, -0.10, -0.52, 0.34, 0.0),
    (6.0, (-0.4, 8.4, -6.0), (0.0, 0.72, -0.69), 0.40, -0.10, -0.52, 0.34, 0.0),
    (9.0, (-0.6, 8.0, -6.4), (0.0, 0.50, -0.86), 0.24, -0.06, -0.30, 0.20, 0.0),
]
# 1‑е лицо: t, хват G (камера), клинок B, откуда идёт правая рука, откуда левая (направления от кисти к плечу),
#           поворот плоскости клинка вокруг его оси (рад; 0 — к камере ребром, π/2 — плашмя)
# 1‑е лицо — просто: меч вертикально перед собой в обеих руках, кромкой к себе (поворот 0 — плоскость клинка
# смотрит вбок, удар идёт лезвием); при замахе поднимается, при ударе резко идёт вниз; руки — снизу
FP_REST = (0.0, (0.36, -0.46, -0.80), (-0.28, 0.93, -0.24), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.25)
FP_CHARGE = [
    (0.00, (0.10, -0.44, -0.74), (0.00, 0.97, -0.24), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (0.50, (0.08, -0.16, -0.76), (0.00, 1.00, -0.05), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (1.00, (0.06, 0.10, -0.78), (0.00, 0.96, 0.28), (0.30, -1.0, 0.12), (-0.30, -1.0, 0.12), 0.0),
]
FP_SLAM = [
    (0.0, (0.06, 0.10, -0.78), (0.00, 0.96, 0.28), (0.30, -1.0, 0.12), (-0.30, -1.0, 0.12), 0.0),
    (1.0, (0.06, -0.02, -0.84), (0.00, 0.55, -0.83), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (2.0, (0.06, -0.28, -0.84), (0.00, -0.12, -0.99), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (3.0, (0.06, -0.58, -0.62), (-0.04, -0.40, -0.92), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (6.0, (0.06, -0.62, -0.60), (-0.04, -0.46, -0.89), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
    (9.0, (0.14, -0.50, -0.74), (-0.12, 0.30, -0.95), (0.30, -1.0, 0.10), (-0.30, -1.0, 0.10), 0.0),
]
SLAM_TICKS = 14.0        # с 9 до 14 тиков — возврат к обычной стойке (смешивание)


# =====================================================================
# Векторы
# =====================================================================
def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def scl(a, k):
    return [a[i] * k for i in range(3)]


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def norm(a):
    l_ = math.sqrt(dot(a, a)) or 1.0
    return [v / l_ for v in a]


def mv(R, v):
    """R — столбцы (X, Y, Z)."""
    return [R[0][i] * v[0] + R[1][i] * v[1] + R[2][i] * v[2] for i in range(3)]


def basis(y_axis, x_ref):
    """Поворот: +y → y_axis, +x — как можно ближе к x_ref. → столбцы (X, Y, Z)."""
    Y = norm(y_axis)
    X = sub(x_ref, scl(Y, dot(x_ref, Y)))
    if math.sqrt(dot(X, X)) < 1e-4:
        X = cross([0, 0, 1], Y)
    X = norm(X)
    Z = cross(X, Y)
    return [X, Y, Z]


def rot_x(v, a):
    c, s = math.cos(a), math.sin(a)
    return [v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c]


def rot_y(v, a):
    c, s = math.cos(a), math.sin(a)
    return [v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c]


def body_R(lean, twist):
    """Как ModelPart: Ry(yaw)·Rx(pitch)."""
    return [rot_y(rot_x(e, lean), twist) for e in ([1, 0, 0], [0, 1, 0], [0, 0, 1])]


def euler_zyx(R):
    """Столбцы (X, Y, Z) → углы ModelPart (pitch, yaw, roll): R = Rz·Ry·Rx."""
    r20 = R[0][2]
    yaw = math.asin(max(-1.0, min(1.0, -r20)))
    pitch = math.atan2(R[1][2], R[2][2])
    roll = math.atan2(R[0][1], R[0][0])
    return pitch, yaw, roll


# =====================================================================
# Сплайн по ключам
# =====================================================================
def flat(key):
    out = [key[0]]
    for v in key[1:]:
        out.extend(v if isinstance(v, (tuple, list)) else [v])
    return out


def sample(keys, t):
    ks = [flat(k) for k in keys]
    if t <= ks[0][0]:
        return ks[0][1:]
    if t >= ks[-1][0]:
        return ks[-1][1:]
    i = 0
    while ks[i + 1][0] < t:
        i += 1
    p0, p1, p2, p3 = ks[max(0, i - 1)], ks[i], ks[i + 1], ks[min(len(ks) - 1, i + 2)]
    f = (t - p1[0]) / (p2[0] - p1[0])
    f2, f3 = f * f, f * f * f
    out = []
    for j in range(1, len(p1)):
        a, b, c, d = p0[j], p1[j], p2[j], p3[j]
        out.append(0.5 * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f2 + (-a + 3 * b - 3 * c + d) * f3))
    return out


# =====================================================================
# 3‑е лицо: поза
# =====================================================================
def tp_pose(v):
    """v — 13 чисел ключа → кости и меч (всё в пространстве модели)."""
    g, b = v[0:3], norm(v[3:6])
    lean, twist, leg_l, leg_r, shrug = v[6:11]
    R = body_R(lean, twist)
    hip = [0, 12, 0]
    neck = add(hip, mv(R, [0, -12, 0]))
    sh_r = add(neck, mv(R, [-5 + 0.3 * shrug, 2 - SHRUG * shrug, 0]))
    sh_l = add(neck, mv(R, [5 - 0.3 * shrug, 2 - SHRUG * shrug, 0]))
    # хват задан относительно корпуса без наклона — поворачиваем вместе с корпусом
    g = add(neck, mv(R, g))
    b = mv(R, b)

    def aim(shoulder, target, hand_x):
        a = target
        for _ in range(4):
            Rm = basis(sub(a, shoulder), [1, 0, 0])
            hand = add(shoulder, mv(Rm, [hand_x, ARM_REACH, 0]))
            a = add(a, sub(target, hand))
        Rm = basis(sub(a, shoulder), [1, 0, 0])
        return Rm, add(shoulder, mv(Rm, [hand_x, ARM_REACH, 0]))

    Rr, hand_r = aim(sh_r, g, -1.0)
    # левая кисть — на рукояти ниже правой, где достаёт рука
    best = None
    for k in range(0, 41):
        tt = HAND_GAP * (0.55 + 0.9 * k / 40)
        p = add(hand_r, scl(b, -tt * SCALE_3P))
        err = abs(math.sqrt(dot(sub(p, sh_l), sub(p, sh_l))) - math.hypot(ARM_REACH, 1.0))
        if best is None or err < best[0]:
            best = (err, p)
    Rl, hand_l = aim(sh_l, best[1], 1.0)
    # плоскость клинка: нормаль ≈ ось x корпуса (рубящий удар сверху вниз)
    side = mv(R, [1, 0, 0])
    Z = norm(sub(side, scl(b, dot(side, b))))
    X = cross(b, Z)
    return dict(R=R, neck=neck, sh_r=sh_r, sh_l=sh_l, Rr=Rr, Rl=Rl, hand_r=hand_r, hand_l=hand_l,
                grip=hand_r, sword=[X, b, Z], lean=lean, twist=twist, leg_l=leg_l, leg_r=leg_r,
                gap=best[0], g_err=math.sqrt(dot(sub(hand_r, g), sub(hand_r, g))))


# =====================================================================
# 1‑е лицо: поза
# =====================================================================
def fp_pose(v):
    g, b = v[0:3], norm(v[3:6])
    off_r, off_l, roll = v[6:9], v[9:12], v[12]
    Z0 = norm(sub([1, 0, 0], scl(b, dot([1, 0, 0], b))))
    Z1 = cross(b, Z0)
    Z = add(scl(Z0, math.cos(roll)), scl(Z1, math.sin(roll)))
    X = cross(b, Z)
    hand_l = add(g, scl(b, -HAND_GAP * SCALE_1P / 16))
    return dict(grip=g, sword=[X, b, Z], sh_r=add(g, off_r), sh_l=add(hand_l, off_l), hand_r=g, hand_l=hand_l)


# =====================================================================
# Java
# =====================================================================
def java_keys(name, keys):
    rows = []
    for k in keys:
        rows.append("            {" + ", ".join("%.4ff" % x for x in flat(k)) + "}")
    return "    static final float[][] %s = {\n%s\n    };\n" % (name, ",\n".join(rows))


def write_java():
    src = ("package com.lesha_top1972.basedefense.client.weapon;\n\n"
           "/**\n * Ключевые кадры меча кровожада. СОЗДАНО tools/bt_sword_anim.py — правь скрипт, а не этот файл.\n"
           " * 3‑е лицо: {t, хват x,y,z (px модели), клинок x,y,z, наклон, поворот корпуса, левая нога, правая нога, плечи}.\n"
           " * 1‑е лицо: {t, хват x,y,z (камера, блоки), клинок x,y,z, направление к правому плечу x,y,z, к левому x,y,z, поворот плоскости}.\n"
           " * Время: замах — доля 0..1, удар — тики (3 — попадание).\n */\n"
           "final class BloodSwordKeys {\n\n"
           "    static final float SCALE_3P = %.3ff;\n    static final float SCALE_1P = %.3ff;\n"
           "    static final float GRIP_GAP = %.3ff;        // px модели меча между кистями\n"
           "    static final float ARM_REACH = %.3ff;\n    static final float SHRUG = %.3ff;\n"
           "    static final float MODEL_K = %.1ff;           // модель меча построена в K раз крупнее\n\n"
           % (SCALE_3P, SCALE_1P, HAND_GAP, ARM_REACH, SHRUG, G.K))
    src += java_keys("TP_CHARGE", TP_CHARGE) + "\n" + java_keys("TP_SLAM", TP_SLAM) + "\n"
    src += java_keys("FP_REST", [FP_REST]) + "\n"
    src += java_keys("FP_CHARGE", FP_CHARGE) + "\n" + java_keys("FP_SLAM", FP_SLAM) + "\n"
    src += "    private BloodSwordKeys() {\n    }\n}\n"
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as f:
        f.write(src)
    print("→", os.path.relpath(JAVA))


# =====================================================================
# Превью
# =====================================================================
SKIN = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures",
                    "entity", "settler", "male_1.png")


def box(u, v, x, y, z, w, h, d):
    return (u, v, x, y, z, w, h, d, 0)


def part_quads(cubes, tf):
    import settler_preview as sp
    out = []
    for c in cubes:
        for verts, u1, v1, u2, v2 in sp.cuboid_quads(c):
            out.append(([tf(vv) for vv in verts], u1, v1, u2, v2))
    return out


def euler_tf(pivot, pitch, yaw, roll, parent):
    def f(v):
        v = rot_x(list(v), pitch)
        v = rot_y(v, yaw)
        c, s = math.cos(roll), math.sin(roll)
        v = [v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]]
        return parent(add(v, pivot))
    return f


def matrix_tf(origin, R, parent, scale=1.0, local_off=(0, 0, 0)):
    def f(v):
        v = add(list(v), local_off)
        return parent(add(origin, mv(R, scl(v, scale))))
    return f


def sword_quads(tf_sword):
    """Модель меча (Java‑px ×K, y вниз) → точки в системе меча (+y = клинок, px меча) → tf_sword."""
    if not hasattr(G.M, "bones_out"):
        G.finalize_bones()

    def conv(v):
        # Java модели: x, 24 − yb, z (yb — Bedrock) → система меча: x/K, yb/K, −z/K (поворот на 180° вокруг x)
        return tf_sword([v[0] / G.K, (24 - v[1]) / G.K, -v[2] / G.K])
    return G.java_quads(G.M, conv)


def tp_scene(pose, view):
    """Игрок + меч в пространстве модели → мир превью через view(p)."""
    q = []
    neck, R = pose["neck"], pose["R"]
    pitch_b, yaw_b, roll_b = euler_zyx(R)
    q += part_quads([box(0, 0, -4, -8, -4, 8, 8, 8)], euler_tf(neck, pitch_b, yaw_b, roll_b, view))
    q += part_quads([box(16, 16, -4, 0, -2, 8, 12, 4)], euler_tf(neck, pitch_b, yaw_b, roll_b, view))
    q += part_quads([box(0, 16, -2, 0, -2, 4, 12, 4)], euler_tf([-1.9, 12, 0], pose["leg_r"], 0, 0, view))
    q += part_quads([box(16, 48, -2, 0, -2, 4, 12, 4)], euler_tf([1.9, 12, 0], pose["leg_l"], 0, 0, view))
    for key, sh, cube in (("Rr", "sh_r", box(40, 16, -3, -2, -2, 4, 12, 4)), ("Rl", "sh_l", box(32, 48, -1, -2, -2, 4, 12, 4))):
        p, y_, r_ = euler_zyx(pose[key])
        q += part_quads([cube], euler_tf(pose[sh], p, y_, r_, view))
    sw = sword_quads(matrix_tf(pose["grip"], pose["sword"], view, SCALE_3P))
    return q, sw


def fp_scene(pose, cam):
    q = []
    U = 16.0   # px → блоки
    for hand, sh, cube, hx in (("hand_r", "sh_r", box(40, 16, -3, -2, -2, 4, 12, 4), -1.0),
                               ("hand_l", "sh_l", box(32, 48, -1, -2, -2, 4, 12, 4), 1.0)):
        d = norm(sub(pose[hand], pose[sh]))
        Rm = basis(d, [-1, 0, 0])
        origin = sub(pose[hand], mv(Rm, [hx / U, ARM_REACH / U, 0]))
        q += part_quads([cube], matrix_tf(origin, Rm, cam, 1 / U))
    sw = sword_quads(matrix_tf(pose["grip"], pose["sword"], cam, SCALE_1P / 16))
    return q, sw


def preview():
    import mob_preview  # noqa: F401  (подменяет settler_preview.collect)
    skin = Image.open(SKIN).convert("RGBA")
    PT = G.make_painter()
    tex, glow = PT.img, PT.glow
    frames = []
    W, H = 200, 260

    def side(p):          # вид сбоку справа: мир x = −z модели, y = −y
        return [-p[2], -p[1], p[0]]

    def front(p):         # вид спереди‑сбоку
        q = rot_y([p[0], -p[1], -p[2]], 0.55)
        return [q[0], q[1], -q[2]]

    def proj_tp(p):
        return (W / 2 + p[0] * 5.0, H / 2 + 20 - p[1] * 5.0 - 30, p[2])

    shots = [("charge", 0.0), ("charge", 0.3), ("charge", 0.65), ("charge", 1.0),
             ("slam", 1.0), ("slam", 2.0), ("slam", 3.0), ("slam", 6.0)]
    rows = []
    for view in (side, front):
        row = []
        for clip, t in shots:
            v = sample(TP_CHARGE if clip == "charge" else TP_SLAM, t)
            pose = tp_pose(v)
            q, sw = tp_scene(pose, view)
            img = G.raster([(q, skin, None), (sw, tex, glow)], (W, H),
                           lambda p: (W / 2 + p[0] * 5.0, H / 2 - 10 - p[1] * 5.0, p[2]))
            if view is side:
                print("%s %.2f: зазор левой кисти %.2f px, правая не дотянулась на %.2f px" % (clip, t, pose["gap"], pose["g_err"]))
            row.append(img)
        rows.append(row)
    # 1‑е лицо: перспектива (FOV 70° по вертикали)
    FW, FH = 320, 180
    fy = FH / 2 / math.tan(math.radians(35))
    row = []
    for clip, t in [("rest", 0)] + shots[:-1]:
        v = flat(FP_REST)[1:] if clip == "rest" else sample(FP_CHARGE if clip == "charge" else FP_SLAM, t)
        pose = fp_pose(v)
        q, sw = fp_scene(pose, lambda p: p)

        def proj(p):
            if p[2] > -0.05:
                return None
            return (FW / 2 + p[0] / -p[2] * fy, FH / 2 - p[1] / -p[2] * fy, -p[2])
        img = G.raster([(q, skin, None), (sw, tex, glow)], (FW, FH), proj, bg=(110, 150, 200, 255))
        row.append(img)
    sheet = Image.new("RGBA", (W * len(shots), H * 2 + 2 * FH + 16), (40, 42, 48, 255))
    for r, imgs in enumerate(rows):
        for i, im in enumerate(imgs):
            sheet.alpha_composite(im, (i * W, r * H))
    for i, im in enumerate(row):
        sheet.alpha_composite(im, ((i % 4) * (FW + 80) + 40, 2 * H + 8 + (i // 4) * (FH + 8)))
    sheet.save(os.path.join(os.path.dirname(__file__), "mob_preview.png"))
    print("preview → tools/mob_preview.png")


if __name__ == "__main__":
    write_java()
    if "--preview" in sys.argv:
        preview()
