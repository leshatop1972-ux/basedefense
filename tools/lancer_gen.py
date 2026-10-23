"""
Генератор босса «Оборванный улан» (tattered_lancer): модель Bedrock, анимации, текстура, маска.

Запуск:  python tools/lancer_gen.py
Проверка без игры:  python tools/mob_preview.py tattered_lancer thrust 0.2,0.35,1.0

Оригинальный персонаж: худой дуэлянт‑копейщик в длинном тёмно‑бирюзовом сюртуке с золотыми
шнурами, малиновый кушак, полуплащ на левом плече, широкополая шляпа с белым пером,
высокие сапоги со шпорами и длинное турнирное копьё с вымплатом и вымпелом.

Анимации собираются из ключевых поз (keyframes с плавностью) и обратной кинематики:
правая рука держит копьё за рукоять, копьё направляется вектором, левая рука при «хвате»
ложится на древко (mobgen.Model.two_bone). Полы сюртука следуют за ногами.
"""
import math
import os

from mobgen import (FACE_LIGHT, Model, Painter, add, bake, clamp, ease, ease_in, ease_out, faces, lerp, lerp3, look_R,
                    mix, mm, mul, mv, norm, rx_m, ry_m, rz_m, scl, sub, write_anims)

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
NAME = "tattered_lancer"
M = Model(NAME, 128, 128)
bone, cube = M.bone, M.cube
UPPER, FORE = 5.5, 5.5

# =====================================================================
# 1. Модель (Bedrock: y вверх, лицо в −z, правая сторона персонажа — −x)
# =====================================================================
bone("root", None, (0, 0, 0))
bone("body", "root", (0, 12, 0))
cube("body", (-4, 9.5, -2), (8, 3, 4), "trousers", face="hips")
cube("body", (-4.3, 11.2, -2.3), (8.6, 2.2, 4.6), "sash", face="sash")
cube("body", (3.6, 6.5, -2.6), (1.4, 5, 0.6), "sash", face="sash_tail", rot=(0, 0, -8), pivot=(4.3, 11.5, -2.3))
cube("body", (-1, 11.4, -2.7), (2, 1.8, 0.5), "gold")

# полы сюртука (развеваются, следуют за ногами)
bone("coat_fl", "body", (2.1, 12, -2.1))
cube("coat_fl", (0.2, 2.5, -2.55), (4, 9.5, 0.7), "coat", face="coat_front")
bone("coat_fr", "body", (-2.1, 12, -2.1))
cube("coat_fr", (-4.2, 2.5, -2.55), (4, 9.5, 0.7), "coat", face="coat_front")
bone("coat_bl", "body", (2.1, 12, 2.1))
cube("coat_bl", (0.05, 1.5, 1.85), (4.2, 10.5, 0.7), "coat", face="coat_back")
bone("coat_br", "body", (-2.1, 12, 2.1))
cube("coat_br", (-4.25, 1.5, 1.85), (4.2, 10.5, 0.7), "coat", face="coat_back")

# ноги: бедро → голень → стопа (высокие сапоги, шпоры)
bone("right_leg", "body", (-2, 10, 0))
cube("right_leg", (-3.8, 5.5, -1.8), (3.6, 4.5, 3.6), "trousers", face="thigh")
bone("right_shin", "right_leg", (-2, 5.5, 0))
cube("right_shin", (-3.85, 0.8, -1.85), (3.7, 4.7, 3.7), "boot", face="boot")
cube("right_shin", (-4.2, 4.3, -2.2), (4.4, 1.7, 4.4), "boot", face="cuff")
bone("right_foot", "right_shin", (-2, 0.8, -0.2))
cube("right_foot", (-3.8, -0.2, -3.7), (3.6, 1.4, 4.7), "boot", face="foot")
cube("right_foot", (-2.3, 0.1, 1.0), (0.6, 0.6, 1.6), "gold")
M.mirror_x("right_leg", "left_leg")
M.mirror_x("right_shin", "left_shin", parent="left_leg")
M.mirror_x("right_foot", "left_foot", parent="left_shin")

# корпус
bone("torso", "body", (0, 12, 0))
cube("torso", (-3.6, 12, -2), (7.2, 5, 4), "coat", face="waist")
cube("torso", (-4, 17, -2.2), (8, 5.5, 4.4), "coat", face="chest")
cube("torso", (-1.5, 19.8, -2.75), (3, 2.7, 0.6), "shirt", face="jabot")
cube("torso", (-4.3, 22, -2.5), (8.6, 2, 5), "coat", face="collar", inflate=0.1)
cube("torso", (-7.6, 21.6, -2.1), (3.8, 1.3, 4.2), "gold", face="epaulette")
# полуплащ на левом плече
bone("cape", "torso", (1.5, 23.5, 2.3))
cube("cape", (-1.6, 12, 2.3), (7.2, 11.5, 0.8), "coat", face="cape")
cube("torso", (2.3, 21.6, -2.8), (5.2, 2.3, 5.6), "fur", inflate=0.05)

# руки
bone("right_arm", "torso", (-5, 22, 0))
cube("right_arm", (-6.6, 16.5, -1.6), (3.2, 5.5, 3.2), "coat", face="sleeve")
bone("right_fore", "right_arm", (-5, 16.5, 0))
cube("right_fore", (-6.6, 11, -1.6), (3.2, 5.5, 3.2), "coat", face="sleeve")
cube("right_fore", (-6.9, 11.4, -1.9), (3.8, 2, 3.8), "coat", face="cuff_coat")
cube("right_fore", (-6.4, 10.1, -1.4), (2.8, 1.4, 2.8), "glove")
bone("left_arm", "torso", (5, 22, 0))
cube("left_arm", (3.4, 16.5, -1.6), (3.2, 5.5, 3.2), "coat", face="sleeve")
bone("left_fore", "left_arm", (5, 16.5, 0))
cube("left_fore", (3.4, 11, -1.6), (3.2, 5.5, 3.2), "coat", face="sleeve")
cube("left_fore", (3.1, 11.4, -1.9), (3.8, 2, 3.8), "coat", face="cuff_coat")
cube("left_fore", (3.6, 10.1, -1.4), (2.8, 1.4, 2.8), "glove")

# копьё в правой руке: ось вдоль −z (остриё вперёд), рукоять в кулаке
G = (-5.0, 10.8, 0.0)
bone("lance", "right_fore", G)
gx, gy, gz = G
cube("lance", (gx - 0.55, gy - 0.55, gz + 0.5), (1.1, 1.1, 7), "wood", face="butt")
cube("lance", (gx - 0.8, gy - 0.8, gz + 7.5), (1.6, 1.6, 1.2), "gold")
cube("lance", (gx - 0.65, gy - 0.65, gz - 2.0), (1.3, 1.3, 2.5), "grip")
cube("lance", (gx - 2.6, gy - 2.6, gz - 2.8), (5.2, 5.2, 0.8), "steel", face="vamp1")
cube("lance", (gx - 2.0, gy - 2.0, gz - 3.6), (4, 4, 0.8), "steel", face="vamp2")
cube("lance", (gx - 1.4, gy - 1.4, gz - 4.4), (2.8, 2.8, 0.8), "steel", face="vamp3")
cube("lance", (gx - 0.8, gy - 0.8, gz - 14.4), (1.6, 1.6, 10), "wood", face="shaft")
cube("lance", (gx - 0.62, gy - 0.62, gz - 24.4), (1.24, 1.24, 10), "wood", face="shaft")
cube("lance", (gx - 0.45, gy - 0.45, gz - 31.4), (0.9, 0.9, 7), "wood", face="shaft")
cube("lance", (gx - 0.3, gy - 0.3, gz - 34.2), (0.6, 0.6, 2.8), "steel", face="tip")
for z in (-8.0, -13.0, -18.5, -23.5):
    cube("lance", (gx - 0.9, gy - 0.9, gz + z), (1.8, 1.8, 0.6), "gold")
