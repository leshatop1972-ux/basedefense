"""
Генератор босса «Кровожад» (bloodthirster), его выводка — «мелкого кровожада» (bloodling),
«яйца кровожада» (blood_egg) и текстуры блока «кровавая лужа» (blood_pool).

Запуск:  python tools/bloodthirster_gen.py
Проверка без игры:
    python tools/mob_preview.py bloodthirster swing 0.5,0.95,1.3
    python tools/mob_preview.py bloodling attack
    python tools/mob_preview.py blood_egg

Оригинальный персонаж: высокое (≈3.5 блока) худое чудовище из голой алой плоти — длинные руки
с когтями, рёбра под чёрными лакированными пластинами, гладкая бордовая маска вместо лица
с щелями горящих глаз и огромной челюстью, длинные чёрные волосы, лоскуты кожи вместо юбки.
1‑я фаза — сгорбленный голодный зверь; 2‑я — выпрямляется, за головой венец из шипов,
в руках два красно‑чёрных меча (текстура *_p2 — больше чёрного лака и светящихся прожилок).

Анимации: ключевые позы (keyframes с плавностью) + обратная кинематика (ноги стоят на земле,
кисти тянутся к точкам, мечи — по вектору), см. mobgen.Model.two_bone.
"""
import math
import os

from PIL import Image

from mobgen import (FACE_LIGHT, Model, Painter, add, bake, clamp, ease, ease_in, ease_out, faces, lerp, look_R, mix,
                    mm, mul, mv, norm, rx_m, ry_m, scl, write_anims)

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")

# =====================================================================
# Палитра: алый, бордовый, красный, немного чёрного
# =====================================================================
SCARLET = (196, 30, 36)
SCARLET_L = (228, 74, 66)
RED = (150, 16, 28)
CRIMSON = (112, 10, 24)
BORDEAUX = (76, 6, 20)
BORDEAUX_D = (48, 4, 14)
BLACK = (20, 10, 14)
BLACK_L = (46, 22, 30)
LACQUER_HI = (110, 52, 62)
IVORY = (222, 196, 186)
IVORY_D = (168, 136, 128)
GLOW = (255, 72, 56)
GLOW_HOT = (255, 170, 140)


# =====================================================================
# 1. Босс: модель (Bedrock: y вверх, лицо в −z, правая сторона — −x)
# =====================================================================
NAME = "bloodthirster"
M = Model(NAME, 256, 256)
bone, cube = M.bone, M.cube
THIGH, SHIN = 13.0, 11.0
UPPER, FORE = 13.0, 12.0

bone("root", None, (0, 0, 0))
bone("body", "root", (0, 27, 0))
cube("body", (-4, 24.5, -2.6), (8, 4, 5.2), "flesh", face="pelvis")
cube("body", (-4.4, 27.2, -2.9), (8.8, 1.6, 5.8), "lacquer", face="belt")
cube("body", (-1.2, 23.4, -3.3), (2.4, 4.6, 1.0), "lacquer", face="buckle")

# лоскуты кожи вместо юбки (следуют за ногами)
for nm, x, z, w, h in (("ribbon_fr", -4.0, -2.9, 3.6, 15), ("ribbon_fl", 0.4, -2.9, 3.6, 13),
                       ("ribbon_br", -4.2, 2.3, 4.0, 17), ("ribbon_bl", 0.2, 2.3, 4.0, 16)):
    bone(nm, "body", (x + w / 2, 27.5, z + 0.3))
    cube(nm, (x, 27.5 - h, z), (w, h, 0.6), "ribbon", face="ribbon")

# ноги: бедро → голень → стопа (длинные, тонкие, с когтями на пальцах)
bone("right_leg", "body", (-2.6, 26, 0))
cube("right_leg", (-4.2, 13, -1.6), (3.2, 13, 3.2), "flesh", face="thigh")
cube("right_leg", (-4.5, 12.6, -2.3), (3.8, 3.2, 1.2), "lacquer", face="kneecap")
bone("right_shin", "right_leg", (-2.6, 13, 0))
cube("right_shin", (-3.95, 2, -1.35), (2.7, 11, 2.7), "flesh", face="shin")
cube("right_shin", (-4.1, 5, -1.9), (3.0, 7, 0.8), "lacquer", face="greave")
bone("right_foot", "right_shin", (-2.6, 2, 0))
cube("right_foot", (-4.0, 0, -3.6), (2.8, 2, 5.0), "flesh", face="foot")
for k, dx in enumerate((-3.9, -2.9, -1.9)):
    cube("right_foot", (dx - 0.1 + 0.15 * k, 0, -5.4), (0.8, 0.9, 1.9), "claw", face="toe")
cube("right_foot", (-3.0, 0.4, 1.0), (0.8, 0.8, 1.6), "claw", face="toe")
M.mirror_x("right_leg", "left_leg")
M.mirror_x("right_shin", "left_shin", parent="left_leg")
M.mirror_x("right_foot", "left_foot", parent="left_shin")

# корпус: тонкий живот‑позвоночник, широкая грудная клетка под лаковыми пластинами
bone("torso", "body", (0, 28, 0))
cube("torso", (-2.6, 28, -1.8), (5.2, 6.5, 3.6), "flesh", face="abdomen")
cube("torso", (-5, 34, -3), (10, 10, 6), "flesh", face="ribcage")
cube("torso", (-1.4, 34.5, -3.7), (2.8, 9, 1.0), "lacquer", face="sternum")
cube("torso", (-7.3, 43.4, -0.5), (1.0, 3.2, 1.0), "claw", face="spike", rot=(0, 0, 22), pivot=(-6.8, 43.4, 0))
cube("torso", (6.3, 43.4, -0.5), (1.0, 3.2, 1.0), "claw", face="spike", rot=(0, 0, -22), pivot=(6.8, 43.4, 0))
for k, y in enumerate((31.0, 35.0, 38.6, 42.0)):            # гребень позвоночника
    cube("torso", (-0.5, y, 2.4), (1.0, 2.4 + 0.3 * k, 1.0), "claw", face="spike", rot=(-40, 0, 0),
         pivot=(0, y, 2.8))

bone("neck", "torso", (0, 44, 0))
cube("neck", (-1.6, 44, -1.6), (3.2, 4, 3.2), "flesh", face="neck")
bone("head", "neck", (0, 47.5, -0.4))
cube("head", (-3, 49, -4.2), (6, 8, 6.4), "mask", face="skull")
cube("head", (-2.6, 50.5, 2.2), (5.2, 6, 2.4), "mask", face="occiput")
cube("head", (-3.3, 54.6, -4.7), (6.6, 1.2, 1.0), "lacquer", face="brow")
bone("jaw", "head", (0, 49.5, 1.0))
cube("jaw", (-2.8, 46.2, -4.5), (5.6, 3.2, 5.6), "jaw", face="jaw")
# Лицо и подбородок — тонкие пластины в 4 раза крупнее, кость уменьшена в анимациях до 0.25:
# так на лице в 4 раза больше пикселей (не «квадратное» лицо Minecraft).
FACE_S = 0.25
bone("face", "head", (0, 49, -4.2))
cube("face", (-12, 49, -5.2), (24, 32, 1), "face_hi", face="faceplate")
bone("jaw_face", "jaw", (0, 46.2, -4.5))
cube("jaw_face", (-11, 46.2, -5.5), (22, 12.8, 1), "face_hi", face="jawplate")
# волосы: два звена, свисают по спине
bone("hair", "head", (0, 55.5, 3.0))
cube("hair", (-3.2, 43.5, 3.0), (6.4, 12.5, 1.2), "hair", face="hair1")
cube("hair", (-3.4, 49.5, -3.4), (0.9, 7.5, 6.2), "hair", face="hair_side")
cube("hair", (2.5, 49.5, -3.4), (0.9, 7.5, 6.2), "hair", face="hair_side")
bone("hair_tip", "hair", (0, 43.5, 3.3))
cube("hair_tip", (-3.2, 33.5, 2.8), (6.4, 10, 1.0), "hair", face="hair2")
# большой широкий плащ: мантия на плечах, высокий воротник, полотнище до колен (почти без анимации)
bone("cloak", "torso", (0, 44, 3.6))
# полотнище из трёх частей: середина и два крыла, загнутые вокруг тела (складки), низ расширяется
cube("cloak", (-6, 29, 3.3), (12, 15, 0.8), "cloak", face="cloak_top")
cube("cloak", (5.6, 29, 3.3), (4.6, 15, 0.8), "cloak", face="cloak_top", rot=(0, 28, 0), pivot=(5.8, 36, 3.7))
cube("cloak", (-10.2, 29, 3.3), (4.6, 15, 0.8), "cloak", face="cloak_top", rot=(0, -28, 0), pivot=(-5.8, 36, 3.7))
cube("cloak", (-7, 6, 3.7), (14, 23.5, 0.8), "cloak", face="cloak_low", rot=(10, 0, 0), pivot=(0, 29.5, 3.7))
cube("cloak", (6.6, 6, 3.7), (6.2, 23.5, 0.8), "cloak", face="cloak_low", rot=(10, 32, 0), pivot=(6.8, 29.5, 3.9))
cube("cloak", (-12.8, 6, 3.7), (6.2, 23.5, 0.8), "cloak", face="cloak_low", rot=(10, -32, 0), pivot=(-6.8, 29.5, 3.9))
cube("torso", (-8.8, 41.2, -3.4), (17.6, 3.6, 7.6), "cloak", face="mantle")
cube("torso", (-4.4, 44.2, 1.2), (8.8, 5.6, 1.0), "cloak", face="collar", rot=(-14, 0, 0), pivot=(0, 44.2, 1.7))
cube("torso", (-5.2, 44.2, -2.2), (1.0, 5.0, 3.6), "cloak", face="collar", rot=(0, 0, 12), pivot=(-4.7, 44.2, 0))
cube("torso", (4.2, 44.2, -2.2), (1.0, 5.0, 3.6), "cloak", face="collar", rot=(0, 0, -12), pivot=(4.7, 44.2, 0))
# венец из шипов за головой (только 2‑я фаза — прячется кодом)
bone("halo", "head", (0, 56, 4.6))
for k in range(11):
    a = -150 + k * 30                                       # веер шипов вверх и в стороны
    cube("halo", (-0.45, 59.0, 4.4), (0.9, 4.2 + 1.6 * (k % 2), 0.9), "halo", face="halo",
         rot=(0, 0, a), pivot=(0, 56, 4.6))
