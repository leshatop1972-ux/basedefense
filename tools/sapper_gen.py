"""
Генератор моба «Подкопщик» (sapper): модели Bedrock, текстуры, анимации и дорожка кирки.

Запуск:  python tools/sapper_gen.py
Проверка без игры:
    python tools/mob_preview.py sapper                          — поза покоя с 3 ракурсов
    python tools/sapper_gen.py --preview throw 0.2,0.4,0.55     — кадры С КИРКОЙ (YAW=…, SCALE=…)
    python tools/sapper_gen.py --check                          — кирка не протыкает голову и корпус
    python tools/anim_check.py sapper

Что создаёт (assets/basedefense):
  geo/sapper, sapper_pick, sapper_bomb, sapper_charge, sapper_smoke (.geo.json)
  textures/entity/<то же>.png + _glowmask.png
  animations/sapper.animation.json и sapper.weapon.json (дорожка кирки)

Внешность: сутулый карлик‑сапёр ≈1.3 блока. Медная шахтёрская каска с козырьком и оплывшей свечой
(огонёк — кость candle_flame, светится), закопчённое лицо, круглые очки‑консервы с бликом, густая
борода в саже (3 пряди качаются), кожаный фартук в подпалинах и заплатах, за спиной мешок с порохом
на лямках (4 тлеющих фитиля качаются), на поясе связка из трёх бомб‑банок, наколенники, обмотки,
тяжёлые ботинки. Правая рука — кирка‑коготь (отдельная модель sapper_pick), левая свободна:
в ней появляются бомба (held_bomb) и шашка (held_charge).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import (FACE_LIGHT, Model, Painter, add, bake, cross, dot, ease, faces, length, lerp,  # noqa: E402
                    mix, mm, mul, mv, norm, rx_m, scl, sub, to_euler, tr, write_anims)
import nabat_gen as ng  # noqa: E402  (IK, ключи поз, дорожки оружия, предпросмотр)

ROOT = ng.ROOT
MODEL_SCALE = 0.85        # в игре (SapperRenderer.SCALE)

# =====================================================================
# Палитра
# =====================================================================
SOOT = (30, 27, 25)
SOOT_L = (56, 50, 46)
LEATHER = (112, 70, 40)
LEATHER_D = (74, 44, 26)
LEATHER_L = (148, 98, 60)
APRON = (140, 82, 44)
APRON_D = (96, 54, 30)
APRON_L = (172, 108, 62)
SCORCH = (36, 24, 18)
SKIN = (156, 112, 86)
SKIN_D = (112, 80, 62)
SKIN_L = (184, 140, 110)
BEARD = (86, 54, 34)
BEARD_D = (54, 36, 26)
BEARD_L = (122, 80, 50)
COPPER = (186, 110, 58)
COPPER_L = (228, 160, 98)
COPPER_D = (116, 62, 34)
PATINA = (86, 138, 116)
IRON = (98, 100, 106)
IRON_L = (156, 158, 166)
IRON_D = (58, 58, 64)
WOOD = (112, 78, 46)
WOOD_D = (76, 50, 30)
BURLAP = (150, 128, 92)
BURLAP_D = (110, 90, 62)
BURLAP_L = (180, 158, 118)
WAX = (232, 216, 172)
WAX_D = (196, 176, 128)
FLAME = (255, 210, 104)
FLAME_HI = (255, 248, 206)
FLAME_O = (255, 138, 40)
EMBER = (255, 112, 28)
SHIRT = (84, 82, 88)
SHIRT_D = (58, 56, 62)
TROUSER = (72, 62, 52)
TROUSER_D = (50, 42, 36)
WRAP = (150, 134, 104)
WRAP_D = (110, 96, 74)
BOOT = (58, 40, 28)
BOOT_D = (36, 26, 20)
LENS = (44, 70, 72)
LENS_HI = (214, 244, 232)
BRASS = (196, 156, 72)
RED_PAPER = (150, 46, 34)
RED_PAPER_D = (104, 30, 24)
TIN = (128, 132, 128)
TIN_D = (84, 88, 86)
TIN_L = (176, 180, 174)
SMOKE_TIN = (96, 100, 98)

# =====================================================================
# 1. Модели
# =====================================================================
UPPER, FORE = 4.5, 4.5          # плечо и предплечье
THIGH, SHIN = 3.5, 3.5          # бедро и голень
SH_X = 5.6                      # плечо по x (Bedrock)
FUSES = 4
BOMBS = 3
BEARDS = 3


def octo(M, bone, cx, cz, y0, h, w, mat, face=None, d=None):
    """Куб + такой же, повёрнутый на 45° (×0.84) — «круглый» предмет."""
    d = d or w
    M.cube(bone, (cx - w / 2, y0, cz - d / 2), (w, h, d), mat, face=face)
    w2, d2 = w * 0.84, d * 0.84
    M.cube(bone, (cx - w2 / 2, y0 + 0.01, cz - d2 / 2), (w2, h - 0.02, d2), mat, face=face,
           rot=(0, 45, 0), pivot=(cx, y0, cz))


def build_sapper():
    M = Model("sapper", 128, 128)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    # --- таз, пояс, фартук, бомбы ---
    b("body", "root", (0, 8.5, 0))
    c("body", (-3.8, 8.0, -2.4), (7.6, 3.0, 4.8), "trouser", face="pelvis")
    c("body", (-4.05, 10.0, -2.65), (8.1, 1.1, 5.3), "belt", face="belt")
    c("body", (-1.0, 9.9, -2.95), (2.0, 1.3, 0.5), "brass", face="buckle")
    c("body", (-4.7, 8.3, -1.3), (1.0, 2.0, 2.6), "leather", face="pouch")
    b("apron", "body", (0, 10.4, -2.7))
    c("apron", (-3.4, 4.6, -3.0), (6.8, 5.8, 0.5), "apron", face="apron_low")
    for k in range(BOMBS):
        nm = "bomb%d" % k
        x, z = ((4.2, -1.3), (4.5, 0.5), (3.8, 2.2))[k]
        b(nm, "body", (x, 10.2, z))
        c(nm, (x - 0.2, 8.9, z - 0.2), (0.4, 1.3, 0.4), "cord")
        c(nm, (x - 0.9, 6.9, z - 0.9), (1.8, 2.1, 1.8), "tin", face="bomb_can")
        c(nm, (x - 0.15, 6.6, z - 0.15), (0.3, 0.4, 0.3), "fuse_glow")
    # --- ноги ---
    for side, sx in (("right", -1), ("left", 1)):
        leg, shin, foot = side + "_leg", side + "_shin", side + "_foot"
        px = sx * 2.1
        b(leg, "body", (px, 8.5, 0))
        c(leg, (px - 1.45, 5.0, -1.45), (2.9, 3.9, 2.9), "trouser", face="thigh")
        b(shin, leg, (px, 5.0, 0))
        c(shin, (px - 1.3, 1.6, -1.3), (2.6, 3.5, 2.6), "wrap", face="wraps")
        c(shin, (px - 1.55, 3.4, -1.9), (3.1, 2.2, 1.1), "leather", face="kneepad")
        b(foot, shin, (px, 1.5, 0))
        c(foot, (px - 1.7, 0.4, -3.3), (3.4, 1.8, 4.9), "boot", face="boot")
        c(foot, (px - 1.8, 0.0, -3.4), (3.6, 0.5, 5.1), "boot", face="sole")
        c(foot, (px - 1.85, 2.0, -1.85), (3.7, 0.7, 3.4), "boot", face="cuff")
    # --- корпус ---
    b("torso", "body", (0, 11.0, 0))
    c("torso", (-4.6, 11.0, -3.0), (9.2, 6.2, 6.0), "shirt", face="chest")
    c("torso", (-5.3, 15.4, -2.7), (10.6, 2.3, 5.4), "leather", face="yoke")
    c("torso", (-3.0, 11.2, -3.45), (6.0, 5.2, 0.5), "apron", face="bib")
    for sx in (-1, 1):
        c("torso", (sx * 3.4 - 0.6, 17.6, -2.9), (1.2, 0.5, 5.8), "strap")
        c("torso", (sx * 3.6 - 0.5, 12.4, -3.35), (1.0, 5.3, 0.4), "strap", face="strap_v")
    # мешок с порохом
    b("pack", "torso", (0, 17.2, 2.9))
    c("pack", (-3.9, 9.4, 2.9), (7.8, 7.4, 4.8), "sack", face="sack")
    c("pack", (-3.4, 8.8, 3.3), (6.8, 0.8, 4.0), "sack", face="sack_bottom")
    c("pack", (-4.2, 10.6, 3.4), (0.5, 5.0, 3.8), "sack", face="sack_side")
    c("pack", (3.7, 10.6, 3.4), (0.5, 5.0, 3.8), "sack", face="sack_side")
    c("pack", (-2.6, 16.8, 3.9), (5.2, 1.4, 2.8), "sack", face="sack_neck")
    c("pack", (-2.8, 16.6, 3.7), (5.6, 0.6, 3.2), "cord")
    c("pack", (-1.6, 12.0, 7.65), (3.2, 2.8, 0.3), "leather", face="patch")
    for k in range(FUSES):
        nm = "fuse%d" % k
        x, z, h = ((-1.5, 4.6, 4.4), (0.3, 5.6, 3.6), (1.5, 4.3, 4.8), (-0.3, 3.9, 3.2))[k]
        b(nm, "pack", (x, 17.8, z))
        c(nm, (x - 0.25, 17.8, z - 0.25), (0.5, h, 0.5), "fuse", face="fuse")
        c(nm, (x - 0.35, 17.8 + h, z - 0.35), (0.7, 0.6, 0.7), "fuse_glow")
    # --- голова ---
    b("head", "torso", (0, 17.4, -1.0))
    c("head", (-3.4, 17.6, -5.8), (6.8, 6.0, 6.4), "skin", face="head")
    c("head", (-0.95, 19.2, -6.9), (1.9, 2.3, 1.3), "skin", face="nose")
    c("head", (-3.9, 19.5, -3.4), (0.6, 1.6, 1.3), "skin", face="ear")
    c("head", (3.3, 19.5, -3.4), (0.6, 1.6, 1.3), "skin", face="ear")
    c("head", (-3.55, 20.6, -5.95), (7.1, 1.0, 6.7), "strap", face="goggle_strap")
    c("head", (-3.1, 20.2, -6.55), (2.6, 2.3, 0.8), "goggle", face="lens")
    c("head", (0.5, 20.2, -6.55), (2.6, 2.3, 0.8), "goggle", face="lens")
    c("head", (-0.5, 20.9, -6.4), (1.0, 0.6, 0.5), "brass")
    c("head", (-3.3, 15.6, -6.3), (6.6, 3.2, 2.6), "beard", face="beard")
    c("head", (-3.5, 17.4, -5.9), (0.9, 2.4, 3.4), "beard", face="sideburn")
    c("head", (2.6, 17.4, -5.9), (0.9, 2.4, 3.4), "beard", face="sideburn")
    c("head", (-2.5, 18.6, -6.75), (5.0, 0.9, 0.9), "beard", face="mustache")
    for k in range(BEARDS):
        nm = "beard%d" % k
        x = (-1.9, 0.0, 1.9)[k]
        b(nm, "head", (x, 15.9, -5.6))
        w_ = 1.6 if k != 1 else 1.9
        h_ = 2.4 if k != 1 else 3.0
        c(nm, (x - w_ / 2, 15.9 - h_, -6.3), (w_, h_, 1.5), "beard", face="strand")
    # каска
    c("head", (-3.85, 23.0, -6.25), (7.7, 1.1, 7.3), "copper", face="rim")
    octo(M, "head", 0, -2.6, 24.0, 1.7, 6.8, "copper", face="dome", d=6.4)
    octo(M, "head", 0, -2.6, 25.6, 0.9, 5.0, "copper", face="dome_top", d=4.6)
    c("head", (-0.45, 24.1, -6.1), (0.9, 2.3, 6.9), "copper", face="ridge")
    c("head", (-3.6, 23.0, -7.9), (7.2, 0.5, 1.8), "copper", face="visor")
    c("head", (-1.1, 23.6, -6.7), (2.2, 2.0, 0.6), "copper", face="mount")
    c("head", (-0.95, 25.5, -7.3), (1.9, 0.5, 1.9), "brass", face="cup")
    c("head", (-0.5, 26.0, -6.85), (1.0, 1.7, 1.0), "wax", face="candle")
    c("head", (-0.75, 25.6, -7.05), (0.5, 0.9, 0.45), "wax", face="drip")
    c("head", (0.3, 25.7, -6.95), (0.45, 0.7, 0.4), "wax", face="drip")
    b("candle_flame", "head", (0, 27.7, -6.35))
    c("candle_flame", (-0.35, 27.7, -6.7), (0.7, 1.2, 0.7), "flame", face="flame")
    c("candle_flame", (-0.25, 27.75, -6.6), (0.5, 0.9, 0.5), "flame", face="flame_core", rot=(0, 45, 0),
      pivot=(0, 27.7, -6.35))
    # --- руки ---
    for side, sx in (("right", -1), ("left", 1)):
        a, f, h = side + "_arm", side + "_fore", side + "_hand"
        px = sx * SH_X
        b(a, "torso", (px, 16.6, 0))
        c(a, (px - 1.5 + sx * 0.3, 15.1, -1.95), (3.0, 2.6, 3.9), "leather", face="pauldron")
        c(a, (px - 1.2, 16.6 - UPPER, -1.3), (2.4, UPPER, 2.6), "shirt", face="sleeve")
        b(f, a, (px, 16.6 - UPPER, 0))
        c(f, (px - 1.1, 16.6 - UPPER - FORE, -1.2), (2.2, FORE, 2.4), "skin", face="forearm")
        c(f, (px - 1.35, 16.6 - UPPER - FORE - 0.2, -1.45), (2.7, 2.2, 2.9), "leather", face="bracer")
        c(f, (px - 1.3, 16.6 - UPPER - 1.1, -1.4), (2.6, 1.1, 2.8), "shirt", face="cuff")
        b(h, f, (px, 16.6 - UPPER - FORE, 0))
        c(h, (px - 1.25, 16.6 - UPPER - FORE - 2.5, -1.3), (2.5, 2.5, 2.6), "leather", face="glove")
    wy = 16.6 - UPPER - FORE - 1.4       # центр кулака левой руки
    b("held_bomb", "left_hand", (SH_X, wy, 0))
    c("held_bomb", (SH_X - 0.9, wy - 1.1, -0.9), (1.8, 2.2, 1.8), "tin", face="bomb_can")
    c("held_bomb", (SH_X - 0.2, wy - 1.9, -0.2), (0.4, 0.9, 0.4), "fuse_glow")
    b("held_charge", "left_hand", (SH_X, wy, 0))
    for dx, dz in ((-0.6, -0.45), (0.6, -0.45), (0.0, 0.55)):
        c("held_charge", (SH_X + dx - 0.55, wy - 2.2, dz - 0.55), (1.1, 4.4, 1.1), "paper", face="stick")
    c("held_charge", (SH_X - 1.3, wy - 0.8, -1.2), (2.6, 0.6, 2.4), "cord")
    M.pack()
    return M


def build_pick():
    """Кирка‑коготь: хват в начале координат, рукоять вдоль +y, клюв вперёд (−z), обух назад."""
    M = Model("sapper_pick", 64, 64)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("pick", "root", (0, 0, 0))
    c("pick", (-0.5, -3.2, -0.5), (1.0, 14.6, 1.0), "wood", face="haft")
    c("pick", (-0.65, -2.8, -0.65), (1.3, 5.2, 1.3), "wrap", face="grip")
    c("pick", (-0.75, -3.9, -0.75), (1.5, 0.9, 1.5), "iron", face="pommel")
    c("pick", (-0.95, 9.6, -0.95), (1.9, 2.8, 1.9), "iron", face="collar")
    c("pick", (-1.0, 10.2, -1.0), (2.0, 0.5, 2.0), "copper", face="rivets")
    c("pick", (-1.0, 11.6, -1.0), (2.0, 0.5, 2.0), "copper", face="rivets")
    # клюв: три звена, загибается вниз
    c("pick", (-0.65, 10.5, -4.3), (1.3, 1.5, 3.5), "iron", face="claw")
    c("pick", (-0.55, 10.25, -6.9), (1.1, 1.25, 2.9), "iron", face="claw", rot=(-24, 0, 0), pivot=(0, 11.2, -4.3))
    c("pick", (-0.4, 9.6, -8.6), (0.8, 1.0, 2.0), "iron", face="claw_tip", rot=(-50, 0, 0), pivot=(0, 10.6, -6.8))
    for k in range(3):     # зазубрины снизу
        c("pick", (-0.3, 10.1, -2.4 - k * 1.2), (0.6, 0.5, 0.6), "iron", face="tooth")
    # обух
    c("pick", (-0.85, 10.3, 0.9), (1.7, 1.9, 2.1), "iron", face="poll")
    c("pick", (-0.95, 10.2, 2.9), (1.9, 2.1, 0.5), "iron", face="poll_face")
    M.pack()
    return M


def build_bomb():
    """Бомба‑банка: жестяная банка с фитилём; центр — в начале координат."""
    M = Model("sapper_bomb", 64, 32)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("bomb", "root", (0, 0, 0))
    octo(M, "bomb", 0, 0, -1.6, 3.2, 3.0, "tin", face="can")
    octo(M, "bomb", 0, 0, 1.6, 0.4, 3.2, "tin", face="lid")
    c("bomb", (-0.5, 2.0, -0.5), (1.0, 0.5, 1.0), "iron")
    b("bomb_fuse", "bomb", (0, 2.5, 0))
    c("bomb_fuse", (-0.2, 2.5, -0.2), (0.4, 1.4, 0.4), "fuse", face="fuse", rot=(0, 0, 18), pivot=(0, 2.5, 0))
    c("bomb_fuse", (0.15, 3.65, -0.3), (0.6, 0.6, 0.6), "fuse_glow")
    M.pack()
    return M


def build_charge():
    """Связка из трёх шашек со шнуром; задней стороной (+z) к стене, центр — в начале координат."""
    M = Model("sapper_charge", 64, 32)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("charge", "root", (0, 0, 0))
    for k, (x, z) in enumerate(((-1.25, 0.3), (1.25, 0.3), (0.0, -0.75))):
        c("charge", (x - 1.0, -3.0, z - 1.0), (2.0, 6.0, 2.0), "paper", face="stick")
    for y in (-2.0, 1.4):
        c("charge", (-2.45, y, -1.95), (4.9, 0.6, 3.5), "cord")
    c("charge", (-0.6, 3.0, -1.1), (1.2, 0.4, 1.2), "iron")
    c("charge", (-1.2, -0.6, 1.25), (2.4, 1.2, 0.5), "iron", face="spike")     # скоба, вбитая в стену
    b("charge_fuse", "charge", (0, 3.4, -0.5))
    c("charge_fuse", (-0.2, 3.4, -0.7), (0.4, 2.2, 0.4), "fuse", face="fuse", rot=(-25, 0, 0), pivot=(0, 3.4, -0.5))
    c("charge_fuse", (-0.3, 5.3, -1.95), (0.6, 0.6, 0.6), "fuse_glow")
    M.pack()
    return M


def build_smoke():
    """Дымовая банка с дырками; центр — в начале координат."""
    M = Model("sapper_smoke", 64, 32)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("smoke", "root", (0, 0, 0))
    octo(M, "smoke", 0, 0, -1.8, 3.6, 2.6, "smoke_tin", face="holes")
    octo(M, "smoke", 0, 0, 1.8, 0.5, 2.8, "smoke_tin", face="lid")
    c("smoke", (-0.6, 2.3, -0.6), (1.2, 0.6, 1.2), "iron")
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

    def grime(col, j, h, k=0.35):
        """Копоть гуще книзу."""
        return mix(col, SOOT, k * (j / max(1, h - 1)) ** 1.5)

    def px(mat, face, f, i, j, w, h):
        side = f not in ("top", "bottom")
        r = rnd.random()
        if mat == "flame":
            if face == "flame_core":
                return ("glow", FLAME_HI)
            if not side:
                return ("glow", FLAME)
            return ("glow", FLAME_HI if j > 0 and 0 < i < w - 1 else (FLAME if j < h - 1 else FLAME_O))
        if mat == "fuse_glow":
            return ("glow", EMBER if (i + j) % 2 else FLAME)
        if mat == "fuse":
            if face == "fuse" and side and j == 0:
                return ("glow", EMBER)
            return lit(f, mix((74, 60, 44), SOOT, 0.3) if (j % 2) else (96, 80, 58))
        if mat == "skin":
            if face == "head":
                if f == "front":
                    if j >= h - 2:
                        return lit(f, BEARD_D if (i + j) % 2 else BEARD)
                    if j <= 1:
                        return lit(f, SKIN_D)
                    sm = SKIN_D if (i in (0, w - 1) or r < 0.3) else SKIN
                    return lit(f, mix(sm, SOOT, 0.25 + 0.2 * r))
                if f == "top":
                    return lit(f, SOOT_L)
                if f == "back":
                    return lit(f, mix(BEARD_D, SOOT, 0.3) if j > 1 else SOOT_L)
                if f in ("left", "right"):
                    if j >= h - 2 and ((f == "right" and i < 3) or (f == "left" and i > w - 4)):
                        return lit(f, BEARD)
                    return lit(f, mix(SKIN_D, SOOT, 0.2 + 0.25 * r) if j > 0 else SOOT_L)
                return lit(f, SKIN_D)
            if face == "nose":
                if f == "front":
                    return lit(f, SKIN_L if j == 0 and i == 0 else (mix(SKIN, (170, 90, 70), 0.35) if j >= h - 1 else SKIN))
                return lit(f, SKIN_D if f == "bottom" else mix(SKIN, SOOT, 0.15))
            if face == "ear":
                return lit(f, SKIN_D if j == h - 1 else SKIN)
            if face == "forearm":
                # закатанный рукав: волосатое, закопчённое предплечье
                base = SKIN if (i + j * 3) % 5 else SKIN_D
                return lit(f, grime(base, j, h, 0.35))
            return lit(f, SKIN)
        if mat == "beard":
            n = (i * 7 + j * 3 + int(r * 3)) % 7
            col = BEARD_L if n == 0 else (BEARD_D if n in (3, 5) else BEARD)
            if face in ("beard", "strand") and side:
                col = grime(col, j, h, 0.45)          # кончики в саже
                if j == h - 1 and (i % 2):
                    col = mix(col, (120, 116, 110), 0.4)   # седина/зола на кончиках
            if face == "mustache" and j == 0:
                col = BEARD_L
            return lit(f, col)
        if mat == "goggle":
            if f == "front":
                if i == 0 or j == 0 or i == w - 1 or j == h - 1:
                    return lit(f, BRASS if (i + j) % 2 else mix(BRASS, COPPER_D, 0.4))
                if i == 1 and j == 1:
                    return LENS_HI                    # блик
                return mix(LENS, (20, 32, 36), 0.3 * r)
            return lit(f, mix(BRASS, COPPER_D, 0.3))
        if mat == "strap":
            if face == "goggle_strap" and f == "front":
                return lit(f, LEATHER_D)
            return lit(f, LEATHER_D if j % 3 else LEATHER)
        if mat == "brass":
            return lit(f, BRASS if (i + j) % 3 else (230, 196, 112))
        if mat == "copper":
            base = COPPER
            if r < 0.025:
                base = PATINA
            elif r < 0.09:
                base = mix(COPPER, COPPER_D, 0.6)
            if face in ("rim", "visor") and side:
                base = COPPER_L if j == 0 else (COPPER_D if j == h - 1 else base)
            if face in ("dome", "dome_top") and f == "top":
                base = mix(COPPER_L, base, 0.5)
            if face == "dome" and side:
                base = mix(base, COPPER_L, 0.35) if i < w // 3 else base
                if (i * 5 + j * 3) % 17 == 0:
                    base = mix(base, SOOT_L, 0.5)     # вмятины/копоть
            if face == "mount":
                base = COPPER_D if (i + j) % 2 else COPPER
            if face == "visor" and f == "bottom":
                base = SOOT_L
            # копоть над свечой
            return lit(f, base, n=0.07)
        if mat == "wax":
            if face == "candle":
                if f == "top":
                    return ("glow", FLAME_O) if (i, j) == (0, 0) else lit(f, WAX)
                return lit(f, WAX if j < h - 1 or i % 2 else WAX_D)
            return lit(f, WAX_D if j == h - 1 else WAX)
        if mat == "apron":
            # бурая кожа в подпалинах и заплатах
            col = APRON
            if (i * 3 + j * 5) % 13 == 0:
                col = mix(APRON, APRON_L, 0.5)
            if (i + j * 2) % 9 == 0:
                col = mix(APRON, APRON_D, 0.6)
            if face == "apron_low" and f == "front":
                if j == h - 1:
                    return lit(f, APRON_D) if i % 2 else None    # обтрёпанный край
                cx, cy = w * 0.68, h * 0.62
                d = math.hypot(i - cx, j - cy)
                if d < 1.0:
                    return lit(f, SCORCH)
                if d < 1.8:
                    return ("glow", (150, 60, 20)) if (i + j) % 5 == 0 else lit(f, mix(APRON_D, SCORCH, 0.6))
                if 1 <= i <= 2 and 1 <= j <= 2:
                    return lit(f, mix(LEATHER, LEATHER_D, 0.4))   # заплата
                return lit(f, grime(col, j, h, 0.45))
            if face == "bib" and f == "front":
                if j == 0:
                    return lit(f, APRON_D)
                if i == 1 and j == 3:
                    return lit(f, SCORCH)
                if j == 2 and 2 <= i <= 4:
                    return lit(f, APRON_D)            # кармашек
                return lit(f, grime(col, j, h, 0.25))
            return lit(f, col)
        if mat == "leather":
            col = LEATHER if (i * 5 + j * 3) % 7 else LEATHER_D
            if side and j == 0:
                col = LEATHER_L
            if face == "patch":
                if i in (0, w - 1) or j in (0, h - 1):
                    return lit(f, (200, 180, 140) if (i + j) % 2 else LEATHER_D)
                return lit(f, LEATHER_D)
            if face == "kneepad" and f == "front":
                if (i, j) in ((1, 1), (w - 2, 1)):
                    return lit(f, BRASS)
                return lit(f, LEATHER_L if j == 0 else (LEATHER_D if j == h - 1 else LEATHER))
            if face == "glove":
                col = mix(LEATHER, SOOT, 0.35 + 0.2 * r)
            if face == "pauldron" and side:
                col = mix(col, SOOT, 0.2) if j > h // 2 else col
                if j == h - 1:
                    col = LEATHER_D
            return lit(f, grime(col, j, h, 0.2))
        if mat == "belt":
            if side and (j == 0 or j == h - 1):
                return lit(f, LEATHER_D)
            return lit(f, LEATHER if (i % 6) else BRASS)
        if mat == "cord":
            return lit(f, (156, 132, 92) if (i + j) % 2 else (118, 98, 66))
        if mat == "shirt":
            col = SHIRT if (i + j) % 4 else SHIRT_D
            if face == "chest" and f == "front":
                if i == w // 2 and j % 2 == 0:
                    return lit(f, (150, 140, 120))    # пуговицы
                return lit(f, grime(col, j, h, 0.3))
            if face == "cuff":
                return lit(f, mix(SHIRT, (140, 136, 130), 0.3) if j == 0 else SHIRT)
            return lit(f, grime(col, j, h, 0.25))
        if mat == "trouser":
            col = TROUSER if (i * 3 + j) % 5 else TROUSER_D
            if face == "thigh" and f == "front" and 0 < i < w - 1 and j == h - 2:
                col = mix(TROUSER, SOOT, 0.5)
            return lit(f, grime(col, j, h, 0.3))
        if mat == "wrap":
            # обмотки: диагональные витки
            k = (i + j * 2) % 4
            col = WRAP if k else WRAP_D
            return lit(f, grime(col, j, h, 0.55))
        if mat == "boot":
            if face == "sole":
                return lit(f, (40, 32, 26))
            if face == "cuff":
                return lit(f, BOOT_D if j else BOOT)
            col = BOOT if (i * 3 + j * 7) % 9 else BOOT_D
            if face == "boot" and f == "front" and j == h - 1:
                col = mix(BOOT, IRON, 0.5)            # окованный носок
            if f == "top" and r < 0.15:
                col = (70, 52, 36)
            return lit(f, col)
        if mat == "sack":
            # мешковина: переплетение, швы, пятна пороха
            weave = (i % 2) ^ (j % 2)
            col = BURLAP if weave else mix(BURLAP, BURLAP_D, 0.45)
            if r < 0.08:
                col = BURLAP_L
            if face == "sack" and f == "back":
                if j == h // 2 and i % 2 == 0:
                    return lit(f, (90, 72, 50))       # шов
                cx, cy = w * 0.3, h * 0.75
                if math.hypot(i - cx, j - cy) < 1.6:
                    return lit(f, mix(col, SOOT, 0.7))  # пороховое пятно
                if 2 <= i <= w - 3 and 1 <= j <= 3 and (i + j) % 3 == 0:
                    return lit(f, mix(col, SOOT, 0.55))  # трафарет «ПОРОХЪ» — намёк штрихами
            if face == "sack_neck":
                col = mix(col, SOOT, 0.2)
            return lit(f, grime(col, j, h, 0.3))
        if mat == "tin":
            if face == "bomb_can":
                if f == "front" and 0 < i < w - 1 and 0 < j < h - 1:
                    return lit(f, (210, 200, 170))    # бумажная наклейка
                return lit(f, TIN_L if j == 0 else (TIN_D if j == h - 1 else TIN))
            if face == "can" and side:
                # наклейка с черепом (пиксели) на одной стороне, вмятины — на других
                if f == "front" and 0 < j < h - 1:
                    sk = [" ### ", "#.#.#", "#####", " #.# "]
                    jj, ii = j - 1, i - max(0, (w - 5) // 2)
                    if 0 <= jj < len(sk) and 0 <= ii < 5:
                        ch = sk[jj][ii]
                        if ch == "#":
                            return lit(f, (226, 218, 196))
                        if ch == ".":
                            return lit(f, SOOT)
                    return lit(f, (176, 60, 40))      # красная наклейка
                if r < 0.1:
                    return lit(f, TIN_D)              # вмятина
                return lit(f, TIN_L if i == 0 else TIN)
            if face == "lid":
                return lit(f, TIN_L if (i + j) % 3 == 0 else TIN)
            return lit(f, TIN)
        if mat == "smoke_tin":
            if face == "holes" and side and (i % 2 == 1) and (j % 2 == 1) and 0 < j < h - 1:
                return SOOT
            if face == "holes" and side and j == h // 2 - 1 and i % 2 == 0:
                return lit(f, (190, 190, 180))        # белая полоса
            return lit(f, SMOKE_TIN if r > 0.12 else TIN_D)
        if mat == "paper":
            if face == "stick" and f == "top":
                return lit(f, (60, 40, 30))
            return lit(f, RED_PAPER if (j % 4) else RED_PAPER_D)
        if mat == "wood":
            return lit(f, WOOD if (j + (i % 2)) % 4 else WOOD_D)
        if mat == "wrap":
            return lit(f, WRAP)
        if mat == "iron":
            col = IRON if r > 0.15 else IRON_D
            if face in ("claw", "claw_tip", "poll", "poll_face") and side and j == 0:
                col = IRON_L                          # заточка/блик по кромке
            if face == "tooth":
                col = IRON_L
            if face == "spike":
                col = IRON_D
            return lit(f, col)
        return (255, 0, 255)

    # grip у кирки — обмотка (кожа), не обмотки ног
    for cube in M.cubes():
        mat = cube["mat"]
        if M.name == "sapper_pick" and mat == "wrap":
            mat = "leather"
        for fname, rect in faces(cube).items():
            PT.fill(rect, lambda i, j, w, h, f=fname, cu=cube, m=mat: px(m, cu.get("face"), f, i, j, w, h))
    return PT


def save_textures(M, PT):
    PT.img.save(os.path.join(ROOT, "textures", "entity", M.name + ".png"))
    PT.glow.save(os.path.join(ROOT, "textures", "entity", M.name + "_glowmask.png"))


def build_all():
    out = {}
    for fn, seed in ((build_sapper, 41), (build_pick, 43), (build_bomb, 47), (build_charge, 53), (build_smoke, 59)):
        M_ = fn()
        M_.write_geo(ROOT)
        save_textures(M_, paint(M_, seed))
        out[M_.name] = M_
    return out


# =====================================================================
# 3. Позы: параметры, IK, кирка, маятники
# =====================================================================
# Ступни (rf/lf) — лодыжка в системе модели (Java, земля y = 22.5). Кисти (rh/lh) — запястье в системе
# корпуса, плечо (∓5.6, −5.6, 0). wdir — куда смотрит рукоять от хвата к голове кирки (система корпуса),
# wface — куда смотрит клюв (берётся перпендикулярная к рукояти часть). Всё — Java: +y вниз, −z вперёд.
SHOULDER = {"rh": (-SH_X, -5.6, 0.0), "lh": (SH_X, -5.6, 0.0)}
DEF = dict(root=(0, 0, 0), root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0.6, 0), torso=(20, 0, 0),
           head=(-18, 0, 0), rf=(-2.2, 22.5, 0.4), lf=(2.2, 22.5, -0.6), toe_r=0.0, toe_l=0.0,
           rh=(-6.0, -2.4, -4.2), lh=(5.6, 2.6, -1.6), r_pole=(-0.7, 0.4, 0.6), l_pole=(0.5, -0.1, 1.0),
           wdir=(0.05, -0.72, 0.69), wface=(0.0, 0.69, 0.72), lhand=(0, 0, 0), apron=0.0, swing=1.0)
ng.SHOULDER = SHOULDER
ng.DIRS = ("r_pole", "l_pole", "wdir", "wface")
LAG = {"head": -0.02, "rh": 0.03, "lh": 0.04, "wdir": 0.04, "wface": 0.04, "r_pole": 0.03, "l_pole": 0.04,
       "apron": 0.12}
GRIP_G = ng.GRIP_G
PALM = (0.0, 1.3, 0.0)
GRIP_OFFSET = mv(tr(GRIP_G), list(PALM))     # центр кулака в системе кирки (кисть = Rw·Gᵀ)


def P(base=None, **kw):
    d = dict(DEF)
    if base:
        d.update(base)
    d.update(kw)
    return d


def K(keys, t):
    return ng.keyed(keys, t, DEF)


def L(keys, t):
    pr = K(keys, t)
    for name, lag in LAG.items():
        pr[name] = K(keys, max(0.0, t - lag))[name]
    return pr


def weapon_R(model, pose, pr):
    Rt = model.chain_R("torso", pose)
    up = norm(list(pr["wdir"]))
    # клюв — всегда «впереди по замаху»: рукоять, повёрнутая на 90° вокруг оси «вправо» корпуса.
    # Так кисть не прокручивается вокруг рукояти между позами.
    fc = cross([1.0, 0.0, 0.0], up)
    fc = sub(fc, scl(up, dot(fc, up)))
    if length(fc) < 1e-4:
        fc = cross(up, [1.0, 0.0, 0.0])
    fc = norm(fc)
    y = scl(up, -1)
    z = scl(fc, -1)
    x = cross(y, z)
    Rl = [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]
    return mm(Rt, Rl)


def resolve(pr, model, hang=None):
    pose = {"root": {"rot": pr["root"], "pos": pr["root_pos"]},
            "body": {"rot": pr["body"], "pos": pr["body_pos"]},
            "torso": {"rot": pr["torso"]}, "head": {"rot": pr["head"]}}
    Rroot = model.chain_R("root", pose)
    for s_, key, toe in (("right", "rf", "toe_r"), ("left", "lf", "toe_l")):
        tgt = model.world("root", [pr[key][0], pr[key][1] - 24, pr[key][2]], pose)
        ng.limb_ik(model, pose, s_ + "_leg", s_ + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN, -1)
        model.set_world_rot(pose, s_ + "_foot", mm(Rroot, rx_m(math.radians(pr[toe]))))
    Rt = model.chain_R("torso", pose)
    for s_, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        out = mv(Rt, [-1.0 if s_ == "right" else 1.0, 0.3, 0.4])
        ng.limb_ik(model, pose, s_ + "_arm", s_ + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE, 1, out)
    model.set_world_rot(pose, "right_hand", mm(weapon_R(model, pose, pr), tr(GRIP_G)))
    pose["left_hand"] = {"rot": pr["lhand"]}
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    tilt = pr["body"][0]
    pose["apron"] = {"rot": (min(rl, ll, 0) * 0.75 - tilt * 0.8 + pr["apron"], 0, 0)}
    hang = hang or {}
    Rtor = model.chain_R("torso", pose)
    for name in HANGERS:
        d = hang.get(name, [0.0, 1.0, 0.0])
        if name.startswith("fuse"):
            model.set_world_rot(pose, name, ng.frame_X(scl(d, -1), mv(Rtor, [1.0, 0, 0])))
        elif name == "pack":
            model.set_world_rot(pose, name, ng.frame_X(d, mv(Rtor, [1.0, 0, 0])))
        else:
            model.set_world_rot(pose, name, ng.frame_X(d, mv(model.chain_R(model.parent[name], pose), [1.0, 0, 0])))
    return pose


HANGERS = ["pack"] + ["beard%d" % k for k in range(BEARDS)] + ["bomb%d" % k for k in range(BOMBS)] + \
          ["fuse%d" % k for k in range(FUSES)]
HANG_K = {"pack": (0.004, 0.003), "beard": (0.011, 0.007), "bomb": (0.013, 0.008), "fuse": (0.009, 0.005)}
HANG_LIM = {"pack": 14, "beard": 50, "bomb": 55, "fuse": 32}


def pose_at(fn, t, model, length_s, loop):
    """Поза с маятниками: скорость точки подвеса (t, t−0.1, t−0.18, t−0.4) → отклонение от отвеса."""
    def tt(x):
        return x % length_s if loop else max(0.0, x)

    pr = fn(tt(t))
    ng.AUX[0] = True
    now = resolve(pr, model)
    p_a = resolve(fn(tt(t - 0.10)), model)
    p_b = resolve(fn(tt(t - 0.40)), model)
    p_c = resolve(fn(tt(t - 0.18)), model)
    ng.AUX[0] = False
    hang = {}
    for name in HANGERS:
        kind = name.rstrip("0123456789")
        par = model.parent[name]
        loc = model.rel_pivot(name)
        x0 = model.world(par, loc, now)
        v1 = ng.sat(scl(sub(x0, model.world(par, loc, p_c)), 1 / 0.18), 60.0)
        v2 = ng.sat(scl(sub(model.world(par, loc, p_a), model.world(par, loc, p_b)), 1 / 0.30), 60.0)
        k1, k2 = HANG_K[kind]
        k1 *= pr["swing"]
        k2 *= pr["swing"]
        if kind == "fuse":
            # фитили торчат вверх из горловины: «обратный маятник», кончики отстают от движения
            rest = norm(add(mv(model.chain_R("pack", now), [0, -1.0, 0]), [0, -0.6, 0]))
            d = add(rest, add(scl(v1, -k1), scl(sub(v2, v1), k2)))
            lim_axis = rest
            d = norm(d)
            sign = -1
        else:
            if kind == "pack":
                rest = norm(add(mv(model.chain_R("torso", now), [0, 1.0, 0]), [0, 0.4, 0]))
            else:
                rest = [0.0, 1.0, 0.0]
            d = norm(add(rest, add(scl(v1, -k1), scl(sub(v2, v1), k2))))
            lim_axis = rest
            sign = 1
        lim = math.radians(HANG_LIM[kind])
        c_ = dot(d, lim_axis)
        if c_ < math.cos(lim):
            perp = norm(sub(d, scl(lim_axis, c_)))
            d = add(scl(lim_axis, math.cos(lim)), scl(perp, math.sin(lim)))
        hang[name] = scl(norm(d), -1) if sign < 0 else norm(d)
    return pr, resolve(pr, model, hang)


def gait(w, stride, lift, base_y=22.5, width=2.2, z0=0.0):
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l_ = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l_


# =====================================================================
# 4. Анимации
# =====================================================================
CARRY = dict(rh=(-6.0, -2.4, -4.2), wdir=(0.05, -0.72, 0.69), wface=(0.0, 0.69, 0.72))       # кирка на плече
READY = dict(rh=(-4.2, 2.4, -5.6), r_pole=(-0.6, 0.4, 0.7), wdir=(0.15, -0.6, -0.8), wface=(0.0, 0.8, -0.6))
HELM_L = (2.8, -10.6, -5.0)          # левая кисть у козырька каски


def idle(t):
    T_ = 3.0
    w = 2 * math.pi * t / T_
    pr = P()
    pr["body_pos"] = (0, 0.6 + 0.25 * math.sin(w * 2), 0)
    pr["torso"] = (20 + 1.5 * math.sin(w * 2 + 0.4), 2 * math.sin(w), 0)
    # принюхивается: голова вверх и по сторонам (1.0–1.9 с), поправляет каску левой (1.9–2.7 с)
    sniff = ng.smooth01(0.9, 1.2, t) * (1 - ng.smooth01(1.7, 2.0, t))
    adj = ng.smooth01(1.85, 2.15, t) * (1 - ng.smooth01(2.5, 2.85, t))
    pr["head"] = (-18 - 12 * sniff + 1.5 * math.sin(w * 2) + 6 * adj,
                  14 * sniff * math.sin(2 * math.pi * (t - 1.0) / 0.7) + 6 * math.sin(w) * (1 - sniff),
                  -6 * adj)
    pr["lh"] = tuple(lerp(a, b, adj) for a, b in zip((5.6, 2.6 + 0.3 * math.sin(w * 2), -1.6), HELM_L))
    pr["l_pole"] = (0.6, 0.6 * adj, 1.0 - 0.6 * adj)
    pr["rh"] = (-6.0, -2.4 + 0.25 * math.sin(w * 2 + 0.6), -4.2)
    return pr


def sneak(t):
    T_ = 1.3
    w = 2 * math.pi * t / T_
    pr = P(READY, torso=(32, 0, 0), head=(-30, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 2.4, 1.0, base_y=22.5)
    pr["body_pos"] = (0, 2.2 + 0.3 * abs(math.sin(w)), 0)
    pr["body"] = (4, 5 * math.sin(w), 0)
    pr["torso"] = (32, -4 * math.sin(w), 0)
    pr["head"] = (-30, 8 * math.sin(w * 0.5), 0)
    pr["lh"] = (4.6, 2.0 - 0.6 * math.sin(w), -4.6 + 1.4 * math.sin(w))
    pr["rh"] = (-4.2, 2.4 + 0.4 * math.sin(w), -5.6)
    pr["toe_r"] = -8 * max(0.0, math.sin(w))
    pr["toe_l"] = -8 * max(0.0, -math.sin(w))
    return pr


def walk(t):
    T_ = 0.9
    w = 2 * math.pi * t / T_
    pr = P()
    pr["rf"], pr["lf"] = gait(w, 2.8, 1.5)
    pr["body_pos"] = (0, 0.5 + 0.55 * abs(math.sin(w)), 0)
    pr["body"] = (4, 7 * math.sin(w), 3 * math.sin(w))
    pr["torso"] = (22, -6 * math.sin(w), 0)
    pr["head"] = (-20, 3 * math.sin(w), 0)
    pr["rh"] = (-6.0, -2.4 + 0.4 * abs(math.sin(w)), -4.2)
    pr["lh"] = (5.6, 2.4, -1.4 + 3.2 * math.sin(w))
    pr["toe_r"] = -12 * max(0.0, math.sin(w))
    pr["toe_l"] = -12 * max(0.0, -math.sin(w))
    pr["apron"] = 3 * math.sin(2 * w)
    return pr


def run(t):
    T_ = 0.55
    w = 2 * math.pi * t / T_
    pr = P(torso=(30, 0, 0), head=(-30, 0, 0), wdir=(0.15, -0.45, 0.88), wface=(0.0, 0.88, 0.45))
    pr["rf"], pr["lf"] = gait(w, 4.0, 2.6)
    pr["body_pos"] = (0, 0.9 + 1.1 * abs(math.sin(w)), 0)
    pr["body"] = (14, 9 * math.sin(w), 0)
    pr["torso"] = (24, -10 * math.sin(w), 0)
    pr["head"] = (-32, 3 * math.sin(w), 0)
    pr["rh"] = (-6.0, 1.0 + 0.8 * math.sin(w), 0.6 - 3.0 * math.sin(w))
    pr["lh"] = (5.4, 1.0 - 0.8 * math.sin(w), -1.6 + 4.0 * math.sin(w))
    pr["l_pole"] = (0.5, 0.4, 1.0)
    pr["toe_r"] = -22 * max(0.0, math.sin(w))
    pr["toe_l"] = -22 * max(0.0, -math.sin(w))
    pr["apron"] = 14 + 4 * math.sin(2 * w)
    return pr


# удары киркой: замах (над плечом, клюв вперёд‑вверх) → удар (вперёд‑вниз, клюв в землю)
PICK_UP = dict(rh=(-5.0, -7.2, -1.2), r_pole=(-1.0, 0.0, 0.3), wdir=(-0.05, -0.9, 0.42), wface=(0.0, -0.78, -0.62))
PICK_HIT = dict(rh=(-3.0, 2.4, -7.6), r_pole=(-0.7, -0.6, 0.4), wdir=(0.05, 0.42, -0.9), wface=(0.0, 0.9, 0.42))
CROUCH = dict(body_pos=(0, 2.6, 0), torso=(36, 0, 0), head=(-30, 0, 0), rf=(-2.4, 22.5, 1.2), lf=(2.4, 22.5, -1.6))


def tunnel(t):
    """Под землёй: идёт пригнувшись и вгрызается киркой вперёд (цикл 1.0 с — один удар)."""
    T_ = 1.0
    w = 2 * math.pi * t / T_
    keys = [(0.0, dict(CROUCH, **PICK_UP), "lin"), (0.35, dict(CROUCH, **PICK_UP, torso=(26, 6, 0)), "soft"),
            (0.5, dict(CROUCH, **PICK_HIT, torso=(42, -4, 0), body_pos=(0, 3.2, 0)), "in"),
            (0.62, dict(CROUCH, **PICK_HIT, torso=(40, -4, 0)), "out"), (1.0, dict(CROUCH, **PICK_UP), "soft")]
    pr = L(keys, t)
    pr["rf"], pr["lf"] = gait(w, 1.4, 0.7, width=2.4)
    pr["lh"] = (4.6, 1.0, -5.0 + 0.8 * math.sin(w))
    return pr


HIDDEN = dict(body_pos=(0, 6.2, 0), body=(-6, 0, 0), torso=(40, 0, 0), head=(-22, 0, 0),
              rf=(-2.6, 22.5, -2.6), lf=(2.6, 22.5, -2.2), toe_r=-6.0, toe_l=-6.0,
              rh=(-3.6, 4.0, -5.4), r_pole=(-0.8, 0.0, 0.6), wdir=(0.3, -0.8, -0.5), wface=(0.0, 0.6, -0.8),
              lh=(3.4, 4.6, -5.2), l_pole=(0.8, 0.0, 0.6))


def hidden(t):
    w = 2 * math.pi * t / 3.0
    pr = P(HIDDEN)
    pr["body_pos"] = (0, 6.2 + 0.2 * math.sin(w), 0)
    pr["torso"] = (40 + 1.2 * math.sin(w + 0.5), 0, 0)
    pr["head"] = (-22 + 1.5 * math.sin(w), 10 * math.sin(w * 0.5), 0)
    pr["swing"] = 0.5
    return pr


HIDE = [(0.0, {}, "lin"), (0.35, dict(HIDDEN, lh=HELM_L, l_pole=(0.6, 0.6, 0.4), head=(-10, 0, 0)), "soft"),
        (0.6, dict(HIDDEN, lh=(2.4, -11.6, -6.2), l_pole=(0.6, 0.6, 0.4), head=(-8, 0, 0)), "soft"),
        (1.0, HIDDEN, "soft")]
HIDE_SNUFF = 0.6


def hide(t):
    return L(HIDE, t)


THROW_RELEASE = 0.55
THROW = [
    (0.00, {}, "lin"),
    (0.14, dict(lh=(4.6, 5.8, 0.6), l_pole=(0.6, -0.4, 1.0), torso=(24, -8, 0)), "soft"),            # к поясу
    (0.30, dict(lh=(2.6, -9.4, -5.6), l_pole=(0.6, 0.6, 0.3), head=(-10, -14, 14), torso=(18, -4, 0)), "soft"),  # к свече
    (0.44, dict(lh=(7.2, -6.8, 4.2), l_pole=(0.2, 1.0, 0.4), torso=(6, 28, 0), head=(-14, -20, 0),
                body=(0, 10, 0), lf=(2.4, 22.5, 1.4), rf=(-2.2, 22.5, -1.0)), "soft"),            # замах
    (THROW_RELEASE, dict(lh=(2.4, -4.4, -8.0), l_pole=(0.4, 0.8, 0.5), torso=(30, -24, 0), head=(-26, 18, 0),
                         body=(4, -10, 0), lf=(2.4, 22.5, -2.2), rf=(-2.2, 22.5, 1.6)), "in"),    # бросок
    (0.72, dict(lh=(3.8, 0.0, -6.0), torso=(28, -16, 0), head=(-24, 10, 0), body=(2, -6, 0)), "out"),
    (1.10, {}, "soft"),
]


def throw(t):
    return L(THROW, t)


JAB_HIT = 0.35
JAB = [
    (0.00, {}, "lin"),
    (0.22, dict(PICK_UP, torso=(10, 12, 0), head=(-14, -6, 0), body_pos=(0, 0.2, 0)), "soft"),
    (JAB_HIT, dict(PICK_HIT, torso=(40, -8, 0), head=(-30, 4, 0), body_pos=(0, 2.4, 0), lf=(2.4, 22.5, -2.4)), "in"),
    (0.45, dict(PICK_HIT, rh=(-3.2, 1.6, -7.2), torso=(34, -6, 0), head=(-26, 4, 0), body_pos=(0, 1.8, 0)), "out"),
    (0.90, {}, "soft"),
]


def jab(t):
    return L(JAB, t)


PLANT_HITS = (0.45, 0.8)
WALL_H = dict(rh=(-7.0, -5.0, 1.2), r_pole=(-1.0, -0.2, 0.3), wdir=(0.2, -0.95, 0.2), wface=(0.0, -0.2, -0.98))
WALL_X = dict(rh=(-2.4, -2.6, -7.8), r_pole=(-1.0, 0.0, 0.2), wdir=(0.5, -0.1, -0.86), wface=(0.0, 1.0, -0.1))
HOLD_C = dict(lh=(1.6, -2.4, -7.6), l_pole=(0.8, 0.4, 0.4), torso=(14, 0, 0), head=(-12, 0, 0))
PLANT = [
    (0.00, {}, "lin"),
    (0.28, dict(HOLD_C, **WALL_H), "soft"),
    (PLANT_HITS[0], dict(HOLD_C, **WALL_X, torso=(18, -8, 0)), "in"),
    (0.62, dict(HOLD_C, **WALL_H), "soft"),
    (PLANT_HITS[1], dict(HOLD_C, **WALL_X, torso=(18, -8, 0)), "in"),
    (0.95, dict(HOLD_C, **WALL_X, lh=(4.6, -1.0, -5.0), torso=(16, -4, 0)), "out"),
    (1.15, dict(CROUCH, root_pos=(0, -3.0, 7.0), torso=(-4, 0, 0), head=(-6, 0, 0), lh=(6.4, -1.0, 1.0),
                rf=(-2.4, 20.5, 2.0), lf=(2.4, 20.5, 1.0)), "out"),          # отскок назад
    (1.32, dict(CROUCH, root_pos=(0, 0.0, 10.0), torso=(26, 0, 0)), "in"),
    (1.60, dict(root_pos=(0, 0, 10.0)), "soft"),
]


def plant(t):
    return L(PLANT, t)


DIG_HITS = (0.25, 0.55, 0.85)
DIG_UP = dict(PICK_UP, torso=(14, 0, 0), head=(-20, 0, 0), body_pos=(0, 0.8, 0))
DIG_DN = dict(PICK_HIT, rh=(-2.6, 5.4, -6.4), wdir=(0.0, 0.2, -0.98), wface=(0.0, 0.66, 0.75),
              torso=(46, 0, 0), head=(-28, 0, 0), body_pos=(0, 2.6, 0))
DIVE_POSE = dict(DIG_DN, root=(0, 0, 0), torso=(20, 0, 0), head=(-30, 0, 0), lh=(3.6, -9.6, -4.0),
                 l_pole=(0.4, 1.0, 0.0), rh=(-3.6, -9.6, -4.0), r_pole=(-0.4, 1.0, 0.0),
                 wdir=(0.0, -1.0, 0.1), wface=(0.0, 0.1, 1.0))
DIG_DOWN = [
    (0.00, {}, "lin"), (0.12, DIG_UP, "soft"), (DIG_HITS[0], DIG_DN, "in"),
    (0.40, DIG_UP, "soft"), (DIG_HITS[1], DIG_DN, "in"), (0.70, DIG_UP, "soft"), (DIG_HITS[2], DIG_DN, "in"),
    (1.00, dict(DIVE_POSE, root=(40, 0, 0), root_pos=(0, -3.0, -4.0), body_pos=(0, 0, 0)), "out"),     # прыжок
    (1.20, dict(DIVE_POSE, root=(110, 0, 0), root_pos=(0, 12.0, -7.0)), "lin"),                       # «рыбкой»
    (1.40, dict(DIVE_POSE, root=(150, 0, 0), root_pos=(0, 30.0, -7.0)), "in"),
]
DIG_DIVE = 1.0


def dig_down(t):
    return L(DIG_DOWN, t)


EDGE = dict(lh=(6.6, -8.4, -3.0), l_pole=(0.6, 1.0, 0.2), rh=(-6.6, -8.4, -3.0), r_pole=(-0.6, 1.0, 0.2),
            wdir=(-0.3, -0.9, 0.3), wface=(0.0, 0.3, 1.0), torso=(8, 0, 0), head=(-6, 0, 0))
EMERGE = [
    (0.00, dict(EDGE, root_pos=(0, 24.0, 0), body_pos=(0, 0, 0)), "lin"),
    (0.25, dict(EDGE, root_pos=(0, 10.0, 0), head=(-22, 0, 0)), "out"),
    (0.45, dict(CROUCH, root_pos=(0, -1.5, 0), lh=(5.0, 1.0, -2.6), rh=(-5.4, -1.0, -3.4)), "out3"),   # выскочил
    (0.58, dict(CROUCH, body_pos=(0, 3.2, 0)), "soft"),
    (0.70, dict(torso=(22, 0, 14), head=(-16, 12, -10)), "soft"),        # отряхивается
    (0.80, dict(torso=(22, 0, -14), head=(-16, -12, 10)), "soft"),
    (0.90, dict(torso=(22, 0, 8), head=(-18, 6, -6)), "soft"),
    (1.00, {}, "soft"),
]


def emerge(t):
    return L(EMERGE, t)


SMOKE_DROP = 0.3
SMOKE = [
    (0.00, {}, "lin"),
    (0.12, dict(lh=(4.6, 5.6, 0.6), l_pole=(0.6, -0.4, 1.0)), "soft"),
    (SMOKE_DROP, dict(lh=(3.0, 8.4, -4.0), l_pole=(0.8, 0.0, 0.6), torso=(40, 0, 0), head=(-26, 0, 0),
                      body_pos=(0, 2.0, 0)), "in"),
    (0.55, dict(torso=(38, 20, 0), head=(-34, 26, 0), body=(10, 30, 0), lh=(5.6, 1.0, 2.0),
                rf=(-2.2, 22.5, 2.4), lf=(2.4, 21.0, -2.0)), "soft"),
    (0.80, dict(torso=(30, 0, 0), head=(-30, 0, 0), body=(14, 0, 0), wdir=(0.15, -0.45, 0.88),
                wface=(0.0, 0.88, 0.45), rh=(-6.0, 1.0, 0.6)), "soft"),
]


def smoke(t):
    return L(SMOKE, t)


STAGGER = [
    (0.00, {}, "lin"),
    (0.15, dict(torso=(4, 0, 14), head=(-2, 16, 18), body_pos=(0, 0.2, 1.0), rh=(-6.6, 3.0, -1.0),
                wdir=(0.3, -0.7, -0.6), wface=(0, 0.6, -0.7), lh=HELM_L, l_pole=(0.6, 0.6, 0.4)), "out3"),
    (0.45, dict(torso=(26, 0, -12), head=(-14, -14, -14), body_pos=(0, 1.4, 0), rh=(-6.2, 3.6, -1.6),
                wdir=(0.3, -0.7, -0.6), wface=(0, 0.6, -0.7), lh=(3.4, -9.6, -5.0), l_pole=(0.6, 0.6, 0.4)), "soft"),
    (0.70, dict(torso=(22, 0, 8), head=(-18, 10, 8), lh=(5.0, 1.0, -2.0)), "soft"),
    (1.00, {}, "soft"),
]


def stagger(t):
    return L(STAGGER, t)


# видимость предметов в левой руке (для предпросмотра; в игре то же считает SapperModel)
HELD = {"throw": ("held_bomb", 0.12, THROW_RELEASE), "smoke": ("held_bomb", 0.10, SMOKE_DROP),
        "plant": ("held_charge", 0.0, PLANT_HITS[1] + 0.05)}

CLIPS = [
    ("idle", idle, 3.0, 30, True),
    ("sneak", sneak, 1.3, 30, True),
    ("walk", walk, 0.9, 30, True),
    ("run", run, 0.55, 60, True),
    ("tunnel", tunnel, 1.0, 60, True),
    ("hidden", hidden, 3.0, 20, True),
    ("dig_down", dig_down, DIG_DOWN[-1][0], 60, False),
    ("emerge", emerge, EMERGE[-1][0], 60, False),
    ("hide", hide, HIDE[-1][0], 60, False),
    ("throw", throw, THROW[-1][0], 60, False),
    ("plant", plant, PLANT[-1][0], 60, False),
    ("jab", jab, JAB[-1][0], 60, False),
    ("stagger", stagger, STAGGER[-1][0], 60, False),
    ("smoke", smoke, SMOKE[-1][0], 60, False),
]


# =====================================================================
# 5. Запекание, дорожка кирки, проверка, предпросмотр
# =====================================================================
def bake_clip(model, fn, length_s, fps, loop):
    ng.PREV_PN.clear()
    poses = {}
    bones = [b_["name"] for b_ in model.bones]

    def full(t):
        pr, pose = pose_at(fn, t, model, length_s, loop)
        poses[t] = (pr, pose)
        return pose

    anim = bake(full, length_s, fps, loop, bones, unwrap=True)
    if not loop:
        ng.anchor_end(anim)
    track, prev_q = [], None
    for k in range(int(round(length_s * fps)) + 1):
        t = min(k / fps, length_s)
        pr, pose = poses[t]
        q = ng.quat(weapon_R(model, pose, pr))
        if prev_q is not None and sum(q[i] * prev_q[i] for i in range(4)) < 0:
            q = [-v for v in q]
        prev_q = q
        track.append([round(v, 5) for v in q])
    return anim, {"fps": fps, "length": length_s, "loop": loop, "q": track}


def export(model):
    anims, tracks = {}, {}
    for name, fn, ln, fps, loop in CLIPS:
        a, w = bake_clip(model, fn, ln, fps, loop)
        anims["animation.sapper." + name] = a
        tracks[name] = w
        print("  %-10s %.2f с" % (name, ln))
    write_anims(ROOT, "sapper", anims)
    with open(os.path.join(ROOT, "animations", "sapper.weapon.json"), "w", encoding="utf-8") as f:
        json.dump({"weapon": "sapper_pick", "hand": "right_hand",
                   "grip_offset": [round(v, 4) for v in GRIP_OFFSET], "clips": tracks}, f, separators=(",", ":"))


def check(model):
    """Кирка (рукоять и голова) не должна проходить сквозь голову, корпус и мешок."""
    bad = 0
    for name, fn, ln, fps, loop in CLIPS:
        ng.PREV_PN.clear()
        for k in range(int(ln * 30) + 1):
            t = min(k / 30.0, ln)
            pr, pose = pose_at(fn, t, model, ln, loop)
            Rw = weapon_R(model, pose, pr)
            grip = add(model.world("right_hand", [0, 0, 0], pose), mv(Rw, GRIP_OFFSET))
            top = add(grip, mv(Rw, [0, -11.0, 0]))
            butt = add(grip, mv(Rw, [0, 3.0, 0]))
            claw = add(top, mv(Rw, [0, 1.5, -6.0]))
            hc = model.world("head", model.local_of("head", (0, 21.5, -2.6)), pose)
            tc = model.world("torso", model.local_of("torso", (0, 14.0, 0.0)), pose)
            probs = []
            for nm, c_, r_ in (("голова", hc, 4.2), ("корпус", tc, 4.2)):
                for a_, b_ in ((butt, top), (top, claw)):
                    d = ng.seg_dist(c_, a_, b_)
                    if d < r_:
                        probs.append("%s %.1f" % (nm, d))
                        break
            if probs:
                bad += 1
                print("%-10s %.2f с: %s" % (name, t, ", ".join(probs)))
    print("проблемных кадров:", bad)


def _preview_hide(clip, t):
    hide_ = {"held_bomb", "held_charge"}
    h = HELD.get(clip)
    if h and h[1] <= t <= h[2]:
        hide_.discard(h[0])
    return hide_


def main():
    args = sys.argv[1:]
    if args and args[0] == "--preview":
        orig = ng.preview_parts
        ng.preview_parts = lambda mob, weapon, clip, t, hide=(): orig(mob, weapon, clip, t, _preview_hide(clip, t))
        clip = args[1] if len(args) > 1 and args[1] != "-" else None
        times = [float(x) for x in args[2].split(",")] if len(args) > 2 else [0.0]
        ng.preview("sapper", "sapper_pick", clip, times, yaw=float(os.environ.get("YAW", "0.6")),
                   scale=float(os.environ.get("SCALE", "13")), lift=float(os.environ.get("LIFT", "0")),
                   size=(int(os.environ.get("W", "420")), 560))
        return
    ms = build_all()
    M = ms["sapper"]
    M.finalize()
    if args and args[0] == "--check":
        check(M)
        return
    print("кубов:", {k: len(v.cubes()) for k, v in ms.items()})
    export(M)


if __name__ == "__main__":
    main()