LANCE_TIP = 34.2
bone("pennant", "lance", (gx, gy - 0.4, gz - 27.5))
cube("pennant", (gx - 0.1, gy - 4.2, gz - 27.5), (0.2, 3.8, 5.5), "pennant", face="flag1")
bone("pennant_tip", "pennant", (gx, gy - 0.4, gz - 22.0))
cube("pennant_tip", (gx - 0.1, gy - 3.6, gz - 22.0), (0.2, 3.2, 4.5), "pennant", face="flag2")

# голова, волосы, шляпа с пером
bone("head", "torso", (0, 24, 0))
cube("head", (-4, 24, -4), (8, 8, 8), "head", face="face")
cube("head", (-4, 24, -4), (8, 8, 8), "hair", inflate=0.3, face="hair_layer")
cube("head", (-4.6, 25.5, -1.5), (1.2, 4.5, 4), "hair", face="sideburn")
cube("head", (3.4, 25.5, -1.5), (1.2, 4.5, 4), "hair", face="sideburn")
cube("head", (-3.5, 23.0, 3.2), (7, 3, 1.6), "hair", face="nape")
bone("hat", "head", (0, 31.2, 0))
cube("hat", (-7, 31.2, -6.5), (14, 0.8, 13), "hat", face="brim")
cube("hat", (-4.2, 31.8, -4.2), (8.4, 4, 8.4), "hat", face="crown")
cube("hat", (-4.4, 31.9, -4.4), (8.8, 1.1, 8.8), "sash", face="band")
cube("hat", (3.9, 32.2, -1.2), (1, 1.6, 1.6), "gold")
bone("plume", "hat", (4.3, 33.5, -0.4))
cube("plume", (3.5, 33.2, -0.6), (1.6, 1.6, 6.5), "feather", face="plume1", rot=(22, 0, 0), pivot=(4.3, 33.5, -0.4))
bone("plume_tip", "plume", (4.3, 36.0, 5.6))
cube("plume_tip", (3.6, 35.4, 5.4), (1.4, 1.3, 6), "feather", face="plume2", rot=(-12, 0, 0), pivot=(4.3, 36.0, 5.6))

M.pack()
M.finalize()

# =====================================================================
# 2. Текстура
# =====================================================================
PT = Painter(M.tex_w, M.tex_h, seed=31)
R = PT.rnd

TEAL = (38, 80, 80)                 # выцветший бирюзовый
TEAL_L = (62, 112, 108)
TEAL_D = (20, 46, 50)
GOLD = (182, 150, 78)               # потускневшее золото
GOLD_L = (214, 188, 112)
GOLD_D = (122, 96, 48)
CRIMSON = (124, 30, 40)
CRIMSON_L = (156, 50, 56)
CRIMSON_D = (78, 18, 26)
BLACK = (28, 30, 32)
BLACK_L = (48, 50, 52)
BROWN = (80, 54, 36)
BROWN_L = (108, 76, 50)
BROWN_D = (50, 34, 22)
SHIRT = (206, 200, 176)             # пожелтевшее жабо
SKIN = (126, 150, 116)              # кожа мертвеца
SKIN_S = (98, 120, 90)
SKIN_D = (72, 90, 68)
BONE = (214, 208, 184)
ROT = (70, 46, 50)
HAIR = (60, 62, 60)
HAIR_L = (88, 92, 88)
HAIR_S = (196, 200, 196)            # седая прядь
EYE = (150, 228, 255)               # холодный мертвенный огонь
STEEL = (188, 194, 204)
STEEL_D = (120, 126, 140)
WOOD = (84, 52, 34)
WOOD_L = (112, 72, 46)
FEATHER = (226, 226, 220)
FUR = (62, 58, 54)


def light(fname, col, k=1.0):
    return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.03))


def side_face(fname):
    return fname not in ("top", "bottom")


