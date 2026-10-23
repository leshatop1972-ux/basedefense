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

from mobgen import (FACE_LIGHT, Model, Painter, add, bake, clamp, cross, dot, ease, ease_in, ease_out, faces, length,
                    lerp, look_R, mix, mm, mul, mv, norm, rx_m, ry_m, scl, sub, tr, write_anims)

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
# кольцо‑венец на лбу с камнем (как на скрине), три шипа над ним
RC = (0, 59.4, -4.6)
for k in range(12):
    cube("head", (RC[0] - 0.7, RC[1] + 2.0, RC[2] - 0.45), (1.4, 0.75, 0.9), "bone", face="ring",
         rot=(0, 0, k * 30), pivot=RC)
cube("head", (-1.1, 58.3, -5.1), (2.2, 2.2, 0.9), "gem", face="gem")
for k, a in enumerate((-30, 0, 30)):
    cube("head", (-0.4, 61.8, -4.9), (0.8, 2.2 + (1.2 if a == 0 else 0), 0.8), "bone", face="crest_spike",
         rot=(0, 0, a), pivot=(0, 61.4, -4.5))
# рога‑серпы: три звена, из висков вбок и вверх (левый строится, правый — зеркально)
bone("horn_l", "head", (3.0, 54.0, -0.6))
px, py = 3.0, 54.0
for ln, th, ang in ((4.4, 2.2, 8), (4.0, 1.8, 30), (3.6, 1.3, 58), (2.6, 0.8, 88)):
    cube("horn_l", (px, py - th / 2, -0.6 - th / 2), (ln, th, th), "bone", face="horn",
         rot=(0, 0, -ang), pivot=(px, py, -0.6))
    px += ln * math.cos(math.radians(ang)) * 0.92
    py += ln * math.sin(math.radians(ang)) * 0.92
M.mirror_x("horn_l", "horn_r")
# клыки‑жвалы по бокам челюсти
cube("jaw", (-2.9, 43.6, -4.6), (0.8, 3.0, 0.8), "bone", face="fang", rot=(18, 0, 8), pivot=(-2.5, 46.4, -4.2))
cube("jaw", (2.1, 43.6, -4.6), (0.8, 3.0, 0.8), "bone", face="fang", rot=(18, 0, -8), pivot=(2.5, 46.4, -4.2))
# волосы: два звена, свисают по спине
bone("hair", "head", (0, 55.5, 3.0))
cube("hair", (-3.2, 43.5, 3.0), (6.4, 12.5, 1.2), "hair", face="hair1")
cube("hair", (-3.4, 49.5, -3.4), (0.9, 7.5, 6.2), "hair", face="hair_side")
cube("hair", (2.5, 49.5, -3.4), (0.9, 7.5, 6.2), "hair", face="hair_side")
bone("hair_tip", "hair", (0, 43.5, 3.3))
cube("hair_tip", (-3.2, 33.5, 2.8), (6.4, 10, 1.0), "hair", face="hair2")
# большой широкий плащ: мантия на плечах, высокий воротник, полотнище до колен (почти без анимации)
bone("cloak", "torso", (0, 44, 3.6))
# полотнище — два прямоугольника («квадратный» плащ), низ чуть шире и отходит от тела
cube("cloak", (-9.5, 29, 3.3), (19, 15, 0.8), "cloak", face="cloak_top")
cube("cloak", (-11.5, 6, 3.7), (23, 23.5, 0.8), "cloak", face="cloak_low", rot=(10, 0, 0), pivot=(0, 29.5, 3.7))
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
cube("right_arm", (-8.2, 29, -1.6), (3.2, 13, 3.2), "flesh", face="upper")
cube("right_arm", (-8.4, 35.5, -1.8), (3.6, 1.2, 3.6), "lacquer", face="band")
bone("right_fore", "right_arm", (-6.6, 29, 0))
cube("right_fore", (-8.1, 17, -1.5), (3.0, 12, 3.0), "flesh", face="fore")
cube("right_fore", (-8.2, 17.2, -1.6), (3.2, 3.2, 3.2), "lacquer", face="bracer")
cube("right_fore", (-7.2, 28.0, 1.0), (1.0, 1.0, 2.4), "claw", face="spike", rot=(30, 0, 0), pivot=(-6.7, 28.5, 1.2))
bone("right_hand", "right_fore", (-6.6, 17, 0))
cube("right_hand", (-8.0, 14, -1.7), (2.8, 3, 3.4), "flesh", face="palm")
bone("right_claws", "right_hand", (-6.6, 14.2, -0.3))
for k, (dx, ln) in enumerate(((-8.0, 9.0), (-7.0, 10.5), (-6.0, 9.0))):
    cube("right_claws", (dx, 14.2 - ln, -1.9), (0.95, ln, 0.95), "claw", face="finger",
         rot=(-12, 0, (k - 1) * 6), pivot=(dx + 0.47, 14.2, -1.4))
cube("right_claws", (-7.0, 9.4, 0.4), (0.9, 4.8, 0.9), "claw", face="finger", rot=(25, 0, 0), pivot=(-6.6, 14.2, 0.8))
# меч в правой руке: ось вдоль −z, рукоять в кулаке (только 2‑я фаза — прячется кодом)
SG = (-6.6, 15.4, -0.2)
bone("sword_r", "right_hand", SG)
sx, sy, sz = SG
cube("sword_r", (sx - 0.55, sy - 0.55, sz - 1.2), (1.1, 1.1, 5.0), "sword", face="grip")
cube("sword_r", (sx - 0.9, sy - 0.9, sz + 3.6), (1.8, 1.8, 1.6), "sword", face="pommel")
cube("sword_r", (sx - 0.3, sy - 0.3, sz + 5.0), (0.6, 0.6, 1.6), "sword", face="pommel_spike")
# крестовина: широкая, концы загнуты к клинку
cube("sword_r", (sx - 0.65, sy - 4.2, sz - 2.4), (1.3, 8.4, 1.2), "sword", face="guard")
cube("sword_r", (sx - 0.5, sy + 3.6, sz - 4.6), (1.0, 1.0, 2.6), "sword", face="guard_tip", rot=(-25, 0, 0),
     pivot=(sx, sy + 4.0, sz - 2.0))
cube("sword_r", (sx - 0.5, sy - 4.6, sz - 4.6), (1.0, 1.0, 2.6), "sword", face="guard_tip", rot=(25, 0, 0),
     pivot=(sx, sy - 4.0, sz - 2.0))