cube("halo", (-2.2, 54.4, 4.2), (4.4, 1.0, 1.0), "halo", face="halo_bar")

# руки: длинные, кисти с тремя когтями
bone("right_arm", "torso", (-6.6, 42, 0))
cube("right_arm", (-8.0, 29, -1.4), (2.8, 13, 2.8), "flesh", face="upper")
cube("right_arm", (-8.4, 35.5, -1.8), (3.6, 1.2, 3.6), "lacquer", face="band")
bone("right_fore", "right_arm", (-6.6, 29, 0))
cube("right_fore", (-7.9, 17, -1.3), (2.6, 12, 2.6), "flesh", face="fore")
cube("right_fore", (-8.2, 17.2, -1.6), (3.2, 3.2, 3.2), "lacquer", face="bracer")
cube("right_fore", (-7.2, 28.0, 1.0), (1.0, 1.0, 2.4), "claw", face="spike", rot=(30, 0, 0), pivot=(-6.7, 28.5, 1.2))
bone("right_hand", "right_fore", (-6.6, 17, 0))
cube("right_hand", (-8.0, 14, -1.7), (2.8, 3, 3.4), "flesh", face="palm")
bone("right_claws", "right_hand", (-6.6, 14.2, -0.3))
for k, (dx, dz, ln) in enumerate(((-7.8, -1.7, 7.0), (-6.95, -1.7, 8.0), (-6.1, -1.7, 7.0))):
    cube("right_claws", (dx, 14.2 - ln, dz), (0.75, ln, 0.75), "claw", face="finger")
cube("right_claws", (-6.9, 10.8, 0.6), (0.75, 4.2, 0.75), "claw", face="finger")
# меч в правой руке: ось вдоль −z, рукоять в кулаке (только 2‑я фаза — прячется кодом)
SG = (-6.6, 15.4, -0.2)
bone("sword_r", "right_hand", SG)
sx, sy, sz = SG
cube("sword_r", (sx - 0.5, sy - 0.5, sz - 1.0), (1.0, 1.0, 4.4), "sword", face="grip")
cube("sword_r", (sx - 0.8, sy - 0.8, sz + 3.2), (1.6, 1.6, 1.4), "sword", face="pommel")
cube("sword_r", (sx - 0.55, sy - 2.6, sz - 1.8), (1.1, 5.2, 0.8), "sword", face="guard")
cube("sword_r", (sx - 0.3, sy - 1.0, sz - 22.0), (0.6, 2.0, 20.2), "sword", face="blade")
cube("sword_r", (sx - 0.2, sy - 0.6, sz - 25.0), (0.4, 1.2, 3.0), "sword", face="point")
M.mirror_x("right_arm", "left_arm")
M.mirror_x("right_fore", "left_fore", parent="left_arm")
M.mirror_x("right_hand", "left_hand", parent="left_fore")
M.mirror_x("right_claws", "left_claws", parent="left_hand")
M.mirror_x("sword_r", "sword_l", parent="left_hand")

M.pack()
M.finalize()


# =====================================================================
# 2. Текстуры: фаза 1 (голая плоть) и фаза 2 (чёрный лак, светящиеся прожилки)
# =====================================================================
def side(fname):
    return fname not in ("top", "bottom")


def sh(col, k):
    return tuple(clamp(ch * k) for ch in col[:3])


def face_pixel(face, i, j, phase):
    """Детальное лицо 24×32 (лоб, брови, миндалевидные глаза, нос, скулы, губы) и подбородок 22×13.
    Не «пиксельная» маска: мягкая светотень по овалу, свет сверху‑слева."""
    if face == "faceplate":
        W, H = 24, 32
        u = (i + 0.5) / W * 2 - 1          # −1..1 слева направо (как видит зритель)
        v = (j + 0.5) / H                  # 0 сверху .. 1 снизу
        x = abs(u)
    else:
        W, H = 22, 13
        u = (i + 0.5) / W * 2 - 1
        v = 1.0 + (j + 0.5) / H * 0.42     # продолжение лица ниже (1.0..1.42)
        x = abs(u)
    # основа: «фарфоровая» бордовая кожа, во 2‑й фазе — чёрный лак с алым подтоном
    base = mix(BORDEAUX, RED, 0.55) if phase == 1 else mix(BLACK, BORDEAUX, 0.55)
    hi = (196, 86, 92) if phase == 1 else (120, 40, 52)
    # объём: овал лица (темнее к краям и к подбородку), блик на лбу и скулах
    oval = 1.0 - 0.55 * x ** 2.2 - 0.25 * max(0.0, v - 0.85) ** 1.5
    light_k = 0.78 + 0.32 * oval + 0.08 * (-u) * (1 - v)
    col = sh(base, light_k)
    forehead = math.exp(-((v - 0.16) / 0.12) ** 2) * math.exp(-(u / 0.55) ** 2)
    cheek = math.exp(-((v - 0.58) / 0.07) ** 2) * math.exp(-((x - 0.58) / 0.14) ** 2)
    col = mix(col, hi, 0.45 * forehead + 0.55 * cheek)
    # впадины под скулами и у висков
    hollow = math.exp(-((v - 0.68) / 0.06) ** 2) * math.exp(-((x - 0.72) / 0.12) ** 2)
    col = mix(col, BORDEAUX_D, 0.55 * hollow)

    # орнамент на лбу: тонкие расходящиеся линии‑филигрань (2‑я фаза светится)
    if face == "faceplate" and 0.02 < v < 0.26:
        for k in (0.0, 0.33, 0.66):
            yy = 0.24 - (x * 0.45 + k * 0.12) - 0.05 * math.sin(x * 9 + k * 4)
            if abs(v - yy) < 0.012 and x < 0.8:
                return ("glow", mix(GLOW, RED, 0.3)) if phase == 2 else sh(BLACK_L, 1.1)
        if x < 0.05 and v > 0.12:
            return ("glow", GLOW) if phase == 2 else sh(BLACK, 1.0)
    # брови: тонкие изогнутые дуги
    if face == "faceplate":
        by = 0.34 - 0.06 * math.sin(min(1.0, max(0.0, (x - 0.12) / 0.78)) * math.pi) + 0.03 * x
        if 0.14 < x < 0.9 and abs(v - by) < 0.012 + 0.006 * (1 - x):
            return sh(BLACK, 1.0)
        # глаза: миндалевидные, слегка раскосые
        ex, ey = 0.47, 0.42 - 0.035 * (x - 0.47) / 0.3
        dx, dy = (x - ex) / 0.30, (v - ey) / 0.055
        r = dx * dx + dy * dy * (1.0 + 0.6 * dx)
        if r < 1.0:
            if abs(dx) < 0.12 and abs(dy) < 0.8:
                return ("glow", GLOW_HOT)                 # вертикальный зрачок
            if dx * dx + dy * dy < 0.3:
                return ("glow", GLOW)                     # радужка
            return sh(BLACK, 1.0)                         # чёрный белок
        if 1.0 <= r < 1.45 and dy < 0:
            return sh(BLACK_L, 0.8)                       # ресницы / веко
        if 1.0 <= r < 1.35 and dy >= 0:
            col = mix(col, BORDEAUX_D, 0.6)               # тень под глазом
        # кровавые слёзы от внутреннего уголка
        tx = 0.21 + 0.02 * math.sin(v * 30)
        if 0.47 < v < 0.8 and abs(x - tx) < 0.03 and u < 0:
            k = (v - 0.46) / 0.4
            return mix(SCARLET, mix(RED, BORDEAUX, 0.4), k) if phase == 1 else ("glow", mix(GLOW, RED, k * 0.6))
        # нос: светлая спинка, тень с одной стороны, крылья
        if 0.44 < v < 0.70:
            if -0.06 < u < 0.02:
                col = mix(col, hi, 0.35)
            elif 0.02 <= u < 0.12:
                col = mix(col, BORDEAUX_D, 0.4)
        if 0.66 < v < 0.72 and 0.06 < x < 0.17:
            return sh(BORDEAUX_D, 0.7)                    # ноздри
        # губы: тонкая верхняя (низ пластины лица)
        if v > 0.86:
            lip = mix(CRIMSON, SCARLET, 0.3) if phase == 1 else mix(BLACK, CRIMSON, 0.5)
            top = 0.905 - 0.02 * math.exp(-(x / 0.08) ** 2) + 0.025 * math.exp(-((x - 0.16) / 0.07) ** 2)
            if x < 0.4 and v > top + x * 0.06:            # верхняя губа с «луком Купидона»
                if v > 0.975:
                    return sh(BLACK, 1.0)                 # тонкий разрез рта
                return sh(lip, 0.85 + 0.2 * (1 - x))
    else:
        # подбородок: полная нижняя губа с бликом, ямка, кровавая капля
        lip = mix(CRIMSON, SCARLET, 0.35) if phase == 1 else mix(BLACK, CRIMSON, 0.55)
        if v < 1.14 and x < 0.40 - (v - 1.0) * 1.2:
            if v < 1.015:
                return sh(BLACK, 1.0)
            k = 1.0 + 0.35 * math.exp(-((v - 1.07) / 0.03) ** 2) * math.exp(-(u + 0.1) ** 2 / 0.02)
            return sh(lip, k)
        if abs(u - 0.12) < 0.035 and 1.12 < v < 1.38:
            return mix(SCARLET, RED, (v - 1.12) / 0.3) if phase == 1 else ("glow", mix(GLOW, RED, 0.4))
        if 1.2 < v < 1.27 and x < 0.15:
            col = mix(col, BORDEAUX_D, 0.4)               # ямка подбородка
    return col