def paint_cube(c):
    mat, face = c["mat"], c.get("face")
    F = faces(c)

    if face == "face":
        for fname, rect in F.items():
            if fname == "front":
                continue
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, HAIR if fn != "bottom" else SKIN_D))
        x0, y0, w, h = F["front"]
        # лицо мертвеца: тёмные впадины глаз с холодным огнём, провал носа, оскал, швы на щеке
        rows = [
            "HHHHHHHH",
            "HHHHHHHH",
            "HSSSSSSH",
            "HKKSSKKH",
            "HKESSEKH",
            "HSSNNScH",
            "HStDtDtH",
            "HSSjSjSH",
        ]
        pal = {"S": SKIN, "K": (34, 40, 36), "N": (52, 60, 50), "c": ROT, "t": BONE, "D": (30, 22, 24),
               "j": SKIN_D}
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "H":
                    PT.put(x0 + i, y0 + j, light("front", HAIR if (i + j) % 3 else HAIR_L))
                elif ch == "E":
                    PT.glow_put(x0 + i, y0 + j, EYE)
                else:
                    col = pal[ch]
                    if ch == "S" and j >= 5:
                        col = mix(SKIN, SKIN_S, 0.25)
                    PT.put(x0 + i, y0 + j, mul(col, PT.noise(0.02)))
        # шов через щёку и трупные пятна
        PT.put(x0 + 6, y0 + 5, (40, 34, 30))
        PT.put(x0 + 6, y0 + 3, ROT)
        PT.put(x0 + 1, y0 + 2, SKIN_S)
        return

    if mat == "hair":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if face == "hair_layer":
                    if fname == "front":
                        fringe = ["HHHHHHHH", "HH.HHHsH", "H......s", "........", "H.......", "........",
                                  "........", "........"]
                        ch = fringe[j][i]
                        if ch == ".":
                            return None
                        return light(fname, HAIR_S if ch == "s" else (HAIR_L if (i * 7) % 3 == 0 else HAIR))
                    if fname == "bottom":
                        return None if 0 < i < w - 1 and j < h - 1 else light(fname, HAIR)
                    if side_face(fname) and j == h - 1 and (i + len(fname)) % 2 == 0:
                        return None
                    if fname == "left" and i < 2:
                        return light(fname, HAIR_S)
                col = HAIR_L if (i * 7 + 3) % 4 == 0 else HAIR          # пряди вертикальные
                return light(fname, col, 1.0 - 0.1 * j / max(1, h))
            PT.fill(rect, fn)
        return

    if mat == "coat":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = TEAL
                if (i + j) % 2 == 0:
                    col = mul(col, 0.96)
                if face in ("coat_front", "coat_back", "cape") and side_face(fname):
                    outer = (fname == "front" and face == "coat_front") or (fname == "back" and face in ("coat_back", "cape"))
                    if not outer and fname in ("front", "back"):
                        return light(fname, CRIMSON_D)                 # подкладка
                    if j >= h - 1:
                        if (i + j) % 2 == 0 and face != "cape":
                            return None                                # обтрёпанный край
                        return light(fname, GOLD_D)
                    if j == h - 2:
                        return light(fname, GOLD)
                    if outer and i in (0, w - 1):
                        return light(fname, GOLD_D)
                torn = R.random()
                if face in ("coat_front", "coat_back", "cape") and side_face(fname) and torn < 0.05 and j < h - 2:
                    return None                                        # дыры в истлевшей ткани
                if face == "sleeve" and side_face(fname) and torn < 0.12:
                    return light(fname, SKIN_S)                        # прорехи, видна мёртвая кожа
                if face == "chest" and fname == "front" and i in (1, 2) and 1 <= j <= 4:
                    return light(fname, BONE if j % 2 == 1 else (36, 26, 28))   # рёбра сквозь разорванный сюртук
                if face == "chest" and fname == "front":
                    # золотые шнуры‑бранденбуры и вырез под жабо
                    if j % 2 == 1 and 0 < i < w - 1 and i not in (w // 2 - 1, w // 2):
                        return light(fname, GOLD)
                    if i in (w // 2 - 1, w // 2) and j < 3:
                        return light(fname, SHIRT)
                    if i in (1, w - 2) and j % 2 == 0:
                        return light(fname, GOLD_L)
                if face == "waist" and fname == "front":
                    if i in (w // 2 - 1, w // 2):
                        return light(fname, GOLD if j % 2 == 0 else TEAL_D)
                if face == "collar" and side_face(fname) and j == 0:
                    return light(fname, GOLD)
                if face == "sleeve" and side_face(fname) and j == 0 and c["origin"][1] > 15:
                    return light(fname, TEAL_L)
                if face == "cuff_coat":
                    return light(fname, GOLD if j in (0, h - 1) else CRIMSON)
                return light(fname, col, 1.0 - 0.06 * j / max(1, h))
            PT.fill(rect, fn)
        return

    if mat == "trousers":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, BLACK if i not in (0,) or not side_face(fn) else BLACK_L))
        return
    if mat == "boot":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = BROWN
                if face == "cuff":
                    col = BROWN_L if j == 0 else BROWN
                if face == "boot" and fname == "front" and i == w // 2:
                    col = BROWN_L                                   # блик голенища
                if face == "foot" and (fname == "bottom" or (side_face(fname) and j == h - 1)):
                    col = BROWN_D
                return light(fname, col, 1.0 - 0.08 * j / max(1, h))
            PT.fill(rect, fn)
        return
    if mat == "sash":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = CRIMSON if (i + j) % 3 else CRIMSON_L
                if face == "band":
                    col = CRIMSON
                if face == "sash_tail" and side_face(fname) and j >= h - 1:
                    return light(fname, GOLD)
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "shirt":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, SHIRT if (i + j) % 2 else mul(SHIRT, 0.92)))
        return
    if mat == "glove":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, BLACK_L if j == 0 else BLACK))
        return
    if mat == "fur":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, mul(FUR, 0.85 + 0.3 * R.random())))
        return
    if mat == "gold":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if face == "epaulette" and side_face(fname) and j == h - 1 and i % 2 == 1:
                    return None                                     # бахрома
                return light(fname, GOLD_L if (i + j) % 3 == 0 else GOLD)
            PT.fill(rect, fn)
        return
    if mat == "hat":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = BLACK
                if face == "brim" and fname in ("top", "bottom") and i >= w - 2 and 3 <= j <= 5:
                    return None                                     # рваный край полей
                if face == "brim" and fname == "top" and (i in (0, w - 1) or j in (0, h - 1)):
                    col = BLACK_L
                if face == "crown" and fname == "top":
                    col = BLACK_L if (i + j) % 4 == 0 else BLACK
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "feather":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if fname in ("left", "right") and j == 0 and (i * 3) % 4 == 1:
                    return None                                     # облезлое перо
                if fname in ("left", "right") and (i + j) % 3 == 0:
                    return light(fname, mul(FEATHER, 0.82))
                return light(fname, FEATHER)
            PT.fill(rect, fn)
        return
    if mat == "wood":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                # спираль: тёмная/светлая полоса наискось
                band = ((j + i * 2) // 2) % 3
                return light(fname, WOOD_L if band == 0 else WOOD)
            PT.fill(rect, fn)
        return
    if mat == "grip":
        for fname, rect in F.items():
            PT.fill(rect, lambda i, j, w, h, fn=fname: light(fn, CRIMSON if (i + j) % 2 else CRIMSON_D))
        return
    if mat == "steel":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                col = mix(STEEL, STEEL_D, (i + j) / max(1, w + h))
                if face in ("vamp1", "vamp2", "vamp3") and side_face(fname) is False:
                    col = STEEL
                if face == "vamp1" and fname in ("front", "back") and (i in (0, w - 1) or j in (0, h - 1)):
                    col = GOLD
                if face == "tip" and fname == "front":
                    return ("glow", (255, 236, 200))
                return light(fname, col)
            PT.fill(rect, fn)
        return
    if mat == "pennant":
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if face == "flag2" and fname in ("left", "right"):
                    # раздвоенный хвост вымпела
                    if i >= w - 2 and h // 2 - 1 <= j <= h // 2:
                        return None
                if fname in ("left", "right"):
                    if j == 0:
                        return light(fname, GOLD)
                    return light(fname, CRIMSON if (i + j) % 5 else CRIMSON_L)
                return light(fname, CRIMSON_D)
            PT.fill(rect, fn)
        return
    for fname, rect in F.items():
        PT.fill(rect, lambda i, j, w, h: (255, 0, 255))


for c in M.cubes():
    paint_cube(c)


# =====================================================================
# 3. Анимации: ключевые позы → кости (IK для рук, копьё по вектору)
# =====================================================================
A_BONES = ["root", "body", "torso", "head", "hat", "plume", "plume_tip", "cape",
           "right_arm", "right_fore", "left_arm", "left_fore", "lance", "pennant", "pennant_tip",
           "right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot",
           "coat_fl", "coat_fr", "coat_bl", "coat_br"]

# Параметры позы. grip — точка кулака правой руки в системе КОРПУСА (Java: +y вниз, −z вперёд,
# от опоры корпуса на поясе); dir — направление копья в системе модели (dir_mode=1 — в системе корпуса);
# sup — вес хвата левой рукой за древко на расстоянии sup_d от кулака; twirl — вращение копья
# в вертикальной плоскости (°); ltarget/lt — левая рука тянется к точке (система корпуса).
DEFAULT = dict(
    root_y=0.0, root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0, 0), torso=(0, 0, 0), head=(0, 0, 0),
    hat=(0, 0, 0), rleg=(0, 0, 0, 2), lleg=(0, 0, 0, -2),
    grip=(-3.0, -2.0, 0.5), dir=(0, 0.3, -1), dir_mode=0.0, roll=0.0, twirl=0.0, sup=1.0, sup_d=6.5,
    larm=(-10, 0, -12), lfore=(-20, 0, 0), ltarget=(2, -19, -4), lt=0.0,
    r_pole=(-1, 0.4, 0.8), l_pole=(1, 0.5, 0.4),
    coat=2.0, cape=4.0, plume=0.0, pennant=0.0,
)