cube("sword_r", (sx - 0.8, sy - 0.8, sz - 3.4), (1.6, 1.6, 1.2), "gem", face="sword_gem")
# клинок: широкий у основания, сужается к острию (4 части), зубцы по обуху
cube("sword_r", (sx - 0.35, sy - 1.8, sz - 13.4), (0.7, 3.6, 10.2), "sword", face="blade")
cube("sword_r", (sx - 0.3, sy - 1.45, sz - 25.4), (0.6, 2.9, 12.0), "sword", face="blade")
cube("sword_r", (sx - 0.25, sy - 0.95, sz - 31.4), (0.5, 1.9, 6.0), "sword", face="blade")
cube("sword_r", (sx - 0.2, sy - 0.45, sz - 34.4), (0.4, 0.9, 3.0), "sword", face="point")
for z in (-8.0, -14.0, -20.0):
    cube("sword_r", (sx - 0.2, sy + 1.2, sz + z), (0.4, 1.4, 1.4), "sword", face="serration", rot=(45, 0, 0),
         pivot=(sx, sy + 1.6, sz + z + 0.7))
SWORD_TIP = 34.4
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


BONE_W = (232, 206, 200)          # бледная кость с розовым отливом (рога, кольцо, узоры маски)
BONE_S = (178, 136, 134)
BONE_D = (120, 78, 80)


def face_pixel(face, i, j, phase):
    """Маска‑лицо 24×32 и подбородок 22×13 в духе скрина пользователя: тёмно‑алая маска,
    бледные костяные узоры (дуги над глазами, гребень от камня ко лбу, скуловые пластины),
    узкие горящие глаза под тяжёлыми надбровьями, оскал с клыками. Мягкая светотень."""
    if face == "faceplate":
        u = (i + 0.5) / 24 * 2 - 1
        v = (j + 0.5) / 32
    else:
        u = (i + 0.5) / 22 * 2 - 1
        v = 1.0 + (j + 0.5) / 13 * 0.42
    x = abs(u)
    base = mix(CRIMSON, RED, 0.45) if phase == 1 else mix(BLACK, CRIMSON, 0.45)
    shade = 0.8 + 0.3 * (1.0 - 0.6 * x ** 2) - 0.2 * max(0.0, v - 0.9)
    col = sh(base, shade)
    glow2 = phase == 2

    def bone(k=1.0):
        return sh(mix(BONE_S, BONE_W, 0.5 + 0.5 * (1 - x)), k)

    if face == "faceplate":
        # центральный гребень от камня вниз по переносице
        if x < 0.07 and v < 0.62:
            return ("glow", GLOW) if (glow2 and v < 0.3) else bone(1.0 if x < 0.04 else 0.85)
        # костяные дуги: от центра вверх‑вбок (как рога), две параллельные
        for off, wdt in ((0.0, 0.022), (0.075, 0.016)):
            yy = 0.30 - 0.32 * x ** 1.3 - off
            if 0.08 < x < 0.98 and abs(v - yy) < wdt + 0.01 * (1 - x):
                return bone(1.0 - 0.2 * x)
        # надбровья — тяжёлые тёмные дуги, под ними глаза
        brow = 0.36 + 0.07 * x
        if 0.1 < x < 0.86 and brow - 0.03 < v < brow + 0.012:
            return sh(BLACK, 1.0)
        ex, ey = 0.46, 0.415 + 0.05 * (x - 0.46)
        dx, dy = (x - ex) / 0.30, (v - ey) / 0.04
        if dx * dx + dy * dy < 1.0:
            if dx * dx + (dy * 2.2) ** 2 < 0.3:
                return ("glow", GLOW_HOT)
            return ("glow", GLOW)
        if dx * dx * 0.7 + ((v - ey) / 0.07) ** 2 < 1.0:
            return sh(BLACK, 0.9)                         # тёмная глазница вокруг огня
        # скуловые костяные пластины с канавкой
        cheek_y = 0.56 + 0.08 * x
        if 0.3 < x < 0.95 and abs(v - cheek_y) < 0.05:
            if abs(v - cheek_y) < 0.012:
                return sh(BONE_D, 0.9)
            return bone(0.95)
        # кровавая слеза из левого глаза
        if u < 0 and abs(x - 0.33 - 0.015 * math.sin(v * 40)) < 0.03 and 0.46 < v < 0.84:
            return ("glow", mix(GLOW, RED, 0.4)) if glow2 else mix(SCARLET, RED, (v - 0.46) / 0.4)
        # нос: две тёмные щели
        if 0.66 < v < 0.72 and 0.07 < x < 0.15:
            return sh(BLACK, 1.0)
        # оскал: тёмный рот с верхними клыками
        if v > 0.84 and x < 0.62 - 0.4 * (v - 0.84):
            tooth = (i % 3 != 2) and v > 0.88 and (v < 0.97 - 0.03 * (i % 3))
            if x > 0.12 and (i * 7) % 5 == 0 and v > 0.86:
                tooth = True                              # длинные клыки
            return bone(1.05) if tooth else sh(BLACK, 1.0)
        # мелкая филигрань на лбу (2‑я фаза светится)
        if v < 0.2 and abs((u * 8) % 1 - 0.5) < 0.06 and x < 0.5:
            return ("glow", mix(GLOW, RED, 0.3)) if glow2 else sh(BORDEAUX_D, 1.0)
        return col
    # подбородок: нижние клыки, костяная пластина‑«жвало»
    if v < 1.16 and x < 0.6:
        tooth = (i % 3 != 1) and v < 1.12 - 0.02 * (i % 2)
        return bone(1.0) if tooth else sh(BLACK, 1.0)
    if x < 0.08:
        return ("glow", mix(GLOW, RED, 0.4)) if glow2 else bone(0.9)
    if abs(v - (1.25 + 0.2 * x)) < 0.025 and x < 0.8:
        return bone(0.9)
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
                        # бледные костяные когти, к концу — тёмно‑алые
                        col = mix(BONE_W, BONE_S, t * 0.7)
                        if t > 0.75:
                            col = mix(col, CRIMSON if phase == 1 else BLACK, (t - 0.75) * 3)
                        return lit(fname, col)
                    return lit(fname, mix(BONE_S, BONE_D, t))
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
                if mat == "bone":
                    t = j / max(1, h - 1)
                    if face == "horn":
                        k = (i * 2 + j) % 7 == 0
                        col = mix(BONE_W, BONE_S, 0.25 + 0.35 * t) if not k else BONE_D
                        if c["size"][0] < 3.0:               # кончик рога краснеет
                            col = mix(col, SCARLET, 0.6) if phase == 1 else mix(col, GLOW, 0.4)
                        return lit(fname, col)
                    if face == "fang":
                        return lit(fname, mix(BONE_W, BORDEAUX, t * 0.8))
                    if phase == 2 and face == "ring" and fname in ("front", "top"):
                        return ("glow", mix(GLOW, BONE_W, 0.3))
                    return lit(fname, mix(BONE_W, BONE_S, 0.3 + 0.4 * t))
                if mat == "gem":
                    if fname in ("front", "top") and 0 < i < w - 1 and 0 < j < h - 1:
                        return ("glow", GLOW_HOT)
                    return ("glow", GLOW) if fname == "front" or face == "gem" else lit(fname, CRIMSON)
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
                    if face in ("blade", "point"):
                        if fname in ("left", "right"):
                            # плоскость клинка: чёрная сталь с алыми волнами, светящаяся кромка, дол
                            if j in (0, h - 1):
                                return ("glow", GLOW_HOT if face == "point" else GLOW)
                            if j == h // 2:
                                return ("glow", mix(GLOW, CRIMSON, 0.55))
                            wave = math.sin(i * 0.55 + j * 1.3)
                            col = mix(BLACK, CRIMSON, 0.25 + 0.25 * wave) if wave > 0.3 else BLACK_L if wave > -0.4 else BLACK
                            return lit(fname, col)
                        if fname in ("top", "bottom"):
                            return ("glow", GLOW)
                        return lit(fname, RED)
                    if face == "serration":
                        return ("glow", GLOW) if fname in ("top", "front") else lit(fname, BLACK)
                    if face in ("pommel", "pommel_spike"):
                        return ("glow", GLOW) if face == "pommel" and fname in ("back", "top") else lit(fname, BLACK_L)
                    if face in ("guard", "guard_tip"):
                        if side(fname) and (j in (0, h - 1) or i in (0, w - 1)):
                            return lit(fname, mix(BLACK_L, CRIMSON, 0.5))
                        return lit(fname, BLACK if (i + j) % 3 else BLACK_L)
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


