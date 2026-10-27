"""
Генератор «Осадного жука» (siege_beetle), «Маленького жука» (beetle_mite) и яйца жука (beetle_egg):
модели Bedrock, текстуры, анимации.

Запуск:  python tools/beetle_gen.py
Проверка без игры:
    python tools/beetle_gen.py --preview siege_beetle walk 0,0.3,0.6     — кадры (YAW, SCALE, W)
    python tools/mob_preview.py siege_beetle
    python tools/anim_check.py siege_beetle

Жук‑носорог: низкая широкая голова с изогнутым рогом‑тараном (зазубрины по верху) и малым вторым рогом
на щите, жвала, выпуклый переднеспинковый щит с гребнем и шипами по краю, сложенные надкрылья с бороздами
(под ними — перепончатые крылья), сегментированное брюшко, шесть ног по 3 звена (бедро — голень — лапка,
IK), усики (маятники), светящиеся глаза. Модель ×1.5 в игре (SiegeBeetleRenderer.SCALE).
Походка — «треножник»: L1, R2, L3 против R1, L2, R3.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import (FACE_LIGHT, Model, Painter, add, bake, cross, dot, faces, length, lerp, mix, mm, mul, mv,  # noqa: E402
                    norm, rx_m, ry_m, rz_m, scl, sub, tr, write_anims)
import nabat_gen as ng  # noqa: E402

ROOT = ng.ROOT
SCALE = 1.5

# =====================================================================
# Палитра: ржаво‑чёрный хитин с металлическим отливом
# =====================================================================
CH = (40, 31, 28)
CH_D = (21, 17, 16)
CH_DD = (12, 10, 10)
CH_L = (78, 60, 50)
CH_HI = (150, 126, 104)
RUST = (98, 58, 38)
BRONZE = (112, 84, 52)
BRONZE_L = (156, 120, 74)
MEMB = (84, 30, 36)
MEMB_D = (52, 18, 24)
WING = (120, 104, 92)
HORN = (26, 21, 21)
HORN_TIP = (164, 146, 122)
EYE = (196, 74, 34)
EYE_HI = (255, 150, 90)
CRACK = (72, 24, 12)
EGG = (150, 122, 92)
EGG_D = (104, 80, 60)
EGG_GLOW = (230, 140, 60)

LEGS = [("l1", 1, -7.0), ("l2", 1, 0.0), ("l3", 1, 7.0), ("r1", -1, -7.0), ("r2", -1, 0.0), ("r3", -1, 7.0)]
TRIPOD_A = {"l1", "r2", "l3"}
FEMUR, TIBIA, TARSUS = 12.0, 13.0, 6.0


# =====================================================================
# 1. Модели
# =====================================================================
def octo(M, bone, cx, cz, y0, h, w, mat, face=None, d=None):
    d = d or w
    M.cube(bone, (cx - w / 2, y0, cz - d / 2), (w, h, d), mat, face=face)
    w2, d2 = w * 0.84, d * 0.84
    M.cube(bone, (cx - w2 / 2, y0 + 0.01, cz - d2 / 2), (w2, h - 0.02, d2), mat, face=face,
           rot=(0, 45, 0), pivot=(cx, y0, cz))


def legs(M, parent, specs, femur, tibia, tarsus, th, y0, spikes=True):
    """Ноги: бедро (кость leg_*), голень (shin_*), лапка (foot_*) — висят вниз от опоры (IK поворачивает)."""
    b, c = M.bone, M.cube
    for name, s, z in specs:
        x = s * (th[0] + (1.0 if name.endswith("2") or name.endswith("3") else 0.0))
        px, pz = x, z
        w1, w2, w3 = th[1], th[2], th[3]
        b("leg_" + name, parent, (px, y0, pz))
        c("leg_" + name, (px - w1 / 2, y0 - femur, pz - w1 / 2), (w1, femur, w1), "chitin", face="femur")
        b("shin_" + name, "leg_" + name, (px, y0 - femur, pz))
        c("shin_" + name, (px - w2 / 2, y0 - femur - tibia, pz - w2 / 2), (w2, tibia, w2), "chitin", face="tibia")
        if spikes:
            c("shin_" + name, (px - w2 / 2 - 0.3, y0 - femur - 1.2, pz - w2 / 2 - 0.3), (w2 + 0.6, 1.4, w2 + 0.6), "memb")
        b("foot_" + name, "shin_" + name, (px, y0 - femur - tibia, pz))
        c("foot_" + name, (px - w3 / 2, y0 - femur - tibia - tarsus, pz - w3 / 2), (w3, tarsus, w3), "chitin",
          face="tarsus")
        if spikes:
            for k in range(3):        # шипы по голени (наружу‑назад)
                yy = y0 - femur - 3 - k * 3.4
                c("shin_" + name, (px - 0.4, yy, pz + w2 / 2 - 0.1), (0.8, 1.4, 1.4 + 0.3 * k), "horn", face="spike")
            c("leg_" + name, (px - 0.5, y0 - femur * 0.55, pz + w1 / 2 - 0.1), (1.0, 1.6, 1.6), "horn", face="spike")
            # крючок на конце лапки
            c("foot_" + name, (px - 0.4, y0 - femur - tibia - tarsus - 0.9, pz - w3 / 2 - 0.9), (0.8, 1.2, 1.6),
              "horn", face="claw")


def build_beetle():
    M = Model("siege_beetle", 256, 256)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("body", "root", (0, 16, 0))
    # переднеспинковый щит: купол, гребень, нависающий край, шипы по краю
    c("body", (-13, 13, -12), (26, 10, 15), "chitin", face="shield")
    c("body", (-11.5, 23, -11), (23, 3, 13), "chitin", face="shield_top")
    c("body", (-8, 26, -9.5), (16, 1.6, 10), "chitin", face="shield_top")
    c("body", (-1.5, 26.5, -11), (3, 2.6, 12), "chitin", face="crest")
    for sx in (-1, 1):
        c("body", (sx * 13 - 1.8, 19.5, -11.5), (3.6, 5.0, 13.5), "chitin", face="shield", rot=(0, 0, -sx * 35),
          pivot=(sx * 13, 22.0, -5.0))
    c("body", (-12, 15, -14.5), (24, 7, 3), "chitin", face="lip")
    for sx in (-1, 1):
        for k, z in enumerate((-10.5, -6.0, -1.5, 2.5)):
            c("body", (sx * 13 - 1.0, 13.2 + k * 0.4, z - 1.0), (2.0, 2.0, 2.0), "horn", face="spike",
              rot=(0, 45, 0), pivot=(sx * 13, 13.2, z))
        c("body", (sx * 12.6 - 0.8, 12.6, -13.5), (1.6, 3.0, 2.0), "horn", face="spike")
    # малый (второй) рог на переднем краю щита
    c("body", (-1.6, 22.0, -18.5), (3.2, 2.6, 6.0), "horn", face="horn", rot=(-18, 0, 0), pivot=(0, 23.2, -13))
    c("body", (-1.0, 23.6, -22.5), (2.0, 1.8, 3.4), "horn", face="horn_tip", rot=(-34, 0, 0), pivot=(0, 24.0, -18.5))
    # грудь снизу
    c("body", (-9, 10, -11), (18, 4, 14), "memb", face="under")
    # надкрылья
    for side, sx in (("right", -1), ("left", 1)):
        e = "elytra_" + side
        b(e, "body", (sx * 0.4, 25, 2.5))
        x0 = -13.6 if sx < 0 else 0.4
        c(e, (x0, 15.0, 2.5), (13.2, 10.5, 21.0), "elytra", face="elytra")
        c(e, (x0 + (1.0 if sx < 0 else 0.0), 25.4, 3.5), (12.2, 2.2, 18.5), "elytra", face="elytra_top")
        c(e, (x0 + (1.5 if sx < 0 else 0.0), 15.8, 23.5), (11.7, 9.0, 2.6), "elytra", face="elytra_tip")
        c(e, (x0 + (2.6 if sx < 0 else 0.0), 17.0, 26.0), (10.6, 6.5, 1.4), "elytra", face="elytra_tip")
        # скос внешнего края (купол, а не коробка)
        ex = sx * 13.6
        c(e, (ex - 1.6, 21.0, 3.0), (3.2, 4.2, 20.0), "elytra", face="elytra_edge", rot=(0, 0, -sx * 40),
          pivot=(ex, 23.0, 13.0))
        c(e, (ex - sx * 0.2 - 1.2, 14.6, 3.5), (2.4, 2.0, 19.0), "elytra", face="elytra_edge")
        w = "wing_" + side
        b(w, "body", (sx * 2.0, 23.6, 5.0))
        wx0 = sx * 2.0 - (17.0 if sx < 0 else 0.0)
        wx0 = sx * 2.0 - (11.0 if sx < 0 else 0.0)
        c(w, (wx0, 23.4, 5.0), (11.0, 0.4, 19.0), "wing", face="wing")
    # брюшко (дышит)
    b("abdomen", "body", (0, 16, 3))
    for k, (w, h, z, d, y) in enumerate(((22, 9, 3, 6, 10), (21, 9, 9, 6, 9.5), (19, 8, 15, 5, 10), (15, 6, 20, 4, 11),
                                         (9, 4, 24, 2.5, 12))):
        c("abdomen", (-w / 2, y, z), (w, h, d), "belly", face="belly")
    # голова: низкая и широкая, рог, жвала, глаза, усики
    b("head", "body", (0, 15, -13))
    c("head", (-7.5, 8.5, -21), (15, 8.5, 9), "chitin", face="head")
    c("head", (-6, 8.5, -23), (12, 4.5, 2.2), "chitin", face="clypeus")
    for sx in (-1, 1):
        c("head", (sx * 7.4 - 0.9, 12.2, -19.5), (1.8, 2.6, 3.2), "eye", face="eye")
    # рог‑таран: 4 звена вверх‑вперёд, зазубрины по верху
    c("head", (-2.8, 12.4, -30.5), (5.6, 5.2, 10.0), "horn", face="horn", rot=(-14, 0, 0), pivot=(0, 15.0, -21.5))
    c("head", (-2.2, 15.4, -37.0), (4.4, 4.2, 7.6), "horn", face="horn", rot=(-36, 0, 0), pivot=(0, 17.4, -30.0))
    c("head", (-1.6, 20.0, -40.6), (3.2, 3.2, 6.0), "horn", face="horn", rot=(-58, 0, 0), pivot=(0, 21.6, -35.2))
    c("head", (-0.9, 24.4, -42.4), (1.8, 2.0, 4.6), "horn", face="horn_tip", rot=(-76, 0, 0), pivot=(0, 25.4, -38.4))
    for k, (y, z, r) in enumerate(((17.6, -25.0, -14), (18.6, -28.5, -14), (20.8, -31.8, -36), (23.4, -34.0, -36))):
        c("head", (-0.7, y, z), (1.4, 1.6, 1.4), "horn", face="tooth", rot=(r + 45, 0, 0), pivot=(0, y, z + 0.7))
    for side, sx in (("right", -1), ("left", 1)):
        m_ = "mand_" + side
        b(m_, "head", (sx * 4.0, 10.0, -22.0))
        c(m_, (sx * 4.0 - 1.1, 8.8, -27.0), (2.2, 2.0, 5.2), "horn", face="mand")
        c(m_, (sx * 4.0 - 0.8 - sx * 1.2, 9.0, -28.6), (1.6, 1.6, 2.4), "horn", face="mand_tip",
          rot=(0, -sx * 35, 0), pivot=(sx * 4.0, 9.8, -27.0))
        a = "ant_" + side
        b(a, "head", (sx * 6.2, 14.6, -21.0))
        c(a, (sx * 6.2 - 0.4, 9.6, -21.4), (0.8, 5.0, 0.8), "chitin", face="ant")
        c(a, (sx * 6.2 - 0.9, 7.4, -21.9), (1.8, 2.4, 1.8), "chitin", face="ant_club")
    legs(M, "body", LEGS, FEMUR, TIBIA, TARSUS, (10.0, 3.6, 2.6, 1.6), 13.0)
    M.pack()
    return M


MITE_LEGS = [("l1", 1, -2.0), ("l2", 1, 0.4), ("l3", 1, 2.8), ("r1", -1, -2.0), ("r2", -1, 0.4), ("r3", -1, 2.8)]


def build_mite():
    """Маленький жук ≈0.9×0.5 блока: острые жвала, без большого рога, маленькие надкрылья, светящиеся глазки."""
    M = Model("beetle_mite", 64, 64)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("body", "root", (0, 4.5, 0))
    c("body", (-3.4, 3.2, -4.5), (6.8, 3.4, 4.6), "chitin", face="shield")
    c("body", (-2.6, 6.6, -4.0), (5.2, 0.8, 3.6), "chitin", face="shield_top")
    c("body", (-3.6, 3.0, -4.9), (1.0, 1.0, 1.0), "horn", face="spike")
    c("body", (2.6, 3.0, -4.9), (1.0, 1.0, 1.0), "horn", face="spike")
    for side, sx in (("right", -1), ("left", 1)):
        e = "elytra_" + side
        b(e, "body", (sx * 0.2, 6.4, 0.0))
        x0 = -3.4 if sx < 0 else 0.1
        c(e, (x0, 3.0, 0.0), (3.3, 3.6, 6.0), "elytra", face="elytra")
        c(e, (x0 + (0.4 if sx < 0 else 0.0), 3.4, 6.0), (2.9, 2.4, 0.9), "elytra", face="elytra_tip")
    b("abdomen", "body", (0, 4.5, 0.0))
    c("abdomen", (-2.8, 2.6, 0.0), (5.6, 2.0, 6.4), "belly", face="belly")
    b("head", "body", (0, 4.6, -4.5))
    c("head", (-2.4, 2.8, -7.2), (4.8, 2.8, 2.8), "chitin", face="head")
    for sx in (-1, 1):
        c("head", (sx * 2.4 - 0.35, 4.1, -6.6), (0.7, 0.9, 1.1), "eye", face="eye")
    for side, sx in (("right", -1), ("left", 1)):
        m_ = "mand_" + side
        b(m_, "head", (sx * 1.3, 3.4, -7.0))
        c(m_, (sx * 1.3 - 0.5, 3.0, -9.4), (1.0, 0.9, 2.6), "horn", face="mand")
        c(m_, (sx * 1.3 - 0.4 - sx * 0.5, 3.1, -10.2), (0.8, 0.7, 1.2), "horn", face="mand_tip",
          rot=(0, -sx * 40, 0), pivot=(sx * 1.3, 3.5, -9.4))
        a = "ant_" + side
        b(a, "head", (sx * 1.8, 5.2, -7.0))
        c(a, (sx * 1.8 - 0.25, 2.6, -7.25), (0.5, 2.6, 0.5), "chitin", face="ant")
    legs(M, "body", MITE_LEGS, 2.6, 3.0, 1.6, (3.0, 1.0, 0.8, 0.6), 3.6, spikes=False)
    M.pack()
    return M


def build_egg():
    """Кожистое яйцо с хитиновыми пластинами (пульсирует)."""
    M = Model("beetle_egg", 64, 128)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("egg", "root", (0, 0, 0))
    for y0, h, w in ((0, 2, 7), (2, 4, 10), (6, 4, 11), (10, 3, 9.5), (13, 2, 6.5)):
        octo(M, "egg", 0, 0, y0, h, w, "egg", face="egg")
    b("plates", "egg", (0, 0, 0))
    for k in range(5):
        a = k * 72
        r = 5.2
        x, z = math.sin(math.radians(a)) * r, -math.cos(math.radians(a)) * r
        c("plates", (x - 1.8, 5.0, z - 0.6), (3.6, 6.0, 1.2), "elytra", face="plate", rot=(0, -a, 0), pivot=(x, 5.0, z))
    M.pack()
    return M


# =====================================================================
# 2. Текстуры
# =====================================================================
def paint(M, seed):
    PT = Painter(M.tex_w, M.tex_h, seed=seed)
    rnd = PT.rnd

    def lit(f, col, k=1.0, n=0.05):
        return mul(col, FACE_LIGHT[f] * k * PT.noise(n))

    def chitin(f, i, j, w, h, base=CH, sheen=0.0):
        """Хитин: поры, царапины, светлее по рёбрам граней (металлический блик), потёки."""
        r = rnd.random()
        col = base
        if (i * 7 + j * 13) % 11 == 0:
            col = mix(col, CH_DD, 0.6)                 # поры
        if (i + j) % 9 == 0 and r < 0.5:
            col = mix(col, CH_L, 0.6)                  # царапины по диагонали
        if r < 0.04:
            col = mix(col, RUST, 0.7)                  # ржавые потёки
        if f not in ("bottom",) and (j == 0 or i == 0 or i == w - 1):
            col = mix(col, CH_HI, 0.30)                # блик по рёбрам
        if sheen:
            col = mix(col, BRONZE, sheen * (0.5 + 0.5 * math.sin((i + j * 0.5) * 0.7)))
        if f == "bottom":
            col = mix(col, CH_DD, 0.4)
        return lit(f, col)

    def px(mat, face, f, i, j, w, h):
        side = f not in ("top", "bottom")
        if mat == "eye":
            if f in ("left", "right", "front"):
                return ("glow", EYE_HI if (i, j) == (1, 1) else EYE)
            return lit(f, CH_D)
        if mat == "memb":
            return lit(f, MEMB if (i + j) % 3 else MEMB_D)
        if mat == "wing":
            if (i % 5 == 0) or (j % 7 == 0):
                return lit(f, mix(WING, CH_D, 0.5))   # жилки
            return lit(f, WING, n=0.08)
        if mat == "egg":
            if (i * 5 + j * 3) % 23 == 0:
                return ("glow", mix(EGG_GLOW, EGG, 0.7))   # тёплые прожилки (слабо)
            if (i + j * 2) % 7 == 0:
                return lit(f, EGG_D)
            return lit(f, mix(EGG, EGG_D, 0.3 * rnd.random()))
        if mat == "horn":
            col = HORN
            if face in ("horn_tip", "claw", "mand_tip", "tooth"):
                col = mix(HORN_TIP, HORN, 0.25 * rnd.random())      # потёртый светлый кончик
            elif face == "horn" and f == "top" and j % 3 == 0:
                col = mix(HORN, HORN_TIP, 0.35)
            elif face == "horn" and side and (i * 3 + j * 5) % 29 == 0:
                return ("glow", CRACK)                              # тонкие трещины‑прожилки (очень слабые)
            elif face == "spike":
                col = mix(HORN, HORN_TIP, 0.25 if j == 0 else 0.0)
            return lit(f, col)
        if mat == "belly":
            # сегменты брюшка: светлее по швам, снизу темнее
            col = CH_D if f == "bottom" else CH
            if j == 0 or j == h - 1:
                col = mix(col, MEMB, 0.6)                           # перепонка между сегментами
            return chitin(f, i, j, w, h, base=col)
        if mat == "elytra":
            # продольные борозды, тёмно‑бронзовый перелив
            col = CH
            if f in ("top", "left", "right", "back", "front") and i % 3 == 1:
                col = CH_D                                          # борозды
            sheen = 0.35 if f == "top" else 0.25
            if face == "plate":
                sheen = 0.2
            return chitin(f, i, j, w, h, base=col, sheen=sheen)
        if mat == "chitin":
            col = CH
            if face in ("shield", "shield_top") and f == "top":
                return chitin(f, i, j, w, h, sheen=0.15)
            if face == "crest":
                col = mix(CH, CH_HI, 0.15)
            if face in ("femur", "tibia", "tarsus") and side and j == h - 1:
                col = MEMB_D                                        # сустав
            if face == "head" and f == "front" and j > h - 3:
                col = CH_D
            if face == "ant_club":
                col = RUST
            return chitin(f, i, j, w, h, base=col)
        return (255, 0, 255)

    for cube in M.cubes():
        for fname, rect in faces(cube).items():
            PT.fill(rect, lambda i, j, w, h, f=fname, cu=cube: px(cu["mat"], cu.get("face"), f, i, j, w, h))
    return PT


def save_textures(M, PT):
    PT.img.save(os.path.join(ROOT, "textures", "entity", M.name + ".png"))
    PT.glow.save(os.path.join(ROOT, "textures", "entity", M.name + "_glowmask.png"))


# =====================================================================
# 3. Позы
# =====================================================================
# Параметры (Java: +y вниз, −z вперёд). f_<нога> — конец голени («щиколотка») в системе модели, земля y = 24.
# elytra 0..1 — раскрытие надкрылий, wings 0..1 — крылья, mand 0..1 — жвала разведены, breath — дыхание брюшка.
class Rig:
    def __init__(self, M, specs, femur, tibia, tarsus, rest, foot_y, scale_k=1.0, lower=0.0):
        self.lower = lower
        self.M, self.specs = M, specs
        self.femur, self.tibia, self.tarsus = femur, tibia, tarsus
        self.rest = rest            # нога → (x, z) покоя щиколотки
        self.foot_y = foot_y
        self.k = scale_k

    def default(self):
        d = dict(root=(0, 0, 0), root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0, 0), head=(0, 0, 0),
                 abdomen=(0, 0, 0), elytra=0.0, wings=0.0, mand=0.15, breath=0.0, ant=(0, 0, 0))
        for name, s, z in self.specs:
            x, zz = self.rest[name]
            d["f_" + name] = (x, self.foot_y, zz)
        return d


def resolve(rig, pr):
    M = rig.M
    bp = pr["body_pos"]
    pose = {"root": {"rot": pr["root"], "pos": pr["root_pos"]},
            "body": {"rot": pr["body"], "pos": (bp[0], bp[1] + rig.lower, bp[2])},
            "head": {"rot": pr["head"]}}
    b = pr["breath"]
    pose["abdomen"] = {"rot": pr["abdomen"], "scale": (1 + 0.03 * b, 1 + 0.05 * b, 1 + 0.02 * b)}
    e = pr["elytra"]
    w = pr["wings"]
    for side, sx in (("right", -1), ("left", 1)):
        # надкрылье: вверх‑наружу и чуть назад
        pose["elytra_" + side] = {"rot": (-20 * e, sx * 22 * e, sx * 55 * e)}
        if "wing_" + side in M.by_name:
            pose["wing_" + side] = {"rot": (-10 * w, sx * 50 * w, sx * 22 * w)}
        m = pr["mand"]
        pose["mand_" + side] = {"rot": (0, sx * (-6 + 34 * m), 0)}
        ax, ay, az = pr["ant"]
        pose["ant_" + side] = {"rot": (-72 + ax, sx * (28 + ay), sx * (12 + az))}
    Rroot = M.chain_R("root", pose)
    Rb = M.chain_R("body", pose)
    for name, s, z in rig.specs:
        tgt = M.world("root", [pr["f_" + name][0], pr["f_" + name][1] - 24, pr["f_" + name][2]], pose)
        pole = mv(Rb, [s * 0.35, -1.0, 0.0])            # колено вверх‑наружу
        ng.limb_ik(M, pose, "leg_" + name, "shin_" + name, tgt, pole, rig.femur, rig.tibia, s)
        # лапка: наружу и вниз к земле
        dvec = norm(mv(Rroot, [s * 0.75, 0.62, -0.15 if name.endswith("1") else (0.2 if name.endswith("3") else 0.0)]))
        M.set_world_rot(pose, "foot_" + name, ng.frame_X(dvec, mv(Rroot, [0, 0, 1.0])))
    return pose


def tripod(rig, pr, w, stride, lift):
    """Фаза w: группа A в фазе 0, группа B в фазе π. На земле нога едет назад, в воздухе — вперёд и вверх."""
    for name, s, z in rig.specs:
        ph = w + (0.0 if name in TRIPOD_A else math.pi)
        x, zz = rig.rest[name]
        up = max(0.0, math.sin(ph))
        pr["f_" + name] = (x + s * 0.4 * up * rig.k, rig.foot_y - lift * up, zz + stride * math.cos(ph))
    return pr


def K(rig, keys, t):
    full, cur = [], rig.default()
    for tk, delta, e in keys:
        cur = dict(cur)
        cur.update(delta)
        full.append((tk, cur, e))
    if t <= full[0][0]:
        return dict(full[0][1])
    for k in range(1, len(full)):
        t1, p1, e = full[k]
        if t <= t1:
            t0, p0, _ = full[k - 1]
            f = ng.EASES[e]((t - t0) / (t1 - t0)) if t1 > t0 else 1.0
            return {n: ng.lerp_val(p0[n], p1[n], f) for n in p1}
    return dict(full[-1][1])


def L(rig, keys, t, lag=None):
    """Ключи с запаздыванием: голова чуть раньше, надкрылья и жвала — позже."""
    lag = lag or {"head": -0.03, "elytra": 0.08, "wings": 0.12, "abdomen": 0.10, "mand": 0.04}
    pr = K(rig, keys, t)
    for n, l_ in lag.items():
        pr[n] = K(rig, keys, max(0.0, t - l_))[n]
    return pr


# =====================================================================
# 4. Анимации осадного жука
# =====================================================================
BIG_REST = {"l1": (27.0, -19.0), "l2": (30.0, -1.0), "l3": (28.0, 17.0),
            "r1": (-27.0, -19.0), "r2": (-30.0, -1.0), "r3": (-28.0, 17.0)}
BIG = None   # Rig — после постройки модели


def with_feet(pr, src):
    for k in src:
        if k.startswith("f_"):
            pr[k] = src[k]
    return pr


def b_idle(t):
    T_ = 3.0
    w = 2 * math.pi * t / T_
    pr = BIG.default()
    pr["breath"] = math.sin(w * 2)
    pr["body_pos"] = (0, 0.3 * math.sin(w * 2), 0)
    pr["body"] = (0.8 * math.sin(w), 1.5 * math.sin(w * 0.5 + 0.4), 0.6 * math.sin(w + 1))
    pr["head"] = (2 * math.sin(w + 0.8), 5 * math.sin(w), 0)
    pr["mand"] = 0.15 + 0.25 * max(0.0, math.sin(w * 3)) ** 4
    pr["ant"] = (10 * math.sin(w * 2 + 0.3), 8 * math.sin(w * 3), 4 * math.sin(w * 4))
    pr["elytra"] = 0.05 + 0.05 * max(0.0, math.sin(w + 2.0)) ** 3
    # переступает: в 0.6–1.0 с — правая средняя, в 1.9–2.3 с — левая передняя
    for name, a, b_ in (("r2", 0.6, 1.0), ("l1", 1.9, 2.3)):
        if a <= t <= b_:
            u = (t - a) / (b_ - a)
            x, y, z = pr["f_" + name]
            pr["f_" + name] = (x, y - 3.5 * math.sin(math.pi * u), z - 1.2 * math.sin(math.pi * u))
    return pr


def b_walk(t, T_=1.6, stride=5.0, lift=4.0, sway=2.0):
    w = 2 * math.pi * t / T_
    pr = BIG.default()
    tripod(BIG, pr, w, stride, lift)
    pr["body_pos"] = (0, 0.6 * abs(math.sin(w)), 0)                 # проседает на каждом шаге
    pr["body"] = (0.8 * math.sin(2 * w), 1.5 * math.sin(w), sway * math.sin(w))
    pr["head"] = (1.5 * math.sin(2 * w + 0.6), -5 * math.sin(w + 0.4), 0)
    pr["breath"] = 0.5 * math.sin(2 * w)
    pr["ant"] = (8 * math.sin(2 * w + 1.0), 6 * math.sin(w), 0)
    return pr


def b_run(t):
    pr = b_walk(t, T_=0.8, stride=7.5, lift=5.5, sway=3.0)
    pr["body"] = (pr["body"][0] + 4, pr["body"][1], pr["body"][2])
    pr["head"] = (pr["head"][0] + 8, pr["head"][1], 0)
    pr["ant"] = (-25 + pr["ant"][0], pr["ant"][1], 0)
    return pr


def run_feet(t, T_=0.8):
    pr = BIG.default()
    tripod(BIG, pr, 2 * math.pi * t / T_, 7.5, 5.5)
    return pr


# таран: опускает голову (0–0.5), разгон (0.5–1.1, ноги бегут), удар рогом (1.1), отдача, встряхивает головой
RAM_HIT = 1.1
RAM_T = 2.3


def b_ram(t):
    keys = [(0.0, {}, "lin"),
            (0.45, dict(body=(10, 0, 0), body_pos=(0, 1.5, 0), head=(16, 0, 0), root_pos=(0, 0, 1.5), mand=0.6,
                        ant=(-30, 0, 0)), "soft"),
            (1.0, dict(body=(12, 0, 0), body_pos=(0, 1.0, 0), head=(20, 0, 0), root_pos=(0, 0, -2.0), mand=0.3,
                       ant=(-40, 10, 0)), "in"),
            (RAM_HIT, dict(body=(4, 0, 0), body_pos=(0, 0.2, 0), head=(-10, 0, 0), root_pos=(0, 0, -4.0), mand=0.1,
                           elytra=0.25), "in3"),
            (1.35, dict(body=(-6, 0, 0), body_pos=(0, -0.6, 0), head=(-14, 0, 0), root_pos=(0, 0, 2.5), elytra=0.15,
                        ant=(20, -10, 0)), "out3"),
            (1.6, dict(body=(2, 0, 0), head=(4, 18, 6)), "soft"),
            (1.8, dict(body=(2, 0, 0), head=(4, -16, -6)), "soft"),
            (2.0, dict(head=(2, 8, 3)), "soft"),
            (RAM_T, {}, "soft")]
    pr = L(BIG, keys, t)
    # ноги: в разгоне — бег, на ударе упираются
    run_k = ng.smooth01(0.4, 0.6, t) * (1 - ng.smooth01(1.05, 1.25, t))
    rp = run_feet(t)
    base = BIG.default()
    for k in rp:
        if k.startswith("f_"):
            pr[k] = tuple(lerp(base[k][i], rp[k][i], run_k) for i in range(3))
    return pr


# «Рогом»: замах головой вниз‑назад (0–0.4), мощный подброс вверх‑вперёд (0.5), надкрылья приоткрываются
HORN_HIT = 0.5
HORN_T = 1.5


def b_horn(t):
    keys = [(0.0, {}, "lin"),
            (0.40, dict(body=(8, 0, 0), body_pos=(0, 1.8, 0), head=(26, 0, 0), root_pos=(0, 0, 1.5), mand=0.5,
                        ant=(-20, 0, 0)), "soft"),
            (HORN_HIT, dict(body=(-12, 0, 0), body_pos=(0, -1.6, 0), head=(-34, 0, 0), root_pos=(0, 0, -2.5),
                            mand=0.2, elytra=0.55, wings=0.4, ant=(25, 0, 0)), "in3"),
            (0.75, dict(body=(-8, 0, 0), body_pos=(0, -0.8, 0), head=(-22, 0, 0), elytra=0.45, wings=0.3), "out"),
            (HORN_T, {}, "soft")]
    pr = L(BIG, keys, t)
    # передние ноги отталкиваются, задние упираются
    for name, dz in (("l1", -2.5), ("r1", -2.5), ("l3", 2.0), ("r3", 2.0)):
        x, y, z = pr["f_" + name]
        k = ng.smooth01(0.3, 0.5, t) * (1 - ng.smooth01(0.9, 1.3, t))
        pr["f_" + name] = (x, y, z + dz * k)
    return pr


BITE_HIT = 0.35
BITE_T = 0.8


def b_bite(t):
    keys = [(0.0, {}, "lin"), (0.22, dict(head=(10, 0, 0), mand=1.0, body=(-2, 0, 0)), "soft"),
            (BITE_HIT, dict(head=(4, 0, 0), mand=0.0, body=(4, 0, 0), root_pos=(0, 0, -2.0)), "in3"),
            (0.5, dict(head=(6, 0, 0), mand=0.1, root_pos=(0, 0, -1.0)), "out"), (BITE_T, {}, "soft")]
    return L(BIG, keys, t)


# кладка: приседает задом, брюшко сокращается, яйцо выскальзывает (1.0 с)
LAY_DROP = 1.0
LAY_T = 1.7


def b_lay(t):
    keys = [(0.0, {}, "lin"), (0.4, dict(body=(-8, 0, 0), body_pos=(0, 2.5, 0), abdomen=(10, 0, 0), elytra=0.2), "soft"),
            (0.7, dict(body=(-9, 0, 0), body_pos=(0, 2.8, 0), abdomen=(14, 0, 0), breath=-1.0, elytra=0.25), "soft"),
            (0.85, dict(body=(-9, 0, 0), body_pos=(0, 2.8, 0), abdomen=(16, 0, 0), breath=1.0, elytra=0.25), "soft"),
            (LAY_DROP, dict(body=(-8, 0, 0), body_pos=(0, 2.6, 0), abdomen=(18, 0, 0), breath=-1.0, elytra=0.25), "in"),
            (1.25, dict(body=(-4, 0, 0), body_pos=(0, 1.0, 0), abdomen=(6, 0, 0), breath=0.3, elytra=0.1), "out"),
            (LAY_T, {}, "soft")]
    pr = L(BIG, keys, t)
    for name in ("l3", "r3"):
        x, y, z = pr["f_" + name]
        k = ng.smooth01(0.1, 0.4, t) * (1 - ng.smooth01(1.2, 1.6, t))
        pr["f_" + name] = (x * (1 + 0.08 * k), y, z + 3.0 * k)
    return pr


HURT_T = 0.5


def b_hurt(t):
    keys = [(0.0, {}, "lin"), (0.08, dict(body=(-4, 6, 4), head=(-10, -8, 0), body_pos=(0, -0.8, 0), mand=0.6,
                                          elytra=0.12), "out3"),
            (HURT_T, {}, "soft")]
    return L(BIG, keys, t)


BIG_CLIPS = [
    ("idle", b_idle, 3.0, 20, True),
    ("walk", b_walk, 1.6, 30, True),
    ("run", b_run, 0.8, 30, True),
    ("ram", b_ram, RAM_T, 30, False),
    ("horn", b_horn, HORN_T, 60, False),
    ("bite", b_bite, BITE_T, 60, False),
    ("lay", b_lay, LAY_T, 30, False),
    ("hurt", b_hurt, HURT_T, 30, False),
]

# =====================================================================
# 5. Маленький жук
# =====================================================================
MITE_REST = {"l1": (6.6, -5.2), "l2": (7.4, 1.1), "l3": (6.8, 5.4),
             "r1": (-6.6, -5.2), "r2": (-7.4, 1.1), "r3": (-6.8, 5.4)}
MITE = None


def m_idle(t):
    w = 2 * math.pi * t / 2.0
    pr = MITE.default()
    pr["breath"] = math.sin(w * 2)
    pr["body"] = (1.2 * math.sin(w), 3 * math.sin(w * 0.5), 0)
    pr["head"] = (2 * math.sin(w + 1), 8 * math.sin(w), 0)
    pr["mand"] = 0.2 + 0.5 * max(0.0, math.sin(w * 2)) ** 6
    pr["ant"] = (12 * math.sin(w * 3), 10 * math.sin(w * 2), 0)
    return pr


def m_run(t):
    T_ = 0.4
    w = 2 * math.pi * t / T_
    pr = MITE.default()
    tripod(MITE, pr, w, 1.8, 1.3)
    pr["body_pos"] = (0, 0.2 * abs(math.sin(w)), 0)
    pr["body"] = (3, 2 * math.sin(w), 3 * math.sin(w))
    pr["head"] = (4, -4 * math.sin(w), 0)
    pr["ant"] = (-25 + 6 * math.sin(2 * w), 0, 0)
    pr["mand"] = 0.3
    return pr


MITE_BITE_HIT = 0.3
MITE_BITE_T = 0.7


def m_bite(t):
    keys = [(0.0, {}, "lin"), (0.18, dict(body=(-10, 0, 0), body_pos=(0, -0.5, 1.0), mand=1.0, head=(-8, 0, 0)), "soft"),
            (MITE_BITE_HIT, dict(body=(8, 0, 0), root_pos=(0, 0, -3.0), mand=0.0, head=(10, 0, 0)), "in3"),
            (0.45, dict(body=(4, 0, 0), root_pos=(0, 0, -2.0), mand=0.1), "out"), (MITE_BITE_T, {}, "soft")]
    return L(MITE, keys, t)


MITE_CLIPS = [("idle", m_idle, 2.0, 20, True), ("run", m_run, 0.4, 30, True), ("bite", m_bite, MITE_BITE_T, 60, False)]


def egg_idle(t):
    """Пульсация: двойной удар как у сердца."""
    T_ = 1.6
    x = (t % T_) / T_
    beat = math.exp(-((x - 0.1) / 0.05) ** 2) + 0.6 * math.exp(-((x - 0.28) / 0.05) ** 2)
    s = 1 + 0.06 * beat
    return {"egg": {"scale": (s, 1 + 0.04 * beat, s)}, "plates": {"scale": (1 + 0.02 * beat,) * 3}}


# =====================================================================
# 6. Запекание, предпросмотр
# =====================================================================
def bake_rig(rig, clips):
    out = {}
    bones = [b_["name"] for b_ in rig.M.bones]
    for name, fn, ln, fps, loop in clips:
        ng.PREV_PN.clear()
        for _ in range(3):                    # «прогрев» памяти сгиба коленей — первый кадр как остальные
            resolve(rig, fn(0.0))
        a = bake(lambda t, fn=fn: resolve(rig, fn(t)), ln, fps, loop, bones, unwrap=True)
        if not loop:
            ng.anchor_end(a)
        out["animation.%s.%s" % (rig.M.name, name)] = a
        print("  %-14s %-6s %.2f с" % (rig.M.name, name, ln))
    write_anims(ROOT, rig.M.name, out)


def preview(mob, clip, times):
    import subprocess
    env = dict(os.environ)
    env.setdefault("PREVIEW_YAW", os.environ.get("YAW", "0.7"))
    env.setdefault("PREVIEW_SCALE", os.environ.get("SCALE", "4" if mob == "siege_beetle" else "18"))
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "mob_preview.py"), mob, clip,
                    ",".join(str(x) for x in times)], env=env, check=True)


def main():
    global BIG, MITE
    args = sys.argv[1:]
    if args and args[0] == "--preview":
        preview(args[1], args[2], [float(x) for x in args[3].split(",")])
        return
    Mb, Mm, Me = build_beetle(), build_mite(), build_egg()
    for M_, seed in ((Mb, 61), (Mm, 67), (Me, 71)):
        M_.write_geo(ROOT)
        save_textures(M_, paint(M_, seed))
        M_.finalize()
    print("кубов:", len(Mb.cubes()), len(Mm.cubes()), len(Me.cubes()))
    BIG = Rig(Mb, LEGS, FEMUR, TIBIA, TARSUS, BIG_REST, 20.6, lower=3.0)
    MITE = Rig(Mm, MITE_LEGS, 2.6, 3.0, 1.6, MITE_REST, 23.0, scale_k=0.3)
    bake_rig(BIG, BIG_CLIPS)
    bake_rig(MITE, MITE_CLIPS)
    a = bake(egg_idle, 1.6, 20, True, ["egg", "plates"])
    write_anims(ROOT, "beetle_egg", {"animation.beetle_egg.idle": a})


if __name__ == "__main__":
    main()