GUARD = dict(body=(2, 15, 0), body_pos=(0, 0.6, 0), torso=(0, 12, 0), head=(0, -24, 0),
             rleg=(14, 6, 4, 4), lleg=(-14, 14, 8, -4), grip=(-2.6, -2.4, 0.2), dir=(0.04, 0.32, -1),
             dir_mode=0.0, twirl=0.0, sup=1.0, sup_d=6.5, coat=2, cape=4, plume=0, pennant=0, lt=0.0,
             root_y=0.0, root_pos=(0, 0, 0), hat=(0, 0, 0), roll=0.0)


def P(base=None, **kw):
    d = dict(DEFAULT)
    if base:
        d.update(base)
    d.update(kw)
    return d


def lerp_val(a, b, t):
    if isinstance(a, (list, tuple)):
        return tuple(lerp(a[i], b[i], t) for i in range(len(a)))
    return lerp(a, b, t)


EASES = {"lin": lambda x: x, "ease": ease, "out": ease_out, "in": ease_in}


def keyed(keys, t):
    """keys: [(время, параметры‑изменения, плавность)] — каждая поза наследует предыдущую."""
    full = []
    cur = dict(DEFAULT)
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
            f = EASES[e]((t - t0) / (t1 - t0)) if t1 > t0 else 1.0
            return {n: lerp_val(p0[n], p1[n], f) for n in p1}
    return dict(full[-1][1])


def coat_follow(pose, pr):
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    fl = pr["coat"]
    pose["coat_fr"] = {"rot": (min(rl, 0) * 0.9 + max(rl, 0) * 0.1 + fl * 0.4, 0, 2)}
    pose["coat_fl"] = {"rot": (min(ll, 0) * 0.9 + max(ll, 0) * 0.1 + fl * 0.4, 0, -2)}
    pose["coat_br"] = {"rot": (max(rl, 0) * 0.8 + max(0, -rl) * 0.15 + fl + 2, 0, 3)}
    pose["coat_bl"] = {"rot": (max(ll, 0) * 0.8 + max(0, -ll) * 0.15 + fl + 2, 0, -3)}
    pose["cape"] = {"rot": (pr["cape"], 0, -3)}


def resolve(pr):
    pose = {}
    pose["root"] = {"rot": (0, pr["root_y"], 0), "pos": pr["root_pos"]}
    pose["body"] = {"rot": pr["body"], "pos": pr["body_pos"]}
    pose["torso"] = {"rot": pr["torso"]}
    pose["head"] = {"rot": pr["head"]}
    pose["hat"] = {"rot": pr["hat"]}
    pose["plume"] = {"rot": (pr["plume"], 0, 0)}
    pose["plume_tip"] = {"rot": (pr["plume"] * 0.6, 0, 0)}
    pose["pennant"] = {"rot": (0, pr["pennant"], 0)}
    pose["pennant_tip"] = {"rot": (0, pr["pennant"] * 0.7, 0)}
    for side, key in (("right", "rleg"), ("left", "lleg")):
        th, sh, ft, sp = pr[key]
        pose[side + "_leg"] = {"rot": (th, 0, sp)}
        pose[side + "_shin"] = {"rot": (sh, 0, 0)}
        pose[side + "_foot"] = {"rot": (ft, 0, 0)}
    coat_follow(pose, pr)

    # правая рука → кулак в точке grip (система корпуса)
    grip_w = M.world("torso", list(pr["grip"]), pose)
    Rt = M.chain_R("torso", pose)
    Rroot = M.chain_R("root", pose)
    pole_r = mv(Rt, list(pr["r_pole"]))
    M.two_bone(pose, "right_arm", "right_fore", grip_w, pole_r, UPPER, FORE)

    # направление копья: в системе модели (dir_mode 0) или корпуса (1), плюс вращение «twirl»
    d0 = mv(Rroot, list(norm(pr["dir"])))
    d1 = mv(Rt, list(norm(pr["dir"])))
    d = norm([lerp(d0[k], d1[k], pr["dir_mode"]) for k in range(3)])
    if pr["twirl"]:
        axis_R = mm(Rt, rx_m(math.radians(-pr["twirl"])))
        d = mv(mm(axis_R, [[Rt[j][i] for j in range(3)] for i in range(3)]), d)
    M.set_world_rot(pose, "lance", look_R(d, roll=pr["roll"]))

    # левая рука: хват за древко / к цели / свободно
    free = {"left_arm": {"rot": pr["larm"]}, "left_fore": {"rot": pr["lfore"]}}
    pose.update(free)
    if pr["sup"] > 0.01:
        tgt = add(grip_w, scl(d, pr["sup_d"]))
        ik = dict(pose)
        M.two_bone(ik, "left_arm", "left_fore", tgt, mv(Rt, list(pr["l_pole"])), UPPER, FORE)
        for b in ("left_arm", "left_fore"):
            pose[b] = {"rot": lerp_val(free[b]["rot"], ik[b]["rot"], pr["sup"])}
    if pr["lt"] > 0.01:
        tgt = M.world("torso", list(pr["ltarget"]), pose)
        ik = dict(pose)
        M.two_bone(ik, "left_arm", "left_fore", tgt, mv(Rt, list(pr["l_pole"])), UPPER, FORE)
        for b in ("left_arm", "left_fore"):
            pose[b] = {"rot": lerp_val(pose[b]["rot"], ik[b]["rot"], pr["lt"])}
    return pose


def legs_cycle(w, amp, knee, base_r=(0, 0, 0, 3), base_l=(0, 0, 0, -3)):
    s = math.sin(w)
    r = (base_r[0] - amp * s, base_r[1] + knee * max(0.0, math.sin(w + 0.9)), base_r[2] + 10 * max(0.0, s), base_r[3])
    l = (base_l[0] + amp * s, base_l[1] + knee * max(0.0, -math.sin(w + 0.9)), base_l[2] + 10 * max(0.0, -s), base_l[3])
    return r, l


# --- циклы движения ---
def idle(t):
    w = 2 * math.pi * t / 2.4
    pr = P(GUARD)
    pr["body_pos"] = (0, 0.6 + 0.25 * math.sin(w), 0)
    pr["torso"] = (1.5 * math.sin(w + 0.5), 12, 0)
    pr["head"] = (1.5 * math.sin(w + 1.0), -24, 0)
    pr["grip"] = (-2.6, -2.4 + 0.25 * math.sin(w + 0.3), 0.2)
    pr["coat"] = 2 + 1.5 * math.sin(w - 0.6)
    pr["cape"] = 4 + 2 * math.sin(w - 0.8)
    pr["plume"] = 3 * math.sin(w - 1.0)
    return resolve(pr)


def walk(t):
    w = 2 * math.pi * t / 1.0
    pr = P(GUARD)
    r, l = legs_cycle(w, 24, 22, (8, 6, 4, 4), (-8, 10, 6, -4))
    pr["rleg"], pr["lleg"] = r, l
    pr["body"] = (4, 15, 2 * math.sin(w))
    pr["body_pos"] = (0, 0.6 - 0.4 * abs(math.sin(w)), 0)
    pr["grip"] = (-2.6, -2.4 + 0.4 * math.sin(2 * w), 0.2)
    pr["coat"] = 6 + 3 * math.sin(2 * w)
    pr["cape"] = 8 + 3 * math.sin(2 * w - 0.6)
    pr["plume"] = 6 + 4 * math.sin(2 * w)
    return resolve(pr)


