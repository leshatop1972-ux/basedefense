"""
Генератор босса «Бордюр» (curb) и его прислужника (curb_minion): модели Bedrock, текстуры, анимации.

Запуск:  python tools/curb_gen.py
Проверка без игры:
    python tools/mob_preview.py curb                         — поза покоя
    python tools/mob_preview.py curb combo 0.4,0.65,1.15,1.65
    python tools/mob_preview.py curb_minion emerge
    python tools/anim_check.py curb

Оригинальный боец в чёрно‑графитовой броне (по скинам пользователя): шлем с вертикальной розово‑белой
светящейся щелью, белое ядро на груди, сегменты по корпусу, тёмные наплечники с тусклой латунью,
розовые полосы на наручах. В локтях — реактивные сопла: при замахе из локтя бьёт синяя струя
(кость jet_r/jet_l — только светящаяся маска, длину и мерцание задаёт код).
Прислужник — серый в капюшоне и длинном пальто с пуговицами и плащом, на лице синий знак‑крюк.

Анимации — ключевые позы + обратная кинематика (ступни стоят на земле, кулаки — в точках).
Тайминги ударов совпадают с CurbEntity / CurbMinionEntity.
"""
import math
import os

from mobgen import (FACE_LIGHT, Model, Painter, add, bake, cross, dot, ease, ease_in, ease_out, faces, length, lerp,
                    mix, mm, mul, mv, norm, rx_m, scl, sub, tr, write_anims)
import asset_groups  # noqa: E402  (папки мобов: geo/animations/textures)

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")

# =====================================================================
# Палитра
# =====================================================================
ARM_D = (20, 20, 24)
ARM = (34, 34, 40)
ARM_L = (54, 54, 62)
ARM_HI = (82, 82, 92)
SUIT = (48, 48, 53)
SUIT_D = (32, 32, 36)
PANTS = (66, 66, 70)
PANTS_D = (46, 46, 50)
BRASS = (106, 98, 62)
BRASS_D = (70, 66, 42)
PINK = (232, 140, 222)
PINK_HI = (255, 222, 250)
CORE = (250, 246, 255)
BLUE = (64, 140, 255)
BLUE_HI = (186, 228, 255)
BLUE_D = (30, 70, 190)
VIOLET = (168, 64, 255)                                    # щель визора (фиолетовая)
VIOLET_HI = (226, 176, 255)
# хвост: чёрная чешуя
SCALE = (22, 22, 27)
SCALE_D = (11, 11, 14)
SCALE_HI = (40, 36, 50)
BELLY = (46, 44, 50)
BELLY_D = (32, 31, 36)

COAT = (96, 100, 102)
COAT_L = (122, 126, 128)
COAT_D = (68, 72, 74)
COAT_DD = (50, 53, 56)
HOOD_IN = (18, 20, 24)
SLEEVE = (42, 44, 48)
BUTTON = (30, 30, 33)

THIGH, SHIN = 7.5, 6.0
UPPER, FORE = 6.0, 5.5


# =====================================================================
# 1. Модели
# =====================================================================
def skeleton(M, minion):
    bone, cube = M.bone, M.cube
    bone("root", None, (0, 0, 0))
    bone("body", "root", (0, 15, 0))
    # таз и пояс
    cube("body", (-4, 12.6, -2.2), (8, 3.4, 4.4), "pants", face="pelvis")
    cube("body", (-4.3, 15.0, -2.5), (8.6, 1.4, 5.0), "belt", face="belt")
    cube("body", (-1.2, 14.6, -2.9), (2.4, 2.0, 0.6), "brass" if not minion else "button", face="buckle")
    if not minion:
        # набедренные пластины
        cube("body", (-5.2, 10.8, -2.2), (1.3, 4.6, 4.4), "armor", face="tasset", rot=(0, 0, -8), pivot=(-4.5, 15.4, 0))
        cube("body", (3.9, 10.8, -2.2), (1.3, 4.6, 4.4), "armor", face="tasset", rot=(0, 0, 8), pivot=(4.5, 15.4, 0))
    else:
        # полы пальто: спереди две, сзади одна (следуют за ногами)
        bone("coat_fr", "body", (-2.1, 15.4, -2.4))
        cube("coat_fr", (-4.3, 6.0, -2.75), (4.2, 9.4, 0.6), "coat", face="coat_tail")
        bone("coat_fl", "body", (2.1, 15.4, -2.4))
        cube("coat_fl", (0.1, 6.0, -2.75), (4.2, 9.4, 0.6), "coat", face="coat_tail")
        bone("coat_b", "body", (0, 15.4, 2.4))
        cube("coat_b", (-4.4, 5.2, 2.2), (8.8, 10.2, 0.6), "coat", face="coat_tail")

    # ноги
    bone("right_leg", "body", (-2.1, 15, 0))
    cube("right_leg", (-4.1, 7.5, -2), (4, 7.5, 4), "pants", face="thigh")
    bone("right_shin", "right_leg", (-2.1, 7.5, 0))
    cube("right_shin", (-4.0, 1.5, -1.9), (3.8, 6.0, 3.8), "boot", face="shin")
    cube("right_shin", (-4.3, 5.2, -2.5), (4.4, 2.8, 1.2), "armor" if not minion else "boot", face="kneepad")
    bone("right_foot", "right_shin", (-2.1, 1.5, 0))
    cube("right_foot", (-4.1, 0, -3.3), (4, 1.8, 5.3), "boot", face="foot")
    cube("right_foot", (-4.2, 0, -3.6), (4.2, 1.1, 1.2), "armor" if not minion else "boot", face="toecap")
    # реактивная струя из подошвы (взлёт «Пике»): вниз от стопы, только светящаяся маска
    bone("jet_rf", "right_foot", (-2.1, 0, -0.6))
    cube("jet_rf", (-2.5, -7.0, -1.0), (0.8, 7.0, 0.8), "jet", face="fjet_core")
    cube("jet_rf", (-2.95, -4.6, -1.45), (1.7, 4.6, 1.7), "jet", face="fjet_mid")
    cube("jet_rf", (-3.45, -2.2, -1.95), (2.7, 2.2, 2.7), "jet", face="fjet_out")
    M.mirror_x("right_leg", "left_leg")
    M.mirror_x("right_shin", "left_shin", parent="left_leg")
    M.mirror_x("right_foot", "left_foot", parent="left_shin")
    M.mirror_x("jet_rf", "jet_lf", parent="left_foot")

    if not minion:
        # массивный рептильный хвост: 4 сегмента от таза назад, по верху — пластины гребня
        segs = [  # опора z, (x0, y0, z0, w, h, d) кость, гребень [(z0, d)], толщина гребня
            ("tail1", "body", 1.6, (-2.9, 11.0, 1.2, 5.8, 5.2, 7.2), [(2.4, 2.0), (5.4, 2.0)], (1.6, 1.0)),
            ("tail2", "tail1", 8.0, (-2.3, 11.6, 7.6, 4.6, 4.2, 6.6), [(8.6, 1.8), (11.2, 1.8)], (1.4, 0.9)),
            ("tail3", "tail2", 14.0, (-1.7, 12.1, 13.6, 3.4, 3.2, 6.4), [(15.0, 1.6), (17.4, 1.6)], (1.2, 0.8)),
            ("tail4", "tail3", 19.8, (-1.1, 12.6, 19.4, 2.2, 2.2, 5.6), [(20.6, 1.4), (22.8, 1.2)], (0.9, 0.7)),
        ]
        for name, parent, pz, (x0, y0, z0, w, h, d), plates, (pw, ph) in segs:
            bone(name, parent, (0, 13.6, pz))
            cube(name, (x0, y0, z0), (w, h, d), "scale", face=name)
            for zz, dd in plates:
                cube(name, (-pw / 2, y0 + h, zz), (pw, ph, dd), "tail_plate", face="tail_plate")
        cube("tail4", (-0.6, 12.9, 24.8), (1.2, 1.4, 2.4), "scale", face="tail_tip")

    # корпус
    bone("torso", "body", (0, 16.5, 0))
    cube("torso", (-3.8, 16.4, -2.1), (7.6, 3.8, 4.2), "suit" if not minion else "coat", face="waist")
    cube("torso", (-4.6, 20, -2.6), (9.2, 9, 5.2), "chest" if not minion else "coat", face="chest")
    if not minion:
        cube("torso", (-1.2, 25, -3.15), (2.4, 2.4, 0.7), "core", face="core")
        for y in (17.4, 19.2, 21.0, 22.8):                  # сегменты по животу и груди
            cube("torso", (-0.8, y, -2.75 if y < 20 else -3.0), (1.6, 1.2, 0.5), "armor", face="segment")
        cube("torso", (-3.4, 28.6, -2.9), (6.8, 1.8, 5.8), "armor", face="collar")
        cube("torso", (-3.6, 21.2, 2.4), (7.2, 6.6, 1.4), "armor", face="backplate")
        for x in (-2.6, 1.0):                                # вентиляция на спине
            cube("torso", (x, 22.0, 3.6), (1.6, 4.4, 0.6), "nozzle", face="vent")
    else:
        for y in (17.4, 19.6, 21.8, 24.0, 26.2):            # пуговицы
            cube("torso", (-0.5, y, -2.95), (1.0, 1.0, 0.45), "button", face="button")
        cube("torso", (-3.8, 27.4, -3.0), (2.8, 2.2, 0.6), "coat", face="lapel", rot=(0, 0, 18), pivot=(-2.4, 28.5, -2.7))
        cube("torso", (1.0, 27.4, -3.0), (2.8, 2.2, 0.6), "coat", face="lapel", rot=(0, 0, -18), pivot=(2.4, 28.5, -2.7))
        bone("cape", "torso", (0, 28.8, 2.7))
        cube("cape", (-5.2, 9.0, 2.6), (10.4, 19.8, 0.7), "cape", face="cape")

    # голова
    bone("neck", "torso", (0, 29, 0))
    cube("neck", (-1.6, 28.6, -1.6), (3.2, 1.8, 3.2), "suit" if not minion else "hood_in", face="neck")
    bone("head", "neck", (0, 30, 0))
    if not minion:
        cube("head", (-4, 30, -4), (8, 8, 8), "helmet", face="helmet")
        cube("head", (-1, 31.0, -4.4), (2, 6.4, 0.5), "visor", face="visor")
        cube("head", (-4.7, 31.6, -1.8), (0.9, 4.2, 3.6), "armor", face="fin")
        cube("head", (3.8, 31.6, -1.8), (0.9, 4.2, 3.6), "armor", face="fin")
        cube("head", (-0.6, 37.6, -3.4), (1.2, 0.9, 6.6), "armor", face="crest")
        cube("head", (-3.4, 29.6, -4.2), (6.8, 1.4, 1.0), "armor", face="chin")
    else:
        cube("head", (-3.8, 30.2, -3.8), (7.6, 7.6, 7.6), "hood_in", face="face")
        cube("head", (-4.4, 29.6, -3.6), (8.8, 9.0, 8.4), "hood", face="hood", inflate=0.0)
        cube("head", (-3.0, 34.0, 4.4), (6.0, 3.6, 1.6), "hood", face="hood_tip", rot=(-25, 0, 0), pivot=(0, 36.0, 4.4))

    # руки
    bone("right_arm", "torso", (-5.6, 27.5, 0))
    cube("right_arm", (-7.5, 21.5, -1.8), (3.8, 6.5, 3.6), "suit" if not minion else "sleeve", face="upper")
    if not minion:
        cube("right_arm", (-8.5, 25.0, -2.7), (5.0, 3.8, 5.4), "pauldron", face="pauldron")
        cube("right_arm", (-8.8, 27.6, -2.2), (3.6, 1.2, 4.4), "pauldron", face="pauldron_top")
    else:
        cube("right_arm", (-7.9, 24.2, -2.2), (4.6, 4.4, 4.4), "coat", face="shoulder")
    bone("right_fore", "right_arm", (-5.6, 21.5, 0))
    cube("right_fore", (-7.7, 16, -2.0), (4.2, 5.6, 4.0), "gauntlet" if not minion else "sleeve", face="fore")
    # реактивное сопло у локтя: смотрит вдоль предплечья «назад» (при замахе — за спину)
    cube("right_fore", (-6.5, 20.2, 1.7), (1.8, 2.2, 1.5), "nozzle", face="nozzle")
    bone("jet_r", "right_fore", (-5.6, 22.4, 2.45))
    cube("jet_r", (-6.0, 22.4, 2.05), (0.8, 7.0, 0.8), "jet", face="jet_core")
    cube("jet_r", (-6.45, 22.4, 1.6), (1.7, 4.6, 1.7), "jet", face="jet_mid")
    cube("jet_r", (-6.95, 22.4, 1.1), (2.7, 2.2, 2.7), "jet", face="jet_out")
    bone("right_hand", "right_fore", (-5.6, 16, 0))
    cube("right_hand", (-7.4, 12.6, -1.8), (3.6, 3.4, 3.6), "glove", face="fist")
    if not minion:
        cube("right_hand", (-7.6, 13.2, -2.3), (4.0, 1.6, 1.2), "armor", face="knuckles")
    M.mirror_x("right_arm", "left_arm")
    M.mirror_x("right_fore", "left_fore", parent="left_arm")
    M.mirror_x("jet_r", "jet_l", parent="left_fore")
    M.mirror_x("right_hand", "left_hand", parent="left_fore")
    if minion:
        fatten(M)
    M.pack()
    M.finalize()
    return M