def cloak_pixel(c, face, fname, i, j, w, h, phase, R):
    """Плащ: снаружи бархат с мягкими складками, внутри алая подкладка, рваный низ (во 2‑й фазе
    подол ровнее и с алой каймой)."""
    outer = BLACK if phase == 2 else mix(BLACK, BORDEAUX, 0.45)
    lining = SCARLET if phase == 1 else RED
    fold = 0.82 + 0.22 * math.sin(i * 0.9) + 0.06 * math.sin(i * 2.3 + 1)
    if face in ("cloak_top", "cloak_low"):
        low = face == "cloak_low"
        if fname in ("front", "back"):
            if low and j >= h - 3:
                tear = (i * 7 + 3) % 5 if phase == 1 else (i * 3) % 2
                if j >= h - 1 - tear * (1 if phase == 1 else 0):
                    return None                            # рваный край
            if phase == 1 and R.random() < 0.025 and 2 < j < h - 3:
                return None                                # прорехи
            if fname == "front":
                return mul(lining, (0.75 + 0.2 * math.sin(i * 0.9)) * (1 - 0.25 * j / h))
            col = mul(outer, fold * (1 - 0.15 * j / h))
            if phase == 2 and low and j in (h - 3, h - 4):
                return ("glow", mix(GLOW, RED, 0.35))      # алая кайма подола
            return mul(col, FACE_LIGHT[fname])
        return mul(BLACK_L if phase == 1 else BLACK, FACE_LIGHT[fname])
    if face == "mantle":
        # мантия: плотный мех/бархат, тёмный, с алой оторочкой снизу
        if side(fname) and j == h - 1:
            return mul(lining if phase == 1 else GLOW, 0.8) if phase == 1 else ("glow", mix(GLOW, RED, 0.4))
        col = mul(mix(BLACK, BORDEAUX, 0.35 if phase == 1 else 0.15), (0.85 + 0.3 * R.random()) * FACE_LIGHT[fname])
        return col
    if face == "collar":
        if fname == "front":
            return mul(lining, 0.85)
        if side(fname) and j == 0:
            return ("glow", GLOW) if phase == 2 else mul(BORDEAUX, FACE_LIGHT[fname])
        return mul(outer, FACE_LIGHT[fname] * 0.95)
    return mul(outer, FACE_LIGHT[fname])