RUN_POSE = dict(body=(16, 0, 0), torso=(4, 6, 0), head=(-14, -6, 0), grip=(-4.2, -5.0, 2.6), dir=(0.04, 0.05, -1),
                sup=0.0, larm=(-20, 0, -14), lfore=(-30, 0, 0), coat=24, cape=30, plume=26, pennant=18)


def run(t):
    w = 2 * math.pi * t / 0.6
    pr = P(GUARD, **RUN_POSE)
    r, l = legs_cycle(w, 42, 70, (0, 10, 10, 3), (0, 10, 10, -3))
    pr["rleg"], pr["lleg"] = r, l
    pr["body_pos"] = (0, 0.3 - 0.8 * abs(math.sin(w)), 0)
    pr["body"] = (16, 0, 2.5 * math.sin(w))
    pr["larm"] = (-20 + 40 * math.sin(w), 0, -14)
    pr["coat"] = 24 + 6 * math.sin(2 * w)
    pr["cape"] = 30 + 6 * math.sin(2 * w - 0.7)
    return resolve(pr)


# --- атаки (секунды; тики в TatteredLancerEntity) ---
THRUST = [
    (0.00, GUARD, "lin"),
    (0.18, dict(torso=(0, -8, 0), head=(0, -6, 0), grip=(-4.6, -4.6, 4.4), dir=(0.02, 0.08, -1), sup_d=5,
                rleg=(18, 8, 6, 4), lleg=(-18, 18, 10, -4), body=(0, 5, 0), body_pos=(0, 1, 0)), "out"),
    (0.30, dict(torso=(8, 22, 0), body=(10, 20, 0), body_pos=(0, 1.8, 0), head=(-6, -34, 0), grip=(-1.2, -5.0, -6.8),
                dir=(0.03, 0.05, -1), sup_d=4, lleg=(-34, 30, 12, -5), rleg=(28, 4, 2, 5), coat=10, pennant=-20), "in"),
    (0.45, dict(grip=(-4.2, -7.0, 3.4), dir=(0, -0.08, -1), torso=(0, -4, 0), body=(2, 8, 0), body_pos=(0, 1, 0),
                head=(0, -10, 0), lleg=(-20, 20, 10, -4), rleg=(18, 8, 6, 4), coat=4, pennant=10), "out"),
    (0.60, dict(grip=(-0.8, -8.6, -7.2), dir=(0, -0.15, -1), torso=(4, 24, 0), body=(6, 20, 0), body_pos=(0, 1.4, 0),
                head=(-4, -36, 0), lleg=(-30, 26, 10, -5), rleg=(26, 4, 2, 5), coat=10, pennant=-20), "in"),
    (0.75, dict(grip=(-5.2, -3.6, 5.6), dir=(0.02, 0.1, -1), torso=(-4, -20, 0), body=(0, 0, 0), body_pos=(0, 2.2, 0),
                head=(2, 4, 0), lleg=(-24, 40, 12, -4), rleg=(30, 40, 10, 5), coat=4, pennant=12), "out"),
    (1.00, dict(grip=(-0.2, -4.0, -9.8), dir=(0, 0.06, -1), torso=(10, 28, 0), body=(16, 24, 0), body_pos=(0, 3.2, 0),
                head=(-10, -40, 0), lleg=(-48, 44, 16, -6), rleg=(40, 0, 0, 6), coat=18, cape=16, pennant=-30), "in"),
    (1.15, dict(coat=14, pennant=-10), "lin"),
    (1.50, GUARD, "ease"),
]


def thrust(t):
    return resolve(keyed(THRUST, t))


CHARGE_COUCH = dict(grip=(-4.2, -5.0, 2.6), dir=(0.04, 0.04, -1), sup=0.0, larm=(-70, 0, -15), lfore=(-30, 0, 0))
CHARGE = [
    (0.00, GUARD, "lin"),
    (0.45, dict(body=(22, 0, 0), body_pos=(0, 2.6, 0), torso=(0, -10, 0), head=(-18, 10, 0), rleg=(40, 48, 14, 4),
                lleg=(-42, 60, 18, -4), coat=6, cape=8, **CHARGE_COUCH), "out"),
    (0.55, dict(body=(28, 0, 0), body_pos=(0, 1.6, 0), rleg=(50, 10, 30, 4), lleg=(-50, 40, 10, -4), torso=(0, -4, 0),
                head=(-24, 4, 0)), "in"),
    (1.20, dict(body=(30, 0, 0), coat=40, cape=45, plume=35, pennant=30), "lin"),
    (1.30, dict(body=(-16, 0, 0), body_pos=(0, 2.4, 0), rleg=(-20, 30, -10, 6), lleg=(-44, 20, -12, -6),
                grip=(-4.0, -9.0, 1.0), dir=(0, -0.45, -1), coat=-12, cape=-6, plume=-10, pennant=-20, head=(-4, 0, 0),
                torso=(-4, 0, 0), larm=(-40, 0, -30)), "out"),
    (1.45, dict(body=(-10, 0, 0), body_pos=(0, 2.0, 0), coat=-4), "ease"),
    (1.80, dict(GUARD, sup=1.0, larm=(-10, 0, -12), lfore=(-20, 0, 0)), "ease"),
]


def charge(t):
    pr = keyed(CHARGE, t)
    if 0.55 <= t < 1.22:
        # бег во весь опор: быстрый цикл ног поверх позы
        w = 2 * math.pi * (t - 0.55) / 0.3
        k = min(1.0, (t - 0.55) / 0.08) * min(1.0, (1.22 - t) / 0.06)
        r, l = legs_cycle(w, 48, 80, (10, 10, 10, 4), (-10, 10, 10, -4))
        pr["rleg"] = lerp_val(pr["rleg"], r, k)
        pr["lleg"] = lerp_val(pr["lleg"], l, k)
        pr["body_pos"] = (0, 1.4 - 1.0 * abs(math.sin(w)) * k, 0)
    return resolve(pr)


SPRING = dict(body=(-6, 0, 0), body_pos=(0, -1, 0), rleg=(6, 0, 30, 3), lleg=(-6, 0, 30, -3), grip=(-3.4, -12.0, -1.0),
              dir=(0, -0.6, -0.8), torso=(-4, 6, 0), head=(-12, -4, 0), coat=-8, cape=-6, sup=1.0, sup_d=5)
LEAP_START = [
    (0.00, GUARD, "lin"),
    (0.40, dict(body=(14, 0, 0), body_pos=(0, 4, 0), torso=(-6, 0, 0), head=(-14, 0, 0), grip=(-4.0, -3.0, 3.0),
                dir=(0, 0.25, -1), rleg=(30, 70, 16, 4), lleg=(-30, 70, 16, -4), coat=2), "out"),
    (0.50, SPRING, "in"),
]


def leap_start(t):
    return resolve(keyed(LEAP_START, t))