# Прислужник — толстый и почти квадратный: корпус шире и глубже, ноги толще, руки отодвинуты к краям
FAT_BODY = (1.4, 1.7)           # корпус и пальто: x, z
FAT_LEG = 1.35                  # толщина ног
FAT_ARM_SHIFT = 1.9             # руки дальше от центра (пикс.)
FAT_ARM = 1.2                   # толщина рук


def fatten(M):
    def scale_cube(c, kx, kz, cx, cz):
        (ox, oy, oz), (sx, sy, sz) = c["origin"], c["size"]
        c["origin"] = [round(cx + (ox - cx) * kx, 4), oy, round(cz + (oz - cz) * kz, 4)]
        c["size"] = [round(sx * kx, 4), sy, round(sz * kz, 4)]
        if "pivot" in c:
            px, py, pz = c["pivot"]
            c["pivot"] = [round(cx + (px - cx) * kx, 4), py, round(cz + (pz - cz) * kz, 4)]

    trunk = ("body", "torso", "coat_fr", "coat_fl", "coat_b", "cape")
    for name in trunk:
        b = M.by_name[name]
        kx, kz = FAT_BODY
        if name != "body" and name != "torso":
            px, py, pz = b["pivot"]
            b["pivot"] = [round(px * kx, 4), py, round(pz * kz, 4)]
        for c in b["cubes"]:
            scale_cube(c, kx, kz, 0.0, 0.0)
    for side in ("right", "left"):
        sg = -1 if side == "right" else 1
        for name in (side + "_leg", side + "_shin", side + "_foot"):
            b = M.by_name[name]
            for c in b["cubes"]:
                cx = c["origin"][0] + c["size"][0] / 2
                cz = c["origin"][2] + c["size"][2] / 2
                scale_cube(c, FAT_LEG, FAT_LEG, cx, cz)
        for name in (side + "_arm", side + "_fore", side + "_hand", "jet_r" if side == "right" else "jet_l"):
            b = M.by_name[name]
            b["pivot"] = [round(b["pivot"][0] + sg * FAT_ARM_SHIFT, 4), b["pivot"][1], b["pivot"][2]]
            for c in b["cubes"]:
                c["origin"][0] = round(c["origin"][0] + sg * FAT_ARM_SHIFT, 4)
                if "pivot" in c:
                    c["pivot"][0] = round(c["pivot"][0] + sg * FAT_ARM_SHIFT, 4)
                if name != ("jet_r" if side == "right" else "jet_l"):
                    cx = c["origin"][0] + c["size"][0] / 2
                    cz = c["origin"][2] + c["size"][2] / 2
                    scale_cube(c, FAT_ARM, FAT_ARM, cx, cz)
    # голова (капюшон) тоже чуть шире и квадратнее
    for c in M.by_name["head"]["cubes"]:
        scale_cube(c, 1.18, 1.1, 0.0, 0.0)


B = skeleton(Model("curb", 128, 128), False)
N = skeleton(Model("curb_minion", 128, 128), True)


# =====================================================================
# 2. Текстуры
# =====================================================================
def side(fname):
    return fname not in ("top", "bottom")