def make_painter(model, phase, seed):
    PT = Painter(model.tex_w, model.tex_h, seed=seed)
    R = PT.rnd

    def lit(fname, col, k=1.0):
        return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.035))

    def flesh(fname, i, j, w, h, face):
        # мышечные волокна: вертикальные тяжи, впадины темнее, редкие блики «влажной» плоти
        fiber = (i * 5 + (j // 3) * 2) % 4
        col = RED if fiber else CRIMSON
        if (i + j * 3) % 11 == 0:
            col = SCARLET
        if (i * 7 + j * 5) % 23 == 0:
            col = SCARLET_L
        t = j / max(1, h - 1)
        col = mix(col, BORDEAUX, 0.35 * t)                  # к низу темнее
        if side(fname) and i in (0, w - 1):
            col = mix(col, BORDEAUX_D, 0.4)                 # края — тень
        if phase == 2:
            col = mix(col, BORDEAUX, 0.25)                  # во 2‑й фазе плоть темнее и глаже
            if face in ("thigh", "upper", "fore", "shin") and fname in ("front", "left", "right"):
                vein = (w // 2 + int(round(1.1 * math.sin(j * 0.7 + len(fname))))) % w
                if i == vein and j % 5 != 4:
                    return ("glow", mix(GLOW, RED, 0.35))  # светящиеся прожилки
        return lit(fname, col)

    def paint(c):
        mat, face = c["mat"], c.get("face")
        F = faces(c)
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if mat == "flesh":
                    if face == "ribcage" and fname in ("front", "back", "left", "right"):
                        # рёбра: светлые дуги на тёмной плоти, во 2‑й фазе — чёрные лаковые
                        rib = j % 2 == 1 and j < h - 1 and 0 < i < w - 1
                        if fname == "front" and i in (w // 2 - 1, w // 2):
                            rib = False
                        if rib:
                            return lit(fname, IVORY_D if phase == 1 else BLACK_L)
                        if phase == 2 and fname == "front" and 3 <= j <= 6 and 3 <= i <= 6:
                            # сердце горит сквозь рёбра
                            return ("glow", GLOW_HOT if (i, j) in ((4, 4), (5, 4), (4, 5)) else GLOW)
                        return lit(fname, mix(BORDEAUX, CRIMSON, 0.4 * PT.noise(0.4)))
                    if face == "abdomen" and fname == "front" and j % 2 == 0:
                        return lit(fname, mix(RED, BORDEAUX, 0.5))
                    if face == "pelvis" and phase == 2:
                        return lit(fname, BLACK if (i + j) % 5 else BLACK_L)
                    if face == "palm" and fname == "bottom":
                        return lit(fname, BORDEAUX_D)
                    return flesh(fname, i, j, w, h, face)
                if mat == "lacquer":
                    col = BLACK
                    if fname == "top" or (side(fname) and j == 0):
                        col = LACQUER_HI                         # блик лака
                    elif side(fname) and j == h - 1:
                        col = BORDEAUX
                    elif (i + j) % 7 == 0:
                        col = BLACK_L
                    if phase == 2 and side(fname) and face in ("pauldron", "sternum", "greave", "bracer", "belt"):
                        if (face == "belt" and j == 1 and i % 3 == 0) or (face != "belt" and i == w // 2 and 0 < j < h - 1):
                            return ("glow", GLOW)                    # алая филигрань
                    return lit(fname, col)
                if mat == "ribbon":
                    # лоскуты кожи: рваный низ, тёмная изнанка, во 2‑й фазе — чёрно‑алый шлейф
                    if fname in ("front", "back"):
                        tear = (i * 7 + 3) % 5
                        if j >= h - 1 - tear // 2:
                            return None
                        col = mix(RED, BORDEAUX, j / max(1, h)) if phase == 1 else (BLACK if j > 2 else BORDEAUX)
                        if phase == 2 and i in (0, w - 1):
                            col = CRIMSON
                        if fname == "back" and c["origin"][2] < 0 or fname == "front" and c["origin"][2] > 0:
                            col = mul(col, 0.7)
                        return lit(fname, col)
                    return lit(fname, BORDEAUX_D)
                if mat == "claw":
                    t = j / max(1, h - 1)
                    if face in ("finger", "toe"):
                        return lit(fname, mix(IVORY_D, BLACK, min(1.0, t * 1.4)) if phase == 1 else mix(BORDEAUX, BLACK, t))
                    return lit(fname, mix(BLACK_L, BLACK, t))
                if mat == "mask":
                    if face == "skull" and fname == "front":
                        # гладкая маска: раскосые щели глаз с огнём, кровавые слёзы, разрез рта
                        rows = ["MMMMMM",
                                "MMMMMM",
                                "EkMMkE",
                                "eEkkEe",
                                "kTMMTk",
                                "MTMMTM",
                                "MtMMtM",
                                "MLLLLM"]
                        ch = rows[j][i]
                        base = BORDEAUX if phase == 1 else mix(BLACK, BORDEAUX, 0.35)
                        if ch == "E":
                            return ("glow", GLOW)
                        if ch == "e":
                            return ("glow", GLOW_HOT)
                        if ch == "k" or ch == "L":
                            return lit(fname, BLACK)
                        if ch == "T":
                            return lit(fname, SCARLET if phase == 1 else ("glow", GLOW)[1])
                        if ch == "t":
                            return lit(fname, mix(SCARLET, base, 0.5))
                        if phase == 2 and j == 0 and i in (1, 4):
                            return ("glow", GLOW)
                        col = mul(base, 0.85) if i in (0, w - 1) else base
                        if j <= 1:
                            col = mix(col, LACQUER_HI, 0.25 if j == 0 else 0.1)   # блик на лбу
                        return lit(fname, col)
                    col = BORDEAUX if phase == 1 else mix(BLACK, BORDEAUX, 0.4)
                    return lit(fname, col, 1.0 - 0.1 * j / max(1, h))
                if mat == "jaw":
                    if fname == "front" or (side(fname) and fname != "back"):
                        if j == 0:
                            return lit(fname, IVORY if (i % 2 == 0) else IVORY_D)   # зубы
                        if j == 1 and i % 2 == 0 and fname == "front":
                            return lit(fname, IVORY_D)
                    if fname == "top":
                        if 0 < i < w - 1 and 0 < j < h - 1:
                            return ("glow", mix(GLOW, CRIMSON, 0.5)) if (i + j) % 3 == 0 else lit(fname, SCARLET)
                        return lit(fname, IVORY)
                    return lit(fname, BORDEAUX if phase == 1 else mix(BLACK, BORDEAUX, 0.4))
                if mat == "face_hi":
                    if fname != "front":
                        return lit(fname, BORDEAUX_D)
                    return face_pixel(face, i, j, phase)
                if mat == "cloak":
                    return cloak_pixel(c, face, fname, i, j, w, h, phase, R)
                if mat == "hair":
                    col = BLACK if (i * 3 + 1) % 4 else BLACK_L
                    if (i * 5) % 7 == 0:
                        col = mix(BLACK, CRIMSON, 0.5)                # алые пряди
                    if face in ("hair1", "hair2") and fname in ("front", "back") and j >= h - 1 and i % 2 == 0:
                        return None
                    return lit(fname, col)
                if mat == "halo":
                    t = j / max(1, h - 1)
                    if face == "halo" and side(fname) and t < 0.35:
                        return ("glow", GLOW_HOT if t < 0.15 else GLOW)
                    return lit(fname, mix(BLACK_L, BLACK, t))
                if mat == "sword":
                    if face == "blade" or face == "point":
                        if fname in ("left", "right"):
                            # плоскость клинка: чёрная сталь, алая кромка снизу/сверху, канавка
                            if j in (0, h - 1):
                                return ("glow", GLOW)
                            if j == h // 2 and face == "blade":
                                return lit(fname, mix(BLACK, CRIMSON, 0.6))
                            return lit(fname, BLACK if (i // 3) % 2 else BLACK_L)
                        if fname in ("top", "bottom"):
                            return ("glow", GLOW)
                        return lit(fname, RED)
                    if face == "pommel":
                        return ("glow", GLOW) if fname in ("back", "top") else lit(fname, CRIMSON)
                    if face == "guard":
                        return lit(fname, BLACK_L if j in (0, h - 1) else BLACK)
                    return lit(fname, CRIMSON if (i + j) % 2 else BORDEAUX)   # обмотка рукояти
                return (255, 0, 255)
            PT.fill(rect, fn)
    for c in model.cubes():
        paint(c)
    return PT


P1 = make_painter(M, 1, 71)
P2 = make_painter(M, 2, 71)


# =====================================================================
# 3. Анимации
# =====================================================================
A_BONES = ["root", "body", "torso", "neck", "head", "jaw", "face", "jaw_face", "cloak", "hair", "hair_tip", "halo",
           "right_arm", "right_fore", "right_hand", "right_claws", "sword_r",
           "left_arm", "left_fore", "left_hand", "left_claws", "sword_l",
           "right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot",
           "ribbon_fr", "ribbon_fl", "ribbon_br", "ribbon_bl"]

# Параметры позы. Ступни (rf/lf) — точка лодыжки в системе модели (Java: +y вниз, −z вперёд;
# земля y = 22 для лодыжки), toe_r/toe_l — наклон стопы. Кисти (rh/lh) — точка запястья в системе
# корпуса (от опоры корпуса на поясе). rdir/ldir — направление меча в системе модели (root).
# curl — сжатие когтей, jaw — раскрытие челюсти, sw/halo — масштаб мечей/венца.
DEFAULT = dict(
    root_y=0.0, root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0, 0), torso=(0, 0, 0), neck=(0, 0, 0),
    head=(0, 0, 0), jaw=4.0, hair=0.0,
    rf=(-2.8, 22, 0), lf=(2.8, 22, 0), toe_r=0.0, toe_l=0.0,
    rh=(-7.2, 9.5, -1.5), lh=(7.2, 9.5, -1.5), r_pole=(0.3, -0.2, 1), l_pole=(-0.3, -0.2, 1),
    rhand=(0, 0, 0), lhand=(0, 0, 0), rcurl=10.0, lcurl=10.0,
    rdir=(-0.3, 0.8, -0.5), ldir=(0.3, 0.8, -0.5), rroll=0.0, lroll=0.0, sw=1.0, halo=1.0, ribbon=0.0,
)


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


def keyed(keys, t, base=DEFAULT):
    full = []
    cur = dict(base)
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


def resolve(pr, model=M, swords=True):
    pose = {}
    pose["root"] = {"rot": (0, pr["root_y"], 0), "pos": pr["root_pos"]}
    pose["body"] = {"rot": pr["body"], "pos": pr["body_pos"]}
    pose["torso"] = {"rot": pr["torso"]}
    pose["neck"] = {"rot": pr["neck"]}
    pose["head"] = {"rot": pr["head"]}
    pose["jaw"] = {"rot": (pr["jaw"], 0, 0)}
    pose["face"] = {"rot": (0, 0, 0), "scale": (FACE_S,) * 3}
    pose["jaw_face"] = {"rot": (0, 0, 0), "scale": (FACE_S,) * 3}
    # плащ почти не анимируется: только немного свисает, когда корпус наклонён
    lean = pr["body"][0] + pr["torso"][0]
    pose["cloak"] = {"rot": (-lean * 0.45 + 3 + max(0.0, pr["ribbon"]) * 0.25, 0, 0)}
    pose["halo"] = {"rot": (0, 0, 0), "scale": (pr["halo"],) * 3}
    # волосы висят вниз, что бы ни делала голова
    pitch = pr["body"][0] + pr["torso"][0] + pr["neck"][0] + pr["head"][0]
    pose["hair"] = {"rot": (-pitch * 0.85 + pr["hair"], 0, 0)}
    pose["hair_tip"] = {"rot": (pr["hair"] * 0.6 + max(0.0, -pitch) * 0.1, 0, 0)}

    # ноги: лодыжка в точке rf/lf (система модели), колени вперёд, стопа по земле
    Rroot = model.chain_R("root", pose)
    for s, key, toe in (("right", "rf", "toe_r"), ("left", "lf", "toe_l")):
        tgt = model.world("root", [pr[key][0], pr[key][1] - 24, pr[key][2]], pose)   # опора root — на земле (y 24)
        model.two_bone(pose, s + "_leg", s + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN)
        model.set_world_rot(pose, s + "_foot", mm(Rroot, rx_m(math.radians(pr[toe]))))
    # лоскуты следуют за бёдрами
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    fl = pr["ribbon"] - pr["body"][0] * 0.9                  # лоскуты висят, даже если таз наклонён
    pose["ribbon_fr"] = {"rot": (min(rl, 0) * 0.85 - fl * 0.5, 0, 3)}
    pose["ribbon_fl"] = {"rot": (min(ll, 0) * 0.85 - fl * 0.5, 0, -3)}
    pose["ribbon_br"] = {"rot": (max(rl, 0) * 0.7 + fl + 3, 0, 4)}
    pose["ribbon_bl"] = {"rot": (max(ll, 0) * 0.7 + fl + 3, 0, -4)}

    # руки: запястье в точке (система корпуса)
    Rt = model.chain_R("torso", pose)
    for s, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        model.two_bone(pose, s + "_arm", s + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE)
    pose["right_hand"] = {"rot": pr["rhand"]}
    pose["left_hand"] = {"rot": pr["lhand"]}
    pose["right_claws"] = {"rot": (-pr["rcurl"], 0, 0)}
    pose["left_claws"] = {"rot": (-pr["lcurl"], 0, 0)}
    if swords:
        Rr = model.chain_R("root", pose)
        for b, key, roll in (("sword_r", "rdir", "rroll"), ("sword_l", "ldir", "lroll")):
            d = mv(Rr, list(norm(pr[key])))
            model.set_world_rot(pose, b, look_R(d, roll=pr[roll]))
            pose[b]["scale"] = (pr["sw"],) * 3
    return pose


def gait(w, stride, lift, base_y=22.0, width=2.8, z0=0.0):
    """Ступни по эллипсу: фаза w, шаг stride (вперёд‑назад), подъём lift."""
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l


# ---------- фаза 1: сгорбленный голодный зверь ----------
HUNCH = dict(body=(8, 0, 0), body_pos=(0, 3.2, 0), torso=(34, 0, 0), neck=(-8, 0, 0), head=(-30, 0, 0),
             rf=(-3.2, 22, 1.2), lf=(3.2, 22, -1.2), rh=(-7.8, 9.0, -9.5), lh=(7.8, 9.0, -9.5),
             r_pole=(0.6, -0.3, 1), l_pole=(-0.6, -0.3, 1), rhand=(-20, 0, 0), lhand=(-20, 0, 0),
             rcurl=24.0, lcurl=24.0, jaw=8.0, ribbon=2.0, sw=0.0, halo=0.0)


def idle1(t):
    w = 2 * math.pi * t / 2.6
    pr = P(HUNCH)
    pr["body_pos"] = (0, 3.2 + 0.4 * math.sin(w), 0)
    pr["torso"] = (34 + 2.5 * math.sin(w + 0.4), 0, 0)
    pr["head"] = (-30 + 3 * math.sin(w + 1.0), 6 * math.sin(w * 0.5), 3 * math.sin(w * 0.5 + 1))
    pr["jaw"] = 8 + 6 * max(0.0, math.sin(w * 2))
    pr["rh"] = (-7.8, 9.0 + 0.6 * math.sin(w + 0.6), -9.5)
    pr["lh"] = (7.8, 9.0 + 0.6 * math.sin(w + 0.9), -9.5)
    pr["rcurl"] = 24 + 10 * math.sin(w * 2)
    pr["lcurl"] = 24 + 10 * math.sin(w * 2 + 1.5)
    pr["hair"] = 3 * math.sin(w - 0.8)
    return resolve(pr)


def walk1(t):
    w = 2 * math.pi * t / 1.4
    pr = P(HUNCH)
    pr["rf"], pr["lf"] = gait(w, 4.5, 3.0, width=3.2)
    pr["body_pos"] = (0, 3.4 + 0.8 * abs(math.sin(w)), 0)
    pr["body"] = (8, 6 * math.sin(w), 4 * math.sin(w))
    pr["torso"] = (34, -6 * math.sin(w), -3 * math.sin(w))
    pr["head"] = (-30, 8 * math.sin(w), 4 * math.sin(w + 0.5))
    pr["rh"] = (-7.8, 8.5 + 1.0 * math.sin(w), -9.5 - 3.5 * math.sin(w))
    pr["lh"] = (7.8, 8.5 - 1.0 * math.sin(w), -9.5 + 3.5 * math.sin(w))
    pr["toe_r"] = -12 * max(0.0, math.sin(w))
    pr["toe_l"] = -12 * max(0.0, -math.sin(w))
    pr["jaw"] = 10 + 4 * math.sin(2 * w)
    pr["hair"] = 6 * math.sin(2 * w)
    pr["ribbon"] = 4 + 3 * math.sin(2 * w)
    return resolve(pr)


def run1(t):
    # тяжёлый галоп: корпус низко, когти почти касаются земли
    w = 2 * math.pi * t / 0.8
    pr = P(HUNCH, torso=(46, 0, 0), head=(-44, 0, 0), body_pos=(0, 4.2, 0), body=(12, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 7.5, 4.5, width=3.2)
    pr["body_pos"] = (0, 4.4 + 1.4 * abs(math.sin(w)), 0)
    pr["body"] = (12, 8 * math.sin(w), 0)
    pr["rh"] = (-8.2, 6.5 + 2.0 * math.sin(w + 2.4), -12 - 6 * math.sin(w + 2.4))
    pr["lh"] = (8.2, 6.5 - 2.0 * math.sin(w + 2.4), -12 + 6 * math.sin(w + 2.4))
    pr["toe_r"] = -18 * max(0.0, math.sin(w))
    pr["toe_l"] = -18 * max(0.0, -math.sin(w))
    pr["jaw"] = 18 + 6 * math.sin(2 * w)
    pr["hair"] = 14 + 6 * math.sin(2 * w)
    pr["ribbon"] = 18 + 6 * math.sin(2 * w)
    return resolve(pr)


# «Неуклюжий замах»: правая лапа заносится высоко за голову и рушится вниз наискось (удар на 1.0 с)
SWING = [
    (0.00, HUNCH, "lin"),
    (0.70, dict(torso=(10, -28, -10), body=(2, -14, 0), body_pos=(0, 2.4, 0), head=(-14, 18, 0),
                rh=(-9.5, -21, 5), r_pole=(0.2, 1, 0.6), rhand=(30, 0, 0), rcurl=-10.0,
                lh=(8, 6, -10), rf=(-3.4, 22, 2.6), lf=(3.0, 22, -3.0), jaw=26.0), "out"),
    (0.88, dict(torso=(6, -34, -12), rh=(-10, -22, 7.5), head=(-10, 22, 0)), "lin"),
    (1.02, dict(torso=(46, 30, 8), body=(14, 18, 0), body_pos=(0, 4.6, 0), head=(-38, -16, 0),
                rh=(4, 9, -14), r_pole=(1, 0, 0.6), rhand=(-40, 0, 0), rcurl=40.0,
                lh=(9, 4, -6), rf=(-3.4, 22, 3.6), lf=(3.0, 22, -5.0), jaw=12.0, hair=20.0, ribbon=10.0), "in"),
    (1.25, dict(torso=(50, 34, 10), rh=(5, 11, -12), hair=8.0), "out"),
    (1.80, HUNCH, "ease"),
]


def swing(t):
    return resolve(keyed(SWING, t))


# «Жадное пожирание»: подаётся вперёд, лапы по очереди быстро сгребают добычу в пасть (3 с)
DEVOUR_LEAN = dict(torso=(48, 0, 0), body=(12, 0, 0), body_pos=(0, 4.8, 0), head=(-34, 0, 0),
                   rf=(-3.4, 22, 2.8), lf=(3.4, 22, -2.2), jaw=30.0)


def devour(t):
    pr = keyed([(0.0, HUNCH, "lin"), (0.4, DEVOUR_LEAN, "out"), (3.3, DEVOUR_LEAN, "lin"), (3.6, HUNCH, "ease")], t)
    k = min(1.0, t / 0.4) * min(1.0, max(0.0, (3.6 - t) / 0.3))
    if k > 0:
        w = 2 * math.pi * (t - 0.4) / 0.3                  # одна лапа за 0.15 с
        a = math.sin(w)
        b = math.sin(w + math.pi)
        # лапа: вперёд‑вниз (схватить) → к пасти (сгрести)
        def paw(s, x):
            reach = (x * 1.0, 13.0 - 5.0 * (s * 0.5 + 0.5), -16.0 + 9.0 * (s * 0.5 + 0.5))
            return reach
        r = paw(a, -6.0)
        l = paw(b, 6.0)
        pr["rh"] = lerp_val(pr["rh"], r, k)
        pr["lh"] = lerp_val(pr["lh"], l, k)
        pr["rcurl"] = lerp(pr["rcurl"], 20 + 30 * (a * 0.5 + 0.5), k)
        pr["lcurl"] = lerp(pr["lcurl"], 20 + 30 * (b * 0.5 + 0.5), k)
        pr["jaw"] = lerp(pr["jaw"], 16 + 22 * abs(math.sin(w)), k)
        pr["head"] = (-34 + 6 * math.sin(w * 2) * k, 8 * math.sin(w) * k, 0)
        pr["torso"] = (48 + 3 * math.sin(w * 2) * k, 4 * math.sin(w) * k, 0)
        pr["hair"] = 4 * math.sin(w * 2)
    return resolve(pr)


# «Голодный бросок»: распрямляется во весь рост, руки в стороны, вопль; приседает и прыгает (на 1.0 с)
UNFURL = dict(body=(-10, 0, 0), body_pos=(0, -0.6, 0), torso=(-14, 0, 0), neck=(-12, 0, 0), head=(-18, 0, 0),
              rh=(-22, -14, 2), lh=(22, -14, 2), r_pole=(0, 0.4, 1), l_pole=(0, 0.4, 1), rhand=(10, 0, -30),
              lhand=(10, 0, 30), rcurl=-20.0, lcurl=-20.0, jaw=44.0, ribbon=-6.0, rf=(-4.2, 22, 0.5),
              lf=(4.2, 22, -0.5), hair=-8.0)
CROUCH = dict(body=(20, 0, 0), body_pos=(0, 7.5, 0), torso=(40, 0, 0), neck=(-14, 0, 0), head=(-40, 0, 0),
              rh=(-9, 4, -8), lh=(9, 4, -8), rhand=(-20, 0, 0), lhand=(-20, 0, 0), rcurl=30.0, lcurl=30.0, jaw=20.0,
              rf=(-3.6, 22, 3.5), lf=(3.6, 22, -2.5), ribbon=4.0)
POUNCE_START = [
    (0.00, HUNCH, "lin"),
    (0.55, UNFURL, "out"),
    (0.75, dict(UNFURL, head=(-24, 0, 0), jaw=50.0), "lin"),
    (1.00, CROUCH, "in"),
]
AIR = dict(body=(30, 0, 0), body_pos=(0, 0, 0), torso=(30, 0, 0), neck=(-20, 0, 0), head=(-36, 0, 0),
           rh=(-6.5, 1, -22), lh=(6.5, 1, -22), r_pole=(0.4, -1, 0.3), l_pole=(-0.4, -1, 0.3), rhand=(-60, 0, 0),
           lhand=(-60, 0, 0), rcurl=-14.0, lcurl=-14.0, jaw=46.0, rf=(-3.2, 14, 9), lf=(3.2, 12, 7), toe_r=40.0,
           toe_l=40.0, ribbon=40.0, hair=40.0)


def pounce_start(t):
    return resolve(keyed(POUNCE_START, t))


def pounce_air(t):
    w = 2 * math.pi * t / 0.6
    pr = P(HUNCH, **AIR)
    pr["rcurl"] = -14 + 8 * math.sin(w)
    pr["lcurl"] = -14 + 8 * math.sin(w + 1)
    pr["ribbon"] = 40 + 8 * math.sin(w)
    pr["hair"] = 40 + 6 * math.sin(w + 1)
    pr["jaw"] = 46 + 4 * math.sin(2 * w)
    return resolve(pr)


POUNCE_LAND = [
    (0.00, dict(HUNCH, body=(30, 0, 0), body_pos=(0, 9.5, 0), torso=(52, 0, 0), head=(-46, 0, 0),
                rh=(-9, 12, -14), lh=(9, 12, -14), rhand=(-40, 0, 0), lhand=(-40, 0, 0), rcurl=40.0, lcurl=40.0,
                jaw=36.0, rf=(-4.0, 22, 3.0), lf=(4.0, 22, -1.0), ribbon=30.0, hair=30.0), "lin"),
    (0.25, dict(body_pos=(0, 8.5, 0), ribbon=6.0, hair=6.0, rh=(-9, 13.5, -13), lh=(9, 13.5, -13)), "out"),
    (0.65, dict(body_pos=(0, 6.0, 0), torso=(46, 0, 0)), "ease"),
    (1.20, HUNCH, "ease"),
]


def pounce_land(t):
    return resolve(keyed(POUNCE_LAND, t))


# ---------- смена фазы: судороги → выпрямляется, мечи и венец появляются ----------
UPRIGHT = dict(body=(0, 0, 0), body_pos=(0, 0.3, 0), torso=(-3, 0, 0), neck=(2, 0, 0), head=(4, 0, 0),
               rf=(-2.9, 22, 0.8), lf=(2.9, 22, -0.8), rh=(-9.0, 7.0, -3.5), lh=(9.0, 7.0, -3.5),
               r_pole=(0.5, -0.2, 1), l_pole=(-0.5, -0.2, 1), rhand=(-10, 0, 0), lhand=(-10, 0, 0),
               rcurl=70.0, lcurl=70.0, rdir=(-0.45, 0.78, -0.45), ldir=(0.45, 0.78, -0.45), rroll=0.0, lroll=0.0,
               jaw=2.0, ribbon=0.0, sw=1.0, halo=1.0, hair=0.0)


def transform(t):
    keys = [
        (0.00, HUNCH, "lin"),
        (0.30, dict(torso=(54, 10, 8), head=(-50, -20, 0), jaw=40.0, body_pos=(0, 5.0, 0)), "out"),
        (0.55, dict(torso=(40, -14, -10), head=(-20, 24, 10)), "ease"),
        (0.80, dict(torso=(58, 8, 4), head=(-60, -10, 0), jaw=48.0), "ease"),
        (1.10, dict(CROUCH, sw=0.0, halo=0.0), "ease"),
        (2.00, dict(UPRIGHT, torso=(-14, 0, 0), head=(-26, 0, 0), neck=(-10, 0, 0), body_pos=(0, -0.2, 0),
                    rh=(-21, -10, 0), lh=(21, -10, 0), r_pole=(0, 0.5, 1), l_pole=(0, 0.5, 1), rhand=(0, 0, -20),
                    lhand=(0, 0, 20), rcurl=-10.0, lcurl=-10.0, jaw=36.0, sw=0.0, halo=0.0,
                    rdir=(-1, -0.3, 0), ldir=(1, -0.3, 0), ribbon=-10.0), "out"),
        (2.20, dict(rcurl=70.0, lcurl=70.0, sw=0.15), "lin"),
        (2.50, dict(sw=1.0, halo=1.0, jaw=20.0, head=(-20, 0, 0)), "out"),
        (3.00, UPRIGHT, "ease"),
    ]
    pr = keyed(keys, t)
    if t < 1.1:                                             # дрожь
        k = math.sin(math.pi * t / 1.1)
        pr["torso"] = add(list(pr["torso"]), [3 * math.sin(t * 70) * k, 4 * math.sin(t * 53) * k, 0])
        pr["head"] = add(list(pr["head"]), [6 * math.sin(t * 61) * k, 8 * math.sin(t * 47) * k, 0])
    return resolve(pr)


# ---------- фаза 2: выпрямленный, изящный мечник ----------
def idle2(t):
    w = 2 * math.pi * t / 3.0
    pr = P(UPRIGHT)
    pr["body_pos"] = (0, 0.3 + 0.3 * math.sin(w), 0)
    pr["torso"] = (-3 + 1.5 * math.sin(w + 0.5), 3 * math.sin(w * 0.5), 0)
    pr["head"] = (4 + 2 * math.sin(w + 1), -4 * math.sin(w * 0.5), 2 * math.sin(w))
    pr["rh"] = (-9.0, 7.0 + 0.4 * math.sin(w + 0.3), -3.5)
    pr["lh"] = (9.0, 7.0 + 0.4 * math.sin(w + 0.6), -3.5)
    pr["rdir"] = (-0.45, 0.78, -0.45 + 0.05 * math.sin(w))
    pr["ldir"] = (0.45, 0.78, -0.45 + 0.05 * math.sin(w + 1))
    pr["hair"] = 2 * math.sin(w - 0.8)
    pr["ribbon"] = 2 * math.sin(w - 0.4)
    return resolve(pr)


def walk2(t):
    w = 2 * math.pi * t / 1.2
    pr = P(UPRIGHT)
    pr["rf"], pr["lf"] = gait(w, 4.0, 2.4, width=2.4)
    pr["body_pos"] = (0, 0.6 + 0.5 * abs(math.sin(w)), 0)
    pr["body"] = (2, 5 * math.sin(w), 0)
    pr["torso"] = (-2, -7 * math.sin(w), 0)
    pr["head"] = (4, 2 * math.sin(w), 0)
    pr["rh"] = (-9.0, 7.0, -3.5 - 2.0 * math.sin(w))
    pr["lh"] = (9.0, 7.0, -3.5 + 2.0 * math.sin(w))
    pr["toe_r"] = -10 * max(0.0, math.sin(w))
    pr["toe_l"] = -10 * max(0.0, -math.sin(w))
    pr["ribbon"] = 6 + 3 * math.sin(2 * w)
    pr["hair"] = 4 * math.sin(2 * w)
    return resolve(pr)


RUN2 = dict(UPRIGHT, body=(14, 0, 0), torso=(8, 0, 0), head=(-14, 0, 0), rh=(-10, 5, 4), lh=(10, 5, 4),
            rdir=(-0.4, 0.2, 1), ldir=(0.4, 0.2, 1), ribbon=30.0, hair=26.0)


def run2(t):
    w = 2 * math.pi * t / 0.7
    pr = P(RUN2)
    pr["rf"], pr["lf"] = gait(w, 8.0, 4.5, width=2.6)
    pr["body_pos"] = (0, 1.4 + 1.2 * abs(math.sin(w)), 0)
    pr["body"] = (14, 6 * math.sin(w), 0)
    pr["toe_r"] = -20 * max(0.0, math.sin(w))
    pr["toe_l"] = -20 * max(0.0, -math.sin(w))
    pr["ribbon"] = 30 + 6 * math.sin(2 * w)
    pr["hair"] = 26 + 5 * math.sin(2 * w)
    return resolve(pr)


# «Двойной разрез»: правый меч справа налево (0.45 с), левый слева направо (0.8 с)
SLASH = [
    (0.00, UPRIGHT, "lin"),
    (0.30, dict(torso=(0, -34, 0), body=(0, -12, 0), head=(0, 24, 0), rh=(-15, -6, 6), r_pole=(0.2, 1, 0.4),
                rdir=(-0.8, -0.2, 0.55), lh=(7, 5, -8), ldir=(0.2, 0.4, -1), rf=(-3.2, 22, 2.8),
                lf=(3.0, 22, -2.4), body_pos=(0, 1.2, 0)), "out"),
    (0.45, dict(torso=(6, 34, 0), body=(4, 16, 0), head=(-4, -20, 0), rh=(6, 3, -14), r_pole=(1, 0, 0.6),
                rdir=(0.85, 0.1, -0.5), rroll=-30.0, ribbon=10.0, hair=10.0), "in"),
    (0.58, dict(lh=(15, -6, 6), l_pole=(-0.2, 1, 0.4), ldir=(0.8, -0.2, 0.55), rh=(4, 6, -10)), "out"),
    (0.80, dict(torso=(6, -32, 0), body=(4, -14, 0), head=(-4, 20, 0), lh=(-6, 3, -14), l_pole=(-1, 0, 0.6),
                ldir=(-0.85, 0.1, -0.5), lroll=30.0, rf=(-3.0, 22, -2.4), lf=(3.2, 22, 3.0)), "in"),
    (1.00, dict(ribbon=4.0, hair=4.0), "lin"),
    (1.60, UPRIGHT, "ease"),
]


def slash(t):
    return resolve(keyed(SLASH, t))


# «Пронзающий выпад»: оба меча назад, рывок вперёд, двойной укол (попадание 0.65 с)
THRUST = [
    (0.00, UPRIGHT, "lin"),
    (0.45, dict(body=(6, 0, 0), body_pos=(0, 3.5, 0), torso=(4, 0, 0), head=(-8, 0, 0), rh=(-8, 4, 7),
                lh=(8, 4, 7), rdir=(-0.05, 0.05, -1), ldir=(0.05, 0.05, -1), rf=(-3.0, 22, -4.0),
                lf=(3.0, 22, 4.0), ribbon=-4.0), "out"),
    (0.65, dict(body=(24, 0, 0), body_pos=(0, 3.0, 0), torso=(12, 0, 0), head=(-26, 0, 0), rh=(-3.5, -1, -17),
                lh=(3.5, -1, -17), r_pole=(1, 0, 0.2), l_pole=(-1, 0, 0.2), rdir=(0.05, 0.05, -1),
                ldir=(-0.05, 0.05, -1), rf=(-3.0, 22, 6.0), lf=(3.0, 22, -7.0), ribbon=30.0, hair=24.0), "in"),
    (0.85, dict(ribbon=12.0, hair=10.0), "lin"),
    (1.30, UPRIGHT, "ease"),
]


def thrust(t):
    return resolve(keyed(THRUST, t))


# «Зов выводка»: мечи скрещены над головой, затем вонзаются вниз — из крови встают мелкие (1.3 с)
SUMMON = [
    (0.00, UPRIGHT, "lin"),
    (0.70, dict(torso=(-12, 0, 0), head=(-24, 0, 0), neck=(-8, 0, 0), rh=(-3, -22, -4), lh=(3, -22, -4),
                r_pole=(1, 0.3, 0.3), l_pole=(-1, 0.3, 0.3), rdir=(0.6, -0.8, 0.1), ldir=(-0.6, -0.8, 0.1),
                jaw=30.0, body_pos=(0, -0.4, 0)), "out"),
    (1.00, dict(jaw=40.0), "lin"),
    (1.30, dict(body=(16, 0, 0), torso=(20, 0, 0), head=(-20, 0, 0), body_pos=(0, 5.5, 0), rh=(-8, 12, -9),
                lh=(8, 12, -9), r_pole=(0.6, 0, 1), l_pole=(-0.6, 0, 1), rdir=(-0.15, 1, -0.2),
                ldir=(0.15, 1, -0.2), rf=(-3.4, 22, 2.0), lf=(3.4, 22, -2.0), jaw=16.0, ribbon=14.0), "in"),
    (1.55, dict(body_pos=(0, 5.0, 0)), "lin"),
    (2.00, UPRIGHT, "ease"),
]


def summon(t):
    return resolve(keyed(SUMMON, t))


# «Кровавая кладка»: опускается на колено, вонзает оба меча в землю (1.0 с) — вокруг вырастают яйца
EGGS = [
    (0.00, UPRIGHT, "lin"),
    (0.55, dict(torso=(-6, 0, 0), head=(-12, 0, 0), rh=(-6, -16, -8), lh=(6, -16, -8), rdir=(-0.2, -1, -0.1),
                ldir=(0.2, -1, -0.1), r_pole=(1, 0, 0.5), l_pole=(-1, 0, 0.5), jaw=20.0), "out"),
    (1.00, dict(body=(20, 0, 0), body_pos=(0, 9.5, 0), torso=(26, 0, 0), head=(-22, 0, 0), rh=(-7, 14, -10),
                lh=(7, 14, -10), r_pole=(0.6, 0, 1), l_pole=(-0.6, 0, 1), rdir=(-0.05, 1, -0.1),
                ldir=(0.05, 1, -0.1), rf=(-3.0, 22, -3.5), lf=(3.0, 22, 5.0), toe_l=30.0, jaw=34.0,
                ribbon=10.0), "in"),
    (1.70, dict(body_pos=(0, 9.0, 0), head=(-30, 0, 0)), "lin"),
    (2.20, UPRIGHT, "ease"),
]


def eggs(t):
    return resolve(keyed(EGGS, t))


# «Багровый вальс» (мощная): раскрывается — вихрь с мечами в стороны (цикл) — финальный крестовой разрез
WALTZ_OPEN = dict(UPRIGHT, torso=(-6, 0, 0), head=(-6, 0, 0), rh=(-20, -6, -2), lh=(20, -6, -2),
                  r_pole=(0, 0.6, 1), l_pole=(0, 0.6, 1), rdir=(-1, 0.05, -0.25), ldir=(1, 0.05, 0.25),
                  rroll=-80.0, lroll=80.0, body_pos=(0, 0.8, 0), ribbon=12.0, hair=10.0)


def waltz_start(t):
    return resolve(keyed([(0.0, UPRIGHT, "lin"), (0.45, dict(WALTZ_OPEN, root_y=40.0), "out"),
                          (0.6, dict(root_y=0.0), "in")], t))


def waltz_spin(t):
    w = 2 * math.pi * t / 0.4
    pr = P(WALTZ_OPEN, root_y=-360.0 * t / 0.4)
    pr["rf"], pr["lf"] = gait(w, 2.0, 2.0, width=3.0)
    pr["body_pos"] = (0, 0.8 + 0.4 * abs(math.sin(w)), 0)
    pr["ribbon"] = 44 + 6 * math.sin(w)
    pr["hair"] = 34 + 6 * math.sin(w)
    pr["torso"] = (-6, 0, 4 * math.sin(w))
    return resolve(pr)


WALTZ_END = [
    (0.00, WALTZ_OPEN, "lin"),
    (0.30, dict(torso=(-20, 0, 0), head=(-20, 0, 0), rh=(-10, -20, 2), lh=(10, -20, 2), r_pole=(0.5, 0.4, 1),
                l_pole=(-0.5, 0.4, 1), rdir=(0.5, -0.8, 0.4), ldir=(-0.5, -0.8, 0.4), rroll=0.0, lroll=0.0,
                body_pos=(0, -0.6, 0), jaw=34.0, ribbon=-4.0, hair=-4.0), "out"),
    (0.50, dict(body=(20, 0, 0), torso=(26, 0, 0), head=(-30, 0, 0), body_pos=(0, 5.0, 0), rh=(5, 10, -14),
                lh=(-5, 10, -14), r_pole=(1, 0, 0.5), l_pole=(-1, 0, 0.5), rdir=(0.75, 0.55, -0.4),
                ldir=(-0.75, 0.55, -0.4), rf=(-3.2, 22, 3.0), lf=(3.2, 22, -5.0), ribbon=34.0, hair=30.0), "in"),
    (0.75, dict(body_pos=(0, 4.6, 0), ribbon=16.0, hair=12.0), "lin"),
    (1.40, UPRIGHT, "ease"),
]


def waltz_end(t):
    return resolve(keyed(WALTZ_END, t))


animations = {
    "idle1": bake(idle1, 2.6, 20, True, A_BONES),
    "walk1": bake(walk1, 1.4, 20, True, A_BONES),
    "run1": bake(run1, 0.8, 30, True, A_BONES),
    "swing": bake(swing, 1.8, 40, False, A_BONES),
    "devour": bake(devour, 3.6, 40, False, A_BONES),
    "pounce_start": bake(pounce_start, 1.0, 40, False, A_BONES),
    "pounce_air": bake(pounce_air, 0.6, 20, True, A_BONES),
    "pounce_land": bake(pounce_land, 1.2, 40, False, A_BONES),
    "transform": bake(transform, 3.0, 40, False, A_BONES),
    "idle2": bake(idle2, 3.0, 20, True, A_BONES),
    "walk2": bake(walk2, 1.2, 20, True, A_BONES),
    "run2": bake(run2, 0.7, 30, True, A_BONES),
    "slash": bake(slash, 1.6, 40, False, A_BONES),
    "thrust": bake(thrust, 1.3, 40, False, A_BONES),
    "summon": bake(summon, 2.0, 40, False, A_BONES),
    "eggs": bake(eggs, 2.2, 40, False, A_BONES),
    "waltz_start": bake(waltz_start, 0.6, 40, False, A_BONES),
    "waltz_spin": bake(waltz_spin, 0.4, 40, True, A_BONES),
    "waltz_end": bake(waltz_end, 1.4, 40, False, A_BONES),
}
animations = {"animation.%s.%s" % (NAME, k): v for k, v in animations.items()}


# =====================================================================
# 4. Мелкий кровожад (bloodling): сгорбленный бесёнок из плоти, 0.7×1.2
# =====================================================================
LN = "bloodling"
L = Model(LN, 64, 64)
lb, lc = L.bone, L.cube
lb("root", None, (0, 0, 0))
lb("body", "root", (0, 9, 0))
lc("body", (-2.5, 8, -1.8), (5, 3, 3.6), "flesh", face="pelvis")
lb("torso", "body", (0, 10, 0))
lc("torso", (-3.2, 10, -2.2), (6.4, 6, 4.4), "flesh", face="ribcage")
lc("torso", (-0.4, 13, 2.0), (0.8, 2.2, 0.8), "claw", face="spike", rot=(-40, 0, 0), pivot=(0, 13, 2.4))
lc("torso", (-0.4, 10.8, 1.9), (0.8, 1.8, 0.8), "claw", face="spike", rot=(-40, 0, 0), pivot=(0, 10.8, 2.3))
lb("head", "torso", (0, 16, -1.0))
lc("head", (-2.5, 16.5, -5.0), (5, 4.5, 5), "mask", face="skull")
lb("jaw", "head", (0, 17.2, -0.5))
lc("jaw", (-2.3, 15.0, -5.2), (4.6, 2.2, 4.6), "jaw", face="jaw")
for nm, px, s in (("right", -3.6, -1), ("left", 3.6, 1)):
    lb(nm + "_arm", "torso", (px, 15.2, -0.5))
    lc(nm + "_arm", (px - 0.9, 8.5, -1.4), (1.8, 6.7, 1.8), "flesh", face="upper")
    lb(nm + "_fore", nm + "_arm", (px, 8.5, -0.5))
    lc(nm + "_fore", (px - 0.8, 3.5, -1.3), (1.6, 5, 1.6), "flesh", face="fore")
    for k in range(3):
        lc(nm + "_fore", (px - 0.75 + 0.55 * k, 0.5, -1.4), (0.5, 3.2, 0.5), "claw", face="finger")
    lb(nm + "_leg", "body", (px * 0.5, 8.5, 0))
    lc(nm + "_leg", (px * 0.5 - 1, 4, -1), (2, 4.5, 2), "flesh", face="thigh")
    lb(nm + "_shin", nm + "_leg", (px * 0.5, 4, 0))
    lc(nm + "_shin", (px * 0.5 - 0.9, 0, -0.9), (1.8, 4, 1.8), "flesh", face="shin")
    lc(nm + "_shin", (px * 0.5 - 1, 0, -2.6), (2, 1, 1.8), "claw", face="toe")
L.pack()
L.finalize()
LP = make_painter(L, 1, 13)

L_BONES = ["root", "body", "torso", "head", "jaw", "right_arm", "right_fore", "left_arm", "left_fore",
           "right_leg", "right_shin", "left_leg", "left_shin"]


def lpose(body=(0, 0, 0), bpos=(0, 0, 0), torso=(30, 0, 0), head=(-30, 0, 0), jaw=8.0, ra=(-30, 0, 10),
          rf=(-30, 0, 0), la=(-30, 0, -10), lf=(-30, 0, 0), rl=(-10, 20), ll=(-10, 20)):
    return {"root": {"rot": (0, 0, 0)}, "body": {"rot": body, "pos": bpos}, "torso": {"rot": torso},
            "head": {"rot": head}, "jaw": {"rot": (jaw, 0, 0)}, "right_arm": {"rot": ra}, "right_fore": {"rot": rf},
            "left_arm": {"rot": la}, "left_fore": {"rot": lf},
            "right_leg": {"rot": (rl[0], 0, 0)}, "right_shin": {"rot": (rl[1], 0, 0)},
            "left_leg": {"rot": (ll[0], 0, 0)}, "left_shin": {"rot": (ll[1], 0, 0)}}


def l_idle(t):
    w = 2 * math.pi * t / 1.6
    return lpose(bpos=(0, 0.6 + 0.3 * math.sin(w), 0), torso=(30 + 3 * math.sin(w), 0, 0),
                 head=(-30 + 5 * math.sin(w + 1), 10 * math.sin(w * 0.5), 0), jaw=8 + 8 * max(0.0, math.sin(2 * w)),
                 ra=(-30 + 6 * math.sin(w), 0, 10), la=(-30 + 6 * math.sin(w + 1), 0, -10), rl=(-14, 26), ll=(-14, 26))


def l_walk(t):
    w = 2 * math.pi * t / 0.6
    s = math.sin(w)
    return lpose(bpos=(0, 0.8 - 0.6 * abs(s), 0), body=(0, 8 * s, 0), torso=(36, -8 * s, 0), head=(-36, 6 * s, 0),
                 jaw=12 + 6 * math.sin(2 * w), ra=(-36 + 30 * s, 0, 10), la=(-36 - 30 * s, 0, -10),
                 rl=(-10 - 32 * s, 20 + 24 * max(0.0, math.sin(w + 0.9))),
                 ll=(-10 + 32 * s, 20 + 24 * max(0.0, -math.sin(w + 0.9))))


def l_attack(t):
    # оба когтя вскидываются и рвут вниз (удар на 0.25 с)
    k = ease_out(min(1.0, t / 0.18)) if t < 0.18 else 1.0 - ease(min(1.0, (t - 0.18) / 0.25))
    hit = ease_in(min(1.0, max(0.0, (t - 0.12) / 0.14)))
    rec = ease(min(1.0, max(0.0, (t - 0.32) / 0.28)))
    a = lerp(-30, -150, k) if t < 0.18 else lerp(lerp(-150, -10, hit), -30, rec)
    return lpose(torso=(lerp(20, 44, hit) if t < 0.32 else lerp(44, 30, rec), 0, 0), jaw=lerp(8, 30, k),
                 ra=(a, 0, 14), la=(a, 0, -14), rf=(-30 + 20 * k, 0, 0), lf=(-30 + 20 * k, 0, 0), rl=(-20, 30),
                 ll=(-4, 14))


l_animations = {
    "animation.%s.idle" % LN: bake(l_idle, 1.6, 20, True, L_BONES),
    "animation.%s.walk" % LN: bake(l_walk, 0.6, 30, True, L_BONES),
    "animation.%s.attack" % LN: bake(l_attack, 0.6, 40, False, L_BONES),
}


# =====================================================================
# 5. Яйцо кровожада (blood_egg): пульсирующий кокон из плоти, 1.0×1.4
# =====================================================================
EN = "blood_egg"
E = Model(EN, 128, 64)
eb, ec = E.bone, E.cube
eb("root", None, (0, 0, 0))
ec("root", (-5, 0, -5), (10, 2, 10), "flesh", face="roots")
eb("egg", "root", (0, 2, 0))
ec("egg", (-5, 2, -5), (10, 5, 10), "egg", face="egg_low")
ec("egg", (-5.5, 6, -5.5), (11, 9, 11), "egg", face="egg_mid")
ec("egg", (-4.5, 14.5, -4.5), (9, 5, 9), "egg", face="egg_top")
ec("egg", (-2.8, 19, -2.8), (5.6, 2.4, 5.6), "egg", face="egg_cap")
for k in range(6):                                         # жилы‑корни, расползающиеся по земле
    a = k * 60 + 15
    ec("root", (-0.6, 0, 5.0), (1.2, 1.0, 4.0), "flesh", face="vein", rot=(0, a, 0), pivot=(0, 0, 0))
E.pack()
E.finalize()


def paint_egg(PT):
    def lit(fname, col, k=1.0):
        return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.04))
    for c in E.cubes():
        F = faces(c)
        face = c.get("face")
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname, face=face):
                if face == "vein":
                    return ("glow", mix(GLOW, RED, 0.5)) if (i + j) % 3 == 0 and fname == "top" else lit(fname, RED)
                if face == "roots":
                    if fname == "top":
                        return lit(fname, mix(BORDEAUX, RED, 0.5 * PT.noise(0.6)))
                    return lit(fname, BORDEAUX_D)
                # оболочка: тёмная плёнка, сквозь неё светятся жилы и зародыш
                col = mix(BORDEAUX, CRIMSON, 0.5 + 0.5 * math.sin(i * 1.3 + j * 0.7))
                vein = (i * 2 + j * 3) % 7 == 0 or (i * 5 - j * 2) % 11 == 0
                if vein and side(fname):
                    return ("glow", GLOW if (i + j) % 2 else mix(GLOW, SCARLET, 0.5))
                if face == "egg_mid" and fname == "front" and 3 <= i <= 7 and 2 <= j <= 6:
                    return ("glow", GLOW_HOT if (i, j) in ((5, 4), (5, 3)) else mix(GLOW, CRIMSON, 0.35))
                if (i * 7 + j * 3) % 13 == 0:
                    col = SCARLET_L                                  # влажный блик
                return lit(fname, col)
            PT.fill(rect, fn)


EP = Painter(E.tex_w, E.tex_h, seed=5)
paint_egg(EP)

E_BONES = ["egg"]


def e_idle(t):
    # сердцебиение: два толчка за 1.2 с
    beat = math.exp(-((t - 0.1) / 0.06) ** 2) + 0.6 * math.exp(-((t - 0.32) / 0.07) ** 2)
    s = 1 + 0.07 * beat
    return {"egg": {"rot": (0, 0, 0), "scale": (s, 1 + 0.05 * beat, s)}}


e_animations = {"animation.%s.idle" % EN: bake(e_idle, 1.2, 20, True, E_BONES)}


# =====================================================================
# 6. Текстуры: блок «кровавая лужа», иконка не нужна
# =====================================================================
def pool_texture():
    PT = Painter(16, 16, seed=9)
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.9 + y * 0.4) + math.sin(y * 1.1 - x * 0.5) + (PT.rnd.random() - 0.5)
            col = mix(BORDEAUX, RED, 0.5 + 0.25 * v)
            if v > 1.4:
                col = SCARLET_L                                  # блики на поверхности
            elif v < -1.2:
                col = BORDEAUX_D
            PT.put(x, y, col)
    PT.img.save(os.path.join(ROOT, "textures", "block", "blood_pool.png"))


if __name__ == "__main__":
    M.write_geo(ROOT)
    write_anims(ROOT, NAME, animations)
    P1.img.save(os.path.join(ROOT, "textures", "entity", NAME + ".png"))
    P1.glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_glowmask.png"))
    P2.img.save(os.path.join(ROOT, "textures", "entity", NAME + "_p2.png"))
    P2.glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_p2_glowmask.png"))
    L.write_geo(ROOT)
    write_anims(ROOT, LN, l_animations)
    LP.img.save(os.path.join(ROOT, "textures", "entity", LN + ".png"))
    LP.glow.save(os.path.join(ROOT, "textures", "entity", LN + "_glowmask.png"))
    E.write_geo(ROOT)
    write_anims(ROOT, EN, e_animations)
    EP.img.save(os.path.join(ROOT, "textures", "entity", EN + ".png"))
    EP.glow.save(os.path.join(ROOT, "textures", "entity", EN + "_glowmask.png"))
    pool_texture()
    print("готово:", NAME, len(M.cubes()), "кубов;", LN, len(L.cubes()), ";", EN, len(E.cubes()))