# «in»/«out» — квадратичные (резко, но без телепортации кости за кадр на быстрых ударах)
EASES = {"lin": lambda x: x, "ease": ease, "out": lambda x: 1 - (1 - x) ** 2, "in": lambda x: x * x,
         "in3": ease_in, "out3": ease_out}


# Кисти движутся по дуге вокруг плеча (сферическая интерполяция направления + длины), а направления
# мечей и сгибов — по дуге большого круга. Иначе при линейной интерполяции кисть может пройти
# сквозь плечо (рука «выворачивается» за кадр), а меч — через нулевой вектор (переворот на 180°).
SHOULDER = {"rh": (-6.6, -14.0, 0.0), "lh": (6.6, -14.0, 0.0)}
DIRS = ("rdir", "ldir", "r_pole", "l_pole")


def slerp_dir(a, b, t):
    a, b = norm(list(a)), norm(list(b))
    c = max(-1.0, min(1.0, dot(a, b)))
    ang = math.acos(c)
    if ang < 1e-4:
        return a
    if ang > math.pi - 1e-3:                               # почти противоположные: обход через «вверх»
        mid = norm(add(cross(a, [0, 1, 0]) if abs(a[1]) < 0.9 else cross(a, [1, 0, 0]), [0, 0, 0]))
        return slerp_dir(a, mid, t * 2) if t < 0.5 else slerp_dir(mid, b, t * 2 - 1)
    s0 = math.sin((1 - t) * ang) / math.sin(ang)
    s1 = math.sin(t * ang) / math.sin(ang)
    return [a[i] * s0 + b[i] * s1 for i in range(3)]


def lerp_key(name, a, b, t):
    if name in SHOULDER:
        o = SHOULDER[name]
        va, vb = sub(list(a), list(o)), sub(list(b), list(o))
        la, lb = length(va), length(vb)
        d = slerp_dir(va, vb, t)
        return tuple(add(list(o), scl(d, lerp(la, lb, t))))
    if name in DIRS:
        return tuple(slerp_dir(a, b, t))
    return lerp_val(a, b, t)


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
            return {n: lerp_key(n, p0[n], p1[n], f) for n in p1}
    return dict(full[-1][1])


def frame_X(y_axis, x_hint):
    """Поворот, у которого локальная +y (вдоль кости) = y_axis, а локальная +x — по x_hint
    (ось сгиба сустава: одинакова для обоих звеньев, поэтому кости не «прокручиваются»)."""
    y = norm(y_axis)
    x = sub(x_hint, scl(y, dot(x_hint, y)))
    if length(x) < 1e-6:
        x = cross(y, [0, 0, 1]) if abs(y[2]) < 0.9 else cross(y, [1, 0, 0])
    x = norm(x)
    z = cross(x, y)
    return [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]


# Непрерывность между кадрами одной анимации: направление сгиба суставов (чтобы локоть/колено не
# перескакивали на другую сторону, когда цель почти на линии сгиба). Сбрасывается в bake_clip.
PREV_PN = {}


