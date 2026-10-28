"""
Генератор моба «Кровопускатель» (bloodletter) и его кровавого копья (bloodletter_spear): модели Bedrock,
текстуры (+ glowmask), анимации и текстура кровавого следа «Вихря» (textures/fx/blood_trail*.png).

Запуск:  python tools/bloodletter_gen.py
Превью:  PREVIEW_SCALE=7 python tools/mob_preview.py bloodletter
         PREVIEW_SCALE=7 python tools/mob_preview.py bloodletter summon 0,0.4,0.9,1.5
         PREVIEW_SCALE=7 python tools/mob_preview.py bloodletter whirl 0.3,0.8,1.3,2.0
         PREVIEW_SCALE=14 python tools/mob_preview.py bloodletter_spear
Проверка: python tools/anim_check.py bloodletter

Оригинальный персонаж в стиле кровожада (алая плоть, чёрный лак, бледная кость, светящаяся кровь), но
другого склада: сгорбленный «жрец крови» ≈2.5 блока в длинной рваной чёрно‑бордовой рясе с алой подкладкой.
Лица нет — гладкая костяная маска с одной вертикальной светящейся щелью‑раной, из неё кровавые потёки;
капюшон‑митра из чёрного лака с венцом костяных шипов, за спиной веер из трёх позвоночных шипов.
Грудь рясы распахнута: рёбра и светящееся сердце. Длинные руки из голой плоти с лаковыми наручами,
кисти с длинными костяными когтями (руки висят ниже колен). Медленный, тяжёлый.

Анимации: ключевые позы + IK (ступни на земле, кисти тянутся к точкам) — тот же подход, что в curb_gen.py.
  idle 3.2, walk 1.8 (циклы); summon 1.5 → channel 1.4 (цикл) и броски копий fling_r / fling_l 0.6;
  whirl 2.3 («Кровавый вихрь»: присед, руки в стороны, оборот на 360°, возврат).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import (FACE_LIGHT, Model, Painter, add, bake, clamp, cross, dot, ease, ease_in, ease_out, faces,  # noqa: E402
                    length, lerp, mix, mm, mul, mv, norm, rx_m, scl, sub, write_anims)

from PIL import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
NAME = "bloodletter"

# =====================================================================
# Палитра (как у кровожада: алый, бордовый, чёрный лак, бледная кость, светящаяся кровь)
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
CLOTH = (30, 12, 18)
CLOTH_L = (52, 20, 28)
BONE_W = (232, 210, 200)
BONE_S = (182, 146, 140)
BONE_D = (122, 82, 82)
GLOW = (255, 60, 48)
GLOW_HOT = (255, 168, 136)

# =====================================================================
# 1. Модель (Bedrock: y вверх, лицо в −z, правая сторона — −x)
# =====================================================================
ANKLE, THIGH, SHIN = 1.6, 10.0, 8.6
KNEE = ANKLE + SHIN
HIP = KNEE + THIGH                    # 20.2
UPPER, FORE = 9.0, 8.6
SH_Y = 31.6                           # плечо
TORSO_Y = 22.0

M = Model(NAME, 128, 128)
bone, cube = M.bone, M.cube

bone("root", None, (0, 0, 0))
bone("body", "root", (0, 20.6, 0))
cube("body", (-3.6, 19.0, -2.2), (7.2, 3.4, 4.4), "flesh", face="pelvis")
cube("body", (-4.0, 21.0, -2.6), (8.0, 1.6, 5.2), "lacquer", face="belt")
cube("body", (-1.0, 19.8, -3.0), (2.0, 2.2, 0.6), "bone", face="buckle")                 # костяная пряжка
for k, x in enumerate((-3.6, 2.4)):                                                         # подвески на поясе
    cube("body", (x, 16.4, -2.9), (1.2, 4.6, 0.5), "lacquer", face="tassel", rot=(0, 0, 6 if k else -6),
         pivot=(x + 0.6, 21.0, -2.7))

# ряса ниже пояса — пять полотнищ, следуют за ногами
ROBE = (("robe_fr", (-1.9, 21.6, -2.6), (-4.2, 6.2, -2.95), (4.2, 15.4, 0.6)),
        ("robe_fl", (1.9, 21.6, -2.6), (0.0, 6.2, -2.95), (4.2, 15.4, 0.6)),
        ("robe_b", (0.0, 21.6, 2.5), (-4.5, 4.0, 2.3), (9.0, 17.6, 0.7)),
        ("robe_sr", (-4.2, 21.6, 0.0), (-4.75, 7.2, -2.5), (0.6, 14.4, 5.0)),
        ("robe_sl", (4.2, 21.6, 0.0), (4.15, 7.2, -2.5), (0.6, 14.4, 5.0)))
for nm, piv, org, size in ROBE:
    bone(nm, "body", piv)
    cube(nm, org, size, "robe", face="robe_panel")

# ноги: бедро → голень → стопа (тонкая плоть, лаковые поножи, костяные когти)
bone("right_leg", "body", (-2.1, HIP, 0))
cube("right_leg", (-3.5, KNEE, -1.5), (2.8, THIGH, 3.0), "flesh", face="thigh")
bone("right_shin", "right_leg", (-2.1, KNEE, 0))
cube("right_shin", (-3.2, ANKLE, -1.1), (2.2, SHIN, 2.2), "flesh", face="shin")
cube("right_shin", (-3.45, ANKLE + 2.6, -1.55), (2.7, 5.6, 0.8), "lacquer", face="greave")
cube("right_shin", (-3.5, KNEE - 1.4, -1.8), (2.8, 2.2, 1.1), "lacquer", face="kneecap")
cube("right_shin", (-2.6, KNEE - 0.8, -2.3), (1.0, 1.0, 1.4), "bone", face="spike", rot=(-30, 0, 0),
     pivot=(-2.1, KNEE, -1.8))
bone("right_foot", "right_shin", (-2.1, ANKLE, 0))
cube("right_foot", (-3.3, 0, -3.0), (2.4, 1.6, 4.0), "flesh", face="foot")
for k, dx in enumerate((-3.4, -2.55, -1.7)):
    cube("right_foot", (dx + 0.1, 0, -4.9), (0.7, 0.8, 2.0), "bone", face="toe")
cube("right_foot", (-2.45, 0.3, 0.8), (0.7, 0.7, 1.6), "bone", face="toe")
M.mirror_x("right_leg", "left_leg")
M.mirror_x("right_shin", "left_shin", parent="left_leg")
M.mirror_x("right_foot", "left_foot", parent="left_shin")

# корпус
bone("torso", "body", (0, TORSO_Y, 0))
cube("torso", (-4.6, 22.2, -2.7), (9.2, 11.2, 5.4), "robe", face="chest")
cube("torso", (-2.3, 26.6, -3.0), (4.6, 5.6, 0.5), "flesh", face="ribcage")
bone("heart", "torso", (0, 29.2, -2.8))
cube("heart", (-1.0, 28.2, -3.45), (2.0, 2.0, 1.0), "heart", face="heart")
cube("torso", (-5.6, 30.6, -3.3), (11.2, 3.4, 6.8), "robe", face="mantle")                 # наплечная пелерина
cube("torso", (-4.0, 32.4, 1.6), (8.0, 4.2, 1.4), "lacquer", face="collar")                 # высокий ворот сзади
for k in range(3):                                                                          # костяные пластины на вороте
    cube("torso", (-3.4 + k * 2.6, 35.6, 1.7), (1.6, 1.4, 1.0), "bone", face="collar_spike", rot=(-12, 0, (k - 1) * 14),
         pivot=(-2.6 + k * 2.6, 35.6, 2.2))
# веер позвоночных шипов за спиной
for k, (zr, ln, w) in enumerate(((-30, 8.0, 1.0), (0, 10.0, 1.3), (30, 8.0, 1.0))):
    x = (k - 1) * 1.6
    cube("torso", (x - w / 2, 32.0, 3.0), (w, ln, w), "bone", face="spine", rot=(-24, 0, zr), pivot=(x, 32.0, 3.4))
    cube("torso", (x - 0.35, 32.0 + ln, 3.15), (0.7, 2.2, 0.7), "bone", face="spine_tip", rot=(-24, 0, zr),
         pivot=(x, 32.0, 3.4))
    for s in range(2):                                                                      # позвонки‑кольца
        cube("torso", (x - w / 2 - 0.3, 33.4 + s * 2.8, 2.7), (w + 0.6, 0.7, w + 0.6), "bone", face="vertebra",
             rot=(-24, 0, zr), pivot=(x, 32.0, 3.4))

# голова: капюшон‑митра, маска, челюсть
bone("head", "torso", (0, 33.8, -0.8))
cube("head", (-3.6, 33.2, -4.0), (7.2, 7.2, 7.2), "hood", face="hood")
cube("head", (-2.6, 39.6, -3.0), (5.2, 1.8, 5.2), "hood", face="mitre")
cube("head", (-1.4, 41.0, -2.0), (2.8, 1.4, 2.8), "lacquer", face="mitre_top", rot=(-8, 0, 0), pivot=(0, 41.0, -0.6))
cube("head", (-2.5, 34.4, -4.35), (5.0, 5.6, 0.5), "mask", face="mask")
cube("head", (-3.9, 39.0, -4.3), (7.8, 1.0, 1.0), "bone", face="band")                       # костяной обруч
for k, x in enumerate((-3.2, -1.6, 0.0, 1.6, 3.2)):                                          # венец шипов
    h = (1.6, 2.4, 3.2, 2.4, 1.6)[k]
    cube("head", (x - 0.35, 39.8, -4.1), (0.7, h + 0.6, 0.7), "lacquer", face="crown", rot=(-14, 0, -x * 6),
         pivot=(x, 39.8, -3.8))
bone("jaw", "head", (0, 35.2, -3.6))
cube("jaw", (-2.4, 33.0, -4.45), (4.8, 1.8, 0.8), "bone", face="jaw")
cube("jaw", (-2.0, 32.4, -4.2), (4.0, 0.8, 0.6), "bone", face="chin")

# руки: рукав рясы → голая плоть с наручем → кисть с когтями
bone("right_arm", "torso", (-5.3, SH_Y, 0))
cube("right_arm", (-7.1, SH_Y - UPPER, -1.7), (3.6, UPPER, 3.4), "robe", face="sleeve")
cube("right_arm", (-7.9, SH_Y - 2.6, -2.5), (4.8, 3.6, 5.0), "lacquer", face="pauldron")
cube("right_arm", (-7.4, SH_Y + 0.6, -0.6), (1.2, 2.6, 1.2), "bone", face="spike", rot=(0, 0, 28),
     pivot=(-6.8, SH_Y + 0.6, 0))
bone("right_fore", "right_arm", (-5.3, SH_Y - UPPER, 0))
cube("right_fore", (-7.6, SH_Y - UPPER - 2.6, -2.2), (4.6, 2.6, 4.4), "robe", face="cuff")      # раструб рукава
cube("right_fore", (-6.4, SH_Y - UPPER - FORE, -1.1), (2.2, FORE, 2.2), "flesh", face="fore")
cube("right_fore", (-6.65, SH_Y - UPPER - FORE + 1.0, -1.35), (2.7, 4.0, 2.7), "lacquer", face="bracer")
bone("right_hand", "right_fore", (-5.3, SH_Y - UPPER - FORE, 0))
HY = SH_Y - UPPER - FORE
cube("right_hand", (-6.6, HY - 2.4, -1.2), (2.6, 2.4, 2.4), "flesh", face="palm")
for k, z in enumerate((-1.2, -0.4, 0.4)):                                                    # когти — вниз и вперёд
    cube("right_hand", (-6.55 + k * 0.05, HY - 6.4, z - 0.3), (0.7, 4.2, 0.7), "bone", face="finger",
         rot=(-16 + k * 6, 0, 0), pivot=(-6.2, HY - 2.2, z))
cube("right_hand", (-6.6, HY - 5.4, -1.6), (0.7, 3.2, 0.7), "bone", face="finger", rot=(-28, 0, -10),
     pivot=(-6.2, HY - 2.2, -1.2))
cube("right_hand", (-4.6, HY - 4.6, -1.1), (0.7, 3.0, 0.7), "bone", face="finger", rot=(-20, 0, 18),
     pivot=(-4.3, HY - 1.6, -0.8))                                                           # большой палец
M.mirror_x("right_arm", "left_arm")
M.mirror_x("right_fore", "left_fore", parent="left_arm")
M.mirror_x("right_hand", "left_hand", parent="left_fore")
M.pack()
M.finalize()

# =====================================================================
# 1b. Кровавое копьё (ось — z, остриё в −z, центр на высоте 24 → в Java y = 0)
# =====================================================================
S = Model(NAME + "_spear", 64, 64)
S.bone("spear", None, (0, 24, 0))
sc = S.cube
sc("spear", (-0.5, 23.5, -9.0), (1.0, 1.0, 22.0), "shaft", face="shaft")
sc("spear", (-0.9, 23.1, 12.8), (1.8, 1.8, 1.6), "shaft", face="butt")
sc("spear", (-0.4, 23.6, 14.4), (0.8, 0.8, 2.4), "blade", face="butt_spike")
for z in (-3.0, 3.0, 8.5):                                                                   # кольца сгустков
    sc("spear", (-0.8, 23.2, z), (1.6, 1.6, 1.0), "clot", face="ring")
sc("spear", (-1.1, 22.9, -10.4), (2.2, 2.2, 1.6), "bone", face="collar")                      # костяная муфта
sc("spear", (-0.45, 22.5, -17.6), (0.9, 3.0, 7.2), "blade", face="blade")                      # клинок (плоский, по y)
sc("spear", (-0.35, 23.0, -21.4), (0.7, 2.0, 3.8), "blade", face="tip")
sc("spear", (-0.25, 23.5, -23.4), (0.5, 1.0, 2.0), "blade", face="tip")
for s_ in (-1, 1):                                                                             # зазубрины‑крюки
    sc("spear", (-0.3, 24.0 + s_ * 1.6 - 0.5, -12.6), (0.6, 1.0, 2.6), "blade", face="barb",
       rot=(s_ * 28, 0, 0), pivot=(0, 24.0 + s_ * 1.2, -11.4))
for k, (z, h) in enumerate(((-6.0, 1.6), (1.0, 1.2), (6.0, 1.8))):                             # застывшие капли
    sc("spear", (-0.3, 23.5 - h, z), (0.6, h, 0.6), "clot", face="drip")
S.pack()
S.finalize()


# =====================================================================
# 2. Текстуры
# =====================================================================
def side(fname):
    return fname not in ("top", "bottom")


def make_painter(model, seed):
    PT = Painter(model.tex_w, model.tex_h, seed=seed)
    R = PT.rnd

    def lit(fname, col, k=1.0):
        return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.035))

    def flesh(fname, i, j, w, h, face):
        fiber = (i * 5 + (j // 3) * 2) % 4
        col = RED if fiber else CRIMSON
        if (i + j * 3) % 11 == 0:
            col = SCARLET
        if (i * 7 + j * 5) % 23 == 0:
            col = SCARLET_L                                       # влажный блик
        col = mix(col, BORDEAUX, 0.35 * j / max(1, h - 1))
        if side(fname) and i in (0, w - 1):
            col = mix(col, BORDEAUX_D, 0.45)
        if face in ("shin", "foot", "thigh"):
            col = mix(col, BORDEAUX_D, 0.45)                      # ноги темнее (в тени рясы)
        if face == "fore" and fname in ("front", "left", "right"):
            vein = (w // 2 + int(round(0.9 * math.sin(j * 0.8 + len(fname))))) % w
            if i == vein and j % 4 != 3:
                return ("glow", mix(GLOW, RED, 0.45))             # светящиеся жилы
        return lit(fname, col)

    def robe(c, face, fname, i, j, w, h):
        """Ряса: чёрно‑бордовый бархат со складками, алая кайма у подола, рваный низ, алая подкладка."""
        fold = 0.82 + 0.2 * math.sin(i * 1.1) + 0.06 * math.sin(i * 2.7 + 1)
        base = mix(CLOTH, CLOTH_L, 0.4 + 0.3 * math.sin(i * 0.7 + j * 0.15))
        inner = (fname == "back" and c["origin"][2] < 0) or (fname == "front" and c["origin"][2] > 0)
        if face == "robe_panel":
            if fname in ("front", "back", "left", "right") and j >= h - 3:
                tear = (i * 7 + 3) % 5
                if j >= h - 1 - tear // 2 + (1 if tear == 0 else 0):
                    return None                                   # рваный низ
            if R.random() < 0.02 and 3 < j < h - 4:
                return None                                       # прорехи
            if inner:
                return lit(fname, mul(RED, 0.75 + 0.15 * math.sin(i)))
            if side(fname) and j == h - 4:
                return lit(fname, CRIMSON if (i + j) % 3 else SCARLET)   # алая кайма
            if fname == "front" and w > 3 and i == (w - 1 if c["origin"][0] < 0 else 0) and j < h - 6:
                return ("glow", mix(GLOW, CRIMSON, 0.55)) if j % 3 == 0 else lit(fname, CRIMSON)   # шитьё по краю
            col = mul(base, fold * (1 - 0.18 * j / h))
            if (i * 3 + j * 5) % 17 == 0:
                col = mix(col, BORDEAUX, 0.6)                     # засохшие пятна
            if j > h * 0.6 and (i * 5 + j) % 9 == 0:
                col = mix(col, CRIMSON, 0.3 * (j / h))            # кровь пропитала низ
            return lit(fname, col)
        if face == "chest":
            if fname == "front":
                # ворот распахнут: в середине тёмная плоть, края — алые отвороты
                half = (w - 1) / 2
                dx = abs(i - half)
                gap = 1.4 + 2.2 * (1 - j / h)
                if dx < gap:
                    return lit(fname, mix(BORDEAUX_D, CRIMSON, 0.3 * PT.noise(0.5)))
                if dx < gap + 1.0:
                    return lit(fname, SCARLET if j % 2 else RED)
            col = mul(base, fold)
            return lit(fname, col)
        if face == "mantle":
            if side(fname) and j == h - 1:
                if (i * 5 + 2) % 3 == 0:
                    return None
                return lit(fname, CRIMSON)
            if fname == "top":
                return lit(fname, mix(CLOTH_L, BORDEAUX, 0.3 * PT.noise(0.6)))
            if fname == "front" and abs(i - (w - 1) / 2) < 1.6:
                return lit(fname, BORDEAUX_D)
            return lit(fname, mul(base, fold))
        if face == "sleeve":
            if fname == "bottom":
                return lit(fname, BORDEAUX_D)
            return lit(fname, mul(base, fold))
        if face == "cuff":
            if fname == "bottom":
                return lit(fname, RED)                            # алая подкладка раструба
            if side(fname) and j == h - 1:
                return None if (i * 3) % 4 == 0 else lit(fname, CRIMSON)
            return lit(fname, mul(base, fold))
        return lit(fname, base)

    def mask_front(i, j, w, h):
        """Гладкая костяная маска без глаз: вертикальная светящаяся щель‑рана, кровавые потёки, резьба."""
        u = (i + 0.5) / w * 2 - 1
        v = (j + 0.5) / h
        x = abs(u)
        shade = 0.82 + 0.25 * (1 - x * x) - 0.12 * v
        col = mul(mix(BONE_S, BONE_W, 0.55 + 0.45 * (1 - x)), shade)
        if x < 0.1 and 0.06 < v < 0.9:
            return ("glow", GLOW_HOT if 0.3 < v < 0.62 else GLOW)
        if x < 0.5 and 0.28 < v < 0.68:
            return mul(BORDEAUX_D, 1.0)                           # тёмные губы раны (шире посередине)
        if x < 0.5 and (v < 0.1 or v > 0.86):
            return mul(mix(BONE_D, CRIMSON, 0.4), 1.0)
        if u < 0 and 0.5 < x < 0.9 and v > 0.7 and i == 1:
            return mix(SCARLET, CRIMSON, (v - 0.7) * 2)           # кровавая слеза
        if abs(v - 0.15) < 0.06 and x > 0.42:
            return mul(BONE_D, 1.0)                               # резьба на лбу
        if abs(v - 0.9) < 0.06 and x > 0.3:
            return mul(BONE_D, 1.0)
        return col

    def paint(c):
        mat, face = c["mat"], c.get("face")
        F = faces(c)
        for fname, rect in F.items():
            def fn(i, j, w, h, fname=fname):
                if mat == "flesh":
                    if face == "ribcage" and fname == "front":
                        rib = j % 2 == 0 and 0 < i < w - 1 and j < h - 1
                        if i in (w // 2 - 1, w // 2) and rib:
                            return lit(fname, BONE_S)             # грудина
                        if rib:
                            return lit(fname, mix(BONE_W, BONE_S, abs(i - w / 2) / w))
                        return lit(fname, mix(BORDEAUX_D, CRIMSON, 0.35))
                    if face == "abdomen" and fname == "front" and j % 2 == 0:
                        return lit(fname, mix(RED, BORDEAUX, 0.5))
                    if face == "palm" and fname == "bottom":
                        return lit(fname, BORDEAUX_D)
                    return flesh(fname, i, j, w, h, face)
                if mat == "lacquer":
                    col = BLACK
                    if fname == "top" or (side(fname) and j == 0):
                        col = LACQUER_HI
                    elif side(fname) and j == h - 1:
                        col = BORDEAUX
                    elif (i + j) % 7 == 0:
                        col = BLACK_L
                    if face in ("pauldron", "bracer", "greave") and fname in ("front", "left", "right") \
                            and i == w // 2 and 0 < j < h - 1:
                        return ("glow", mix(GLOW, CRIMSON, 0.4))      # алая филигрань
                    if face == "crown":
                        if j == 0:
                            return ("glow", GLOW)                     # раскалённые острия венца
                        return lit(fname, BLACK if j > 1 else BORDEAUX)
                    if face == "belt" and fname == "front" and j == 1 and i % 3 == 0:
                        return lit(fname, CRIMSON)
                    return lit(fname, col)
                if mat == "robe":
                    return robe(c, face, fname, i, j, w, h)
                if mat == "hood":
                    if face == "hood" and fname == "front":
                        # передний край капюшона: рамка вокруг маски, внутри — тьма
                        if i in (0, w - 1) or j == 0:
                            return lit(fname, CRIMSON if (i + j) % 2 else RED)
                        return mul(BLACK, 0.6)
                    col = BLACK if (i * 3 + j) % 5 else BLACK_L
                    if fname == "top" or (side(fname) and j == 0):
                        col = mix(col, LACQUER_HI, 0.5)
                    if side(fname) and (i * 7 + j * 3) % 19 == 0:
                        col = BORDEAUX
                    return lit(fname, col)
                if mat == "mask":
                    if fname == "front":
                        v = mask_front(i, j, w, h)
                        return v if isinstance(v, tuple) and v[0] == "glow" else lit(fname, v, 1.0 / FACE_LIGHT[fname])
                    return lit(fname, BONE_S)
                if mat == "heart":
                    if fname == "back":
                        return lit(fname, BORDEAUX)
                    hot = (i, j) in ((0, 0), (1, 0)) and fname == "front"
                    return ("glow", GLOW_HOT if hot else (GLOW if (i + j) % 2 == 0 else mix(GLOW, CRIMSON, 0.35)))
                if mat == "bone":
                    t = j / max(1, h - 1)
                    if face in ("finger", "toe", "spike", "crown", "spine_tip"):
                        col = mix(BONE_W, BONE_S, t * 0.7)
                        if face in ("finger", "toe") and t > 0.7:
                            col = mix(col, CRIMSON, (t - 0.7) * 3)    # когти в крови
                        if face in ("crown", "spine_tip", "spike") and j <= 1:
                            col = mix(col, SCARLET, 0.5)              # окровавленные острия
                        return lit(fname, col)
                    if face == "jaw":
                        if fname == "front" and j == 0:
                            return lit(fname, BONE_W if i % 2 == 0 else BONE_D)   # зубы
                        if fname == "front" and j == 1 and i % 2 == 0:
                            return lit(fname, BONE_D)
                        if fname == "top":
                            return ("glow", mix(GLOW, CRIMSON, 0.5)) if (i + j) % 2 == 0 else lit(fname, RED)
                    if face in ("spine", "vertebra"):
                        k = (j % 3 == 0)
                        return lit(fname, mix(BONE_W, BONE_D, 0.25 + (0.35 if k else 0.0) + 0.3 * t))
                    return lit(fname, mix(BONE_W, BONE_S, 0.3 + 0.4 * t))
                # копьё
                if mat == "blade":
                    # застывшая кровь: тёмная сердцевина, светящиеся кромки и острие
                    if face in ("tip", "barb", "butt_spike"):
                        return ("glow", GLOW_HOT if j <= 1 or face == "tip" and i == w // 2 else GLOW)
                    if fname in ("left", "right"):
                        if j in (0, h - 1) or i in (0, w - 1):
                            return ("glow", GLOW_HOT if j in (0, h - 1) else GLOW)
                        wave = math.sin(i * 0.9 + j * 1.6)
                        if wave > 0.55:
                            return ("glow", mix(GLOW, CRIMSON, 0.4))
                        return lit(fname, mix(CRIMSON, BORDEAUX_D, 0.4 + 0.3 * wave))
                    return ("glow", GLOW)
                if mat == "shaft":
                    if (i + j * 2) % 6 == 0:
                        return ("glow", mix(GLOW, CRIMSON, 0.5))     # прожилки
                    return lit(fname, mix(BORDEAUX, CRIMSON, 0.5 + 0.5 * math.sin(i * 1.3 + j * 0.4)))
                if mat == "clot":
                    return lit(fname, SCARLET if (i + j) % 3 == 0 else RED)
                return (255, 0, 255)
            PT.fill(rect, fn)
    for c in model.cubes():
        paint(c)
    return PT


def trail_textures():
    """Кровавый след «Вихря»: u — вдоль следа (0 — хвост, 1 — голова), v — по высоте.
    blood_trail.png — тёмная полупрозрачная кровь, blood_trail_glow.png — яркая сердцевина и кромка."""
    W, H = 128, 32
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bp_, gp = base.load(), glow.load()
    import random
    R = random.Random(41)
    streaks = [R.random() for _ in range(H)]
    for x in range(W):
        u = x / (W - 1)
        for y in range(H):
            v = y / (H - 1)
            # толщина ленты сужается к хвосту, рваный край
            half = 0.28 + 0.22 * u ** 0.6 + 0.04 * math.sin(x * 0.7 + y)
            dv = abs(v - 0.5)
            if dv > half:
                continue
            edge = 1 - dv / half
            streak = 0.65 + 0.35 * math.sin(u * 40 * (0.5 + streaks[y]) + streaks[y] * 6)
            a = (u ** 1.3) * min(1.0, edge * 2.2) * streak
            col = mix(BORDEAUX_D, SCARLET, 0.25 + 0.55 * u * edge)
            bp_[x, y] = (col[0], col[1], col[2], clamp(235 * a))
            g = (u ** 2.2) * max(0.0, edge - 0.55) / 0.45 * streak
            if 0.42 < half - dv < 0.5:
                g = max(g, 0.45 * u ** 1.5)                        # тонкая кромка
            gc = mix(GLOW, GLOW_HOT, max(0.0, edge - 0.8) * 4)
            gp[x, y] = (gc[0], gc[1], gc[2], clamp(255 * g))
    d = os.path.join(ROOT, "textures", "fx")
    os.makedirs(d, exist_ok=True)
    base.save(os.path.join(d, "blood_trail.png"))
    glow.save(os.path.join(d, "blood_trail_glow.png"))


# =====================================================================
# 3. Позы и IK (как в curb_gen.py)
# =====================================================================
# Ступни (rf/lf) — лодыжка в системе модели (Java: y вниз, −z вперёд; на земле y = 22.4).
# Кисти (rh/lh) — запястье в системе корпуса (от опоры корпуса); плечо: (∓5.3, −9.6, 0).
GROUND = 24 - ANKLE
SHOULDER = {"rh": (-5.3, -(SH_Y - TORSO_Y), 0.0), "lh": (5.3, -(SH_Y - TORSO_Y), 0.0)}
DIRS = ("r_pole", "l_pole")
DEFAULT = dict(
    root_y=0.0, root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0, 0), torso=(0, 0, 0), head=(0, 0, 0), jaw=0.0,
    rf=(-2.2, GROUND, 0), lf=(2.2, GROUND, 0), toe_r=0.0, toe_l=0.0,
    rh=(-6.4, 7.0, -1.0), lh=(6.4, 7.0, -1.0), r_pole=(0.2, 0, 1), l_pole=(-0.2, 0, 1),
    rhand=(0, 0, 0), lhand=(0, 0, 0), robe=0.0, flare=0.0, heart=1.0,
)


def P(base=None, **kw):
    d = dict(DEFAULT)
    if base:
        d.update(base)
    d.update(kw)
    return d


def _heavy_in(x):
    return 0.55 * x * x + 0.45 * ease(x)


EASES = {"lin": lambda x: x, "ease": ease, "in": _heavy_in, "out": lambda x: 1 - _heavy_in(1 - x),
         "in3": ease_in, "out3": ease_out}


def slerp_dir(a, b, t):
    a, b = norm(list(a)), norm(list(b))
    c = max(-1.0, min(1.0, dot(a, b)))
    ang = math.acos(c)
    if ang < 1e-4:
        return a
    if ang > math.pi - 1e-3:
        mid = norm(cross(a, [0, 1, 0]) if abs(a[1]) < 0.9 else cross(a, [1, 0, 0]))
        return slerp_dir(a, mid, t * 2) if t < 0.5 else slerp_dir(mid, b, t * 2 - 1)
    s0 = math.sin((1 - t) * ang) / math.sin(ang)
    s1 = math.sin(t * ang) / math.sin(ang)
    return [a[i] * s0 + b[i] * s1 for i in range(3)]


def lerp_key(name, a, b, t):
    if name in SHOULDER:                                   # кисть — по дуге вокруг плеча
        o = SHOULDER[name]
        va, vb = sub(list(a), list(o)), sub(list(b), list(o))
        return tuple(add(list(o), scl(slerp_dir(va, vb, t), lerp(length(va), length(vb), t))))
    if name in DIRS:
        return tuple(slerp_dir(a, b, t))
    if isinstance(a, (list, tuple)):
        return tuple(lerp(a[i], b[i], t) for i in range(len(a)))
    return lerp(a, b, t)


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


# Кисти чуть отстают от корпуса, ряса — сильнее (движение «тяжёлое», с хлёстом).
LAG = {"head": -0.02, "rh": 0.04, "lh": 0.04, "r_pole": 0.04, "l_pole": 0.04, "rhand": 0.06, "lhand": 0.06,
       "robe": 0.14, "flare": 0.1}


def layered(keys, t):
    pr = keyed(keys, t)
    for name, lag in LAG.items():
        pr[name] = keyed(keys, max(0.0, t - lag))[name]
    return pr


def frame_X(y_axis, x_hint):
    y = norm(y_axis)
    x = sub(x_hint, scl(y, dot(x_hint, y)))
    if length(x) < 1e-6:
        x = cross(y, [0, 0, 1]) if abs(y[2]) < 0.9 else cross(y, [1, 0, 0])
    x = norm(x)
    z = cross(x, y)
    return [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]


PREV_PN = {}


def smooth01(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def axis_R(k, ang):
    x, y, z = k
    c, s_, v = math.cos(ang), math.sin(ang), 1 - math.cos(ang)
    return [[c + x * x * v, x * y * v - z * s_, x * z * v + y * s_],
            [y * x * v + z * s_, c + y * y * v, y * z * v - x * s_],
            [z * x * v - y * s_, z * y * v + x * s_, c + z * z * v]]


def limb_ik(model, pose, upper, lower, target, pole, a, b, side_sign, fallback=None):
    """IK из двух звеньев вокруг общей оси сгиба (без прокручивания), сгиб не быстрее 12° за кадр."""
    S_ = model.world(upper, [0, 0, 0], pose)
    d = sub(target, S_)
    dist = max(abs(a - b) + 0.05, min(a + b - 0.05, length(d)))
    dn = norm(d)
    al = math.acos(max(-1.0, min(1.0, (a * a + dist * dist - b * b) / (2 * a * dist))))
    pole = norm(pole)
    if fallback is not None:
        k = smooth01(0.8, 0.97, abs(dot(pole, dn)))
        pole = norm(add(scl(pole, 1 - k), scl(norm(fallback), k)))
    p = sub(pole, scl(dn, dot(pole, dn)))
    pn = norm(p) if length(p) > 1e-6 else [0, 0, -1]
    key = (model.name, upper)
    prev = PREV_PN.get(key)
    if prev is not None:
        pp = sub(prev, scl(dn, dot(prev, dn)))
        if length(pp) > 1e-3:
            pp = norm(pp)
            ang = math.acos(max(-1.0, min(1.0, dot(pp, pn))))
            if ang > math.radians(12):
                ax = cross(pp, pn)
                ax = norm(ax) if length(ax) > 1e-6 else dn
                pn = mv(axis_R(ax, math.radians(12)), pp)
    PREV_PN[key] = pn
    E = add(S_, scl(add(scl(dn, math.cos(al)), scl(pn, math.sin(al))), a))
    T_ = add(S_, scl(dn, dist))
    n = scl(norm(cross(dn, pn)), side_sign)
    model.set_world_rot(pose, upper, frame_X(sub(E, S_), n))
    model.set_world_rot(pose, lower, frame_X(sub(T_, E), n))


def resolve(pr, model=M):
    pose = {"root": {"rot": (0, pr["root_y"], 0), "pos": pr["root_pos"]},
            "body": {"rot": pr["body"], "pos": pr["body_pos"]},
            "torso": {"rot": pr["torso"]}, "head": {"rot": pr["head"]}, "jaw": {"rot": (pr["jaw"], 0, 0)}}
    s = pr["heart"]
    pose["heart"] = {"rot": (0, 0, 0), "scale": (s, s, s)}
    Rroot = model.chain_R("root", pose)
    for sd, key, toe in (("right", "rf", "toe_r"), ("left", "lf", "toe_l")):
        tgt = model.world("root", [pr[key][0], pr[key][1] - 24, pr[key][2]], pose)
        limb_ik(model, pose, sd + "_leg", sd + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN, -1)
        model.set_world_rot(pose, sd + "_foot", mm(Rroot, rx_m(math.radians(pr[toe]))))
    Rt = model.chain_R("torso", pose)
    for sd, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        out = mv(Rt, [-1.0 if sd == "right" else 1.0, 0.3, 0.4])
        limb_ik(model, pose, sd + "_arm", sd + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE, 1, out)
    pose["right_hand"] = {"rot": pr["rhand"]}
    pose["left_hand"] = {"rot": pr["lhand"]}
    # ряса висит вниз, следует за бёдрами и отстаёт; flare — разлёт полотнищ в «Вихре»
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    tilt = pr["body"][0]
    fl = pr["flare"]
    pose["robe_fr"] = {"rot": (min(rl, 0) * 0.85 - tilt * 0.9 - pr["robe"] * 0.4 - fl * 24, 0, 4 - fl * 8)}
    pose["robe_fl"] = {"rot": (min(ll, 0) * 0.85 - tilt * 0.9 - pr["robe"] * 0.4 - fl * 24, 0, -4 + fl * 8)}
    pose["robe_b"] = {"rot": (max(rl, ll, 0) * 0.7 - tilt * 0.9 + pr["robe"] + 3 + fl * 26, 0, 0)}
    pose["robe_sr"] = {"rot": (-tilt * 0.9 + pr["robe"] * 0.3, 0, -3 - fl * 24)}
    pose["robe_sl"] = {"rot": (-tilt * 0.9 + pr["robe"] * 0.3, 0, 3 + fl * 24)}
    return pose


def canonical(r):
    from mobgen import euler, to_euler
    return to_euler(euler(r))


def anchor_end(anim):
    """Конец анимации — в обычной записи углов (иначе при переходе в покой кость делает лишний оборот)."""
    from mobgen import euler_near
    for bone_name, chans in anim["bones"].items():
        rot = chans.get("rotation")
        if not rot or len(rot) < 3:
            continue
        keys = sorted(rot.keys(), key=float)
        F = [rot[k]["post"] if isinstance(rot[k], dict) else rot[k] for k in keys]
        end = canonical(F[-1])
        end = euler_near(end, end)
        if max(abs(end[i] - F[-1][i]) for i in range(3)) < 1.0:
            continue
        B_ = [None] * len(F)
        B_[-1] = end
        for k in range(len(F) - 2, -1, -1):
            B_[k] = euler_near(F[k], B_[k + 1])
        m = len(F) // 2
        for k in range(m, len(F)):
            if k == m:
                rot[keys[k]] = {"pre": [round(v, 2) for v in F[k]], "post": [round(v, 2) for v in B_[k]]}
            else:
                rot[keys[k]] = [round(v, 2) for v in B_[k]]


def gait(w, stride, lift, base_y=GROUND, width=2.2, z0=0.0):
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l


def beat(t, period):
    """Удар сердца: двойной толчок раз в period секунд."""
    x = t % period
    return math.exp(-((x - 0.08) / 0.05) ** 2) + 0.6 * math.exp(-((x - 0.28) / 0.06) ** 2)


# =====================================================================
# 4. Анимации
# =====================================================================
# Сгорбленная стойка: корпус наклонён, голова чуть поднята, длинные руки висят перед коленями
STANCE = dict(body_pos=(0, 0.7, 0), body=(4, 0, 0), torso=(20, 0, 0), head=(-16, 0, 0), jaw=3.0,
              rf=(-2.4, GROUND, 0.9), lf=(2.4, GROUND, -0.9),
              rh=(-6.8, 6.2, -3.4), lh=(6.6, 5.8, -2.8), r_pole=(0.3, 0, 1), l_pole=(-0.3, 0, 1),
              rhand=(-12, 0, -6), lhand=(-12, 0, 6))
# Руки воздеты над головой (вызов копий)
CHANNEL = dict(body_pos=(0, 0.3, 0), body=(-2, 0, 0), torso=(-6, 0, 0), head=(-12, 0, 0), jaw=12.0,
               rf=(-2.7, GROUND, 1.2), lf=(2.7, GROUND, -1.2),
               rh=(-11.0, -21.6, -3.6), lh=(11.0, -21.6, -3.6), r_pole=(-0.4, -0.2, 1), l_pole=(0.4, -0.2, 1),
               rhand=(30, 0, -18), lhand=(30, 0, 18), robe=2.0)


def idle(t):
    L = 3.2
    w = 2 * math.pi * t / L
    pr = P(STANCE)
    pr["body_pos"] = (0, 0.7 + 0.25 * math.sin(2 * w), 0)
    pr["torso"] = (20 + 2.0 * math.sin(2 * w + 0.4), 3 * math.sin(w), 0)
    # голова «прислушивается»: медленный поворот и наклон набок
    pr["head"] = (-16 + 3 * math.sin(2 * w + 1.0), 12 * math.sin(w + 0.6), 7 * math.sin(w) ** 3)
    pr["jaw"] = 3 + 5 * max(0.0, math.sin(w * 2 - 1.2)) ** 3
    pr["rh"] = (-6.8 + 0.4 * math.sin(w), 6.2 + 0.4 * math.sin(2 * w + 0.7), -3.4 + 0.6 * math.sin(w + 0.4))
    pr["lh"] = (6.6 - 0.4 * math.sin(w + 1), 5.8 + 0.4 * math.sin(2 * w + 1.1), -2.8 + 0.6 * math.sin(w + 2.0))
    pr["rhand"] = (-12 + 6 * math.sin(2 * w + 2), 0, -6)        # когти шевелятся
    pr["lhand"] = (-12 + 6 * math.sin(2 * w + 0.5), 0, 6)
    pr["robe"] = 1.5 * math.sin(2 * w - 0.6)
    pr["heart"] = 1.0 + 0.16 * beat(t, 1.6)
    return pr


def walk(t):
    L = 1.8
    w = 2 * math.pi * t / L
    pr = P(STANCE)
    pr["rf"], pr["lf"] = gait(w, 3.4, 1.7)
    s = math.sin(w)
    pr["body_pos"] = (0, 0.9 + 0.55 * abs(math.cos(w)), 0)
    pr["body"] = (5, 5 * s, -2 * math.cos(w))
    pr["torso"] = (22 + 1.5 * math.cos(2 * w), -7 * s, 0)
    pr["head"] = (-18 + 2 * math.cos(2 * w), 4 * s, 2 * math.cos(w))
    # длинные руки качаются вяло, в противофазе ногам, с запаздыванием
    pr["rh"] = (-6.6, 6.0 + 0.3 * math.cos(2 * w), -3.2 + 2.2 * math.sin(w - 0.5))
    pr["lh"] = (6.6, 6.0 + 0.3 * math.cos(2 * w), -3.2 - 2.2 * math.sin(w - 0.5))
    pr["rhand"] = (-14 + 6 * math.sin(w - 1.0), 0, -6)
    pr["lhand"] = (-14 - 6 * math.sin(w - 1.0), 0, 6)
    pr["toe_r"] = -12 * max(0.0, s)
    pr["toe_l"] = -12 * max(0.0, -s)
    pr["robe"] = 3 + 2.5 * math.sin(2 * w)
    pr["jaw"] = 3 + 2 * math.sin(2 * w) ** 2
    pr["heart"] = 1.0 + 0.16 * beat(t, 0.9)
    return pr


def summon(t):
    """Вызов копий: кисти собираются у груди (поклон), затем руки медленно воздеваются над головой, рёв."""
    keys = [(0.0, STANCE, "lin"),
            (0.35, dict(body_pos=(0, 1.4, 0), torso=(28, 0, 0), head=(-6, 0, 0), jaw=1.0,
                        rh=(-3.0, -2.6, -6.0), lh=(3.0, -2.8, -6.0), r_pole=(-0.6, 0.6, 0.6), l_pole=(0.6, 0.6, 0.6),
                        rhand=(-40, 0, 20), lhand=(-40, 0, -20), robe=-1.0), "ease"),
            (1.05, dict(CHANNEL, torso=(-9, 0, 0), head=(-20, 0, 0), jaw=18.0, body_pos=(0, 0.1, 0),
                        rh=(-11.8, -22.2, -3.0), lh=(11.8, -22.2, -3.0), robe=4.0), "out"),
            (1.5, CHANNEL, "ease")]
    pr = layered(keys, t)
    pr["heart"] = 1.0 + 0.22 * beat(t, 0.7)
    return pr


def channel(t):
    L = 1.4
    w = 2 * math.pi * t / L
    pr = P(CHANNEL)
    pr["torso"] = (-6 + 1.0 * math.sin(w), 2 * math.sin(w + 0.5), 0)
    pr["rh"] = (-11.0 + 0.4 * math.cos(w), -21.6 + 0.4 * math.sin(w), -3.6)
    pr["lh"] = (11.0 - 0.4 * math.cos(w + 0.8), -21.6 + 0.4 * math.sin(w + 0.8), -3.6)
    pr["rhand"] = (30 + 5 * math.sin(w), 0, -18)
    pr["lhand"] = (30 + 5 * math.sin(w + 1.3), 0, 18)
    pr["jaw"] = 12 + 3 * math.sin(w)
    pr["robe"] = 2 + 1.0 * math.sin(w)
    pr["heart"] = 1.0 + 0.22 * beat(t, 0.7)
    return pr


def fling(sd):
    """Бросок копья: рука отводится назад‑вверх (0.16 с), хлёстко выбрасывается вперёд на цель (0.3 с), возврат."""
    k = -1 if sd == "r" else 1
    hand, pole, hrot = ("rh", "r_pole", "rhand") if sd == "r" else ("lh", "l_pole", "lhand")
    back = {hand: (5.0 * k, -20.6, 4.2), pole: (0.0, 0.0, 1.0), "torso": (-8, 14 * k, 0), "head": (-12, -6 * k, 0),
            hrot: (60, 0, 0), "jaw": 8.0}
    throw = {hand: (3.0 * k, -8.2, -15.4), pole: (k * -0.8, 0.4, 0.4), "torso": (10, -18 * k, 0), "head": (-26, 16 * k, 0),
             hrot: (-30, 0, 0), "jaw": 20.0, "body_pos": (0, 0.8, 0), "robe": 5.0}

    def fn(t):
        keys = [(0.0, CHANNEL, "lin"), (0.16, back, "ease"), (0.30, throw, "in"), (0.42, throw, "lin"),
                (0.6, CHANNEL, "ease")]
        pr = layered(keys, t)
        pr["heart"] = 1.0 + 0.22 * beat(t, 0.7)
        return pr
    return fn


WHIRL_LOW = dict(body_pos=(0, 2.6, 0), body=(6, 0, 0), torso=(24, 0, 0), head=(-26, 0, 0), jaw=14.0,
                 rf=(-3.6, GROUND, 1.0), lf=(3.6, GROUND, -1.0),
                 rh=(-18.0, -4.6, -1.0), lh=(18.0, -4.6, -1.0), r_pole=(0, -0.4, 1), l_pole=(0, -0.4, 1),
                 rhand=(-20, 0, -30), lhand=(-20, 0, 30), flare=0.4)


def whirl(t):
    """«Кровавый вихрь»: присед, руки в стороны (0.3 с), оборот на 360° с разлётом рясы (0.3–1.8 с), возврат."""
    keys = [(0.0, dict(STANCE), "lin"),
            (0.3, WHIRL_LOW, "out"),
            (1.8, dict(WHIRL_LOW, root_y=360.0, flare=0.4), "lin"),
            (2.05, dict(STANCE, root_y=360.0, body_pos=(0, 1.4, 0), flare=0.1, jaw=8.0), "ease"),
            (2.3, dict(STANCE, root_y=360.0), "ease")]
    pr = layered(keys, t)
    # вращение — плавный разгон и торможение
    if 0.3 <= t <= 1.8:
        x = (t - 0.3) / 1.5
        pr["root_y"] = 360.0 * (x - math.sin(2 * math.pi * x) / (2 * math.pi))
        pr["flare"] = 0.4 + 0.6 * math.sin(math.pi * x)
        pr["torso"] = (24 - 4 * math.sin(math.pi * x), -14 * math.sin(math.pi * x), 0)
    pr["heart"] = 1.0 + 0.22 * beat(t, 0.6)
    return pr


ANIM_BONES = [b["name"] for b in M.bones if b["name"] not in ()]


def clip(fn, length_s, fps, loop):
    PREV_PN.clear()
    fn(0.0)
    resolve(fn(0.0))
    return bake(lambda t: resolve(fn(t)), length_s, fps, loop, ANIM_BONES, unwrap=True)


def main():
    PT = make_painter(M, 23)
    PT.img.save(os.path.join(ROOT, "textures", "entity", NAME + ".png"))
    PT.glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_glowmask.png"))
    M.write_geo(ROOT)
    SP = make_painter(S, 7)
    SP.img.save(os.path.join(ROOT, "textures", "entity", NAME + "_spear.png"))
    SP.glow.save(os.path.join(ROOT, "textures", "entity", NAME + "_spear_glowmask.png"))
    S.write_geo(ROOT)
    trail_textures()

    clips = {"idle": (idle, 3.2, 20, True), "walk": (walk, 1.8, 20, True), "summon": (summon, 1.5, 30, False),
             "channel": (channel, 1.4, 20, True), "fling_r": (fling("r"), 0.6, 40, False),
             "fling_l": (fling("l"), 0.6, 40, False), "whirl": (whirl, 2.3, 40, False)}
    only = set(filter(None, os.environ.get("ONLY", "").split(",")))
    anims = {}
    for cname, (fn, ln, fps, loop) in clips.items():
        if only and cname not in only:
            continue
        a = clip(fn, ln, fps, loop)
        if not loop:
            anchor_end(a)
        anims["animation.%s.%s" % (NAME, cname)] = a
    write_anims(ROOT, NAME, anims)
    print(NAME, len(M.cubes()), "кубов; копьё", len(S.cubes()), "кубов; клипы:", ", ".join(anims))


if __name__ == "__main__":
    main()