PLUNGE = dict(body=(350, 0, 0), body_pos=(0, 0, 0), torso=(-6, 0, 0), head=(-6, 0, 0), grip=(-1.6, -17.0, -1.5),
              dir=(0, 0.85, -0.5), dir_mode=0.0, sup=1.0, sup_d=5, rleg=(-30, 40, 20, 4), lleg=(-50, 70, 20, -4),
              coat=20, cape=30, plume=20, pennant=25)
LEAP_AIR = [
    (0.00, dict(SPRING, dir_mode=1.0, dir=(0, -0.6, -0.8)), "lin"),
    (0.12, dict(body=(60, 0, 0), rleg=(-60, 100, 30, 4), lleg=(-60, 100, 30, -4), grip=(-3.0, -6.0, -4.0),
                dir=(0, 0.2, -1), coat=10, cape=20), "in"),
    (0.50, dict(body=(330, 0, 0), coat=20, cape=30), "lin"),
    (0.70, PLUNGE, "out"),
    (1.60, dict(coat=26, cape=36, pennant=35), "lin"),
]


def leap_air(t):
    return resolve(keyed(LEAP_AIR, t))


IMPACT = dict(body=(30, 0, 0), body_pos=(0, 5, 0), torso=(4, 0, 0), head=(-10, 0, 0), grip=(-2.0, 1.0, -5.0),
              dir=(0, 1, -0.12), dir_mode=0.0, rleg=(20, 80, 10, 6), lleg=(-50, 90, 30, -6), coat=20, cape=30, plume=20,
              pennant=0, sup=1.0, sup_d=4)
LEAP_LAND = [
    (0.00, dict(PLUNGE, body=(-10, 0, 0)), "lin"),
    (0.08, IMPACT, "in"),
    (0.35, dict(body=(26, 0, 0), body_pos=(0, 4.4, 0), coat=6, cape=10, plume=4), "out"),
    (0.55, dict(grip=(-3.0, -5.0, 0.0), dir=(0, 0.1, -1), body=(10, 8, 0), body_pos=(0, 2, 0)), "ease"),
    (0.80, GUARD, "ease"),
]


def leap_land(t):
    return resolve(keyed(LEAP_LAND, t))


SWEEP = [
    (0.00, GUARD, "lin"),
    (0.35, dict(torso=(0, -55, 0), body=(4, -20, 0), head=(0, 60, 0), grip=(-7.0, -6.0, 3.0), dir=(-1, 0.1, 0.4),
                sup_d=5, rleg=(6, 20, 6, 10), lleg=(-12, 24, 8, -10), body_pos=(0, 2, 0), coat=4), "out"),
    (0.40, dict(root_y=0.0, torso=(0, -30, 0), grip=(-9.5, -5.0, -1.0), dir=(-1, 0.05, -0.15), dir_mode=1.0, sup=0.0,
                larm=(0, 0, -70), lfore=(-10, 0, 0), head=(0, 20, 0)), "in"),
    (0.80, dict(root_y=-360.0, torso=(0, 20, 0), body=(6, 0, -6), head=(0, -10, 0), coat=36, cape=40, plume=30,
                pennant=-40), "lin"),
    (0.80001, dict(root_y=0.0), "lin"),
    (0.95, dict(torso=(0, 40, 0), grip=(-2.0, -5.0, -6.0), dir=(0.6, 0.1, -0.8), dir_mode=0.0, coat=10, cape=12,
                pennant=10), "out"),
    (1.30, GUARD, "ease"),
]


def sweep(t):
    return resolve(keyed(SWEEP, t))


UPPERCUT = [
    # «Вихревой подъём»: закрутка низко → взлёт с оборотом, копьё снизу вверх → приземление
    (0.00, GUARD, "lin"),
    (0.30, dict(body=(18, -25, 0), body_pos=(0, 3.2, 0), torso=(6, -50, 0), head=(-10, 55, 0), grip=(-6.0, -1.0, 4.0),
                dir=(-0.6, 0.5, 0.6), sup=1.0, sup_d=5, rleg=(24, 60, 14, 8), lleg=(-34, 62, 16, -8), coat=4), "out"),
    (0.38, dict(body=(6, -10, 0), torso=(0, -20, 0), grip=(-7.0, -3.0, 0.0), dir=(-1, 0.4, -0.3), dir_mode=1.0,
                sup=0.0, larm=(-20, 0, -70), lfore=(-10, 0, 0), head=(-6, 20, 0)), "in"),
    (0.62, dict(root_y=-360.0, body=(-14, 0, 0), body_pos=(0, -1.5, 0), torso=(-8, 15, 0), head=(-24, -15, 0),
                grip=(-2.5, -16.0, -3.0), dir=(0.1, -1, -0.3), rleg=(8, 4, 34, 3), lleg=(-24, 20, 30, -3), coat=30,
                cape=40, plume=24, pennant=-30), "out"),
    (0.62001, dict(root_y=0.0), "lin"),
    (0.85, dict(body=(-10, 0, 0), body_pos=(0, -1, 0), coat=20, cape=26), "lin"),
    (1.05, dict(body=(14, 10, 0), body_pos=(0, 2.4, 0), rleg=(20, 40, 10, 4), lleg=(-20, 44, 12, -4),
                grip=(-3.0, -4.0, -2.0), dir=(0.04, 0.1, -1), dir_mode=0.0, sup=1.0, coat=6, cape=10, plume=4,
                pennant=0, larm=(-10, 0, -12), lfore=(-20, 0, 0), torso=(0, 8, 0), head=(-4, -18, 0)), "in"),
    (1.30, GUARD, "ease"),
]


def uppercut(t):
    return resolve(keyed(UPPERCUT, t))


BACKSTEP = [
    (0.00, GUARD, "lin"),
    (0.15, dict(body=(-14, 0, 0), body_pos=(0, -0.5, 0), rleg=(-30, 60, 20, 4), lleg=(-40, 70, 20, -4), sup=0.0,
                grip=(-6.0, -4.0, 0.0), twirl=90.0, larm=(-30, 0, -40), coat=-16, cape=-14, plume=-10), "out"),
    (0.40, dict(twirl=270.0, body=(-6, 0, 0)), "lin"),
    (0.55, dict(body=(10, 10, 0), body_pos=(0, 2, 0), rleg=(20, 40, 10, 4), lleg=(-20, 40, 10, -4), twirl=360.0, coat=8,
                cape=8, plume=6), "out"),
    (0.70, dict(GUARD, twirl=360.0, larm=(-10, 0, -12)), "ease"),
]


def backstep(t):
    return resolve(keyed(BACKSTEP, t))


ROAR = [
    (0.00, GUARD, "lin"),
    (0.30, dict(grip=(-6.0, -7.0, -3.0), dir=(0, 1, 0.05), sup=0.0, lt=1.0, ltarget=(2.5, -18.5, -4.5),
                body=(2, 0, 0), torso=(0, 0, 0), head=(4, 0, 0), rleg=(4, 2, 2, 5), lleg=(-4, 2, 2, -5)), "ease"),
    (0.60, dict(hat=(-25, 0, 10), head=(14, 0, 0), body=(8, 0, 0)), "ease"),
    (0.95, dict(hat=(0, 0, 0), head=(-12, 0, 0), body=(-10, 0, 0), lt=0.0, larm=(-40, 0, -60), lfore=(-20, 0, 0),
                coat=14, cape=20, plume=16, body_pos=(0, -0.5, 0)), "out"),
    (1.20, dict(coat=6, cape=8), "lin"),
    (1.50, dict(GUARD, lt=0.0), "ease"),
]