def smooth01(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def limb_ik(model, pose, upper, lower, target, pole, a, b, side_sign, fallback=None):
    """IK из двух звеньев без «прокручивания»: обе кости повёрнуты вокруг общей оси сгиба
    n = dn × pn (side_sign: +1 руки, −1 ноги — чтобы в покое совпасть с моделью)."""
    S = model.world(upper, [0, 0, 0], pose)
    d = sub(target, S)
    dist = max(abs(a - b) + 0.05, min(a + b - 0.05, length(d)))
    dn = norm(d)
    al = math.acos(max(-1.0, min(1.0, (a * a + dist * dist - b * b) / (2 * a * dist))))
    pole = norm(pole)
    if fallback is not None:                              # сгиб почти вдоль руки — плавно берём запасной
        k = smooth01(0.8, 0.97, abs(dot(pole, dn)))
        pole = norm(add(scl(pole, 1 - k), scl(norm(fallback), k)))
    p = sub(pole, scl(dn, dot(pole, dn)))
    pn = norm(p) if length(p) > 1e-6 else [0, 0, -1]
    prev = PREV_PN.get(upper)
    if prev is not None:
        pp = sub(prev, scl(dn, dot(prev, dn)))
        if length(pp) > 1e-3:                             # сгиб поворачивается не быстрее 12° за кадр
            pp = norm(pp)
            ang = math.acos(max(-1.0, min(1.0, dot(pp, pn))))
            if ang > math.radians(12):
                ax = cross(pp, pn)
                ax = norm(ax) if length(ax) > 1e-6 else dn
                pn = mv(axis_R(ax, math.radians(12)), pp)
    PREV_PN[upper] = pn
    E = add(S, scl(add(scl(dn, math.cos(al)), scl(pn, math.sin(al))), a))
    T = add(S, scl(dn, dist))
    n = scl(norm(cross(dn, pn)), side_sign)
    model.set_world_rot(pose, upper, frame_X(sub(E, S), n))
    model.set_world_rot(pose, lower, frame_X(sub(T, E), n))


def arc_R(a, b):
    """Кратчайший поворот, переводящий направление a в b."""
    a, b = norm(a), norm(b)
    c = dot(a, b)
    if c > 0.99999:
        return [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    ax = cross(a, b)
    if length(ax) < 1e-6:
        ax = cross(a, [1, 0, 0]) if abs(a[0]) < 0.9 else cross(a, [0, 1, 0])
    ax = norm(ax)
    ang = math.acos(max(-1.0, min(1.0, c)))
    return axis_R(ax, ang)


def axis_R(k, ang):
    x, y, z = k
    c, s_, v = math.cos(ang), math.sin(ang), 1 - math.cos(ang)
    return [[c + x * x * v, x * y * v - z * s_, x * z * v + y * s_],
            [y * x * v + z * s_, c + y * y * v, y * z * v - x * s_],
            [z * x * v - y * s_, z * y * v + x * s_, c + z * z * v]]


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
        limb_ik(model, pose, s + "_leg", s + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN, -1)
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
        out = mv(Rt, [-1.0 if s == "right" else 1.0, 0.2, 0.4])   # запасной сгиб: локоть наружу‑назад
        limb_ik(model, pose, s + "_arm", s + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE, 1, out)
    pose["right_hand"] = {"rot": pr["rhand"]}
    pose["left_hand"] = {"rot": pr["lhand"]}
    pose["right_claws"] = {"rot": (-pr["rcurl"], 0, 0)}
    pose["left_claws"] = {"rot": (-pr["lcurl"], 0, 0)}
    if swords:
        # меч: кратчайший поворот от направления «вперёд из кисти» к нужному + крен вокруг клинка
        # (без внезапных переворотов у вертикали, как было с look_R)
        Rr = model.chain_R("root", pose)
        for b, hand, key, roll in (("sword_r", "right_hand", "rdir", "rroll"), ("sword_l", "left_hand", "ldir", "lroll")):
            d = norm(mv(Rr, list(norm(pr[key]))))
            Rh = model.chain_R(hand, pose)
            hx = mv(Rh, [1, 0, 0])                         # ось сгиба кисти — плоскость клинка
            hy = mv(Rh, [0, 1, 0])
            k = smooth01(0.75, 0.95, abs(dot(hx, d)))
            hint = norm(add(scl(hx, 1 - k), scl(hy, k)))
            x = norm(sub(hint, scl(d, dot(hint, d))))
            z = scl(d, -1)
            y = cross(z, x)
            Rw = [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]
            Rw = mm(axis_R(d, math.radians(pr[roll])), Rw)
            model.set_world_rot(pose, b, Rw)
            pose.setdefault("_sword", {})[b] = (d, Rw, model.chain_R(model.parent[b], pose))
            pose[b]["scale"] = (pr["sw"],) * 3
    return pose


def twist_about(d, Ra, Rb):
    """Угол (рад) поворота вокруг оси d, переводящий Ra в Rb (оба с одной осью клинка d)."""
    xa = [Ra[0][0], Ra[1][0], Ra[2][0]]
    xb = [Rb[0][0], Rb[1][0], Rb[2][0]]
    return math.atan2(dot(cross(xa, xb), d), dot(xa, xb))


def bake_clip(fn, length_s, fps, loop, bones):
    """Как mobgen.bake, но мечи без переворотов: ориентация клинка «переносится» от кадра к кадру
    (кратчайший поворот за направлением), а накопленный крен плавно убирается к концу анимации —
    начало и конец совпадают с обычной позой (для плавного смешивания с покоем)."""
    PREV_PN.clear()
    n = int(round(length_s * fps))
    times = [min(k / fps, length_s) for k in range(n + 1)]
    poses = [fn(t) for t in times]
    for b in ("sword_r", "sword_l"):
        if not all("_sword" in p and b in p["_sword"] for p in poses):
            continue
        Rs = []
        for k, p in enumerate(poses):
            d, Rc, _ = p["_sword"][b]
            if k == 0:
                R = Rc
            else:
                dp = poses[k - 1]["_sword"][b][0]
                R = mm(arc_R(dp, d), Rs[-1])
            Rs.append(R)
        drift = twist_about(poses[-1]["_sword"][b][0], Rs[-1], poses[-1]["_sword"][b][1])
        for k, p in enumerate(poses):
            d, _, Rp = p["_sword"][b]
            R = mm(axis_R(d, drift * k / max(1, n)), Rs[k])
            p[b]["rot"] = to_euler_safe(mm(tr(Rp), R))
    lookup = dict(zip(times, poses))
    return bake(lambda t: lookup[t], length_s, fps, loop, bones, unwrap=True)


def to_euler_safe(R):
    from mobgen import to_euler
    if abs(R[2][0]) > 0.99999:                            # шарнирный замок: крен в 0, всё в тангаж
        b = -90.0 if R[2][0] > 0 else 90.0
        a = math.degrees(math.atan2(-R[1][2], R[1][1]))
        return [a, b, 0.0]
    return to_euler(R)


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


# «Неуклюжий замах»: разворот и рёв, лапа высоко за головой, пауза‑напряжение — и всем телом обрушивает
# её в землю перед собой (удар на 1.15 с), когти застревают и волочатся назад.
SWING = [
    (0.00, HUNCH, "lin"),
    (0.40, dict(torso=(28, -38, -6), body=(6, -16, 0), body_pos=(0, 4.6, 0), head=(-26, 26, 0), rh=(-11, 8, 6),
                r_pole=(0.6, -0.4, 1), lh=(6, 8, -13), rhand=(10, 0, 0), rcurl=10.0, jaw=24.0,
                rf=(-3.6, 22, 2.6), lf=(3.0, 22, -3.4)), "out"),
    (0.85, dict(torso=(-2, -44, -14), body=(-6, -18, 0), body_pos=(0, 1.2, 0), head=(-6, 30, 0), neck=(-6, 0, 0),
                rh=(-9, -24, 9), r_pole=(0.3, 1, 0.5), rhand=(40, 0, 0), rcurl=-15.0, lh=(9, 2, -12), jaw=44.0,
                ribbon=-4.0), "ease"),
    (1.00, dict(torso=(-4, -46, -15), rh=(-9.5, -25, 10), jaw=48.0), "lin"),
    (1.15, dict(torso=(56, 30, 10), body=(18, 18, 0), body_pos=(0, 7.5, 0), head=(-44, -18, 0), neck=(-10, 0, 0),
                rh=(4, 15, -16), r_pole=(1, 0, 0.6), rhand=(-50, 0, 0), rcurl=45.0, lh=(10, 6, -6),
                rf=(-3.6, 22, 4.2), lf=(3.4, 22, -6.0), jaw=20.0, hair=24.0, ribbon=14.0), "in"),
    (1.35, dict(torso=(60, 32, 11), body_pos=(0, 8.0, 0), rh=(4.5, 16, -15.5), hair=10.0), "out"),
    (1.75, dict(torso=(48, 16, 6), rh=(-3, 13, -10), rcurl=55.0, body_pos=(0, 6.0, 0), jaw=14.0), "ease"),
    (2.20, HUNCH, "ease"),
]


def swing(t):
    return resolve(keyed(SWING, t))


# «Жадное пожирание»: встаёт на дыбы с раскрытой пастью, бросается вниз и 3 с рвёт добычу лапами,
# по очереди запихивая её в пасть; голова дёргается при каждом укусе.
DEVOUR_REAR = dict(torso=(6, 0, 0), body=(-2, 0, 0), body_pos=(0, 1.5, 0), head=(-20, 0, 0), neck=(-10, 0, 0),
                   rh=(-15, -12, -3), lh=(15, -12, -3), r_pole=(0.3, 0.7, 1), l_pole=(-0.3, 0.7, 1),
                   rhand=(20, 0, -20), lhand=(20, 0, 20), rcurl=-20.0, lcurl=-20.0, jaw=52.0, ribbon=-4.0)
DEVOUR_LEAN = dict(torso=(52, 0, 0), body=(16, 0, 0), body_pos=(0, 6.0, 0), head=(-40, 0, 0), neck=(-6, 0, 0),
                   rf=(-3.6, 22, 3.2), lf=(3.6, 22, -2.6), jaw=34.0, rhand=(-20, 0, 0), lhand=(-20, 0, 0),
                   r_pole=(0.6, -0.3, 1), l_pole=(-0.6, -0.3, 1), rh=(-7, 12, -14), lh=(7, 12, -14), ribbon=8.0)


def devour(t):
    pr = keyed([(0.0, HUNCH, "lin"), (0.45, DEVOUR_REAR, "out"), (0.7, DEVOUR_LEAN, "in"),
                (3.4, DEVOUR_LEAN, "lin"), (3.8, HUNCH, "ease")], t)
    k = min(1.0, max(0.0, (t - 0.7) / 0.15)) * min(1.0, max(0.0, (3.4 - t) / 0.2))
    if k > 0:
        w = 2 * math.pi * (t - 0.7) / 0.64                 # пара лап за 0.64 с
        a = math.sin(w)
        b = math.sin(w + math.pi)

        def paw(s, x):
            g = s * 0.5 + 0.5                              # 0 — схватить у земли, 1 — у пасти
            return (x * (1 - 0.4 * g), 14.0 - 9.0 * g, -18.0 + 12.0 * g)
        pr["rh"] = lerp_val(pr["rh"], paw(a, -7.0), k)
        pr["lh"] = lerp_val(pr["lh"], paw(b, 7.0), k)
        pr["rcurl"] = lerp(pr["rcurl"], 10 + 45 * (a * 0.5 + 0.5), k)
        pr["lcurl"] = lerp(pr["lcurl"], 10 + 45 * (b * 0.5 + 0.5), k)
        bite = abs(math.sin(w))
        pr["jaw"] = lerp(pr["jaw"], 12 + 36 * bite, k)
        pr["head"] = (-40 + 10 * bite * k, 10 * math.sin(w) * k, 4 * math.sin(w * 2) * k)
        pr["torso"] = (52 + 4 * math.sin(w * 2) * k, 6 * math.sin(w) * k, 3 * math.sin(w) * k)
        pr["body_pos"] = (0, 6.0 + 0.6 * math.sin(w * 2) * k, 0)
        pr["hair"] = 6 * math.sin(w * 2)
    return resolve(pr)


# «Голодный бросок»: распрямляется во весь рост, руки с когтями вскинуты в стороны, вопль;
# резко приседает и прыгает (прыжок на 1.2 с)
UNFURL = dict(body=(-12, 0, 0), body_pos=(0, -0.8, 0), torso=(-16, 0, 0), neck=(-12, 0, 0), head=(-20, 0, 0),
              rh=(-18, -22, 1), lh=(18, -22, 1), r_pole=(0, 0.4, 1), l_pole=(0, 0.4, 1), rhand=(20, 0, -30),
              lhand=(20, 0, 30), rcurl=-25.0, lcurl=-25.0, jaw=48.0, ribbon=-8.0, rf=(-4.2, 22, 0.5),
              lf=(4.2, 22, -0.5), hair=-10.0)
CROUCH = dict(body=(22, 0, 0), body_pos=(0, 8.5, 0), torso=(42, 0, 0), neck=(-14, 0, 0), head=(-42, 0, 0),
              rh=(-9, 5, -8), lh=(9, 5, -8), rhand=(-20, 0, 0), lhand=(-20, 0, 0), rcurl=30.0, lcurl=30.0, jaw=24.0,
              rf=(-3.8, 22, 3.6), lf=(3.8, 22, -2.6), ribbon=6.0)
POUNCE_START = [
    (0.00, HUNCH, "lin"),
    (0.55, UNFURL, "out"),
    (0.85, dict(UNFURL, head=(-28, 0, 0), jaw=56.0, rh=(-19, -23, 2), lh=(19, -23, 2)), "lin"),
    (1.20, CROUCH, "in"),
]
AIR = dict(body=(32, 0, 0), body_pos=(0, 0, 0), torso=(30, 0, 0), neck=(-20, 0, 0), head=(-38, 0, 0),
           rh=(-7, 1, -22), lh=(7, 1, -22), r_pole=(0.4, -1, 0.3), l_pole=(-0.4, -1, 0.3), rhand=(-60, 0, 0),
           lhand=(-60, 0, 0), rcurl=-20.0, lcurl=-20.0, jaw=50.0, rf=(-3.2, 14, 9), lf=(3.2, 12, 7), toe_r=40.0,
           toe_l=40.0, ribbon=40.0, hair=40.0)


def pounce_start(t):
    pr = keyed(POUNCE_START, t)
    if 0.55 < t < 0.85:                                     # дрожь от вопля
        k = math.sin(math.pi * (t - 0.55) / 0.3)
        pr["head"] = add(list(pr["head"]), [4 * math.sin(t * 90) * k, 5 * math.sin(t * 70) * k, 0])
    return resolve(pr)


def pounce_air(t):
    w = 2 * math.pi * t / 0.6
    pr = P(HUNCH, **AIR)
    pr["rcurl"] = -20 + 8 * math.sin(w)
    pr["lcurl"] = -20 + 8 * math.sin(w + 1)
    pr["ribbon"] = 40 + 8 * math.sin(w)
    pr["hair"] = 40 + 6 * math.sin(w + 1)
    pr["jaw"] = 50 + 4 * math.sin(2 * w)
    return resolve(pr)


# приземление: обе лапы впечатываются в землю, низкая поза, медленно поднимается с рыком
POUNCE_LAND = [
    (0.00, dict(HUNCH, body=(30, 0, 0), body_pos=(0, 10.0, 0), torso=(54, 0, 0), head=(-46, 0, 0),
                rh=(-8, 13, -15), lh=(8, 13, -15), rhand=(-40, 0, 0), lhand=(-40, 0, 0), rcurl=45.0, lcurl=45.0,
                jaw=40.0, rf=(-4.2, 22, 3.0), lf=(4.2, 22, -1.0), ribbon=30.0, hair=30.0), "lin"),
    (0.25, dict(body_pos=(0, 9.0, 0), ribbon=6.0, hair=6.0, rh=(-8, 14, -14), lh=(8, 14, -14)), "out"),
    (0.70, dict(body_pos=(0, 8.2, 0), torso=(50, 0, 0), head=(-34, 0, 0), jaw=50.0), "ease"),
    (1.40, HUNCH, "ease"),
]


def pounce_land(t):
    return resolve(keyed(POUNCE_LAND, t))


# ---------- смена фазы: судороги → выпрямляется, мечи и венец появляются ----------
UPRIGHT = dict(body=(0, 0, 0), body_pos=(0, 0.3, 0), torso=(-3, 0, 0), neck=(2, 0, 0), head=(4, 0, 0),
               rf=(-2.9, 22, 0.8), lf=(2.9, 22, -0.8), rh=(-9.0, 7.0, -3.5), lh=(9.0, 7.0, -3.5),
               r_pole=(0.5, -0.2, 1), l_pole=(-0.5, -0.2, 1), rhand=(-10, 0, 0), lhand=(-10, 0, 0),
               rcurl=70.0, lcurl=70.0, rdir=(-0.5, 0.42, 0.75), ldir=(0.5, 0.42, 0.75), rroll=0.0, lroll=0.0,
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
    pr["rdir"] = (-0.5, 0.42 + 0.04 * math.sin(w), 0.75)
    pr["ldir"] = (0.5, 0.42 + 0.04 * math.sin(w + 1), 0.75)
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


# «Двойной разрез»: глубокий замах правым мечом за спину → широкий горизонтальный разрез (0.6 с);
# левый меч взлетает над головой → косой рубящий удар сверху (1.1 с), корпус падает вперёд.
SLASH = [
    (0.00, UPRIGHT, "lin"),
    (0.42, dict(torso=(2, -55, 0), body=(0, -20, 0), head=(0, 38, 0), rh=(-14, -12, 9), r_pole=(0.2, 1, 0.4),
                rdir=(-0.55, -0.5, 0.65), rroll=-40.0, lh=(8, 4, -10), ldir=(0.3, 0.3, -1), rf=(-3.4, 22, 3.2),
                lf=(3.2, 22, -3.6), body_pos=(0, 1.8, 0), ribbon=-6.0), "out"),
    (0.60, dict(torso=(8, 48, 0), body=(6, 22, 0), head=(-6, -34, 0), rh=(9, 2, -14), r_pole=(1, 0, 0.5),
                rdir=(0.95, 0.05, -0.25), rroll=-80.0, ribbon=16.0, hair=14.0, body_pos=(0, 2.8, 0),
                rf=(-3.4, 22, -3.0), lf=(3.4, 22, 4.0)), "in"),
    (0.80, dict(lh=(9, -22, 4), l_pole=(-0.3, 1, 0.5), ldir=(0.2, -0.7, 0.7), rh=(6, 5, -11), torso=(-8, 30, 0),
                head=(-14, -20, 0), body_pos=(0, 1.0, 0), rroll=-40.0), "out"),
    (1.10, dict(torso=(26, -18, 0), body=(16, -8, 0), head=(-30, 10, 0), body_pos=(0, 5.0, 0), lh=(-4, 12, -15),
                l_pole=(-1, 0, 0.5), ldir=(-0.55, 0.42, -0.72), rh=(-12, 2, 4), rdir=(-0.6, 0.2, 0.8), rroll=0.0,
                rf=(-3.4, 22, 3.0), lf=(3.4, 22, -5.0), ribbon=20.0, hair=18.0), "in"),
    (1.40, dict(body_pos=(0, 4.6, 0), ribbon=8.0, hair=6.0), "lin"),
    (1.50, dict(ldir=(-0.2, 0.12, -1), lh=(2, 6, -14)), "ease"),
    (1.65, dict(rdir=(-0.85, 0.2, 0.45), ldir=(0.85, 0.2, 0.45), body_pos=(0, 2.2, 0)), "ease"),
    (2.00, UPRIGHT, "ease"),
]


def slash(t):
    return resolve(keyed(SLASH, t))


# «Пронзающий выпад»: сжимается как пружина, мечи у бёдер → рывок с двойным уколом (0.75 с) →
# мечи вспарывают вверх и в стороны (1.05 с, подбрасывает).
THRUST = [
    (0.00, UPRIGHT, "lin"),
    (0.50, dict(body=(-4, 0, 0), body_pos=(0, 4.0, 0), torso=(-2, 0, 0), head=(-4, 0, 0), rh=(-9, 5, 8),
                lh=(9, 5, 8), rdir=(-0.05, 0.1, -1), ldir=(0.05, 0.1, -1), rf=(-3.2, 22, -5.0), lf=(3.2, 22, 5.5),
                ribbon=-8.0, jaw=20.0), "out"),
    (0.75, dict(body=(26, 0, 0), body_pos=(0, 3.0, 0), torso=(14, 0, 0), head=(-30, 0, 0), rh=(-3, -1, -18),
                lh=(3, -1, -18), r_pole=(1, 0, 0.2), l_pole=(-1, 0, 0.2), rdir=(0.04, 0.05, -1),
                ldir=(-0.04, 0.05, -1), rf=(-3.2, 22, 7.0), lf=(3.2, 22, -8.0), ribbon=34.0, hair=26.0,
                jaw=36.0), "in"),
    (0.90, dict(ribbon=24.0), "lin"),
    (1.05, dict(body=(-6, 0, 0), torso=(-16, 0, 0), head=(-20, 0, 0), body_pos=(0, 2.0, 0), rh=(-10, -18, -6),
                lh=(10, -18, -6), r_pole=(0.5, 0.6, 1), l_pole=(-0.5, 0.6, 1), rdir=(-0.5, -0.85, -0.2),
                ldir=(0.5, -0.85, -0.2), rroll=-30.0, lroll=30.0, ribbon=10.0, hair=6.0), "out"),
    (1.30, dict(body_pos=(0, 1.6, 0)), "lin"),
    (1.80, UPRIGHT, "ease"),
]


def thrust(t):
    return resolve(keyed(THRUST, t))


# «Зов выводка»: скрещённые мечи над головой, вопль, дрожь (из земли бьют фонтаны крови) —
# мечи вонзаются в землю (1.5 с), из фонтанов вылезают мелкие.
SUMMON = [
    (0.00, UPRIGHT, "lin"),
    (0.70, dict(torso=(-14, 0, 0), neck=(-10, 0, 0), head=(-30, 0, 0), rh=(-3, -23, -3), lh=(3, -23, -3),
                r_pole=(1, 0.3, 0.3), l_pole=(-1, 0.3, 0.3), rdir=(0.55, -0.83, 0.1), ldir=(-0.55, -0.83, 0.1),
                jaw=44.0, body_pos=(0, -0.5, 0), ribbon=-8.0), "out"),
    (1.30, dict(jaw=50.0, head=(-34, 0, 0)), "lin"),
    (1.50, dict(body=(18, 0, 0), torso=(24, 0, 0), neck=(-4, 0, 0), head=(-24, 0, 0), body_pos=(0, 7.0, 0),
                rh=(-8, 13, -10), lh=(8, 13, -10), r_pole=(0.6, 0, 1), l_pole=(-0.6, 0, 1),
                rdir=(-0.15, 0.62, -0.8), ldir=(0.15, 0.62, -0.8), rf=(-3.4, 22, 2.4), lf=(3.4, 22, -2.4), jaw=20.0,
                ribbon=16.0), "in"),
    (1.90, dict(body_pos=(0, 6.5, 0)), "lin"),
    (2.25, dict(rdir=(-0.85, 0.2, 0.45), ldir=(0.85, 0.2, 0.45), body_pos=(0, 2.5, 0), body=(4, 0, 0), torso=(2, 0, 0)), "ease"),
    (2.60, UPRIGHT, "ease"),
]


def summon(t):
    pr = keyed(SUMMON, t)
    if 0.7 < t < 1.3:
        k = math.sin(math.pi * (t - 0.7) / 0.6)
        pr["torso"] = add(list(pr["torso"]), [2 * math.sin(t * 80) * k, 3 * math.sin(t * 60) * k, 0])
        pr["head"] = add(list(pr["head"]), [4 * math.sin(t * 70) * k, 5 * math.sin(t * 50) * k, 0])
    return resolve(pr)


# «Кровавая кладка»: руки с мечами раскинуты в стороны и вверх (как на картинке), затем вонзает оба меча
# в землю на колене (1.0 с) — земля вокруг вскипает кровью, вырастают яйца (1.3 с).
EGGS = [
    (0.00, UPRIGHT, "lin"),
    (0.60, dict(torso=(-10, 0, 0), head=(-20, 0, 0), rh=(-21, -14, 0), lh=(21, -14, 0), r_pole=(0, 0.5, 1),
                l_pole=(0, 0.5, 1), rdir=(-0.6, -0.8, 0), ldir=(0.6, -0.8, 0), jaw=36.0, body_pos=(0, -0.3, 0),
                ribbon=-6.0), "out"),
    (0.80, dict(jaw=42.0, head=(-24, 0, 0)), "lin"),
    (1.00, dict(body=(22, 0, 0), body_pos=(0, 10.0, 0), torso=(28, 0, 0), head=(-24, 0, 0), rh=(-8, 15, -10),
                lh=(8, 15, -10), r_pole=(0.6, 0, 1), l_pole=(-0.6, 0, 1), rdir=(-0.1, 0.66, -0.75),
                ldir=(0.1, 0.66, -0.75), rf=(-3.0, 22, -3.5), lf=(3.0, 22, 5.0), toe_l=30.0, jaw=40.0,
                ribbon=12.0), "in"),
    (2.00, dict(body_pos=(0, 9.5, 0), head=(-34, 0, 0)), "lin"),
    (2.40, dict(rdir=(-0.85, 0.2, 0.45), ldir=(0.85, 0.2, 0.45), body_pos=(0, 3.0, 0), body=(6, 0, 0), torso=(4, 0, 0)), "ease"),
    (2.80, UPRIGHT, "ease"),
]


def eggs(t):
    return resolve(keyed(EGGS, t))


# «Багровая казнь» (мощная, вместо вихря): приседает, мечи отведены назад как крылья, взмывает высоко,
# в воздухе вскидывает мечи над головой и обрушивается вниз остриями (удар при приземлении),
# стоит на колене с мечами в земле — и вырывает их широким разрезом в обе стороны (1.15 с после приземления).
EXEC_START = [
    (0.00, UPRIGHT, "lin"),
    (0.60, dict(body=(24, 0, 0), body_pos=(0, 8.0, 0), torso=(20, 0, 0), head=(-30, 0, 0), rh=(-14, -2, 8),
                lh=(14, -2, 8), r_pole=(0.3, 0.5, 1), l_pole=(-0.3, 0.5, 1), rdir=(-0.5, -0.3, 0.8),
                ldir=(0.5, -0.3, 0.8), rf=(-3.6, 22, 2.5), lf=(3.6, 22, -2.5), jaw=30.0, ribbon=10.0), "out"),
    (0.90, dict(body=(-10, 0, 0), body_pos=(0, -2.0, 0), torso=(-10, 0, 0), head=(-10, 0, 0), rh=(-6, -22, 2),
                lh=(6, -22, 2), r_pole=(1, 0.3, 0.6), l_pole=(-1, 0.3, 0.6), rdir=(0.25, -0.95, 0.2),
                ldir=(-0.25, -0.95, 0.2), rf=(-3.0, 16, 3.0), lf=(3.0, 15, -2.0), toe_r=30.0, toe_l=30.0,
                ribbon=-10.0), "in"),
]
AIR_UP = dict(body=(-20, 0, 0), body_pos=(0, 0, 0), torso=(-18, 0, 0), head=(-14, 0, 0), rh=(-5, -21, 4),
              lh=(5, -21, 4), r_pole=(1, 0.3, 0.6), l_pole=(-1, 0.3, 0.6), rdir=(0.2, -0.6, 0.8),
              ldir=(-0.2, -0.6, 0.8), rf=(-3.2, 14, 4), lf=(3.2, 15, 2), toe_r=40.0, toe_l=40.0, ribbon=-20.0,
              hair=-16.0, jaw=30.0)
AIR_DOWN = dict(body=(30, 0, 0), torso=(26, 0, 0), head=(-36, 0, 0), rh=(-4, 6, -14), lh=(4, 6, -14),
                r_pole=(1, 0, 0.4), l_pole=(-1, 0, 0.4), rdir=(0.05, 0.9, -0.45), ldir=(-0.05, 0.9, -0.45),
                rf=(-3.2, 18, -2), lf=(3.2, 17, 3), ribbon=40.0, hair=36.0, jaw=46.0)
EXEC_AIR = [(0.00, dict(EXEC_START[-1][1]), "lin"), (0.45, AIR_UP, "out"), (0.85, AIR_DOWN, "in"),
            (1.30, AIR_DOWN, "lin")]
EXEC_LAND = [
    (0.00, dict(UPRIGHT, body=(30, 0, 0), body_pos=(0, 10.5, 0), torso=(30, 0, 0), head=(-30, 0, 0),
                rh=(-6, 15, -11), lh=(6, 15, -11), r_pole=(0.6, 0, 1), l_pole=(-0.6, 0, 1),
                rdir=(-0.1, 0.66, -0.75), ldir=(0.1, 0.66, -0.75), rf=(-3.4, 22, -3.0), lf=(3.4, 22, 4.5),
                toe_l=30.0, jaw=40.0, ribbon=30.0, hair=26.0), "lin"),
    (0.25, dict(body_pos=(0, 9.8, 0), ribbon=8.0, hair=6.0), "out"),
    (0.80, dict(head=(-40, 0, 0), jaw=50.0), "lin"),
    (0.95, dict(torso=(34, 0, 0), body_pos=(0, 10.2, 0)), "lin"),
    (1.15, dict(body=(4, 0, 0), body_pos=(0, 3.0, 0), torso=(-6, 0, 0), head=(-10, 0, 0), rh=(-21, -4, -4),
                lh=(21, -4, -4), r_pole=(0, 0.5, 1), l_pole=(0, 0.5, 1), rdir=(-1, -0.1, 0.15),
                ldir=(1, -0.1, 0.15), rroll=-80.0, lroll=80.0, rf=(-4.2, 22, 0.5), lf=(4.2, 22, -0.5), toe_l=0.0,
                ribbon=20.0, hair=16.0, jaw=30.0), "in"),
    (1.50, dict(body_pos=(0, 2.6, 0), ribbon=10.0), "lin"),
    (2.20, UPRIGHT, "ease"),
]


def exec_start(t):
    return resolve(keyed(EXEC_START, t))


def exec_air(t):
    return resolve(keyed(EXEC_AIR, t))


def exec_land(t):
    return resolve(keyed(EXEC_LAND, t))


animations = {
    "idle1": bake_clip(idle1, 2.6, 20, True, A_BONES),
    "walk1": bake_clip(walk1, 1.4, 20, True, A_BONES),
    "run1": bake_clip(run1, 0.8, 30, True, A_BONES),
    "swing": bake_clip(swing, 2.2, 60, False, A_BONES),
    "devour": bake_clip(devour, 3.8, 60, False, A_BONES),
    "pounce_start": bake_clip(pounce_start, 1.2, 60, False, A_BONES),
    "pounce_air": bake_clip(pounce_air, 0.6, 20, True, A_BONES),
    "pounce_land": bake_clip(pounce_land, 1.4, 60, False, A_BONES),
    "transform": bake_clip(transform, 3.0, 60, False, A_BONES),
    "idle2": bake_clip(idle2, 3.0, 20, True, A_BONES),
    "walk2": bake_clip(walk2, 1.2, 20, True, A_BONES),
    "run2": bake_clip(run2, 0.7, 30, True, A_BONES),
    "slash": bake_clip(slash, 2.0, 60, False, A_BONES),
    "thrust": bake_clip(thrust, 1.8, 60, False, A_BONES),
    "summon": bake_clip(summon, 2.6, 60, False, A_BONES),
    "eggs": bake_clip(eggs, 2.8, 60, False, A_BONES),
    "exec_start": bake_clip(exec_start, 0.9, 60, False, A_BONES),
    "exec_air": bake_clip(exec_air, 1.3, 60, False, A_BONES),
    "exec_land": bake_clip(exec_land, 2.2, 60, False, A_BONES),
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
