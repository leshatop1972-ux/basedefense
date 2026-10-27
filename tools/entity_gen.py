"""
Генератор «Туманной сущности» (mist_entity) и «Кровавой сущности» (blood_entity): модели Bedrock (как у игрока
Minecraft: голова, тело, руки, ноги + второй слой), текстуры и анимации.

Запуск:  python tools/entity_gen.py
Превью:  PREVIEW_SCALE=10 python tools/mob_preview.py mist_entity
         PREVIEW_SCALE=10 python tools/mob_preview.py blood_entity attack 0,0.3,0.4

Туманная — белая, с дымно‑серыми разводами, второй слой — полупрозрачные клубы дыма, ноги к низу растворяются;
пустые чёрные глаза и растянутый тонкий рот. Кровавая — чёрная, в красных трещинах‑прожилках, светящиеся
красные глаза и рваный рот (светятся в glowmask).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import FACE_LIGHT, Model, Painter, bake, faces, mix, mul, write_anims  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")


# =====================================================================
# Модель как у игрока
# =====================================================================
def build(name):
    M = Model(name, 64, 64)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("body", "root", (0, 24, 0))
    c("body", (-4, 12, -2), (8, 12, 4), "skin", face="body")
    c("body", (-4, 12, -2), (8, 12, 4), "layer", face="body2", inflate=0.25)
    b("head", "body", (0, 24, 0))
    c("head", (-4, 24, -4), (8, 8, 8), "skin", face="head")
    c("head", (-4, 24, -4), (8, 8, 8), "layer", face="head2", inflate=0.5)
    for side, sx in (("right", -1), ("left", 1)):
        a = side + "_arm"
        b(a, "body", (sx * 5, 22, 0))
        x0 = -8 if sx < 0 else 4
        c(a, (x0, 12, -2), (4, 12, 4), "skin", face="arm")
        c(a, (x0, 12, -2), (4, 12, 4), "layer", face="arm2", inflate=0.25)
        lg = side + "_leg"
        b(lg, "root", (sx * 1.9, 12, 0))
        x1 = -3.9 if sx < 0 else -0.1
        c(lg, (x1, 0, -2), (4, 12, 4), "skin", face="leg")
        c(lg, (x1, 0, -2), (4, 12, 4), "layer", face="leg2", inflate=0.25)
    M.pack()
    return M


# =====================================================================
# Текстуры
# =====================================================================
WHITE = (232, 232, 236)
FOG = (176, 178, 184)
FOG_D = (128, 130, 138)
VOID = (8, 8, 10)
BLACK = (22, 14, 16)
BLACK_L = (46, 30, 34)
RED = (196, 22, 32)
RED_HI = (255, 90, 80)
RED_D = (110, 10, 18)


def paint(M, kind, seed):
    PT = Painter(M.tex_w, M.tex_h, seed=seed)
    rnd = PT.rnd

    def blob(i, j, k):
        """Плавный «дымный» шум 0..1."""
        return 0.5 + 0.25 * math.sin(i * 0.9 + j * 0.45 + k) + 0.25 * math.sin(i * 0.37 - j * 0.8 + 2 * k)

    def mist(face, f, i, j, w, h):
        lit = FACE_LIGHT[f]
        if face == "head" and f == "front":
            # пустые глаза (2×3, чуть разной высоты) и растянутый тонкий рот
            if (i in (1, 2) and 2 <= j <= 4) or (i in (5, 6) and 3 <= j <= 5):
                return VOID
            if j == 6 and 1 <= i <= 6:
                return mix(VOID, FOG_D, 0.25)
            if (i, j) in ((0, 6), (7, 5)):
                return mix(VOID, FOG_D, 0.4)                     # уголки рта вверх
        if face in ("head2", "body2", "arm2", "leg2"):
            # второй слой — клубы дыма, полупрозрачные
            n = blob(i, j, seed * 0.7 + (3 if f in ("left", "right") else 0))
            if n < 0.62 or rnd.random() < 0.25:
                return None
            col = mix(FOG, WHITE, rnd.random() * 0.5)
            a = int(60 + 120 * (n - 0.62) / 0.38)
            return ("alpha", mul(col, lit), a)
        n = blob(i, j, seed)
        col = mix(WHITE, FOG, max(0.0, n - 0.45) * 1.4)
        if rnd.random() < 0.12:
            col = mix(col, FOG_D, 0.5)
        col = mul(col, lit * PT.noise(0.03))
        if face == "leg" and f not in ("top", "bottom"):
            # ноги к низу растворяются в дыму
            fade = max(0.0, (j - h * 0.45) / (h * 0.55))
            if rnd.random() < fade * 0.55:
                return None
            return ("alpha", col, int(255 - 140 * fade))
        if face == "leg" and f == "bottom":
            return None
        return col

    def blood(face, f, i, j, w, h):
        lit = FACE_LIGHT[f]
        if face == "head" and f == "front":
            if (i in (1, 2) and j == 3) or (i in (5, 6) and j == 3):
                return ("glow", RED_HI if i in (2, 5) else RED)
            if j in (5, 6) and 1 <= i <= 6 and (i + j) % 2 == 0:
                return ("glow", RED)                           # рваный рот
            if j == 6 and 1 <= i <= 6:
                return VOID
        if face in ("head2", "body2", "arm2", "leg2"):
            # второй слой — сгустки и подтёки
            if face == "body2" and f == "front" and 2 <= i <= 5 and 3 <= j <= 6:
                return ("glow", mix(RED, RED_D, 0.5)) if (i + j) % 3 == 0 else None   # «сердце» сквозь грудь
            if f not in ("top", "bottom") and rnd.random() < 0.04:
                return mul(RED_D, lit)
            return None
        # чёрное тело с красными трещинами‑прожилками (ветвятся)
        crack = abs(math.sin(i * 1.3 + j * 0.7 + seed)) < 0.06 or abs(math.sin(i * 0.5 - j * 1.1 + seed * 2)) < 0.035
        if crack and f not in ("top", "bottom"):
            return ("glow", RED if rnd.random() < 0.7 else RED_HI) if rnd.random() < 0.6 else mul(RED_D, lit)
        col = BLACK if rnd.random() > 0.08 else BLACK_L
        if face in ("arm", "leg") and j >= h - 3 and f not in ("top", "bottom"):
            col = mix(col, RED_D, 0.5)                          # руки и ноги в крови
        return mul(col, lit * PT.noise(0.04))

    fn = mist if kind == "mist" else blood
    for cube in M.cubes():
        for fname, rect in faces(cube).items():
            x0, y0, w, h = rect
            for jj in range(h):
                for ii in range(w):
                    v = fn(cube.get("face"), fname, ii, jj, w, h)
                    x, y = x0 + ii, y0 + jj
                    if v is None:
                        PT.clear(x, y)
                    elif isinstance(v, tuple) and len(v) == 3 and v[0] == "alpha":
                        PT.put(x, y, v[1], v[2])
                    elif isinstance(v, tuple) and len(v) == 2 and v[0] == "glow":
                        PT.glow_put(x, y, v[1])
                    else:
                        PT.put(x, y, v)
    return PT


# =====================================================================
# Анимации (повороты костей, градусы; +x — наклон вперёд / рука вперёд‑вверх при отрицательном)
# =====================================================================
def P(**kw):
    pose = {n: {"rot": (0, 0, 0)} for n in ("root", "body", "head", "right_arm", "left_arm", "right_leg", "left_leg")}
    for k, v in kw.items():
        if k == "body_pos":
            pose["body"]["pos"] = v
        elif k == "root_pos":
            pose["root"]["pos"] = v
        else:
            pose[k] = dict(pose[k], rot=v)
    return pose


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def keys(seq, t):
    """seq: [(время, {кость: (x,y,z)})] — плавная интерполяция."""
    if t <= seq[0][0]:
        return dict(seq[0][1])
    for k in range(1, len(seq)):
        t1, p1 = seq[k]
        if t <= t1:
            t0, p0 = seq[k - 1]
            f = smooth((t - t0) / (t1 - t0))
            out = {}
            for n in set(p0) | set(p1):
                a, b_ = p0.get(n, (0, 0, 0)), p1.get(n, (0, 0, 0))
                out[n] = tuple(a[i] + (b_[i] - a[i]) * f for i in range(3))
            return out
    return dict(seq[-1][1])


def mk(kind):
    stiff = kind == "mist"

    def idle(t):
        w = 2 * math.pi * t / 4.0
        if stiff:
            # стоит неподвижно, руки висят, голова изредка рывком склоняется набок
            tilt = 18 * smooth((t % 4.0 - 2.0) / 0.15) * (1 - smooth((t % 4.0 - 3.3) / 0.4))
            return P(head=(2 + 1.5 * math.sin(w), 0, tilt), body=(1.0 * math.sin(w * 0.5), 0, 0),
                     right_arm=(0, 0, 2 + 1 * math.sin(w)), left_arm=(0, 0, -2 - 1 * math.sin(w)))
        return P(head=(-4 + 3 * math.sin(w * 1.5), 10 * math.sin(w * 0.5), 0), body=(8 + 2 * math.sin(w), 0, 0),
                 right_arm=(-8 + 4 * math.sin(w + 1), 0, 10), left_arm=(-8 + 4 * math.sin(w + 2), 0, -10),
                 body_pos=(0, 0.6 + 0.4 * math.sin(w * 2), 0))

    def walk(t):
        T_ = 1.0 if stiff else 0.8
        w = 2 * math.pi * t / T_
        s = math.sin(w)
        if stiff:
            # неестественная ходьба: ноги шагают, руки висят как плети, голова не двигается
            return P(right_leg=(-30 * s, 0, 0), left_leg=(30 * s, 0, 0), right_arm=(4 * s, 0, 3), left_arm=(-4 * s, 0, -3),
                     head=(4, 0, 6), body=(3, 0, 0))
        return P(right_leg=(-40 * s, 0, 0), left_leg=(40 * s, 0, 0), right_arm=(-30 + 25 * s, 0, 12),
                 left_arm=(-30 - 25 * s, 0, -12), body=(14, 4 * s, 0), head=(-12, 0, 0),
                 body_pos=(0, 0.8 + 0.6 * abs(s), 0))

    def attack(t):
        """Удар рукой: замах правой назад‑вверх (0–0.3), удар вниз‑вперёд (0.35), возврат (0.8)."""
        seq = [(0.0, {}),
               (0.28, {"right_arm": (-160, -10, 25), "body": (-6, 20, 0), "head": (-6, -10, 0), "left_arm": (10, 0, -10)}),
               (0.36, {"right_arm": (-40, 20, -10), "body": (18, -25, 0), "head": (6, 15, 0), "left_arm": (-20, 0, -15)}),
               (0.50, {"right_arm": (-30, 25, -15), "body": (14, -20, 0), "head": (4, 12, 0)}),
               (0.80, {})]
        if not stiff:
            seq[0] = (0.0, idle(0) and {"body": (8, 0, 0)})
            seq[-1] = (0.80, {"body": (8, 0, 0)})
        return P(**keys(seq, t))

    def lunge(t):
        """Кровавая: «Бросок» — приседает (0–0.4), прыжок‑рывок вперёд, обе руки сверху вниз (0.55), выпрямляется."""
        seq = [(0.0, {"body": (8, 0, 0)}),
               (0.40, {"body": (30, 0, 0), "head": (-30, 0, 0), "right_arm": (-60, 0, 30), "left_arm": (-60, 0, -30),
                       "right_leg": (14, 0, 6), "left_leg": (-22, 0, -6)}),
               (0.48, {"body": (-10, 0, 0), "head": (-10, 0, 0), "right_arm": (-170, 0, 10), "left_arm": (-170, 0, -10),
                       "right_leg": (20, 0, 0), "left_leg": (30, 0, 0)}),
               (0.58, {"body": (38, 0, 0), "head": (-30, 0, 0), "right_arm": (-30, 0, 5), "left_arm": (-30, 0, -5),
                       "right_leg": (-40, 0, 0), "left_leg": (20, 0, 0)}),
               (0.80, {"body": (30, 0, 0), "head": (-26, 0, 0), "right_arm": (-20, 0, 10), "left_arm": (-20, 0, -10),
                       "right_leg": (-30, 0, 0), "left_leg": (15, 0, 0)}),
               (1.20, {"body": (8, 0, 0)})]
        pose = P(**keys(seq, t))
        # опускание корпуса в приседе (ноги согнуты — тело ниже)
        down = 2.5 * smooth(t / 0.4) * (1 - smooth((t - 0.42) / 0.06)) + 2.0 * smooth((t - 0.55) / 0.05) * (1 - smooth((t - 0.8) / 0.4))
        pose["root"]["pos"] = (0, down, 0)          # Java: +y вниз
        return pose

    def pose_of(fn):
        return lambda t: fn(t) if isinstance(fn(t), dict) and "root" in fn(t) else P(**fn(t))

    clips = {"idle": (lambda t: idle(t), 4.0, 20, True), "walk": (lambda t: walk(t), 1.0 if stiff else 0.8, 20, True),
             "attack": (attack, 0.8, 60, False)}
    if not stiff:
        clips["lunge"] = (lunge, 1.2, 60, False)
    return clips


def main():
    bones = ["root", "body", "head", "right_arm", "left_arm", "right_leg", "left_leg"]
    for name, kind, seed in (("mist_entity", "mist", 13), ("blood_entity", "blood", 29)):
        M = build(name)
        M.write_geo(ROOT)
        PT = paint(M, kind, seed)
        PT.img.save(os.path.join(ROOT, "textures", "entity", name + ".png"))
        PT.glow.save(os.path.join(ROOT, "textures", "entity", name + "_glowmask.png"))
        anims = {}
        for cname, (fn, ln, fps, loop) in mk(kind).items():
            anims["animation.%s.%s" % (name, cname)] = bake(fn, ln, fps, loop, bones)
        write_anims(ROOT, name, anims)
        print(name, len(M.cubes()), "кубов,", ", ".join(mk(kind).keys()))


if __name__ == "__main__":
    main()