def roar(t):
    return resolve(keyed(ROAR, t))


# --- особая атака второй фазы: «Тройной таран» (тики в сущности: рывки 16–25, 31–40, 46–55, удар 68) ---
DASH = dict(body=(30, 0, 0), body_pos=(0, 1.5, 0), torso=(0, -4, 0), head=(-24, 4, 0), coat=40, cape=45, plume=35,
            pennant=30, **CHARGE_COUCH)
COIL = dict(body=(24, 0, 0), body_pos=(0, 2.8, 0), torso=(0, -10, 0), head=(-18, 10, 0), rleg=(42, 52, 14, 4),
            lleg=(-44, 62, 18, -4), coat=8, cape=10, plume=10, pennant=10, **CHARGE_COUCH)
SKID = dict(body=(-12, 0, 0), body_pos=(0, 2.4, 0), rleg=(-20, 30, -10, 6), lleg=(-44, 20, -12, -6),
            grip=(-4.0, -9.0, 1.0), dir=(0, -0.45, -1), coat=-12, cape=-6, plume=-10, pennant=-20, head=(-4, 0, 0),
            torso=(-4, 0, 0))
DASHES = [(0.80, 1.25), (1.55, 2.00), (2.30, 2.75)]
GRAND = [
    (0.00, GUARD, "lin"),
    (0.40, dict(grip=(-4.0, -16.0, -1.0), dir=(0.05, -1, 0.1), sup=0.0, larm=(-150, 0, -20), lfore=(-10, 0, 0),
                body=(-8, 0, 0), torso=(0, 0, 0), head=(-20, 0, 0), coat=6, cape=10, plume=14,
                rleg=(6, 4, 4, 6), lleg=(-6, 4, 4, -6)), "out"),
    (0.80, COIL, "in"),
]
for k, (d0, d1) in enumerate(DASHES):
    GRAND.append((d1, DASH, "lin"))
    if k < 2:
        mid = d1 + 0.1
        end = DASHES[k + 1][0]
        GRAND.append((mid, dict(SKID, root_y=-180.0), "out"))
        GRAND.append((end, dict(COIL, root_y=-360.0), "in"))
        GRAND.append((end + 0.00001, dict(root_y=0.0), "lin"))
GRAND += [
    (2.85, dict(SKID, root_y=-180.0), "out"),
    (3.05, dict(root_y=-360.0, body=(0, 0, 0), body_pos=(0, 2.2, 0), torso=(-4, -20, 0), head=(2, 4, 0),
                grip=(-5.2, -3.6, 5.6), dir=(0.02, 0.1, -1), sup=1.0, sup_d=5, lleg=(-24, 40, 12, -4),
                rleg=(30, 40, 10, 5), coat=4, cape=8, plume=4, pennant=12, larm=(-10, 0, -12), lfore=(-20, 0, 0)), "in"),
    (3.05001, dict(root_y=0.0), "lin"),
    (3.30, dict(body=(-4, 0, 0), body_pos=(0, 2.6, 0), torso=(-6, -28, 0), grip=(-5.8, -3.0, 6.4)), "out"),
    (3.40, dict(grip=(0.5, -4.0, -10.5), dir=(0, 0.04, -1), torso=(12, 30, 0), body=(20, 24, 0), body_pos=(0, 3.6, 0),
                head=(-12, -42, 0), lleg=(-52, 46, 16, -6), rleg=(44, 0, 0, 6), coat=30, cape=30, plume=26,
                pennant=-40, sup_d=4), "in"),
    (3.60, dict(coat=18, cape=18), "lin"),
    (3.95, GUARD, "ease"),
]


def grand(t):
    pr = keyed(GRAND, t)
    for d0, d1 in DASHES:
        if d0 <= t < d1:
            w = 2 * math.pi * (t - d0) / 0.3
            k = min(1.0, (t - d0) / 0.06) * min(1.0, (d1 - t) / 0.06)
            r, l = legs_cycle(w, 48, 80, (10, 10, 10, 4), (-10, 10, 10, -4))
            pr["rleg"] = lerp_val(pr["rleg"], r, k)
            pr["lleg"] = lerp_val(pr["lleg"], l, k)
            pr["body_pos"] = (0, 1.4 - 1.0 * abs(math.sin(w)) * k, 0)
    return resolve(pr)


# --- «Ливень копий»: взлёт с двойным оборотом, копьё к небу, парение, спуск ---
RAIN_TOP = dict(root_y=-720.0, body=(-16, 0, 0), body_pos=(0, 0, 0), torso=(-6, 0, 0), head=(-22, 0, 0),
                grip=(-3.5, -19.0, -0.5), dir=(0.02, -1, 0.05), dir_mode=0.0, sup=0.0, larm=(-20, 0, -95),
                lfore=(-10, 0, 0), rleg=(6, 20, 44, 6), lleg=(-12, 34, 44, -2), coat=-14, cape=20, plume=30,
                pennant=0)
RAIN_RISE = [
    (0.00, GUARD, "lin"),
    (0.25, dict(body=(12, 0, 0), body_pos=(0, 3, 0), rleg=(28, 60, 14, 4), lleg=(-28, 60, 14, -4),
                grip=(-4.0, -3.0, 2.0), dir=(0, 0.3, -1)), "out"),
    (0.45, dict(body=(-8, 0, 0), body_pos=(0, -1, 0), rleg=(4, 6, 40, 2), lleg=(-4, 10, 40, -2),
                grip=(-3.5, -10.0, -1.0), dir=(0, -0.7, -0.6), sup=0.0, larm=(-30, 0, -60), lfore=(-10, 0, 0)), "in"),
    (1.10, RAIN_TOP, "out"),
    (1.10001, dict(root_y=0.0), "lin"),
    (1.20, dict(root_y=0.0), "lin"),
]


def rain_rise(t):
    return resolve(keyed(RAIN_RISE, t))


def rain_hover(t):
    w = 2 * math.pi * t / 1.0
    pr = P(RAIN_TOP, root_y=0.0)
    pr["body_pos"] = (0, -0.4 * math.sin(w), 0)
    pr["body"] = (-16 + 2 * math.sin(w + 0.5), 0, 0)
    pr["dir"] = (0.02 + 0.04 * math.sin(w), -1, 0.05)
    pr["larm"] = (-20 + 6 * math.sin(w + 1.0), 0, -95)
    pr["rleg"] = (6 + 4 * math.sin(w), 20, 44, 6)
    pr["lleg"] = (-12 - 4 * math.sin(w), 34, 44, -2)
    pr["coat"] = -10 + 6 * math.sin(w)
    pr["cape"] = 20 + 6 * math.sin(w - 0.6)
    pr["plume"] = 30 + 6 * math.sin(w - 0.9)
    return resolve(pr)


def rain_descend(t):
    w = 2 * math.pi * t / 0.6
    pr = P(PLUNGE, body=(-6, 0, 0))
    pr["grip"] = (-1.6, -12.0, -2.0)
    pr["dir"] = (0, 0.8, -0.6)
    pr["coat"] = 26 + 6 * math.sin(w)
    pr["cape"] = 36 + 6 * math.sin(w - 0.6)
    pr["plume"] = 26 + 5 * math.sin(w - 0.9)
    return resolve(pr)