def paint(M, minion, seed):
    PT = Painter(M.tex_w, M.tex_h, seed=seed)

    def lit(fname, col, k=1.0):
        return mul(col, FACE_LIGHT[fname] * k * PT.noise(0.03))

    def armor_px(fname, i, j, w, h, base=ARM, rim=ARM_HI):
        # пластина: светлая кромка сверху, тёмная снизу, редкие царапины
        if side(fname) and j == 0:
            return lit(fname, rim)
        if side(fname) and j == h - 1:
            return lit(fname, ARM_D)
        if side(fname) and (i == 0 or i == w - 1):
            return lit(fname, mix(base, ARM_D, 0.45))
        if (i * 7 + j * 11) % 29 == 0:
            return lit(fname, ARM_L)
        return lit(fname, base)

    for c in M.cubes():
        mat, face = c["mat"], c.get("face")
        for fname, rect in faces(c).items():
            def fn(i, j, w, h, fname=fname):
                if mat == "jet":
                    return "jet"
                if mat in ("armor", "pauldron", "helmet"):
                    if mat == "pauldron":
                        if side(fname) and j == h - 1:
                            return lit(fname, BRASS_D)
                        if side(fname) and j == h - 2 and i % 3 == 1:
                            return lit(fname, BRASS)             # заклёпки
                        if fname == "top" and (i in (0, w - 1) or j in (0, h - 1)):
                            return lit(fname, BRASS_D)
                    if mat == "helmet":
                        if fname == "front":
                            # щель визора идёт отдельным кубом — здесь тёмная маска с рёбрами
                            if i in (3, 4):
                                return lit(fname, ARM_D)
                            if j in (2, 5) and i not in (0, 7):
                                return lit(fname, ARM_L)
                            return lit(fname, ARM if j > 0 else ARM_HI)
                        if fname == "top":
                            return lit(fname, ARM_L if (i + j) % 5 == 0 else ARM)
                        if side(fname) and j == 6:
                            return ("glow", VIOLET) if fname in ("left", "right") and i in (2, 3, 4, 5) else lit(fname, ARM_D)
                    if face == "segment":
                        return lit(fname, ARM_HI if j == 0 else ARM_L)
                    if face == "kneepad" and fname == "front" and j == 1 and i in (1, w - 2):
                        return lit(fname, BRASS)
                    return armor_px(fname, i, j, w, h)
                if mat == "chest":
                    if fname == "front":
                        # нагрудник: два больших сегмента и центральный шов
                        if i in (w // 2 - 1, w // 2):
                            return lit(fname, ARM_D)
                        if j == 0:
                            return lit(fname, ARM_HI)
                        if j in (4, 8):
                            return lit(fname, ARM_D)
                        if i in (1, w - 2) and j in (2, 6):
                            return lit(fname, BRASS)
                        return lit(fname, ARM if j < 4 else ARM_L)
                    if fname == "back":
                        return lit(fname, SUIT_D if j > 2 else ARM)
                    return armor_px(fname, i, j, w, h)
                if mat == "core":
                    if fname == "front":
                        return ("glow", CORE if 0 < i < w - 1 and 0 < j < h - 1 else PINK_HI)
                    return lit(fname, ARM_L)
                if mat == "visor":
                    if fname == "front":
                        return ("glow", VIOLET_HI if j % 6 else VIOLET)
                    if fname in ("left", "right"):
                        return ("glow", VIOLET)
                    return lit(fname, ARM_D)
                if mat == "suit":
                    col = SUIT if (i + j * 2) % 6 else SUIT_D
                    if side(fname) and j == 0:
                        col = mix(col, ARM_L, 0.4)
                    return lit(fname, col)
                if mat == "pants":
                    col = PANTS if (i * 3 + j) % 7 else PANTS_D
                    if side(fname) and i in (0, w - 1):
                        col = PANTS_D
                    if face == "thigh" and fname in ("left", "right") and i == w // 2:
                        col = mix(PANTS_D, ARM_D, 0.5)              # шов
                    return lit(fname, col)
                if mat == "belt":
                    if side(fname) and j == 0:
                        return lit(fname, ARM_L)
                    return lit(fname, ARM_D if (i % 4) else ARM)
                if mat in ("brass",):
                    return lit(fname, BRASS if (i + j) % 2 == 0 else BRASS_D)
                if mat == "boot":
                    col = ARM_D if j > h - 3 else (ARM if (i + j) % 4 else ARM_L)
                    if minion:
                        col = mix(col, SLEEVE, 0.4)
                    return lit(fname, col)
                if mat == "gauntlet":
                    # наруч: розовая светящаяся полоса снаружи, латунный край у кисти
                    if fname in ("left", "right") and j in (1, 2) and 0 < i < w - 1:
                        return ("glow", PINK if j == 1 else mix(PINK, ARM, 0.4))
                    if side(fname) and j == h - 1:
                        return lit(fname, BRASS_D)
                    return armor_px(fname, i, j, w, h)
                if mat == "glove":
                    col = ARM_D if (i + j) % 3 else ARM
                    if minion:
                        col = mix(col, SLEEVE, 0.3)
                    return lit(fname, col)
                if mat == "nozzle":
                    if fname == "top" and face == "nozzle":
                        # горловина сопла светится синим изнутри
                        return ("glow", BLUE_HI) if 0 < i < w - 1 and 0 < j < h - 1 else lit(fname, ARM_HI)
                    if face == "vent" and side(fname) and j % 2 == 1:
                        return ("glow", mix(BLUE, ARM_D, 0.55))
                    return lit(fname, ARM_L if j == 0 else ARM_D)
                if mat == "scale":
                    # чёрная чешуя: ряды со сдвигом, снизу — светлее поперечные пластины брюха
                    if fname == "bottom":
                        return lit(fname, BELLY if j % 2 == 0 else BELLY_D)
                    if fname in ("front", "back"):
                        return lit(fname, SCALE_D if (i + j) % 3 == 0 else SCALE)
                    row = j // 2
                    hi = j % 2 == 0 and (i + row) % 2 == 0
                    col = SCALE_HI if hi else (SCALE_D if (i * 3 + j * 5) % 11 == 0 else SCALE)
                    if fname in ("left", "right") and j >= h - 1:
                        col = mix(col, BELLY_D, 0.6)
                    return lit(fname, col)
                if mat == "tail_plate":
                    if fname == "top":
                        return lit(fname, ARM_L if i in (0, w - 1) else ARM)
                    return lit(fname, ARM_HI if j == 0 else ARM_D)
                # --- прислужник ---
                if mat in ("coat", "cape"):
                    col = COAT if (i * 2 + j) % 9 else COAT_D
                    if side(fname) and j == 0:
                        col = COAT_L
                    if side(fname) and i in (0, w - 1):
                        col = mix(col, COAT_DD, 0.5)
                    if face == "chest" and fname == "front":
                        if i == w // 2:
                            return lit(fname, COAT_DD)            # застёжка
                        if j == 6 and i not in (w // 2,):
                            return lit(fname, COAT_DD)            # пояс
                    if face == "waist" and fname == "front" and i == w // 2:
                        return lit(fname, COAT_DD)
                    if face in ("coat_tail", "cape"):
                        tear = (i * 5 + 2) % 4
                        if fname in ("front", "back") and j >= h - 1 and tear == 0:
                            return None                           # потрёпанный низ
                        if fname in ("front", "back") and j >= h - 3:
                            col = mix(col, COAT_DD, 0.5)
                        if face == "cape" and fname == "back" and j == 1 and i % 2 == 0:
                            col = COAT_L
                    return lit(fname, col)
                if mat == "sleeve":
                    col = SLEEVE if (i + j) % 5 else mix(SLEEVE, COAT_D, 0.5)
                    if face == "fore" and side(fname) and j == h - 1:
                        col = COAT_D                              # манжета
                    return lit(fname, col)
                if mat == "button":
                    return lit(fname, mix(BUTTON, COAT_L, 0.25) if j == 0 else BUTTON)
                if mat == "hood":
                    if fname == "front" and face == "hood":
                        # капюшон спереди открыт: по краю ткань, внутри прозрачно (видно лицо)
                        if 1 <= i <= w - 2 and 1 <= j <= h - 2:
                            return None
                    col = COAT_D if (i + j) % 6 else COAT_DD
                    if side(fname) and j == 0:
                        col = COAT
                    return lit(fname, col)
                if mat == "hood_in":
                    if face == "face" and fname == "front":
                        # синий знак‑крюк на тёмном лице
                        mark = {(1, 2), (2, 2), (3, 2), (4, 2), (5, 2), (5, 3), (5, 4), (4, 5), (1, 3), (2, 4)}
                        if (i, j) in mark:
                            return ("glow", BLUE_HI if (i, j) in ((5, 2), (3, 2)) else BLUE)
                        return lit(fname, HOOD_IN)
                    return lit(fname, HOOD_IN)
                return (255, 0, 255)

            x0, y0, w, h = rect
            for jj in range(h):
                for ii in range(w):
                    v = fn(ii, jj, w, h)
                    x, y = x0 + ii, y0 + jj
                    if v == "jet":
                        # струя — только в светящейся маске (база прозрачна), к концу тускнеет
                        # струи рук смотрят вверх (дальний конец — верх развёртки), струи ног — вниз
                        t = jj / max(1, h - 1) if fname not in ("top", "bottom") else 0.0
                        if not face.startswith("fjet"):
                            t = 1.0 - t
                        core = face in ("jet_core", "fjet_core")
                        col = mix(BLUE_HI if core else BLUE, BLUE_D, t * (0.5 if core else 0.85))
                        PT.clear(x, y)
                        if not (face in ("jet_out", "fjet_out") and (ii + jj) % 2 == 1):
                            PT.gl[x, y] = (col[0], col[1], col[2], 255)
                    elif v is None:
                        PT.clear(x, y)
                    elif isinstance(v, tuple) and len(v) == 2 and v[0] == "glow":
                        PT.glow_put(x, y, v[1])
                    else:
                        PT.put(x, y, v)
    return PT


PB = paint(B, False, 31)
PN = paint(N, True, 37)


# =====================================================================
# 3. Позы: параметры, IK, ключи
# =====================================================================
# Ступни (rf/lf) — лодыжка в системе модели (Java: +y вниз, −z вперёд; на земле y = 22.5).
# Кулаки (rh/lh) — запястье в системе корпуса (от опоры корпуса). Плечо: (∓5.6, −11, 0).
# r_pole/l_pole — куда смотрит локоть (система корпуса); jet — длина струи (0 — нет).
DEFAULT = dict(
    root_y=0.0, root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0.3, 0), torso=(0, 0, 0), neck=(0, 0, 0),
    head=(0, 0, 0), rf=(-2.1, 22.5, 0), lf=(2.1, 22.5, 0), toe_r=0.0, toe_l=0.0,
    rh=(-6.3, -0.2, -0.8), lh=(6.3, -0.2, -0.8), r_pole=(0.2, -0.1, 1), l_pole=(-0.2, -0.1, 1),
    rhand=(0, 0, 0), lhand=(0, 0, 0), coat=0.0, cape=0.0,
    tail=0.0, tail_up=0.0, tail_sway=5.0,
)
SHOULDER = {"rh": (-5.6, -11.0, 0.0), "lh": (5.6, -11.0, 0.0)}
DIRS = ("r_pole", "l_pole")


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
    if name in SHOULDER:                                   # кулак — по дуге вокруг плеча
        o = SHOULDER[name]
        va, vb = sub(list(a), list(o)), sub(list(b), list(o))
        la, lb = length(va), length(vb)
        return tuple(add(list(o), scl(slerp_dir(va, vb, t), lerp(la, lb, t))))
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


# Небольшое запаздывание: кулаки чуть догоняют корпус (удар «хлёсткий»), ткань — сильнее.
LAG = {"head": -0.02, "rh": 0.03, "lh": 0.03, "r_pole": 0.03, "l_pole": 0.03, "coat": 0.12, "cape": 0.16,
       "tail": 0.08, "tail_up": 0.08}
TAIL = 0.12


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
    S = model.world(upper, [0, 0, 0], pose)
    d = sub(target, S)
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
    E = add(S, scl(add(scl(dn, math.cos(al)), scl(pn, math.sin(al))), a))
    T = add(S, scl(dn, dist))
    n = scl(norm(cross(dn, pn)), side_sign)
    model.set_world_rot(pose, upper, frame_X(sub(E, S), n))
    model.set_world_rot(pose, lower, frame_X(sub(T, E), n))


def resolve(pr, model):
    pose = {"root": {"rot": (0, pr["root_y"], 0), "pos": pr["root_pos"]},
            "body": {"rot": pr["body"], "pos": pr["body_pos"]},
            "torso": {"rot": pr["torso"]}, "neck": {"rot": pr["neck"]}, "head": {"rot": pr["head"]}}
    Rroot = model.chain_R("root", pose)
    for s, key, toe in (("right", "rf", "toe_r"), ("left", "lf", "toe_l")):
        tgt = model.world("root", [pr[key][0], pr[key][1] - 24, pr[key][2]], pose)
        limb_ik(model, pose, s + "_leg", s + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN, -1)
        model.set_world_rot(pose, s + "_foot", mm(Rroot, rx_m(math.radians(pr[toe]))))
    Rt = model.chain_R("torso", pose)
    for s, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        out = mv(Rt, [-1.0 if s == "right" else 1.0, 0.3, 0.4])
        limb_ik(model, pose, s + "_arm", s + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE, 1, out)
    pose["right_hand"] = {"rot": pr["rhand"]}
    pose["left_hand"] = {"rot": pr["lhand"]}
    if "tail1" in model.by_name:
        tail_pose(pr, pose)
    if "coat_fr" in model.by_name:
        # полы и плащ висят вниз, следуют за бёдрами и отстают при движении
        rl = pose["right_leg"]["rot"][0]
        ll = pose["left_leg"]["rot"][0]
        tilt = pr["body"][0]
        pose["coat_fr"] = {"rot": (min(rl, 0) * 0.9 - tilt * 0.9 - pr["coat"] * 0.4, 0, 4)}
        pose["coat_fl"] = {"rot": (min(ll, 0) * 0.9 - tilt * 0.9 - pr["coat"] * 0.4, 0, -4)}
        pose["coat_b"] = {"rot": (max(rl, ll, 0) * 0.75 - tilt * 0.9 + pr["coat"] + 4, 0, 0)}
        lean = pr["body"][0] + pr["torso"][0]
        pose["cape"] = {"rot": (-lean * 0.9 + 5 + pr["cape"], 0, 0)}
    return pose


TAIL_BONES = ("tail1", "tail2", "tail3", "tail4")
TAIL_DROOP = (-26.0, 7.0, 7.0, 6.0)          # тяжёлый хвост свисает к земле, к концу чуть загибается


def tail_pose(pr, pose):
    """Хвост: свисает, волна покачивания от основания к кончику, отстаёт при поворотах корпуса (_turn)."""
    t = pr.get("_t", 0.0)
    per = pr.get("_per", 2.0)
    lag = max(-45.0, min(45.0, -pr.get("_turn", 0.0) * 0.8))
    for i, name in enumerate(TAIL_BONES):
        w = 2 * math.pi * t / per - i * 0.85
        k = 0.4 + 0.2 * i
        yaw = pr["tail_sway"] * (0.5 + 0.25 * i) * math.sin(w) + pr["tail"] * k + lag * k
        pitch = TAIL_DROOP[i] + pr["tail_up"] * (0.6 if i == 0 else 0.2) + 1.2 * math.sin(w + 1.3)
        if i == 0:
            pitch -= pr["body"][0] * 0.6                      # корпус наклонился — хвост остаётся над землёй
        pose[name] = {"rot": (pitch, yaw, 0.0)}


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


def gait(w, stride, lift, base_y=22.5, width=2.1, z0=0.0):
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l


# =====================================================================
# 4. Позы и анимации
# =====================================================================
# Боевая стойка: левая нога впереди, кулаки у подбородка, локти вниз
STANCE = dict(body_pos=(0, 1.4, 0), body=(4, -12, 0), torso=(6, -6, 0), head=(-8, 16, 0), neck=(0, 0, 0),
              rf=(-2.8, 22.5, 2.6), lf=(2.4, 22.5, -3.0), toe_r=0.0,
              rh=(-4.2, -7.4, -5.4), lh=(3.2, -8.2, -7.0), r_pole=(-0.3, 1, 0.35), l_pole=(0.3, 1, 0.35),
              rhand=(-10, 0, 0), lhand=(-10, 0, 0), coat=2.0)
# Замах «ровно по плечу»: правая рука отведена назад на уровне плеча, локоть согнут и смотрит назад
COCK_R = dict(body=(2, 8, 0), torso=(4, 16, 0), head=(-6, -24, 0), body_pos=(0, 1.8, 0),
              rh=(-8.4, -10.6, -0.2), r_pole=(0, 0, 1), lh=(3.4, -9.0, -8.0), l_pole=(0.3, 1, 0.2),
              rhand=(0, 0, 0), rf=(-2.9, 22.5, 2.8), lf=(2.4, 22.5, -3.2))
# Прямой удар на уровне плеча
PUNCH_R = dict(body=(6, -20, 0), torso=(8, -26, 0), head=(-12, 30, 0), body_pos=(0, 2.2, 0), root_pos=(0, 0, -2.4),
               rh=(-2.6, -10.8, -11.1), r_pole=(-1, 0.4, 0.3), lh=(3.4, -7.2, -4.2), l_pole=(0.3, 1, 0.3),
               rhand=(0, 0, 0), rf=(-2.6, 22.5, 4.8), lf=(2.4, 22.5, -3.4), toe_r=-22.0, coat=8.0)
# Удар ногой вперёд: колено вверх, нога выстреливает на уровне пояса
KICK_CHAMBER = dict(body=(-6, -6, 0), torso=(-4, 0, 0), head=(4, 0, 0), body_pos=(0, 1.0, 0), root_pos=(0, 0, -0.8),
                    rf=(-2.2, 13.6, -4.6), toe_r=30.0, lf=(1.4, 22.5, -0.6),
                    rh=(-4.6, -7.0, -6.0), lh=(3.6, -7.6, -6.4), coat=-6.0)
KICK = dict(body=(-14, -8, 0), torso=(-8, 4, 0), head=(12, 0, 0), body_pos=(0, 0.8, 0), root_pos=(0, 0, -2.2),
            rf=(-2.0, 12.4, -12.2), toe_r=-30.0, lf=(1.2, 22.5, 0.4),
            rh=(-6.8, -5.0, 3.0), lh=(4.2, -7.8, -6.2), r_pole=(-0.3, 1, 0.6), coat=-12.0)


def idle(t):
    w = 2 * math.pi * t / 2.4
    pr = P(STANCE)
    pr["body_pos"] = (0, 1.4 + 0.35 * math.sin(w), 0)
    pr["torso"] = (6 + 1.5 * math.sin(w + 0.5), -6, 0)
    pr["head"] = (-8 + 2 * math.sin(w + 1.0), 16 + 4 * math.sin(w * 0.5), 0)
    pr["rh"] = (-4.2, -7.4 + 0.5 * math.sin(w + 0.7), -5.4)
    pr["lh"] = (3.2, -8.2 + 0.5 * math.sin(w + 1.0), -7.0 - 0.6 * math.sin(w * 0.5))
    pr["cape"] = 2 * math.sin(w - 0.6)
    pr["tail_sway"] = 6.0
    return pr


def walk(t):
    w = 2 * math.pi * t / 1.0
    pr = P(STANCE, body=(2, 0, 0), torso=(5, 0, 0), head=(-4, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 4.0, 2.4)
    pr["body_pos"] = (0, 1.0 + 0.6 * abs(math.sin(w)), 0)
    pr["body"] = (2, 6 * math.sin(w), 0)
    pr["torso"] = (5, -8 * math.sin(w), 0)
    pr["head"] = (-4, 2 * math.sin(w), 0)
    pr["rh"] = (-5.0, -6.0 + 0.6 * math.sin(w), -4.6 - 1.6 * math.sin(w))
    pr["lh"] = (5.0, -6.0 - 0.6 * math.sin(w), -4.6 + 1.6 * math.sin(w))
    pr["toe_r"] = -10 * max(0.0, math.sin(w))
    pr["toe_l"] = -10 * max(0.0, -math.sin(w))
    pr["coat"] = 3 + 3 * math.sin(2 * w)
    pr["cape"] = 4 + 3 * math.sin(2 * w)
    pr["tail_sway"] = 9.0
    pr["tail_up"] = 4.0
    return pr


def run(t):
    w = 2 * math.pi * t / 0.65
    pr = P(STANCE, body=(14, 0, 0), torso=(10, 0, 0), head=(-20, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 6.5, 4.2)
    pr["body_pos"] = (0, 1.6 + 1.0 * abs(math.sin(w)), 0)
    pr["body"] = (14, 8 * math.sin(w), 0)
    pr["torso"] = (10, -12 * math.sin(w), 0)
    pr["rh"] = (-5.6, -6.5 + 1.5 * math.sin(w), -3.5 - 5.0 * math.sin(w))
    pr["lh"] = (5.6, -6.5 - 1.5 * math.sin(w), -3.5 + 5.0 * math.sin(w))
    pr["r_pole"] = (0, 0.6, 1)
    pr["l_pole"] = (0, 0.6, 1)
    pr["toe_r"] = -20 * max(0.0, math.sin(w))
    pr["toe_l"] = -20 * max(0.0, -math.sin(w))
    pr["coat"] = 16 + 4 * math.sin(2 * w)
    pr["cape"] = 22 + 5 * math.sin(2 * w)
    pr["tail_sway"] = 7.0
    pr["tail_up"] = 14.0                                     # на бегу хвост вытянут назад — противовес
    return pr


# --- атака 1 «Серия»: стойка → прямой правой (0.65 с) → удар ногой (1.15 с) → прямой правой (1.65 с) ---
COMBO = [
    (0.00, {}, "lin"),
    (0.20, STANCE, "out"),
    (0.45, COCK_R, "out"),
    (0.65, PUNCH_R, "in"),
    (0.75, dict(PUNCH_R, rh=(-2.8, -10.6, -10.6)), "out"),
    (0.95, KICK_CHAMBER, "ease"),
    (1.15, KICK, "in3"),
    (1.25, dict(KICK, rf=(-2.1, 12.8, -11.6)), "out"),
    (1.40, dict(STANCE, root_pos=(0, 0, -1.0)), "ease"),
    (1.50, dict(COCK_R, root_pos=(0, 0, -0.6)), "out"),
    (1.65, PUNCH_R, "in"),
    (1.80, dict(PUNCH_R, rh=(-2.8, -10.6, -10.6)), "out"),
    (2.15, dict(STANCE, root_pos=(0, 0, -0.6)), "ease"),
    (2.30, dict(DEFAULT), "ease"),
]


def combo(t):
    return layered(COMBO, t)


# --- атака 2 «Реактивный удар»: замах 1.5 с (из локтя бьёт струя), рывок на 7 блоков, удар ---
JET_COCK = dict(COCK_R, body=(10, 10, 0), torso=(10, 18, 0), head=(-14, -32, 0), body_pos=(0, 3.2, 0),
                rh=(-8.6, -10.6, -0.4), r_pole=(0, 0, 1), lh=(2.0, -9.2, -9.4), l_pole=(0.5, 1, 0.1),
                rf=(-3.2, 22.5, 4.4), lf=(2.6, 22.5, -4.2), toe_r=-12.0, coat=4.0, cape=6.0)
JET_DASH = dict(PUNCH_R, body=(22, -22, 0), torso=(12, -28, 0), head=(-26, 32, 0), body_pos=(0, 3.0, 0),
                root_pos=(0, 0, -3.0), rh=(-2.4, -11.2, -11.2), lh=(4.6, -5.4, 2.6), l_pole=(0.2, 0.4, -1),
                rf=(-2.4, 20.5, 8.5), lf=(2.4, 22.5, -4.6), toe_r=-40.0, coat=30.0, cape=40.0)
JET = [
    (0.00, {}, "lin"),
    (0.30, STANCE, "out"),
    (0.75, JET_COCK, "out"),
    (1.45, dict(JET_COCK, body_pos=(0, 3.6, 0), torso=(12, 20, 0), rh=(-8.7, -10.7, -0.3)), "lin"),
    (1.60, JET_DASH, "in3"),
    (1.85, dict(JET_DASH, rh=(-2.6, -11.0, -11.0)), "lin"),
    (2.10, dict(PUNCH_R, root_pos=(0, 0, -1.6), coat=8.0, cape=10.0), "out"),
    (2.55, dict(DEFAULT), "ease"),
]


def jet(t):
    pr = layered(JET, t)
    if 0.75 < t < 1.5:                                       # дрожь от напряжения и тяги
        k = min(1.0, (t - 0.75) / 0.3)
        pr["body_pos"] = add(list(pr["body_pos"]), [0.12 * math.sin(t * 95) * k, 0.1 * math.sin(t * 71) * k, 0])
        pr["rh"] = add(list(pr["rh"]), [0, 0.15 * math.sin(t * 83) * k, 0.12 * math.sin(t * 101) * k])
    return pr


# --- атака 3 «Двойной таран»: обе руки назад, рывок на 4 блока, оба кулака сходятся, топот ---
COCK_BOTH = dict(body=(8, 0, 0), torso=(-8, 0, 0), head=(-6, 0, 0), body_pos=(0, 2.8, 0),
                 rh=(-6.2, -9.0, 0.7), lh=(6.2, -9.0, 0.7), r_pole=(-0.1, 0, 1), l_pole=(0.1, 0, 1),
                 rf=(-2.8, 22.5, 3.6), lf=(2.6, 22.5, -3.4), toe_r=-10.0, coat=4.0, cape=6.0)
CLAP = dict(body=(18, 0, 0), torso=(10, 0, 0), head=(-20, 0, 0), body_pos=(0, 3.6, 0), root_pos=(0, 0, -2.6),
            rh=(-1.2, -9.4, -11.0), lh=(1.2, -9.4, -11.0), r_pole=(-1, 0.3, 0.2), l_pole=(1, 0.3, 0.2),
            rf=(-2.6, 22.5, 6.0), lf=(2.6, 22.5, -4.2), toe_r=-30.0, coat=20.0, cape=28.0)
STOMP_UP = dict(body=(-4, 0, 0), torso=(-4, 0, 0), head=(4, 0, 0), body_pos=(0, 0.6, 0), root_pos=(0, 0, -1.2),
                rf=(-2.4, 13.2, -2.6), toe_r=10.0, lf=(1.2, 22.5, 0.0),
                rh=(-10.5, -6.0, -1.0), lh=(10.5, -6.0, -1.0), r_pole=(-0.2, 1, 0.5), l_pole=(0.2, 1, 0.5),
                coat=-8.0, cape=-4.0)
STOMP = dict(body=(14, 0, 0), torso=(12, 0, 0), head=(-14, 0, 0), body_pos=(0, 4.4, 0), root_pos=(0, 0, -1.2),
             rf=(-2.8, 22.5, -2.2), toe_r=0.0, lf=(2.6, 22.5, 1.8),
             rh=(-11.5, -2.0, -2.0), lh=(11.5, -2.0, -2.0), coat=14.0, cape=18.0)
SLAM = [
    (0.00, {}, "lin"),
    (0.40, COCK_BOTH, "out"),
    (1.05, dict(COCK_BOTH, body_pos=(0, 3.2, 0), torso=(-10, 0, 0), rh=(-6.3, -9.3, 0.9), lh=(6.3, -9.3, 0.9)), "lin"),
    (1.40, CLAP, "in3"),
    (1.55, dict(CLAP, rh=(-0.9, -9.6, -10.6), lh=(0.9, -9.6, -10.6)), "out"),
    (1.85, STOMP_UP, "ease"),
    (2.10, STOMP, "in3"),
    (2.35, dict(STOMP, body_pos=(0, 4.0, 0)), "out"),
    (3.10, dict(DEFAULT), "ease"),
]


def slam(t):
    return layered(SLAM, t)


# «Вихрь» убран (по просьбе пользователя); поза ног осталась для волны «Я не сдамся»
SPIN_POSE = dict(body=(-10, 0, 0), torso=(-2, 0, 0), head=(6, 0, 0), body_pos=(0, 0.8, 0),
                 rf=(-1.8, 11.6, -13.0), toe_r=-20.0, lf=(0.4, 22.5, 0.4),
                 rh=(-16.4, -9.6, 0.6), lh=(16.4, -9.6, 0.6), r_pole=(0, 1, 0.4), l_pole=(0, 1, 0.4),
                 coat=20.0, cape=36.0)


# --- атака 5 «Призыв» (2‑я фаза): кулак вверх, удар кулаком в землю на колене, из земли встают двое ---
FIST_UP = dict(body=(-6, 0, 0), torso=(-10, 8, 0), head=(-10, -8, 0), body_pos=(0, 0.4, 0),
               rh=(-4.6, -21.6, -2.0), r_pole=(-1, 0, 0.2), lh=(5.4, -6.0, -5.0),
               rf=(-2.6, 22.5, 1.2), lf=(2.6, 22.5, -1.6), cape=-4.0)
KNEEL = dict(body=(18, 0, 0), torso=(34, 0, 0), head=(-36, 0, 0), body_pos=(0, 7.4, 0),
             rf=(-2.6, 21.6, 8.6), toe_r=-60.0, lf=(2.6, 22.5, -4.2),
             rh=(-4.0, 4.6, -10.0), r_pole=(-0.4, -0.4, 1), lh=(6.0, -2.0, -6.0), l_pole=(0.4, 1, 0.2),
             coat=20.0, cape=24.0)
SUMMON = [
    (0.00, {}, "lin"),
    (0.45, FIST_UP, "out"),
    (0.80, dict(FIST_UP, rh=(-4.4, -22.0, -1.6), torso=(-12, 8, 0)), "lin"),
    (1.05, KNEEL, "in3"),
    (1.25, dict(KNEEL, body_pos=(0, 7.8, 0)), "out"),
    (1.70, dict(KNEEL, torso=(26, 0, 0), head=(-20, 0, 0), rh=(-5.4, 2.0, -7.0)), "ease"),
    (2.30, dict(DEFAULT), "ease"),
]


def summon(t):
    return layered(SUMMON, t)


# --- прислужник: выход из‑под земли (1.6 с) ---
def emerge(t):
    rise = ease_out(min(1.0, t / 0.9))
    keys = [(0.0, dict(KNEEL, rh=(-5.0, -14.0, -6.0), lh=(5.0, -14.0, -6.0), r_pole=(-0.4, 1, 0.3),
                       l_pole=(0.4, 1, 0.3), cape=30.0), "lin"),
            (0.85, dict(KNEEL, rh=(-6.0, -6.0, -6.0), lh=(6.0, -6.0, -6.0), cape=10.0), "out"),
            (1.25, dict(STANCE, head=(-14, 0, 0)), "ease"),
            (1.60, dict(DEFAULT), "ease")]
    pr = layered(keys, t)
    pr["root_pos"] = (0, 30.0 * (1 - rise), 0)
    return pr


# --- отскок после атаки: назад / влево / вправо (0.6 с, толчок на 0.1 с) ---
def dodge_keys(side):
    # side: 0 — назад, +1 — влево (к +x модели), −1 — вправо
    lean_z = -14.0 * side
    push = dict(STANCE, body_pos=(0, 3.4, 0), torso=(14 if side == 0 else 8, -6, lean_z * 0.5), body=(6, -12, lean_z * 0.4),
                rf=(-2.8 + 1.2 * side, 22.5, 1.6 if side else 0.6), lf=(2.4 + 1.2 * side, 22.5, -1.6 if side else -0.8))
    air = dict(STANCE, body_pos=(0, 0.6, 0), torso=(-6 if side == 0 else 2, -6, lean_z), body=(-4 if side == 0 else 0, -12, lean_z),
               rf=(-2.6 - 1.6 * side, 19.6, 2.4), lf=(2.6 - 1.6 * side, 20.4, -1.4), toe_r=30.0, toe_l=24.0,
               rh=(-6.4, -7.0, -4.0), lh=(6.0, -7.6, -5.0), coat=-14.0, cape=-18.0)
    land = dict(STANCE, body_pos=(0, 3.0, 0), torso=(12, -6, 0), body=(6, -12, 0), coat=8.0, cape=12.0)
    return [(0.00, {}, "lin"), (0.10, push, "out"), (0.26, air, "out3"), (0.42, land, "in"), (0.70, STANCE, "ease")]


def dodge_back(t):
    return layered(dodge_keys(0), t)


def dodge_left(t):
    return layered(dodge_keys(1), t)


def dodge_right(t):
    return layered(dodge_keys(-1), t)


# --- «Столб»: присел, ладони у земли (собирает силу), рывком встаёт и вскидывает руки (столб на 1.5 с) ---
GATHER = dict(body=(16, 0, 0), torso=(30, 0, 0), head=(-22, 0, 0), body_pos=(0, 6.0, 0),
              rf=(-3.0, 22.5, 2.2), lf=(3.0, 22.5, -2.2), rh=(-5.6, 3.2, -7.4), lh=(5.6, 3.2, -7.4),
              r_pole=(-0.6, -0.4, 1), l_pole=(0.6, -0.4, 1), rhand=(-30, 0, 0), lhand=(-30, 0, 0), coat=10.0, cape=14.0)
RAISE = dict(body=(-8, 0, 0), torso=(-14, 0, 0), head=(-22, 0, 0), body_pos=(0, -0.4, 0),
             rf=(-3.0, 22.5, 1.2), lf=(3.0, 22.5, -1.2), rh=(-7.0, -21.0, -2.6), lh=(7.0, -21.0, -2.6),
             r_pole=(-1, 0.2, 0.3), l_pole=(1, 0.2, 0.3), rhand=(20, 0, 0), lhand=(20, 0, 0), coat=-8.0, cape=-10.0)
PILLAR = [
    (0.00, {}, "lin"),
    (0.40, GATHER, "out"),
    (1.30, dict(GATHER, body_pos=(0, 6.6, 0), rh=(-5.8, 3.6, -7.0), lh=(5.8, 3.6, -7.0)), "lin"),
    (1.50, RAISE, "in3"),
    (1.65, dict(RAISE, rh=(-7.2, -21.4, -2.2), lh=(7.2, -21.4, -2.2), body_pos=(0, -0.7, 0)), "out"),
    (2.15, dict(RAISE, torso=(-10, 0, 0)), "ease"),
    (2.80, dict(STANCE), "ease"),
]


def pillar(t):
    pr = layered(PILLAR, t)
    if 0.4 < t < 1.45:                                      # дрожь — сила копится
        k = min(1.0, (t - 0.4) / 0.4)
        pr["body_pos"] = add(list(pr["body_pos"]), [0.1 * math.sin(t * 90) * k, 0.08 * math.sin(t * 67) * k, 0])
    return pr


# --- «Шипы»: руки крест‑накрест перед лицом (эффект на 0.6 с) ---
GUARD_X = dict(body=(6, 0, 0), torso=(10, 0, 0), head=(4, 0, 0), body_pos=(0, 2.4, 0),
               rf=(-3.0, 22.5, 1.8), lf=(2.8, 22.5, -2.4),
               rh=(2.2, -9.4, -7.6), lh=(-2.2, -8.2, -8.2), r_pole=(-0.2, 1, 0.4), l_pole=(0.2, 1, 0.4),
               rhand=(0, 0, 20), lhand=(0, 0, -20), coat=4.0, cape=4.0)
GUARD = [
    (0.00, {}, "lin"),
    (0.30, GUARD_X, "out"),
    (0.60, dict(GUARD_X, body_pos=(0, 3.0, 0), torso=(14, 0, 0)), "in"),
    (0.75, dict(GUARD_X, body_pos=(0, 2.6, 0)), "out"),
    (1.10, dict(GUARD_X), "ease"),
    (1.45, dict(STANCE), "ease"),
]


def guard(t):
    return layered(GUARD, t)


# --- «Пике»: присел → взлёт на струях из ног → в воздухе замах → удар кулаком вниз (держит до земли) ---
CROUCH_UP = dict(body=(14, 0, 0), torso=(18, 0, 0), head=(-18, 0, 0), body_pos=(0, 5.0, 0),
                 rf=(-2.6, 22.5, 1.0), lf=(2.6, 22.5, -1.0), rh=(-7.0, 1.0, 3.6), lh=(7.0, 1.0, 3.6),
                 r_pole=(-0.3, 0, -1), l_pole=(0.3, 0, -1), coat=8.0, cape=10.0)
LIFT = dict(body=(-4, 0, 0), torso=(-6, 0, 0), head=(-6, 0, 0), body_pos=(0, 0.0, 0),
            rf=(-1.6, 22.8, 1.2), lf=(1.6, 22.8, 0.6), toe_r=36.0, toe_l=36.0,
            rh=(-7.6, 0.4, 2.0), lh=(7.6, 0.4, 2.0), r_pole=(-0.2, 0, -1), l_pole=(0.2, 0, -1), coat=-24.0, cape=-30.0)
APEX = dict(COCK_R, body=(18, 10, 0), torso=(10, 18, 0), head=(14, -18, 0), body_pos=(0, 0.0, 0),
            rf=(-2.2, 18.0, -3.0), lf=(2.2, 21.4, 2.0), toe_r=20.0, toe_l=30.0, coat=-8.0, cape=-12.0)
DIVE_P = dict(PUNCH_R, body=(72, -6, 0), torso=(22, -18, 0), head=(22, 14, 0), body_pos=(0, 0.0, 0), root_pos=(0, 0, 0),
              rh=(-3.0, -10.8, -11.0), lh=(6.8, -6.4, 2.0), l_pole=(0.3, 0.6, -1),
              rf=(-2.2, 9.6, 10.6), lf=(2.2, 11.4, 9.6), toe_r=40.0, toe_l=40.0, coat=-30.0, cape=-40.0)
DIVE = [
    (0.00, {}, "lin"),
    (0.45, CROUCH_UP, "out"),
    (0.60, LIFT, "out3"),
    (1.35, dict(LIFT, torso=(-4, 0, 0), rh=(-7.8, 0.0, 2.6), lh=(7.8, 0.0, 2.6)), "ease"),
    (1.70, APEX, "out"),
    (1.80, dict(APEX, torso=(10, 22, 0)), "lin"),
    (1.95, DIVE_P, "in3"),
    (3.40, dict(DIVE_P, rh=(-2.8, -10.9, -11.1)), "lin"),
]


def dive(t):
    return layered(DIVE, t)


DIVE_LAND_KEYS = [
    (0.00, dict(KNEEL, rh=(-4.2, 5.0, -10.4), body_pos=(0, 7.8, 0), coat=24.0, cape=30.0), "lin"),
    (0.20, dict(KNEEL, rh=(-4.0, 4.8, -10.0), body_pos=(0, 7.4, 0), coat=6.0, cape=8.0), "out"),
    (0.70, dict(KNEEL, torso=(22, 0, 0), head=(-16, 0, 0), rh=(-5.6, 1.0, -6.0)), "ease"),
    (1.20, dict(STANCE), "ease"),
]


def dive_land(t):
    return layered(DIVE_LAND_KEYS, t)


# --- «Я не сдамся» (6 с): картридж в грудь → вращение с реактивной волной (1.5 с) → прыжок и удар пяткой
#     сверху (2.7 с) → шквал ударов (3.0–4.05 с) → кулаки над головой и двойной удар в землю (5.0 с) → выпрямляется ---
LOAD = dict(body=(10, 0, 0), torso=(16, 6, 0), head=(18, -4, 0), body_pos=(0, 2.0, 0),
            rh=(-1.4, -8.4, -5.6), r_pole=(-1, 0.4, 0.3), lh=(5.6, -6.0, -4.0), rhand=(0, 0, 0))
LOAD_HIT = dict(LOAD, rh=(-1.0, -9.0, -4.2), torso=(22, 4, 0), head=(26, 0, 0), body_pos=(0, 2.6, 0))
ROAR = dict(body=(-8, 0, 0), torso=(-16, 0, 0), head=(-26, 0, 0), body_pos=(0, 0.6, 0),
            rh=(-13.0, -6.0, 1.0), lh=(13.0, -6.0, 1.0), r_pole=(0, 1, 0.4), l_pole=(0, 1, 0.4),
            rf=(-3.2, 22.5, 0.8), lf=(3.2, 22.5, -0.8), coat=-6.0, cape=-8.0)
WAVE_ARMS = dict(SPIN_POSE, rf=(-3.0, 22.5, 0.6), lf=(3.0, 22.5, -0.6), toe_r=0.0, body=(4, 0, 0), torso=(4, 0, 0),
                 body_pos=(0, 2.4, 0), head=(-4, 0, 0))
AXE_CROUCH = dict(STANCE, body_pos=(0, 4.6, 0), torso=(14, 0, 0), body=(10, 0, 0), rf=(-2.8, 22.5, 1.8), lf=(2.6, 22.5, -1.6))
AXE_UP = dict(body=(-16, 0, 0), torso=(-6, 0, 0), head=(8, 0, 0), body_pos=(0, 0.0, 0),
              rf=(-2.0, 1.5, -8.6), toe_r=-30.0, lf=(1.6, 22.0, 1.4), toe_l=30.0,
              rh=(-11.0, -6.0, 2.0), lh=(11.0, -6.0, 2.0), r_pole=(0, 1, 0.5), l_pole=(0, 1, 0.5), coat=-20.0, cape=-26.0)
AXE_DOWN = dict(body=(18, 0, 0), torso=(14, 0, 0), head=(-16, 0, 0), body_pos=(0, 4.4, 0),
                rf=(-2.4, 22.5, -6.0), toe_r=0.0, lf=(2.4, 22.5, 3.6), toe_l=-30.0,
                rh=(-9.0, -3.0, -3.0), lh=(9.0, -3.0, -3.0), coat=20.0, cape=26.0)
F_STANCE = dict(STANCE, body=(8, 0, 0), torso=(10, 0, 0), head=(-10, 0, 0), body_pos=(0, 2.6, 0),
                rf=(-3.0, 22.5, 2.4), lf=(3.0, 22.5, -2.4), rh=(-4.0, -8.4, -5.0), lh=(4.0, -8.4, -5.0),
                r_pole=(-0.4, 1, 0.3), l_pole=(0.4, 1, 0.3))
F_R = dict(F_STANCE, torso=(10, -18, 0), rh=(-1.6, -10.4, -11.0), r_pole=(-1, 0.4, 0.3), lh=(4.0, -8.0, -3.6))
F_L = dict(F_STANCE, torso=(10, 18, 0), lh=(1.6, -10.4, -11.0), l_pole=(1, 0.4, 0.3), rh=(-4.0, -8.0, -3.6))
HAMMER_UP = dict(body=(-10, 0, 0), torso=(-16, 0, 0), head=(-20, 0, 0), body_pos=(0, -0.4, 0),
                 rh=(-2.6, -21.8, 1.0), lh=(2.6, -21.8, 1.0), r_pole=(-1, 0, 0.3), l_pole=(1, 0, 0.3),
                 rf=(-3.2, 22.5, 1.0), lf=(3.2, 22.5, -1.0), coat=-10.0, cape=-14.0)
HAMMER_DOWN = dict(body=(22, 0, 0), torso=(36, 0, 0), head=(-34, 0, 0), body_pos=(0, 7.0, 0),
                   rh=(-1.8, 4.8, -9.6), lh=(1.8, 4.8, -9.6), r_pole=(-1, 0, 0.4), l_pole=(1, 0, 0.4),
                   rf=(-3.4, 22.5, 3.6), lf=(3.4, 22.5, -2.2), coat=26.0, cape=32.0)
STAND_TALL = dict(body=(-4, 0, 0), torso=(-10, 0, 0), head=(-16, 0, 0), body_pos=(0, 0.0, 0),
                  rh=(-7.4, -2.0, -1.0), lh=(7.4, -2.0, -1.0), r_pole=(0, 0, 1), l_pole=(0, 0, 1),
                  rf=(-2.6, 22.5, 0.4), lf=(2.6, 22.5, -0.4))
YIELD = [
    (0.00, {}, "lin"),
    (0.35, LOAD, "out"),
    (0.55, LOAD_HIT, "in3"),
    (0.75, ROAR, "out"),
    (0.95, WAVE_ARMS, "ease"),
    (1.50, dict(WAVE_ARMS, body_pos=(0, 3.0, 0)), "lin"),
    (1.75, AXE_CROUCH, "ease"),
    (1.95, dict(AXE_CROUCH, body_pos=(0, 5.2, 0)), "lin"),
    (2.30, AXE_UP, "out"),
    (2.55, dict(AXE_UP, rf=(-2.0, 0.8, -8.4)), "lin"),
    (2.70, AXE_DOWN, "in3"),
    (2.95, F_STANCE, "ease"),
]
t0 = 3.0
for k in range(8):                                          # шквал: удары каждые 0.15 с
    YIELD.append((t0 + k * 0.15, F_R if k % 2 == 0 else F_L, "in3"))
    YIELD.append((t0 + k * 0.15 + 0.07, F_STANCE, "out"))
YIELD += [
    (4.55, HAMMER_UP, "out"),
    (4.85, dict(HAMMER_UP, rh=(-2.4, -22.2, 1.6), lh=(2.4, -22.2, 1.6), body_pos=(0, -0.7, 0)), "lin"),
    (5.00, HAMMER_DOWN, "in3"),
    (5.20, dict(HAMMER_DOWN, body_pos=(0, 7.4, 0)), "out"),
    (5.60, STAND_TALL, "ease"),
    (6.00, dict(STANCE), "ease"),
]


def yield_(t):
    pr = layered(YIELD, t)
    x = max(0.0, min(1.0, (t - 0.95) / 0.55))
    pr["root_y"] = -360.0 * (x * x * (3 - 2 * x))           # оборот с реактивной волной
    return pr


# --- «Хвост» (быстрая, 0.85 с): короткий замах в другую сторону, резкий оборот — хвост проходит перед ним
#     на высоте пояса (удар на 0.3–0.45 с), возврат в стойку ---
WHIP_COCK = dict(STANCE, body_pos=(0, 2.6, 0), body=(8, 0, 0), torso=(10, 14, 0), head=(-10, -14, 0),
                 rf=(-3.2, 22.5, 2.0), lf=(3.0, 22.5, -2.4), rh=(-6.6, -7.0, -3.0), lh=(4.0, -7.6, -6.0),
                 tail=-30.0, tail_up=8.0)
WHIP = dict(STANCE, body_pos=(0, 3.2, 0), body=(12, 0, 0), torso=(12, -10, 0), head=(-12, 10, 0),
            rf=(-3.4, 22.5, 1.6), lf=(3.4, 22.5, -1.6), rh=(-9.4, -6.0, 1.0), lh=(9.4, -6.0, 1.0),
            r_pole=(-0.2, 1, 0.6), l_pole=(0.2, 1, 0.6), tail=10.0, tail_up=34.0, coat=10.0, cape=20.0)
WHIP_KEYS = [
    (0.00, {}, "lin"),
    (0.12, WHIP_COCK, "out"),
    (0.30, WHIP, "in3"),
    (0.50, dict(WHIP, tail_up=26.0), "lin"),
    (0.85, dict(STANCE), "ease"),
]
WHIP_FROM, WHIP_TO = 0.12, 0.62


def tail_whip(t):
    pr = layered(WHIP_KEYS, t)
    x = max(0.0, min(1.0, (t - WHIP_FROM) / (WHIP_TO - WHIP_FROM)))
    f = 1 - (1 - x) ** 2.2                                  # резкий старт, мягкое торможение
    a = min(1.0, t / WHIP_FROM)
    pr["root_y"] = 25.0 * ease(a) * (1 - x) - 360.0 * f
    return pr


# --- «Импульс» (2.0 с): стойка, голова опущена — визор копит заряд (дрожь), на 1.1 с голова рывком вперёд —
#     из маски бьёт фиолетовый луч, корпус откидывает отдачей ---
BEAM_BRACE = dict(STANCE, body_pos=(0, 2.6, 0), body=(4, 0, 0), torso=(8, 0, 0), head=(-6, 0, 0),
                  rf=(-3.4, 22.5, 2.8), lf=(3.0, 22.5, -2.6), rh=(-7.6, -3.0, -1.6), lh=(7.6, -3.0, -1.6),
                  r_pole=(-0.2, 0, 1), l_pole=(0.2, 0, 1), rhand=(-12, 0, 0), lhand=(-12, 0, 0), tail_up=6.0)
BEAM_CHARGE = dict(BEAM_BRACE, body_pos=(0, 3.4, 0), torso=(20, 0, 0), head=(12, 0, 0),
                   rh=(-8.4, -1.6, 2.2), lh=(8.4, -1.6, 2.2), tail=0.0, tail_up=-4.0, coat=4.0)
BEAM_FIRE = dict(BEAM_BRACE, body_pos=(0, 2.0, 0), root_pos=(0, 0, 1.6), body=(-8, 0, 0), torso=(-10, 0, 0),
                 head=(-10, 0, 0), rh=(-9.6, -3.4, 4.2), lh=(9.6, -3.4, 4.2), tail_up=22.0, coat=-10.0, cape=-14.0)
BEAM_KEYS = [
    (0.00, {}, "lin"),
    (0.25, BEAM_BRACE, "out"),
    (0.85, BEAM_CHARGE, "ease"),
    (1.05, dict(BEAM_CHARGE, head=(16, 0, 0), torso=(22, 0, 0)), "lin"),
    (1.10, BEAM_FIRE, "in3"),
    (1.35, dict(BEAM_FIRE, root_pos=(0, 0, 1.2), torso=(-6, 0, 0)), "out"),
    (2.00, dict(STANCE), "ease"),
]


def beam(t):
    pr = layered(BEAM_KEYS, t)
    if 0.3 < t < 1.08:                                       # дрожь — заряд копится
        k = min(1.0, (t - 0.3) / 0.5)
        pr["body_pos"] = add(list(pr["body_pos"]), [0.1 * math.sin(t * 97) * k, 0.09 * math.sin(t * 73) * k, 0])
        pr["head"] = add(list(pr["head"]), [1.5 * math.sin(t * 89) * k, 1.2 * math.sin(t * 61) * k, 0])
    return pr


# --- после «Я не сдамся»: передёргивает плечами — из‑за спины вылетает чёрный чемодан (на 0.35 с) ---
EJ_HUNCH = dict(STANCE, body_pos=(0, 3.0, 0), body=(10, 0, 0), torso=(26, 0, 0), head=(-4, 0, 0),
                rh=(-5.0, -6.4, -4.0), lh=(5.0, -6.4, -4.0), r_pole=(-0.6, 1, 0.2), l_pole=(0.6, 1, 0.2),
                tail_up=-6.0)
EJ_POP = dict(STANCE, body_pos=(0, 1.6, 0), root_pos=(0, 0, -1.0), body=(-6, 0, 0), torso=(-14, 0, 0),
              head=(-22, 0, 0), rh=(-9.0, -5.0, 1.6), lh=(9.0, -5.0, 1.6), r_pole=(0, 0.4, 1), l_pole=(0, 0.4, 1),
              tail_up=24.0, coat=-8.0, cape=-20.0)
EJ_ROLL = dict(STANCE, torso=(6, 14, 0), head=(-8, -10, 0), rh=(-4.0, -8.6, -5.0), lh=(5.0, -6.4, -2.4))
EJECT_KEYS = [
    (0.00, {}, "lin"),
    (0.22, EJ_HUNCH, "out"),
    (0.34, EJ_POP, "in3"),
    (0.65, dict(EJ_POP, head=(-16, 0, 0), torso=(-10, 0, 0)), "out"),
    (0.95, EJ_ROLL, "ease"),
    (1.30, dict(STANCE), "ease"),
]


def eject(t):
    return layered(EJECT_KEYS, t)


# --- смерть (держит последнюю позу, пока играет звук): отшатнулся → на колено → поник ---
D_STAGGER = dict(body=(-14, 0, 0), torso=(-12, 0, 0), head=(-28, 0, 0), body_pos=(0, 0.8, 0), root_pos=(0, 0, 1.6),
                 rf=(-2.6, 22.5, 3.4), lf=(2.6, 22.5, 0.4), rh=(-9.0, -2.0, 1.0), lh=(9.0, -2.0, 1.0),
                 r_pole=(0, 0, 1), l_pole=(0, 0, 1), tail_up=10.0, coat=-8.0, cape=-12.0)
D_KNEE = dict(KNEEL, root_pos=(0, 0, 1.6), torso=(24, 0, 0), head=(10, 0, 0), rh=(-7.0, 3.0, -2.0), lh=(7.4, 1.0, -1.0),
              r_pole=(0, 0, 1), l_pole=(0, 0, 1), tail_up=-12.0, coat=10.0, cape=10.0)
D_SLUMP = dict(D_KNEE, body=(24, 0, 0), torso=(40, 0, 0), head=(34, 0, 0), body_pos=(0, 8.4, 0),
               rh=(-6.4, 6.6, -3.0), lh=(6.6, 6.0, -2.0), rhand=(20, 0, 0), lhand=(20, 0, 0), tail_up=-18.0, tail=14.0)
DEATH_KEYS = [
    (0.00, {}, "lin"),
    (0.30, D_STAGGER, "out"),
    (0.85, D_KNEE, "in"),
    (1.00, dict(D_KNEE, body_pos=(0, 7.9, 0)), "out"),
    (2.20, D_SLUMP, "ease"),
    (2.40, dict(D_SLUMP), "lin"),
]


def death(t):
    pr = layered(DEATH_KEYS, t)
    pr["tail_sway"] = 3.0 * max(0.0, 1 - t / 2.0)
    return pr


CLIPS = [  # имя, функция, длина, кадров/с, цикл
    ("idle", idle, 2.4, 20, True),
    ("walk", walk, 1.0, 24, True),
    ("run", run, 0.65, 30, True),
    ("combo", combo, COMBO[-1][0] + TAIL, 60, False),
    ("jet", jet, JET[-1][0] + TAIL, 60, False),
    ("slam", slam, SLAM[-1][0] + TAIL, 60, False),
    ("summon", summon, SUMMON[-1][0] + TAIL, 60, False),
    ("dodge_back", dodge_back, 0.70, 60, False),
    ("dodge_left", dodge_left, 0.70, 60, False),
    ("dodge_right", dodge_right, 0.70, 60, False),
    ("pillar", pillar, PILLAR[-1][0] + TAIL, 60, False),
    ("guard", guard, GUARD[-1][0] + TAIL, 60, False),
    ("dive", dive, DIVE[-1][0], 60, False),
    ("dive_land", dive_land, DIVE_LAND_KEYS[-1][0] + TAIL, 60, False),
    ("yield", yield_, YIELD[-1][0] + TAIL, 60, False),
    ("tail", tail_whip, WHIP_KEYS[-1][0] + TAIL, 60, False),
    ("beam", beam, BEAM_KEYS[-1][0] + TAIL, 60, False),
    ("eject", eject, EJECT_KEYS[-1][0] + TAIL, 60, False),
    ("death", death, DEATH_KEYS[-1][0], 60, False),
]
MINION_CLIPS = ["idle", "walk", "run", "combo", "jet", "dodge_back", "dodge_left", "dodge_right"]


def with_tail(fn, t, ln, loop):
    """Поза + время для хвоста: период волны и скорость поворота корпуса (для отставания)."""
    pr = fn(t)
    dt = 0.08
    t0 = t - dt
    if t0 < 0:
        t0 = t0 % ln if loop else 0.0
    p0 = fn(t0)
    turn = (pr["root_y"] - p0["root_y"]) + (pr["body"][1] - p0["body"][1]) + 0.5 * (pr["torso"][1] - p0["torso"][1])
    pr["_t"] = t
    pr["_per"] = ln if loop else 2.0
    pr["_turn"] = turn
    return pr


def bake_all(model, names, extra=()):
    bones = [b["name"] for b in model.bones if not b["name"].startswith("jet_")]
    out = {}
    for name, fn, ln, fps, loop in list(CLIPS) + list(extra):
        if name not in names:
            continue
        PREV_PN.clear()
        anim = bake(lambda t, fn=fn, ln=ln, loop=loop: resolve(with_tail(fn, t, ln, loop), model), ln, fps, loop,
                    bones, unwrap=True)
        if not loop:
            anchor_end(anim)
        out["animation.%s.%s" % (model.name, name)] = anim
    return out


def jet_nozzle_point(model, fn, t, side_name="right"):
    """Где сопло (в блоках, система сущности: x вправо, y вверх, z вперёд) — для частиц струи в Java."""
    pose = resolve(fn(t), model)
    piv = model.piv["jet_r" if side_name == "right" else "jet_l"]
    p = model.world("right_fore" if side_name == "right" else "left_fore",
                    sub(piv, model.piv["right_fore" if side_name == "right" else "left_fore"]), pose)
    # Java‑модель: x, y вниз (24 — земля), −z вперёд → сущность: x' = −x (правая сторона модели — −x),
    # y' = (24 − y)/16, z' = −z/16
    return (-p[0] / 16.0, (24 - p[1]) / 16.0, -p[2] / 16.0)


def case_texture(path):
    """Чёрный чемодан (CurbCaseEntity, 32×32). Развёртки совпадают с CurbCaseRenderer:
    корпус uv (0,0) 12×8×4, ручка (0,14) 5×1×1, стойки ручки (0,18) 1×1.5×1, замки (16,14) 2×1.5×0.5."""
    from PIL import Image
    import random
    rnd = random.Random(1972)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

    def put(x, y, c):
        k = 1.0 + (rnd.random() - 0.5) * 0.12
        img.putpixel((x, y), (min(255, int(c[0] * k)), min(255, int(c[1] * k)), min(255, int(c[2] * k)), 255))

    leather, edge, seam = (20, 20, 24), (34, 34, 40), (12, 12, 15)
    for y in range(12):
        for x in range(32):
            c = leather
            if y >= 4 and y in (4, 11):
                c = edge                                     # окантовка по краям граней
            if y == 8:
                c = seam                                     # шов посередине
            if y < 4 and (x % 16 in (0, 15) or y in (0, 3)):
                c = edge
            put(x, y, c)
    for y in range(14, 16):
        for x in range(12):
            put(x, y, (28, 28, 32))
    for y in range(18, 21):
        for x in range(4):
            put(x, y, (40, 40, 46))
    for y in range(14, 16):
        for x in range(16, 21):
            put(x, y, (150, 150, 160) if (x + y) % 3 else (110, 110, 122))
    img.save(path)


if __name__ == "__main__":
    case_texture(asset_groups.tex("curb_case.png", ROOT))
    for M_, PT in ((B, PB), (N, PN)):
        M_.write_geo(ROOT)
        PT.img.save(asset_groups.tex(M_.name + ".png", ROOT))
        PT.glow.save(asset_groups.tex(M_.name + "_glowmask.png", ROOT))
    write_anims(ROOT, B.name, bake_all(B, [c[0] for c in CLIPS]))
    write_anims(ROOT, N.name, bake_all(N, MINION_CLIPS + ["emerge"], extra=[("emerge", emerge, 1.6 + TAIL, 60, False)]))
    print("готово:", B.name, len(B.cubes()), "кубов;", N.name, len(N.cubes()), "кубов")
    print("сопло при замахе (jet 1.2 с):", ["%.2f" % v for v in jet_nozzle_point(B, jet, 1.2)])