animations = {
    "animation.%s.idle" % NAME: bake(idle, 2.4, 20, True, A_BONES),
    "animation.%s.walk" % NAME: bake(walk, 1.0, 20, True, A_BONES),
    "animation.%s.run" % NAME: bake(run, 0.6, 30, True, A_BONES),
    "animation.%s.thrust" % NAME: bake(thrust, 1.5, 40, False, A_BONES),
    "animation.%s.charge" % NAME: bake(charge, 1.8, 40, False, A_BONES),
    "animation.%s.leap_start" % NAME: bake(leap_start, 0.5, 40, False, A_BONES),
    "animation.%s.leap_air" % NAME: bake(leap_air, 1.6, 40, False, A_BONES),
    "animation.%s.leap_land" % NAME: bake(leap_land, 0.8, 40, False, A_BONES),
    "animation.%s.sweep" % NAME: bake(sweep, 1.3, 40, False, A_BONES),
    "animation.%s.uppercut" % NAME: bake(uppercut, 1.3, 40, False, A_BONES),
    "animation.%s.backstep" % NAME: bake(backstep, 0.7, 40, False, A_BONES),
    "animation.%s.roar" % NAME: bake(roar, 1.5, 40, False, A_BONES),
    "animation.%s.grand" % NAME: bake(grand, 3.95, 40, False, A_BONES),
    "animation.%s.rain_rise" % NAME: bake(rain_rise, 1.2, 40, False, A_BONES),
    "animation.%s.rain_hover" % NAME: bake(rain_hover, 1.0, 20, True, A_BONES),
    "animation.%s.rain_descend" % NAME: bake(rain_descend, 0.6, 20, True, A_BONES),
}

# =====================================================================
# 4. Прочие текстуры: призрачное копьё, иконка «Дуэлянта», зона ливня копий
# =====================================================================
from PIL import Image  # noqa: E402


def falling_lance_texture():
    """Призрачное копьё в раскладке ванильной стрелы 32×32 (бок 16×5 — остриё справа, торец 5×5)."""
    a = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = a.load()
    for x in range(16):
        c = (150, 200, 255, 255) if x % 3 else (200, 230, 255, 255)
        p[x, 2] = c
        if 2 <= x <= 11:
            p[x, 1] = (90, 150, 240, 200) if x % 2 else (0, 0, 0, 0)
    for x, ys, col in ((11, (1, 2, 3), (170, 220, 255)), (12, (0, 1, 2, 3, 4), (210, 240, 255)),
                       (13, (1, 2, 3), (235, 250, 255)), (14, (1, 2, 3), (255, 255, 255)), (15, (2,), (255, 255, 255))):
        for y in ys:
            p[x, y] = col + (255,)
    for y in (0, 1, 3, 4):
        p[1, y] = (60, 110, 230, 255)
        p[2, y] = (90, 150, 245, 255)
    for x in range(5):
        for y in range(5, 10):
            if x in (1, 3) or y in (6, 8):
                p[x, y] = (110, 170, 250, 255)
    p[2, 7] = (240, 250, 255, 255)
    a.save(os.path.join(ROOT, "textures", "entity", "falling_lance.png"))


def duelist_icon():
    """Иконка 18×18: два скрещённых копья на синем щите."""
    a = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    p = a.load()
    for y in range(18):
        for x in range(18):
            dx, dy = x - 8.5, y - 8.0
            if abs(dx) + max(0, dy) * 0.6 < 8 and dy > -8 and abs(dx) < 7.5:
                edge = abs(dx) + max(0, dy) * 0.6 > 6.6 or dy < -6.9
                p[x, y] = (150, 190, 255, 255) if edge else (40, 80, 170, 255)
    for k in range(2, 16):
        for (x, y) in ((k, k), (17 - k, k)):
            p[x, y] = (230, 236, 250, 255)
    for (x, y) in ((2, 2), (3, 2), (2, 3), (15, 2), (14, 2), (15, 3)):
        p[x, y] = (255, 255, 255, 255)
    for (x, y) in ((4, 12), (5, 13), (13, 12), (12, 13)):
        p[x, y] = (240, 210, 120, 255)
    a.save(os.path.join(ROOT, "textures", "mob_effect", "duelist.png"))


def rat_icon():
    """Иконка 18×18: серая крыса с хвостом на мутно‑зелёном пятне."""
    a = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    p = a.load()
    for y in range(18):
        for x in range(18):
            if (x - 8.5) ** 2 / 64 + (y - 9) ** 2 / 49 < 1:
                p[x, y] = (110, 124, 36, 160)
    body = ["......xxxxx.......", ".....xxxxxxx......", "...xxxxxxxxxx.....", "..xxxxxxxxxxxx....",
            ".oxxxxxxxxxxxxx...", "eoxxxxxxxxxxxxxx..", ".xxxxxxxxxxxxxxx..", "..xxxxxxxxxxxxx.t.",
            "...x..x....x..x..t", ".................t"]
    for j, row in enumerate(body):
        for i, ch in enumerate(row):
            if 5 + j > 17:
                continue
            if ch == "x":
                p[i, 5 + j] = (120, 116, 112, 255) if j > 1 else (150, 146, 140, 255)
            elif ch == "o":
                p[i, 5 + j] = (230, 170, 180, 255)
            elif ch == "e":
                p[i, 5 + j] = (200, 40, 40, 255)
            elif ch == "t":
                p[i, 5 + j] = (210, 150, 160, 255)
    a.save(os.path.join(ROOT, "textures", "mob_effect", "sneaky_rat.png"))


def zone_texture():
    """Круг зоны 128×128: мягкое синее свечение, яркое кольцо по краю, руническое кольцо внутри."""
    s = 128
    a = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = a.load()
    for y in range(s):
        for x in range(s):
            dx, dy = (x + 0.5 - s / 2) / (s / 2), (y + 0.5 - s / 2) / (s / 2)
            r = math.hypot(dx, dy)
            if r > 1.0:
                continue
            ang = math.atan2(dy, dx)
            alpha = 0.18 + 0.25 * r * r
            if r > 0.93:
                alpha = 0.95
            elif 0.80 < r < 0.84:
                alpha = 0.55 if int((ang + math.pi) / (2 * math.pi) * 48) % 2 == 0 else 0.15
            elif 0.55 < r < 0.57:
                alpha = 0.4
            col = (70, 140, 255) if r < 0.93 else (170, 210, 255)
            p[x, y] = col + (clamp(alpha * 255),)
    a.save(os.path.join(ROOT, "textures", "misc", "lance_zone.png"))


if __name__ == "__main__":
    os.makedirs(os.path.join(ROOT, "textures", "misc"), exist_ok=True)
    falling_lance_texture()
    duelist_icon()
    rat_icon()
    zone_texture()
    M.write_geo(ROOT)
    write_anims(ROOT, NAME, animations)
    PT.img.save(os.path.join(ROOT, "textures", "entity", NAME + ".png"))
    PT.glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_glowmask.png"))
    print("готово:", NAME, "кубов:", len(M.cubes()))
