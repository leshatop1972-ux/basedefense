"""
Генератор босса «Набат» и его мира: модели Bedrock, текстуры, анимации и дорожки оружия.

Запуск:  python tools/nabat_gen.py
Проверка без игры:
    python tools/mob_preview.py nabat                       — поза покоя (парение)
    python tools/mob_preview.py nabat toll 0.3,0.9,1.2,1.6
    python tools/nabat_gen.py --preview toll 0.5,1.2        — кадры С ОРУЖИЕМ (молот/меч рисуются по дорожке)
    python tools/anim_check.py nabat
    python tools/nabat_gen.py --check                       — пересечения молота с телом и крыльями

Что создаёт (assets/basedefense):
  geo/nabat, nabat_maul, nabat_blade, nabat_sword, nabat_bell
  textures/entity/<то же>.png + _glowmask.png (+ nabat_p3 — треснувший колокол 3‑й фазы)
  animations/nabat, nabat_blade (.animation.json) и nabat.weapon.json, nabat_blade.weapon.json

Оружие (молот Набата, меч мечника) — ОТДЕЛЬНЫЕ модели. Для каждой анимации запекается дорожка оружия:
поворот оружия в системе модели (кватернион, 60 кадров/с). В игре оружие ставится в точку кисти,
а поворот берётся из дорожки и смешивается между клипами slerp — без «прокручивания» на переходах.
Кисть в анимации повёрнута по оружию (кулак всегда сжимает древко).

Внешность: низ тела — огромный бронзовый колокол с янтарным языком‑сердцем, маска с круглым «колокольным»
глазом, венец из шпилей, двойной нимб с колокольчиками, крылья из бронзовых перьев‑труб, кадила,
звонница на спине, в левой руке на цепи — бирюзовый реликварий улана. Палитра: бронза, прозелень,
слоновая кость, индиго, янтарь.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import (FACE_LIGHT, Model, Painter, add, bake, cross, dot, ease, euler, faces, length, lerp,  # noqa: E402
                    mix, mm, mul, mv, norm, rx_m, ry_m, rz_m, scl, sub, to_euler, tr, write_anims)

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")

# =====================================================================
# Палитра
# =====================================================================
BR_DD = (34, 24, 16)
BR_D = (52, 37, 24)
BR = (98, 70, 42)
BR_M = (122, 88, 52)
BR_L = (154, 112, 64)
BR_HI = (190, 146, 88)
GOLD = (212, 166, 78)
GOLD_D = (148, 108, 50)
GOLD_L = (242, 210, 126)
VERD = (64, 122, 106)
VERD_L = (112, 168, 146)
VERD_D = (40, 84, 74)
IVORY = (230, 220, 196)
IVORY_D = (172, 160, 136)
IVORY_DD = (118, 108, 90)
CLOTH = (46, 36, 64)
CLOTH_D = (26, 20, 38)
CLOTH_L = (74, 60, 96)
IRON = (40, 40, 46)
IRON_L = (74, 74, 82)
STEEL = (152, 156, 168)
STEEL_L = (214, 218, 226)
STEEL_D = (96, 100, 112)
VOID = (8, 6, 12)
AMBER = (255, 184, 72)
AMBER_HI = (255, 240, 190)
AMBER_D = (196, 110, 30)
TEAL = (70, 224, 212)
TEAL_HI = (200, 255, 250)
SOUND = (150, 220, 255)

OCT = 0.84          # «скругление»: к каждому круглому кубу — такой же, повёрнутый на 45°, ×0.84
OY = 10.0           # вся модель поднята на 10 px: в полёте лохмотья и реликварий не уходят под точку сущности

MODEL_SCALE = 1.6   # в игре (NabatEntity.MODEL_SCALE)
UPPER, FORE = 14.0, 13.0
SHOULDER_X = 11.0


def octo(M, bone, cx, cz, y0, h, w, mat, face=None, d=None, inflate=0.0):
    d = d or w
    M.cube(bone, (cx - w / 2, y0, cz - d / 2), (w, h, d), mat, face=face, inflate=inflate)
    w2, d2 = w * OCT, d * OCT
    M.cube(bone, (cx - w2 / 2, y0 + 0.01, cz - d2 / 2), (w2, h - 0.02, d2), mat, face=face, inflate=inflate,
           rot=(0, 45, 0), pivot=(cx, y0, cz))


# =====================================================================
# 1. Модели
# =====================================================================
ROBE_LENS = [15, 19, 13, 21, 16, 20, 14, 18, 17]
ROBE_ANG = [(k + 0.5) / 9 * 360 for k in range(9)]
ROBES = [k for k in range(9) if abs(((ROBE_ANG[k] + 180) % 360) - 180) >= 22]   # спереди просвет — видно язык
PLATES = [("wing", 9, 18), ("wing", 13.5, 22), ("wing", 18, 26), ("wing2", 23, 31), ("wing2", 27, 35),
          ("wing2", 31, 39), ("wing2", 35, 42), ("wing3", 40, 46), ("wing3", 44.5, 47), ("wing3", 49, 42)]
NPLATES = len(PLATES)


def build_nabat():
    M = Model("nabat", 256, 512)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("body", "root", (0, 36 + OY, 0))
    # ---------- колокол ----------
    prof = [(36, 2, 9.5), (34, 2, 11.5), (32, 2, 12.5), (30, 2, 13), (28, 2, 13.5), (26, 2, 14.2),
            (24, 2, 15), (22, 2, 16.2), (20, 2, 17.6), (18, 2, 19.2), (16, 2, 21), (14, 2, 23)]
    for y0, h, w in prof:
        octo(M, "body", 0, 0, y0 + OY, h, w, "bell", face="bell")
    octo(M, "body", 0, 0, 12 + OY, 2, 25, "bell", face="lip")
    octo(M, "body", 0, 0, 11.4 + OY, 0.8, 24.4, "gold", face="liprim")
    octo(M, "body", 0, 0, 29.6 + OY, 1.2, 13.6, "glow", face="inscr")
    octo(M, "body", 0, 0, 19.2 + OY, 1.0, 18.6, "gold", face="band")
    octo(M, "body", 0, 0, 37.6 + OY, 1.0, 10.4, "gold")
    c("body", (-4, 15.5 + OY, -11.15), (1, 9, 0.6), "glow", face="crack")
    c("body", (-3.2, 22 + OY, -9.0), (1, 5, 0.6), "glow", face="crack")
    c("body", (5.0, 13.5 + OY, -11.6), (1, 4, 0.6), "glow", face="crack_p3")      # трещины 3‑й фазы (в обычной
    c("body", (6.4, 24.0 + OY, -8.6), (1, 6, 0.6), "glow", face="crack_p3")       # текстуре — бронза)
    # язык‑сердце
    b("clapper", "body", (0, 30 + OY, 0))
    c("clapper", (-0.8, 8 + OY, -0.8), (1.6, 22, 1.6), "iron")
    octo(M, "clapper", 0, 0, 1.5 + OY, 7, 6.5, "glow", face="heart")
    octo(M, "clapper", 0, 0, 7.6 + OY, 1.4, 7.6, "gold")
    octo(M, "clapper", 0, 0, 0.6 + OY, 1.0, 4.4, "gold")
    # лохмотья: 8 полос вокруг края (каждая — своя кость)
    for k in ROBES:
        a = math.radians(ROBE_ANG[k])
        r = 11.2
        x, z = math.sin(a) * r, -math.cos(a) * r
        nm = "robe%d" % k
        b(nm, "body", (x, 12.2 + OY, z))
        # полоса строится уже повёрнутой вокруг y (кость без поворота: анимации качают её в своей системе)
        c(nm, (x - 3.2, 12.2 + OY - ROBE_LENS[k], z - 0.3), (6.4, ROBE_LENS[k], 0.6), "robe", face="robe",
          rot=(0, -ROBE_ANG[k], 0), pivot=(x, 12.2 + OY, z))
    # ---------- корпус ----------
    b("torso", "body", (0, 38 + OY, 0))
    octo(M, "torso", 0, 0, 38 + OY, 3, 9, "cloth")
    c("torso", (-6, 40.5 + OY, -4.3), (12, 3, 8.6), "ivory", face="abs")
    c("torso", (-8.5, 43 + OY, -5), (17, 9, 10), "bronze", face="chest")
    c("torso", (-6.5, 43.5 + OY, -5.6), (13, 8, 1), "ivory", face="ribs")
    c("torso", (-2, 45.5 + OY, -6.4), (4, 4, 1), "glow", face="core", rot=(0, 0, 45), pivot=(0, 47.5 + OY, -6))
    c("torso", (-9.2, 50.5 + OY, -5.6), (18.4, 1, 11.2), "gold")
    c("torso", (-7.5, 51.5 + OY, -5.5), (15, 3, 11), "bronze", face="collar")
    for sx in (-1, 1):
        c("torso", (sx * 5 - 2, 53 + OY, -6), (4, 5, 1.4), "bronze", face="collarw", rot=(0, 0, -sx * 14),
          pivot=(sx * 5, 53 + OY, -5))
    b("tabard", "torso", (0, 40 + OY, -6.2))
    c("tabard", (-3, 22 + OY, -6.6), (6, 18, 0.6), "tabard", face="tabard")
    for sx in (-1, 1):
        c("torso", (sx * 3.2 - 0.8, 50 + OY, 5), (1.6, 12, 1.6), "bronze")
    for k, w in enumerate((9, 7, 5, 3, 1.4)):
        c("torso", (-w / 2, 62 + OY + k * 1.2, 5.8 - w / 2 + 0.8), (w, 1.2, w), "gold" if k == 0 else "bronze")
    b("back_bell", "torso", (0, 61.5 + OY, 5.8))
    octo(M, "back_bell", 0, 5.8, 57 + OY, 4.5, 4.2, "bell", face="tinybell")
    octo(M, "back_bell", 0, 5.8, 56.4 + OY, 0.7, 4.8, "gold")
    # ---------- голова ----------
    b("head", "torso", (0, 54 + OY, 0))
    c("head", (-5.5, 54.5 + OY, -5.5), (11, 11, 11), "hood", face="hood")
    c("head", (-4.5, 65 + OY, -4.5), (9, 2.5, 9), "hood", face="hoodtop")
    c("head", (-3, 67 + OY, -3.5), (6, 3, 7), "hood", face="hoodtip", rot=(-28, 0, 0), pivot=(0, 67 + OY, 0))
    c("head", (-3.6, 55.4 + OY, -6.2), (7.2, 8.4, 1), "mask", face="mask")
    c("head", (-4.4, 63.4 + OY, -6.5), (8.8, 1.2, 1.6), "gold")
    for x, hgt, rz in ((-4, 5, 22), (-2, 7, 10), (0, 9, 0), (2, 7, -10), (4, 5, -22)):
        c("head", (x - 0.6, 64 + OY, 2.5), (1.2, hgt, 1.2), "gold", face="spire", rot=(-8, 0, rz), pivot=(x, 64 + OY, 3))
        c("head", (x - 0.4, 64 + OY + hgt, 2.7), (0.8, 0.8, 0.8), "glow", face="dot", rot=(-8, 0, rz), pivot=(x, 64 + OY, 3))
    b("halo", "head", (0, 60.5 + OY, 7.5))
    R = 10.0
    for k in range(18):
        a = 2 * math.pi * k / 18
        x, y = math.sin(a) * R, 60.5 + OY + math.cos(a) * R
        c("halo", (x - 1.9, y - 0.45, 7.2), (3.8, 0.9, 0.9), "glow", face="ring", rot=(0, 0, math.degrees(a)),
          pivot=(x, y, 7.6))
        if k % 3 == 0:
            c("halo", (x - 1, y - 3.4, 6.8), (2, 2.4, 2), "bell", face="tinybell")
            c("halo", (x - 0.45, y - 4.1, 7.35), (0.9, 0.7, 0.9), "glow")
    for k in range(12):
        a = 2 * math.pi * (k + 0.5) / 12
        x, y = math.sin(a) * 7.2, 60.5 + OY + math.cos(a) * 7.2
        c("halo", (x - 1.6, y - 0.3, 7.9), (3.2, 0.6, 0.6), "gold", rot=(0, 0, math.degrees(a)), pivot=(x, y, 8.2))
    # ---------- руки ----------
    for side, sx in (("right", -1), ("left", 1)):
        px = sx * SHOULDER_X
        a, f, h = side + "_arm", side + "_fore", side + "_hand"
        sy = 49 + OY
        b(a, "torso", (px, sy, 0))
        c(a, (px - 1.6, sy - UPPER, -1.6), (3.2, UPPER, 3.2), "cloth", face="sleeve")
        octo(M, a, px + sx * 0.6, 0, sy, 2.5, 7, "bell", face="pauldron")
        octo(M, a, px + sx * 0.6, 0, sy - 3, 3, 9, "bell", face="pauldron")
        octo(M, a, px + sx * 0.6, 0, sy - 3.6, 0.8, 9.6, "gold")
        c(a, (px + sx * 0.6 - 0.5, sy - 6.5, -0.5), (1, 3, 1), "glow")
        ey = sy - UPPER
        b(f, a, (px, ey, 0))
        c(f, (px - 1.3, ey - FORE, -1.3), (2.6, FORE, 2.6), "ivory", face="bone")
        octo(M, f, px, 0, ey - 9, 7, 4.2, "bronze", face="bracer")
        octo(M, f, px, 0, ey - FORE - 1, 4, 6, "cloth", face="cuff")
        wy = ey - FORE
        b(h, f, (px, wy, 0))
        c(h, (px - 1.9, wy - 4, -1.7), (3.8, 4, 3.4), "ivory", face="palm")
        for k, dz in enumerate((-1.6, -0.5, 0.6, 1.7)):
            ln = (6, 7, 6.5, 5)[k] * (0.55 if side == "right" else 1.0)    # правая сжимает молот — пальцы короче
            c(h, (px - 0.45 + sx * 0.8, wy - 4 - ln, dz - 0.45), (0.9, ln, 0.9), "ivory", face="claw",
              rot=(0, 0, sx * 6), pivot=(px, wy - 4, dz))
        c(h, (px - 0.45 - sx * 1.6, wy - 7, -2.2), (0.9, 4, 0.9), "ivory", face="claw")
    # реликварий улана — на цепи в левой руке
    lx, lwy = SHOULDER_X, 49 + OY - UPPER - FORE
    b("reliq_chain", "left_hand", (lx, lwy - 4, 0))
    for k in range(4):
        c("reliq_chain", (lx - 0.5, lwy - 7 - k * 3.2, -0.5), (1, 3, 1), "chain", face="chain",
          rot=(0, 45 * (k % 2), 0), pivot=(lx, lwy - 7 - k * 3.2, 0))
    ry = lwy - 17
    b("reliq", "reliq_chain", (lx, ry, 0))
    c("reliq", (lx - 1.5, ry - 1.5, -1.5), (3, 1.5, 3), "gold")
    octo(M, "reliq", lx, 0, ry - 3, 1.6, 7, "gold")
    octo(M, "reliq", lx, 0, ry - 11, 8, 6, "cage", face="cage")
    octo(M, "reliq", lx, 0, ry - 10.3, 6.6, 3.6, "soul", face="soul")
    octo(M, "reliq", lx, 0, ry - 12.6, 1.6, 7, "gold")
    c("reliq", (lx - 0.6, ry - 15.4, -0.6), (1.2, 2.8, 1.2), "gold")
    for k in range(4):
        c("reliq", (lx - 0.4, ry - 7.5, -5.4), (0.8, 0.8, 2.4), "gold", rot=(0, 45 + 90 * k, 0), pivot=(lx, ry - 7, 0))
    # кадила
    for k, x in enumerate((-12.5, 12.5)):
        nm = "censer%d" % k
        z = 2.0
        b(nm, "body", (x, 12.5 + OY, z))
        c(nm, (x - 0.4, 5 + OY, z - 0.4), (0.8, 7.5, 0.8), "chain", face="chain")
        octo(M, nm, x, z, 0.5 + OY, 4.5, 4.4, "cage", face="censer")
        octo(M, nm, x, z, 1.2 + OY, 3, 2.6, "glow", face="ember")
        octo(M, nm, x, z, 4.8 + OY, 0.8, 5, "gold")
        octo(M, nm, x, z, -0.3 + OY, 0.8, 3.2, "gold")
    # ---------- крылья ----------
    wy = 50 + OY
    for side, sx in (("right", -1), ("left", 1)):
        w1, w2, w3 = side + "_wing", side + "_wing2", side + "_wing3"
        b(w1, "torso", (sx * 5, wy, 5.5))
        c(w1, (min(sx * 5, sx * 21), wy - 1.4, 4.6), (16, 3, 2.6), "bronze", face="spar")
        c(w1, (sx * 21 - 2, wy - 2, 4), (4, 4, 3.6), "gold", face="knuckle")
        b(w2, w1, (sx * 21, wy, 5.8))
        c(w2, (min(sx * 21, sx * 37), wy - 1.2, 5.0), (16, 2.4, 2), "bronze", face="spar")
        c(w2, (sx * 37 - 1.6, wy - 1.6, 4.6), (3.2, 3.2, 2.8), "gold", face="knuckle")
        b(w3, w2, (sx * 37, wy, 6.0))
        c(w3, (min(sx * 37, sx * 50), wy - 0.9, 5.3), (13, 1.8, 1.4), "bronze", face="spar")
        c(w3, (sx * 50 - 0.9, wy - 3.5, 5.2), (1.8, 4, 1.6), "gold", face="hook", rot=(0, 0, sx * 30), pivot=(sx * 50, wy, 6))
        segs = {"wing": w1, "wing2": w2, "wing3": w3}
        for k, (seg, dist, ln) in enumerate(PLATES):
            fx = sx * dist
            fb = "%s_p%d" % (side, k)
            b(fb, segs[seg], (fx, wy - 1.4, 6.6))
            wd = 3.6 if k < 3 else 4.2
            z0 = 6.4 + (k % 2) * 0.5
            top = wy - 1.4
            c(fb, (fx - wd / 2, top - ln, z0), (wd, ln, 1.0), "plate", face="plate")
            c(fb, (fx - wd / 2 - 0.35, top - ln - 1.4, z0 - 0.15), (wd + 0.7, 1.4, 1.3), "glow", face="tip")
            c(fb, (fx - wd / 2 - 0.3, top - ln * 0.42, z0 - 0.9), (wd + 0.6, ln * 0.42, 0.8), "covert", face="covert")
            if seg == "wing3":
                c(fb, (fx - 0.3, top - ln - 4.4, z0 + 0.1), (0.6, 3, 0.6), "chain")
                octo(M, fb, fx, z0 + 0.4, top - ln - 7.4, 3, 2.4, "bell", face="tinybell")
    M.pack()
    return M


def build_maul():
    """«Колокол‑молот». Начало координат — середина хвата (центр кулака), древко вдоль +y (вверх)."""
    M = Model("nabat_maul", 128, 256)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("maul", "root", (0, 0, 0))
    c("maul", (-1.4, -18, -1.4), (2.8, 52, 2.8), "bronze", face="shaft")
    for y in (-14, 6, 20, 30):
        octo(M, "maul", 0, 0, y, 1.6, 4.2, "gold")
    for y in (-3.5, -1.0, 1.5):
        octo(M, "maul", 0, 0, y, 1.2, 3.4, "cloth", face="grip")
    octo(M, "maul", 0, 0, -20, 2, 3.6, "gold")
    c("maul", (-0.6, -26, -0.6), (1.2, 6, 1.2), "steel", face="spike")
    head = [(48, 2, 6), (46, 2, 7.8), (44, 2, 8.8), (42, 2, 9.8), (40, 2, 11), (38, 2, 12.4), (36, 2, 14)]
    for y0, h, w in head:
        octo(M, "maul", 0, 0, y0, h, w, "bell", face="warbell")
    octo(M, "maul", 0, 0, 34.6, 1.6, 15.4, "gold", face="warlip")
    octo(M, "maul", 0, 0, 42.4, 1.0, 10.2, "glow", face="inscr")
    octo(M, "maul", 0, 0, 50, 1.4, 4.0, "gold")
    c("maul", (-0.8, 51.4, -0.8), (1.6, 5, 1.6), "steel", face="spike")
    for k in range(8):
        c("maul", (-0.7, 34.9, -8.8), (1.4, 1.0, 3.8), "steel", face="spike", rot=(0, 22.5 + 45 * k, 0), pivot=(0, 35.4, 0))
    octo(M, "maul", 0, 0, 31.4, 3.2, 3.8, "glow", face="heart")
    for sx in (-1, 1):
        for k, (dy, ln, rz) in enumerate(((0, 6, 25), (5, 6, 0), (10, 6, -25))):
            x = sx * (8.6 + (1.3 if k == 1 else 0))
            c("maul", (x - 0.6, 37 + dy - ln / 2, -0.5), (1.2, ln, 1.0), "steel", face="crescent",
              rot=(0, 0, sx * rz), pivot=(x, 37 + dy, 0))
        c("maul", (min(sx * 6.8, sx * 8.6), 42.4, -0.8), (1.8, 1.6, 1.6), "gold")
    for k, (dx, ln) in enumerate(((-5.8, 8), (5.8, 11))):
        nm = "maul_bell%d" % k
        b(nm, "maul", (dx, 35, 0))
        c(nm, (dx - 0.4, 35 - ln, -0.4), (0.8, ln, 0.8), "chain")
        octo(M, nm, dx, 0, 35 - ln - 3.0, 2.8, 2.4, "bell", face="tinybell")
        c(nm, (dx - 0.4, 35 - ln - 3.8, -0.4), (0.8, 0.8, 0.8), "glow")
    M.pack()
    return M


# ---------------- мечник ----------------
BTHIGH, BSHIN = 6.0, 6.0
BUPPER, BFORE = 6.0, 6.0


def build_blade():
    """Мечник «Отзвук»: пустые бронзовые латы (внутри тлеет свет), шлем‑колокол, щит‑колокол на левой руке."""
    M = Model("nabat_blade", 128, 256)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("body", "root", (0, 15, 0))
    c("body", (-4, 13, -2.4), (8, 3, 4.8), "void_glow", face="hollow")
    for k, (x, z, w) in enumerate(((-3.2, -2.9, 3.4), (0.0, -3.0, 3.4), (3.2, -2.9, 3.4), (0, 2.9, 8.4))):
        nm = "tasset%d" % k
        b(nm, "body", (x, 15.6, z))
        c(nm, (x - w / 2, 8.0, z - 0.4), (w, 7.6, 0.8), "bronze", face="tasset")
    for k, x in enumerate((-4.4, 4.4)):
        nm = "tasset_s%d" % k
        b(nm, "body", (x, 15.6, 0))
        c(nm, (x - 0.4, 8.6, -2.6), (0.8, 7.0, 5.2), "bronze", face="tasset")
    c("body", (-4.6, 15.4, -2.9), (9.2, 1.4, 5.8), "gold")
    b("torso", "body", (0, 16.5, 0))
    c("torso", (-4.4, 16.5, -2.6), (8.8, 3.5, 5.2), "void_glow", face="hollow")
    c("torso", (-5, 19.5, -3.2), (10, 8, 6.4), "bronze", face="cuirass")
    c("torso", (-2, 21, -3.8), (4, 5, 0.8), "glow", face="slit_v")
    c("torso", (-5.4, 27.2, -3.4), (10.8, 1.2, 6.8), "gold")
    octo(M, "torso", 0, 0, 27.8, 1.6, 5, "bronze")
    octo(M, "torso", 0, 4.4, 20, 5, 4.4, "bell", face="tinybell")
    c("torso", (-2.6, 25, 3.2), (5.2, 1, 2.4), "gold")
    b("head", "torso", (0, 28.5, 0))
    for y0, h, w in ((36.5, 1.5, 4.5), (35, 1.5, 6.4), (32, 3, 7.2), (29.2, 2.8, 8.2)):
        octo(M, "head", 0, 0, y0, h, w, "bell", face="helm")
    octo(M, "head", 0, 0, 28.8, 0.8, 9.2, "gold")
    c("head", (-0.6, 29.8, -4.5), (1.2, 5.4, 0.8), "glow", face="slit_v")
    c("head", (-2.4, 32.4, -4.45), (4.8, 1.0, 0.8), "glow", face="slit_h")
    c("head", (-0.5, 38, -0.5), (1, 2.5, 1), "gold")
    for side, sx in (("right", -1), ("left", 1)):
        a, f, h = side + "_arm", side + "_fore", side + "_hand"
        px = sx * 6.6
        b(a, "torso", (px, 26, 0))
        octo(M, a, px + sx * 0.6, 0, 25.4, 3, 5.6, "bell", face="pauldron")
        octo(M, a, px + sx * 0.6, 0, 24.8, 0.7, 6.2, "gold")
        c(a, (px - 1.3, 26 - BUPPER, -1.3), (2.6, BUPPER, 2.6), "void_glow", face="hollow")
        b(f, a, (px, 26 - BUPPER, 0))
        octo(M, f, px, 0, 26 - BUPPER - BFORE + 0.5, BFORE - 0.5, 3.4, "bronze", face="bracer")
        b(h, f, (px, 26 - BUPPER - BFORE, 0))
        c(h, (px - 1.6, 26 - BUPPER - BFORE - 3, -1.6), (3.2, 3, 3.2), "bronze", face="gaunt")
    b("buckler", "left_fore", (6.6, 17, -2))
    octo(M, "buckler", 6.6, -2.6, 13.5, 2, 6.8, "bell", face="buckler", d=2.4)
    for y0, w in ((15.5, 5.8), (17.5, 4.4)):
        octo(M, "buckler", 6.6, -2.6, y0, 2, w, "bell", face="buckler", d=2.2)
    c("buckler", (5.9, 14.2, -4.0), (1.4, 3, 0.6), "glow")
    b("cape", "torso", (0, 27.5, 3.4))
    c("cape", (-4, 8, 3.2), (8, 19.5, 0.6), "robe", face="robe")
    for side, sx in (("right", -1), ("left", 1)):
        l, s, ft = side + "_leg", side + "_shin", side + "_foot"
        px = sx * 2.1
        b(l, "body", (px, 13.5, 0))
        c(l, (px - 1.4, 13.5 - BTHIGH, -1.4), (2.8, BTHIGH, 2.8), "void_glow", face="hollow")
        c(l, (px - 1.7, 13.5 - BTHIGH + 1.4, -1.8), (3.4, 4.5, 3.4), "bronze", face="greave")
        b(s, l, (px, 13.5 - BTHIGH, 0))
        octo(M, s, px, 0, 1.6, 5.9, 3.4, "bronze", face="greave")
        octo(M, s, px, 0, 6.5, 1.4, 3.8, "gold")
        b(ft, s, (px, 1.5, 0))
        c(ft, (px - 1.8, 0, -3.0), (3.6, 1.6, 4.8), "bronze", face="sabaton")
    M.pack()
    return M


def build_sword():
    """Двуручник мечника: хват в начале координат, клинок вдоль +y."""
    M = Model("nabat_sword", 64, 64)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("sword", "root", (0, 0, 0))
    c("sword", (-0.6, -3.5, -0.6), (1.2, 6, 1.2), "cloth", face="grip")
    octo(M, "sword", 0, 0, -4.7, 1.2, 2.2, "gold")
    c("sword", (-4, 2.6, -0.9), (8, 1.4, 1.8), "gold", face="guard")
    octo(M, "sword", 0, 0, 2.4, 1.8, 3.0, "bell", face="tinybell")
    c("sword", (-1.3, 4.0, -0.4), (2.6, 24, 0.8), "steel", face="blade")
    c("sword", (-0.5, 5.0, -0.55), (1.0, 20, 1.1), "glow", face="fuller")
    c("sword", (-1.3, 27.6, -0.4), (2.6, 2.2, 0.8), "steel", face="tip", rot=(0, 0, 45), pivot=(0, 28, 0))
    M.pack()
    return M


def build_bell():
    """Колокол‑опора (и он же — малый падающий колокол и «колокольный плен» в другом масштабе)."""
    M = Model("nabat_bell", 128, 256)
    b, c = M.bone, M.cube
    b("root", None, (0, 0, 0))
    b("bell", "root", (0, 0, 0))
    prof = [(22, 2, 7.5), (20, 2, 9.5), (18, 2, 10.5), (16, 2, 11), (14, 2, 11.6), (12, 2, 12.4),
            (10, 2, 13.4), (8, 2, 14.6), (6, 2, 16), (4, 2, 17.4)]
    for y0, h, w in prof:
        octo(M, "bell", 0, 0, y0, h, w, "bell", face="bell")
    octo(M, "bell", 0, 0, 1.6, 2.4, 19, "bell", face="lip")
    octo(M, "bell", 0, 0, 1.0, 0.7, 18.6, "gold", face="liprim")
    octo(M, "bell", 0, 0, 15.6, 1.0, 11.6, "glow", face="inscr")
    octo(M, "bell", 0, 0, 9.0, 0.8, 14.2, "gold", face="band")
    octo(M, "bell", 0, 0, 24, 1.2, 6, "gold")
    # ушко‑корона
    for k in range(2):
        c("bell", (-2.5, 25.2, -0.6), (5, 3.2, 1.2), "gold", face="canon", rot=(0, 90 * k, 0), pivot=(0, 25.2, 0))
    c("bell", (-0.8, 2.4, -0.8), (1.6, 0.1, 1.6), "glow", face="heart")       # отблеск языка снизу
    b("bell_clapper", "bell", (0, 22, 0))
    octo(M, "bell_clapper", 0, 0, 1.6, 4, 4.4, "glow", face="heart")
    c("bell_clapper", (-0.6, 5.6, -0.6), (1.2, 16, 1.2), "iron")
    M.pack()
    return M


def paint(M, seed, phase3=False):
    PT = Painter(M.tex_w, M.tex_h, seed=seed)

    def lit(f, col, k=1.0):
        return mul(col, FACE_LIGHT[f] * k * PT.noise(0.04))

    def px(mat, face, f, i, j, w, h):
        side = f not in ("top", "bottom")
        if mat == "glow":
            if face == "crack_p3":
                if not phase3:
                    return lit(f, BR_M if j % 3 else BR)
                return ("glow", AMBER_HI if j % 3 == 1 else AMBER)
            if face == "heart":
                if not side:
                    return ("glow", AMBER)
                return ("glow", AMBER_HI if 1 < i < w - 2 and 1 < j < h - 2 else AMBER)
            if face == "inscr":
                return ("glow", AMBER) if side and (i % 4 != 3) and ((i * 7 + j) % 5) else lit(f, BR_D)
            if face == "fuller":
                return ("glow", SOUND if 0 < j < h - 1 else mix(SOUND, (255, 255, 255), 0.5))
            if face in ("slit_v", "slit_h"):
                return ("glow", AMBER_HI if (i + j) % 3 else AMBER)
            if face == "crack":
                return ("glow", AMBER_HI if j % 3 == 1 else AMBER)
            if face == "ember":
                return ("glow", AMBER_HI if (i + j) % 2 else AMBER)
            return ("glow", mix(AMBER, (255, 255, 255), 0.4 if (i + j) % 3 == 0 else 0))
        if mat == "soul":
            return ("glow", TEAL_HI if 0 < i < w - 1 and 0 < j < h - 1 and side else TEAL)
        if mat == "void_glow":
            if side and 0 < j < h - 1 and (i + j * 2) % 3:
                return ("glow", mix(AMBER, VOID, 0.55 + 0.35 * ((i + j) % 2)))
            return VOID
        if mat == "bell":
            if f == "bottom":
                return VOID
            n = (i * 13 + j * 7 + PT.rnd.randrange(3)) % 13
            base = VERD if n == 0 else (VERD_L if n == 6 and j > h / 2 else (BR_M if n in (3, 9) else BR))
            if face == "lip" and side:
                base = BR_L if j < h / 2 else BR_M
            if face != "bell" and side and j == 0:
                base = mix(BR_L, GOLD, 0.25)
            if face != "bell" and side and j == h - 1:
                base = BR_D
            if side:
                base = mix(base, BR_HI, 0.22 if i < max(1, w // 3) else 0.0)       # блик сверху‑слева
            if face in ("pauldron", "helm", "buckler", "warbell") and side and j == h - 1:
                base = GOLD_D
            if face == "helm" and f == "top":
                base = BR_L
            if phase3 and face in ("bell", "lip") and side and (i * 5 + j * 11) % 23 == 0:
                return ("glow", AMBER)                                             # треснувший колокол: искры
            return lit(f, base)
        if mat == "bronze":
            if face == "chest" and f == "front":
                if i == 0 or i == w - 1 or j == 0:
                    return lit(f, GOLD)
                return lit(f, BR_M if (i + j) % 6 else VERD)
            if face == "tasset" and side:
                return lit(f, GOLD_D if j == h - 1 else (BR_L if j == 0 else (BR if j % 3 else BR_M)))
            if face == "cuirass" and f == "front":
                if j in (2, 5):
                    return lit(f, GOLD_D)
                return lit(f, BR_L if i in (1, w - 2) else BR)
            if face == "shaft":
                return lit(f, BR_D if (j // 3) % 2 else BR)
            if face in ("greave", "bracer", "gaunt", "sabaton") and side and j == 0:
                return lit(f, GOLD_D)
            return lit(f, BR_L if side and j == 0 else (VERD if (i * 5 + j * 3) % 17 == 0 else BR))
        if mat == "plate":
            if side and (i == 0 or i == w - 1):
                return lit(f, GOLD_D)
            if side and j in (h - 4, h - 7) and 0 < i < w - 1:
                return ("glow", mix(AMBER, BR, 0.3))
            if side and j % 8 == 4 and 0 < i < w - 1:
                return lit(f, BR_D)
            return lit(f, VERD if (i + j * 3) % 13 == 0 else mix(BR_M, BR_D, j / max(1, h) * 0.9))
        if mat == "covert":
            if side and j == h - 1:
                return lit(f, GOLD_D)
            if side and (i == 0 or i == w - 1):
                return lit(f, BR_D)
            return lit(f, VERD if (i * 3 + j * 5) % 11 == 0 else mix(BR_L, BR, j / max(1, h)))
        if mat == "gold":
            return lit(f, GOLD_L if side and j == 0 else (GOLD if (i + j) % 4 else GOLD_D))
        if mat == "steel":
            if face in ("spike", "crescent", "tip", "blade"):
                if side and (i == 0 or i == w - 1):
                    return lit(f, STEEL_L)
                return lit(f, STEEL if (i + j) % 5 else STEEL_D)
            return lit(f, STEEL)
        if mat == "ivory":
            if face == "ribs" and f == "front":
                return lit(f, IVORY if j % 2 == 0 or i in (0, w // 2, w - 1) else VOID)
            if face == "abs" and f == "front":
                return lit(f, IVORY_D if i == w // 2 or j == 1 else IVORY)
            if face == "bone" and side:
                return lit(f, IVORY_D if j % 5 == 0 else IVORY)
            if face == "claw" and j >= h - 2:
                return lit(f, IVORY_DD)
            return lit(f, IVORY if (i * 3 + j) % 9 else IVORY_D)
        if mat == "mask":
            if f == "front":
                cx, cy = (w - 1) / 2, 3.0
                r = math.hypot(i - cx, j - cy)
                if 1.5 < r < 2.7:
                    return ("glow", AMBER)
                if r <= 1.5:
                    return ("glow", AMBER_HI) if r < 0.7 else VOID
                if (i, j) in ((5, 0), (5, 1), (6, 1)):
                    return ("glow", AMBER)
                if j >= h - 2 and i % 2 == 0:
                    return lit(f, IVORY_D)
            return lit(f, IVORY)
        if mat in ("cloth", "hood"):
            if face == "hood" and f == "front":
                return VOID if 1 < i < w - 2 else lit(f, CLOTH_D)
            if face == "cuff" and side and j == h - 1:
                return lit(f, GOLD_D)
            if face == "grip":
                return lit(f, CLOTH_D if j % 2 else CLOTH)
            return lit(f, CLOTH_L if side and j == 0 else (CLOTH_D if (i + j) % 5 == 0 else CLOTH))
        if mat == "tabard":
            if f == "front":
                if 1 <= i <= 4 and 4 <= j <= 9 and (j - 4) >= abs(i - 2.5) * 1.2 - 1.5:
                    return lit(f, GOLD)
                if i in (0, w - 1):
                    return lit(f, GOLD_D)
                if j >= h - 3 and (i + j) % 3 == 0:
                    return None
            return lit(f, CLOTH if (i + j) % 4 else CLOTH_D)
        if mat == "robe":
            cut = (i * 7) % 5 + (2 if i % 3 == 0 else 0)
            if j >= h - 1 - cut:
                return None
            if j > h - 5 and (i * 3 + j) % 5 == 0:
                return None
            if j == 1:
                return lit(f, GOLD_D)
            return lit(f, CLOTH_D if i % 3 == 0 else (CLOTH_L if i % 5 == 1 else CLOTH))
        if mat == "iron":
            return lit(f, IRON)
        if mat == "chain":
            return lit(f, IRON if j % 2 else IRON_L)
        if mat == "cage":
            if side and 0 < i < w - 1 and 0 < j < h - 1 and i != w // 2:
                return None
            return lit(f, GOLD_D if (j == 0 or j == h - 1) else BR_D)
        return (255, 0, 255)

    for cube in M.cubes():
        for fname, rect in faces(cube).items():
            PT.fill(rect, lambda i, j, w, h, f=fname, cu=cube: px(cu["mat"], cu.get("face"), f, i, j, w, h))
    return PT


def save_textures(M, PT, name=None):
    name = name or M.name
    PT.img.save(os.path.join(ROOT, "textures", "entity", name + ".png"))
    PT.glow.save(os.path.join(ROOT, "textures", "entity", name + "_glowmask.png"))


# =====================================================================
# 3. Позы: параметры, IK, оружие, маятники
# =====================================================================
# Кисти (rh/lh) — запястье в системе корпуса (от опоры корпуса, Java: +y вниз, −z вперёд). Плечо: (∓11, −11, 0).
# wdir — куда смотрит древко молота от хвата к колоколу (система корпуса), wroll — поворот вокруг древка.
# Крылья: wr — подъём (°), ws — отвод назад, wb/wb3 — сгиб второго/третьего звена, r2/r3 — подъём звеньев,
# wt — поворот вокруг лонжерона, fb/fs — веер перьев (первое перо и шаг), flex — изгиб перьев (волна).
DEFAULT = dict(
    root_pos=(0, 0, 0), root=(0, 0, 0), body=(0, 0, 0), torso=(0, 0, 0), head=(0, 0, 0), halo=0.0, halo_k=1.0,
    rh=(-15.0, 1.0, -6.0), lh=(12.0, 2.0, -9.0), r_pole=(-0.6, -0.1, 1.0), l_pole=(0.6, -0.2, 1.0),
    wdir=(-0.28, -1.0, -0.18), wroll=0.0, lhand=(0, 0, 0),
    wr=24.0, ws=24.0, wb=12.0, wb3=6.0, r2=10.0, r3=-2.0, wt=0.0, fb=-24.0, fs=3.0, flex=0.0, flexp=0.0,
    robe_drag=0.0, robe_flare=0.0, tabard=-12.0, swing=1.0, reliq_hide=0.0,
)
SHOULDER = {"rh": (-SHOULDER_X, -11.0, 0.0), "lh": (SHOULDER_X, -11.0, 0.0)}
DIRS = ("r_pole", "l_pole", "wdir")
WING_BONES = None          # заполняется после постройки модели


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


def _soft(x):
    # мягкий S‑образный переход с медленным разгоном и мягкой доводкой (без «механики»)
    x = max(0.0, min(1.0, x))
    return x * x * x * (x * (x * 6 - 15) + 10)


EASES = {"lin": lambda x: x, "ease": ease, "soft": _soft, "in": _heavy_in, "out": lambda x: 1 - _heavy_in(1 - x),
         "in3": lambda x: x ** 3, "out3": lambda x: 1 - (1 - x) ** 3}


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
    if name in SHOULDER:
        o = SHOULDER[name]
        va, vb = sub(list(a), list(o)), sub(list(b), list(o))
        return tuple(add(list(o), scl(slerp_dir(va, vb, t), lerp(length(va), length(vb), t))))
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


# Запаздывание частей: голова чуть впереди, руки и молот догоняют корпус, крылья и ткань — сильнее.
LAG = {"head": -0.03, "rh": 0.05, "lh": 0.06, "r_pole": 0.05, "l_pole": 0.06, "wdir": 0.06, "wroll": 0.06,
       "wr": 0.10, "ws": 0.10, "wb": 0.14, "wb3": 0.18, "r2": 0.14, "r3": 0.18, "fb": 0.16, "fs": 0.16,
       "tabard": 0.20, "robe_drag": 0.20, "robe_flare": 0.20}
TAIL = 0.25


def layered(keys, t, base=DEFAULT):
    pr = keyed(keys, t, base)
    for name, lag in LAG.items():
        pr[name] = keyed(keys, max(0.0, t - lag), base)[name]
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
AUX = [False]          # True — вспомогательный расчёт (маятники): не трогать память сгиба


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
    prev = None if AUX[0] else PREV_PN.get(key)
    if prev is not None:
        pp = sub(prev, scl(dn, dot(prev, dn)))
        if length(pp) > 1e-3:
            pp = norm(pp)
            ang = math.acos(max(-1.0, min(1.0, dot(pp, pn))))
            if ang > math.radians(12):
                ax = cross(pp, pn)
                ax = norm(ax) if length(ax) > 1e-6 else dn
                pn = mv(axis_R(ax, math.radians(12)), pp)
    if not AUX[0]:
        PREV_PN[key] = pn
    E = add(S, scl(add(scl(dn, math.cos(al)), scl(pn, math.sin(al))), a))
    T = add(S, scl(dn, dist))
    n = scl(norm(cross(dn, pn)), side_sign)
    model.set_world_rot(pose, upper, frame_X(sub(E, S), n))
    model.set_world_rot(pose, lower, frame_X(sub(T, E), n))


# Хват: кулак сжимает древко, древко идёт вдоль линии костяшек (локальная −z кисти → «вверх» оружия).
GRIP_G = rx_m(math.radians(-90))          # кисть → оружие: v_оружия = G · v_кисти
PALM = (0.0, 2.2, 0.0)                    # центр кулака от запястья (система кисти, Java)
GRIP_OFFSET = mv(GRIP_G, list(PALM))      # то же в системе оружия: начало модели оружия от запястья


def weapon_R(model, pose, pr, torso="torso"):
    """Поворот оружия в системе модели: «вверх» оружия (Java −y) — по wdir, плюс поворот вокруг древка."""
    Rt = model.chain_R(torso, pose)
    up = norm(list(pr["wdir"]))
    if model.name == "nabat_blade":
        # клинок: кратчайший поворот от «вверх‑вперёд» — непрерывно везде, кроме «вниз‑назад» (так меч не держат)
        u0 = norm([0.0, -1.0, -1.0])
        F0 = frame_X(scl(u0, -1), [1.0, 0.0, 0.0])
        ax = cross(u0, up)
        sn, cs = length(ax), dot(u0, up)
        Rm = axis_R(norm(ax), math.atan2(sn, cs)) if sn > 1e-6 else [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        Rl = mm(Rm, F0)
    else:
        # молот: опорная ось — «вправо» корпуса (непрерывно везде, кроме древка строго вбок)
        Rl = frame_X(scl(up, -1), [1.0, 0.0, 0.0])
    if pr.get("wroll"):
        Rl = mm(axis_R(up, math.radians(pr["wroll"])), Rl)
    return mm(Rt, Rl)


def resolve(pr, model, hang=None):
    """Параметры позы → углы костей. hang — направления маятников (цепи, кадила, лохмотья) в системе модели."""
    pose = {"root": {"rot": pr["root"], "pos": pr["root_pos"]},
            "body": {"rot": pr["body"]}, "torso": {"rot": pr["torso"]}, "head": {"rot": pr["head"]},
            "halo": {"rot": (0, 0, pr["halo"]), "scale": (pr["halo_k"],) * 3}}
    Rt = model.chain_R("torso", pose)
    for s, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        out = mv(Rt, [-1.0 if s == "right" else 1.0, 0.2, 0.3])
        limb_ik(model, pose, s + "_arm", s + "_fore", tgt, mv(Rt, list(pr[pole])), UPPER, FORE, 1, out)
    Rw = weapon_R(model, pose, pr)
    model.set_world_rot(pose, "right_hand", mm(Rw, tr(GRIP_G)))
    pose["left_hand"] = {"rot": pr["lhand"]}
    pose["tabard"] = {"rot": (pr["tabard"], 0, 0)}
    # крылья
    for side, sx in (("right", -1), ("left", 1)):
        m = -sx       # правое: +, левое: −
        pose[side + "_wing"] = {"rot": (pr["wt"], -m * pr["ws"], m * pr["wr"])}
        pose[side + "_wing2"] = {"rot": (0, -m * pr["wb"], m * pr["r2"])}
        pose[side + "_wing3"] = {"rot": (0, -m * pr["wb3"], m * pr["r3"])}
        for k in range(NPLATES):
            ang = pr["fb"] + k * pr["fs"] + pr["flex"] * math.sin(pr["flexp"] - 0.35 * k)
            pose["%s_p%d" % (side, k)] = {"rot": (0, -m * 4.0 * (k / NPLATES), m * ang)}
    # маятники
    hang = hang or {}
    for name in [b["name"] for b in model.bones if b["name"] in HANGERS]:
        d = hang.get(name, [0.0, 1.0, 0.0])
        if name.startswith("robe"):
            k = int(name[4:])
            a = math.radians(ROBE_ANG[k])
            outward = [math.sin(a), 0.0, -math.cos(a)]
            d = norm(add(d, scl(outward, 0.12 + pr["robe_flare"] * 0.01)))
            Rs = frame_X(d, [math.cos(a), 0.0, math.sin(a)])
            Rc = euler((0, -ROBE_ANG[k], 0))
            model.set_world_rot(pose, name, mm(Rs, tr(Rc)))
        else:
            model.set_world_rot(pose, name, frame_X(d, [1.0, 0.0, 0.0]))
    if "reliq" in model.by_name:
        Rc = model.chain_R("reliq_chain", pose)
        pose["reliq"] = {"rot": (0, 0, 0)}
        if pr["reliq_hide"] > 0.5:
            pose["reliq_chain"]["scale"] = (0.001, 0.001, 0.001)
    return pose


HANGERS = {"censer0", "censer1", "clapper", "reliq_chain"} | {"robe%d" % k for k in ROBES}
HANG_LOCAL = {}            # точка подвеса каждой кости (Java, в системе родителя) — заполняется после модели
HANG_K = {"clapper": (0.006, 0.004), "censer0": (0.012, 0.007), "censer1": (0.012, 0.007),
          "reliq_chain": (0.010, 0.006)}
ROBE_K = (0.016, 0.008)


def pose_at(fn, t, model, length_s, loop):
    """Поза с маятниками: точка подвеса в t, t−0.06 и t−0.3 → «скорость» → отклонение от вертикали."""
    if model.name == "nabat_blade":
        pr = fn((t % length_s) if loop else t)
        return pr, blade_resolve(pr, model)

    def tt(x):
        if loop:
            return x % length_s
        return max(0.0, x)

    pr = fn(tt(t))
    AUX[0] = True
    base_now = resolve(pr, model)
    p_a = resolve(fn(tt(t - 0.10)), model)
    p_b = resolve(fn(tt(t - 0.40)), model)
    p_c = resolve(fn(tt(t - 0.18)), model)
    AUX[0] = False
    hang = {}
    for name in HANGERS:
        if name not in model.by_name:
            continue
        par = model.parent[name]
        loc = model.rel_pivot(name)
        x0 = model.world(par, loc, base_now)
        xa = model.world(par, loc, p_a)
        xb = model.world(par, loc, p_b)
        x_prev = model.world(par, loc, p_c)
        v1 = scl(sub(x0, x_prev), 1 / 0.18)
        v2 = scl(sub(xa, xb), 1 / 0.30)
        k1, k2 = ROBE_K if name.startswith("robe") else HANG_K[name]
        v1 = sat(v1, 70.0)
        v2 = sat(v2, 70.0)
        d = [0.0, 1.0, 0.0]
        d = add(d, scl(v1, -k1 * pr["swing"]))
        d = add(d, scl(sub(v2, v1), k2 * pr["swing"]))
        if name.startswith("robe"):
            d = add(d, [0.0, 0.0, pr["robe_drag"] * 0.012])
        d = norm(d)
        # не дальше 60° от отвеса (иначе на ударах цепь «перекидывается» через подвес)
        c_ = dot(d, [0.0, 1.0, 0.0])
        lim = math.cos(math.radians(60))
        if c_ < lim:
            h_ = norm([d[0], 0.0, d[2]]) if abs(d[0]) + abs(d[2]) > 1e-6 else [0.0, 0.0, 1.0]
            d = add(scl(h_, math.sin(math.radians(60))), [0.0, lim, 0.0])
        hang[name] = norm(d)
    return pr, resolve(pr, model, hang)


def sat(v, s_):
    """Мягкое ограничение скорости (на ударах маятник не «перелетает» через подвес)."""
    l_ = length(v)
    return scl(v, s_ / (s_ + l_)) if l_ > 1e-9 else v


def canonical(r):
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


def quat(R):
    tr_ = R[0][0] + R[1][1] + R[2][2]
    if tr_ > 0:
        s = math.sqrt(tr_ + 1.0) * 2
        w, x, y, z = 0.25 * s, (R[2][1] - R[1][2]) / s, (R[0][2] - R[2][0]) / s, (R[1][0] - R[0][1]) / s
    elif R[0][0] > R[1][1] and R[0][0] > R[2][2]:
        s = math.sqrt(1.0 + R[0][0] - R[1][1] - R[2][2]) * 2
        w, x, y, z = (R[2][1] - R[1][2]) / s, 0.25 * s, (R[0][1] + R[1][0]) / s, (R[0][2] + R[2][0]) / s
    elif R[1][1] > R[2][2]:
        s = math.sqrt(1.0 + R[1][1] - R[0][0] - R[2][2]) * 2
        w, x, y, z = (R[0][2] - R[2][0]) / s, (R[0][1] + R[1][0]) / s, 0.25 * s, (R[1][2] + R[2][1]) / s
    else:
        s = math.sqrt(1.0 + R[2][2] - R[0][0] - R[1][1]) * 2
        w, x, y, z = (R[1][0] - R[0][1]) / s, (R[0][2] + R[2][0]) / s, (R[1][2] + R[2][1]) / s, 0.25 * s
    return [x, y, z, w]


# =====================================================================
# 4. Позы и анимации Набата
# =====================================================================
def flap(phi, wr0=26.0, wra=20.0, ws0=6.0, wsa=8.0, wb0=12.0, wba=10.0, r20=10.0, r2a=12.0, r30=-2.0, r3a=12.0,
         fb0=-6.0, fba=6.0, fs0=5.0, fsa=0.9, flex=7.0, wta=6.0):
    """Взмах крыла по фазе phi: 0 — крылья вверху, π — внизу. Звенья и перья — с отставанием (волна по крылу)."""
    return dict(wr=wr0 + wra * math.cos(phi), ws=ws0 + wsa * math.sin(phi),
                wb=wb0 + wba * math.sin(phi + 0.5), wb3=6.0 + 8.0 * math.sin(phi + 0.9),
                r2=r20 + r2a * math.cos(phi - 0.55), r3=r30 + r3a * math.cos(phi - 1.1),
                wt=wta * math.sin(phi), fb=fb0 + fba * math.sin(phi - 1.0), fs=fs0 + fsa * math.cos(phi),
                flex=flex, flexp=phi - 1.3)


# Парение: молот у правого бока древком вверх, реликварий на цепи впереди слева
HOVER = dict(body=(6, 0, 0), torso=(4, 0, 0), head=(-12, 0, 0),
             rh=(-17.0, 1.0, -9.5), r_pole=(-0.5, -0.2, 1.0), wdir=(-0.34, -1.0, -0.30),
             lh=(12.5, 2.5, -9.5), l_pole=(0.7, -0.2, 1.0), lhand=(-20, 0, 0), tabard=-14.0)
HOVER_T = 2.6


def hover(t):
    phi = 2 * math.pi * t / HOVER_T
    pr = P(HOVER, **flap(phi))
    pr["root_pos"] = (0, -2.2 * math.sin(phi - 0.4), 0)
    pr["body"] = (6 + 2.0 * math.sin(phi - 0.9), 0, 0)
    pr["torso"] = (4 + 2.5 * math.sin(phi - 1.2), 0, 0)
    pr["head"] = (-12 + 3.0 * math.sin(phi - 1.5), 4.0 * math.sin(phi * 0.5), 0)
    pr["rh"] = (-17.0, 1.0 + 0.8 * math.sin(phi - 1.3), -9.5)
    pr["lh"] = (12.5, 2.5 + 0.8 * math.sin(phi - 1.5), -9.5)
    pr["halo"] = 20.0 * t / HOVER_T
    pr["robe_flare"] = 2.0 * math.sin(phi - 1.6)
    return pr


FLY_T = 1.5


def fly(t):
    phi = 2 * math.pi * t / FLY_T
    pr = P(HOVER, **flap(phi, wr0=20.0, wra=30.0, ws0=34.0, wsa=10.0, wba=14.0, r2a=16.0, r3a=16.0, flex=10.0))
    pr["root_pos"] = (0, -2.8 * math.sin(phi - 0.4), 0)
    pr["root"] = (26 + 3.0 * math.sin(phi - 0.8), 0, 0)
    pr["body"] = (4, 0, 0)
    pr["torso"] = (2, 0, 0)
    pr["head"] = (-26, 0, 0)
    pr["rh"] = (-15.5, 3.0, 2.0)
    pr["r_pole"] = (-0.4, -0.6, 1.0)
    pr["wdir"] = (-0.25, -0.6, 1.0)              # молот уходит назад вдоль тела
    pr["lh"] = (12.0, 6.0, -4.0)
    pr["robe_drag"] = 28.0
    pr["tabard"] = -40.0
    pr["halo"] = 20.0 * t / FLY_T
    return pr


GLIDE_T = 3.2


def glide(t):
    """Скольжение на быстром пролёте: крылья распахнуты и почти не машут, корпус лёг вперёд,
    раз за цикл — один мягкий взмах (волна идёт по крылу к кончику)."""
    phi = 2 * math.pi * t / GLIDE_T
    beat = math.sin(phi) * (0.5 + 0.5 * math.cos(phi - 1.2))      # несимметричный: один взмах за цикл
    pr = P(HOVER, **flap(phi, wr0=10.0, wra=7.0, ws0=26.0, wsa=4.0, wb0=6.0, wba=5.0, r20=4.0, r2a=6.0,
                         r30=-4.0, r3a=7.0, fba=3.0, fsa=0.5, flex=4.0, wta=2.5))
    pr["wr"] = 10.0 + 9.0 * beat
    pr["root_pos"] = (0, -1.4 * math.sin(phi - 0.6), 0)
    pr["root"] = (32 + 2.0 * math.sin(phi - 0.9), 0, 2.5 * math.sin(phi * 0.5 * 2))
    pr["body"] = (3, 0, 0)
    pr["torso"] = (1, 0, 0)
    pr["head"] = (-31, 3.0 * math.sin(phi), 0)
    pr["rh"] = (-15.5, 3.4, 2.6)
    pr["r_pole"] = (-0.4, -0.6, 1.0)
    pr["wdir"] = (-0.22, -0.5, 1.0)
    pr["lh"] = (12.0, 6.5, -3.0)
    pr["robe_drag"] = 40.0
    pr["tabard"] = -55.0
    pr["halo"] = 20.0 * t / GLIDE_T
    return pr


# На земле: колокол стоит краем на земле, передний край приподнят (видно язык‑сердце), крылья сложены и опущены
GROUND = dict(root_pos=(0, 12.0, 0), body=(-20, 0, 0), torso=(14, 0, 0), head=(18, 0, 0),
              rh=(-16.0, 4.0, -10.0), r_pole=(-0.6, -0.3, 1.0), wdir=(-0.15, -1.0, -0.35),
              lh=(11.0, 8.0, -9.0), l_pole=(0.6, -0.4, 1.0), lhand=(-30, 0, 0),
              wr=-42.0, ws=-58.0, wb=18.0, wb3=12.0, r2=-22.0, r3=-14.0, wt=10.0, fb=-8.0, fs=2.4, flex=0.0,
              tabard=10.0, halo_k=0.85, robe_flare=160.0)
GROUND_T = 3.0


def ground_idle(t):
    phi = 2 * math.pi * t / GROUND_T
    pr = P(GROUND)
    pr["torso"] = (22 + 2.5 * math.sin(phi), 0, 0)
    pr["head"] = (14 + 3.0 * math.sin(phi - 0.6), 5.0 * math.sin(phi * 0.5), 0)
    pr["wr"] = -30.0 + 3.0 * math.sin(phi - 0.4)
    pr["r2"] = -22.0 + 2.0 * math.sin(phi - 0.8)
    pr["lh"] = (11.0, 8.0 + 0.6 * math.sin(phi - 0.5), -9.0)
    pr["halo"] = 20.0 * t / GROUND_T
    return pr



def mixp(a, b, k):
    """Смешать две позы (k: 0 — a, 1 — b) с теми же правилами интерполяции, что и ключи."""
    return {n: lerp_key(n, a[n], b[n], k) for n in a}


def fade_to(fn_pose, t, t0, t1, target_fn):
    """Последний отрезок клипа: плавно перейти в позу цикла (target_fn) — стык без рывка."""
    if t <= t0:
        return fn_pose
    k = _soft((t - t0) / (t1 - t0))
    return mixp(fn_pose, target_fn(t), k)


def wings_of(phi, **kw):
    return flap(phi, **kw)


# --- воздушные действия (крылья в них не запекаются — в игре они продолжают махать циклом парения) ---
RAISE = dict(torso=(-10, 0, 0), head=(-26, 0, 0), rh=(-7.0, -35.0, -3.0), r_pole=(-1.0, 0.1, 0.4),
             wdir=(0.08, -1.0, 0.25), lh=(13.0, -2.0, -8.0))
STRIKE = dict(torso=(18, 0, 0), body=(10, 0, 0), head=(6, 0, 0), rh=(-6.0, -7.0, -19.0), r_pole=(-1.0, 0.3, 0.2),
              wdir=(0.05, 0.45, -1.0), lh=(14.0, 4.0, -4.0))
TOLL_HIT = 1.0
TOLL = [
    (0.00, HOVER, "lin"),
    (0.75, RAISE, "soft"),
    (0.90, dict(RAISE, rh=(-7.5, -35.5, -1.5), wdir=(0.1, -1.0, 0.45)), "out"),
    (TOLL_HIT, STRIKE, "in"),
    (1.20, dict(STRIKE, wdir=(0.05, 0.55, -1.0), torso=(20, 0, 0)), "out"),
    (1.70, dict(STRIKE, torso=(12, 0, 0), wdir=(0.0, 0.25, -1.0)), "soft"),
    (2.40, HOVER, "soft"),
]


def toll(t):
    pr = layered(TOLL, t, P(HOVER))
    k = max(0.0, 1.0 - abs(t - TOLL_HIT) / 0.5)
    pr["halo_k"] = 1.0 + 0.25 * k
    pr["halo"] = 40.0 * t
    return pr


SPREAD = dict(torso=(-6, 0, 0), head=(-20, 0, 0), rh=(-24.0, -10.0, -13.0), r_pole=(-0.3, -1.0, 0.4),
              wdir=(-0.7, -0.5, -0.6), lh=(26.0, -8.0, -5.0), l_pole=(0.3, -1.0, 0.4), lhand=(0, 0, -40))
CENSER_HIT = 1.3
CENSER = [
    (0.00, HOVER, "lin"),
    (0.55, dict(SPREAD, root=(0, 0, -10), torso=(2, 0, 0)), "soft"),
    (1.05, dict(SPREAD, root=(0, 0, 12), torso=(-4, 0, 0)), "soft"),
    (CENSER_HIT, dict(SPREAD, root=(0, 0, 0), torso=(-12, 0, 0), head=(-30, 0, 0)), "out"),
    (1.85, dict(SPREAD, root=(0, 0, -5)), "soft"),
    (2.60, HOVER, "soft"),
]


def censer(t):
    pr = layered(CENSER, t, P(HOVER))
    pr["swing"] = 1.6
    pr["halo"] = 30.0 * t
    return pr


GATHER_HITS = (1.6, 2.35, 3.1)
GATHER = [
    (0.00, HOVER, "lin"),
    (1.00, dict(RAISE, torso=(-16, 0, 0), head=(-34, 0, 0), lh=(20.0, -20.0, -6.0), l_pole=(0.4, -1, 0.3)), "soft"),
    (1.30, dict(RAISE, rh=(-7.5, -36.0, -1.0), wdir=(0.1, -1.0, 0.5)), "out"),
    (GATHER_HITS[0], STRIKE, "in"),
    (1.95, dict(RAISE, rh=(-8.0, -32.0, -4.0)), "soft"),
    (GATHER_HITS[1], STRIKE, "in"),
    (2.70, dict(RAISE, rh=(-8.0, -33.0, -3.0), wdir=(0.1, -1.0, 0.45)), "soft"),
    (GATHER_HITS[2], dict(STRIKE, torso=(24, 0, 0), body=(14, 0, 0)), "in"),
    (3.40, dict(STRIKE, torso=(22, 0, 0), wdir=(0.0, 0.6, -1.0)), "out"),
    (4.20, HOVER, "soft"),
]


def gather(t):
    pr = layered(GATHER, t, P(HOVER))
    k = 0.0
    for h in GATHER_HITS:
        k = max(k, 1.0 - abs(t - h) / 0.45)
    pr["halo_k"] = 1.0 + 0.18 * k + 0.2 * smooth01(0.8, 3.1, t) * (1 - smooth01(3.3, 4.0, t))
    pr["halo"] = 25.0 * t + 120.0 * smooth01(0.5, 3.4, t)
    return pr


BELLS_DROP = 1.0
BELLS = [
    (0.00, HOVER, "lin"),
    (0.70, dict(HOVER, torso=(-8, 0, 0), head=(-18, 0, 0), lh=(16.0, -30.0, -8.0), l_pole=(0.6, -0.2, 0.6),
                lhand=(0, 0, -30), rh=(-17.0, -6.0, -11.0)), "soft"),
    (BELLS_DROP, dict(HOVER, torso=(16, 0, 0), head=(8, 0, 0), lh=(14.0, 10.0, -18.0), l_pole=(0.8, -0.3, 0.4),
                      lhand=(-40, 0, 0), rh=(-16.0, 2.0, -8.0)), "in"),
    (1.40, dict(HOVER, torso=(10, 0, 0), lh=(13.0, 6.0, -14.0)), "out"),
    (2.20, HOVER, "soft"),
]


def bells(t):
    pr = layered(BELLS, t, P(HOVER))
    pr["halo"] = 20.0 * t
    return pr


RAIN_DROP = 0.9
RAIN = [
    (0.00, HOVER, "lin"),
    (0.45, dict(RAISE, wdir=(-0.5, -1.0, -0.15), rh=(-10.0, -33.0, -6.0)), "soft"),
    (0.70, dict(RAISE, wdir=(0.5, -1.0, 0.1), rh=(-6.0, -34.0, -4.0)), "ease"),
    (RAIN_DROP, dict(RAISE, wdir=(-0.45, -1.0, 0.0), rh=(-10.0, -33.0, -4.0)), "ease"),
    (1.10, dict(RAISE, wdir=(0.35, -1.0, 0.05), rh=(-7.0, -34.0, -4.0)), "ease"),
    (1.35, dict(RAISE, wdir=(-0.1, -1.0, 0.2)), "ease"),
    (2.00, HOVER, "soft"),
]


def rain(t):
    pr = layered(RAIN, t, P(HOVER))
    pr["halo"] = 20.0 * t
    return pr


POINT = dict(torso=(14, 0, 0), body=(6, 0, 0), head=(4, 0, 0), rh=(-13.0, -6.0, -21.0), r_pole=(-1.0, 0.0, 0.3),
             wdir=(-0.3, 0.6, -1.0), lh=(14.0, 4.0, -6.0))
MARK_AT = 0.8
MARK = [
    (0.00, HOVER, "lin"),
    (0.55, dict(POINT, wdir=(-0.25, 0.2, -1.0), rh=(-12.0, -14.0, -19.0)), "soft"),
    (MARK_AT, POINT, "in"),
    (1.20, dict(POINT, torso=(10, 0, 0)), "out"),
    (1.80, HOVER, "soft"),
]


def mark(t):
    pr = layered(MARK, t, P(HOVER))
    k = max(0.0, 1.0 - abs(t - MARK_AT) / 0.4)
    pr["halo_k"] = 1.0 + 0.2 * k
    return pr


EGGS_DROP = 1.0
EGGS = [
    (0.00, HOVER, "lin"),
    (0.55, dict(HOVER, root=(-14, 0, 0), torso=(-6, 0, 0), head=(-20, 0, 0), rh=(-17.0, -2.0, -11.0)), "soft"),
    (EGGS_DROP, dict(HOVER, root=(16, 0, 0), torso=(18, 0, 0), head=(10, 0, 0), lh=(12.0, 6.0, -14.0)), "in"),
    (1.40, dict(HOVER, root=(6, 0, 0), torso=(10, 0, 0)), "out"),
    (2.00, HOVER, "soft"),
]


def eggs(t):
    pr = layered(EGGS, t, P(HOVER))
    pr["swing"] = 1.8
    return pr


PHANTOMS_AT = 1.2
WIDE = dict(torso=(-14, 0, 0), head=(-36, 0, 0), rh=(-28.0, -24.0, -3.0), r_pole=(-0.2, -1.0, 0.5),
            wdir=(-0.6, -1.0, -0.2), lh=(28.0, -24.0, -3.0), l_pole=(0.2, -1.0, 0.5), lhand=(0, 0, -50))
PHANTOMS = [
    (0.00, HOVER, "lin"),
    (0.80, dict(HOVER, torso=(14, 0, 0), head=(10, 0, 0), rh=(-12.0, 6.0, -10.0), lh=(10.0, 6.0, -10.0)), "soft"),
    (PHANTOMS_AT, WIDE, "in"),
    (1.70, dict(WIDE, torso=(-10, 0, 0)), "out"),
    (2.40, HOVER, "soft"),
]


def phantoms(t):
    pr = layered(PHANTOMS, t, P(HOVER))
    k = max(0.0, 1.0 - abs(t - PHANTOMS_AT) / 0.6)
    pr["halo_k"] = 1.0 + 0.3 * k
    pr["halo"] = 20.0 * t + 90.0 * smooth01(0.8, 1.6, t)
    return pr


RELIQ_THROW = 2.2
RELIQ = [
    (0.00, HOVER, "lin"),
    (0.80, dict(HOVER, torso=(-4, 0, 0), head=(-14, 0, 0), lh=(6.0, -28.0, -14.0), l_pole=(0.8, 0.2, 0.4),
                lhand=(0, 0, -20)), "soft"),
    (1.80, dict(HOVER, torso=(-10, 0, 0), head=(-22, 0, 0), lh=(7.0, -31.0, -12.0), l_pole=(0.8, 0.2, 0.4),
                lhand=(0, 0, -20)), "soft"),
    (RELIQ_THROW, dict(HOVER, torso=(22, 0, 0), body=(10, 0, 0), head=(14, 0, 0), lh=(9.0, 8.0, -22.0),
                       l_pole=(0.9, -0.3, 0.2), lhand=(-50, 0, 0)), "in"),
    (2.70, dict(HOVER, torso=(14, 0, 0), lh=(11.0, 5.0, -15.0)), "out"),
    (4.00, HOVER, "soft"),
]


def reliquary(t):
    pr = layered(RELIQ, t, P(HOVER))
    pr["reliq_hide"] = 1.0 if t >= RELIQ_THROW - 0.05 else 0.0
    pr["swing"] = 1.5
    return pr


CRACK_AT = 1.2
CRACK = [
    (0.00, HOVER, "lin"),
    (0.50, dict(HOVER, torso=(18, 0, 0), head=(16, 0, 0), rh=(-12.0, 4.0, -10.0), lh=(10.0, 4.0, -10.0)), "soft"),
    (CRACK_AT, dict(WIDE, torso=(-22, 0, 0), head=(-42, 0, 0)), "in"),
    (1.60, dict(WIDE, torso=(-18, 0, 0), root=(0, 0, 6)), "out"),
    (1.90, dict(WIDE, torso=(-16, 0, 0), root=(0, 0, -5)), "ease"),
    (2.60, HOVER, "soft"),
]


def crack(t):
    pr = layered(CRACK, t, P(HOVER))
    k = max(0.0, 1.0 - abs(t - CRACK_AT) / 0.7)
    pr["halo_k"] = 1.0 + 0.25 * k
    jit = 3.0 * k * math.sin(t * 61.0)
    r = pr["root"]
    pr["root"] = (r[0] + jit * 0.5, r[1], r[2] + jit)
    return pr


# --- полные клипы (с крыльями) ---
INTRO_T = 5.0
COCOON = dict(torso=(16, 0, 0), head=(26, 0, 0), rh=(-17.0, 6.0, -17.0), r_pole=(-1.0, 0.0, 0.3),
              wdir=(-0.3, -1.0, -0.55), lh=(5.0, 4.0, -12.0), l_pole=(1.0, 0.0, 0.0), lhand=(-30, 0, 0),
              wr=-50.0, ws=40.0, wb=40.0, wb3=30.0, r2=-10.0, r3=-10.0, wt=20.0, fb=-6.0, fs=1.5, flex=0.0,
              halo_k=0.5, robe_drag=-10.0)
BURST = dict(torso=(-18, 0, 0), head=(-34, 0, 0), rh=(-23.0, -18.0, -16.0), r_pole=(-0.2, -1.0, 0.5),
             wdir=(-0.4, -1.0, -0.65), lh=(28.0, -18.0, -2.0), l_pole=(0.2, -1.0, 0.5), lhand=(0, 0, -40),
             wr=62.0, ws=-6.0, wb=-6.0, wb3=-4.0, r2=22.0, r3=14.0, wt=-10.0, fb=4.0, fs=7.0, flex=4.0, halo_k=1.45)
INTRO = [
    (0.00, COCOON, "lin"),
    (1.90, dict(COCOON, torso=(10, 0, 0), head=(18, 0, 0)), "soft"),
    (2.45, BURST, "out3"),
    (3.00, dict(BURST, halo_k=1.15, torso=(-10, 0, 0)), "soft"),
]


def intro(t):
    pr = layered(INTRO, t, P(HOVER))
    pr["root"] = (0, -30.0 * (1 - _soft(t / 2.2)), 0)
    pr["halo"] = 60.0 * t
    return fade_to(pr, t, 3.0, INTRO_T, hover)


DIVE_FALL, DIVE_HIT, DIVE_T = 0.9, 1.4, 3.2
REAR = dict(body=(-14, 0, 0), torso=(-10, 0, 0), head=(-6, 0, 0), rh=(-8.0, -34.0, 2.0), r_pole=(-1.0, 0.1, 0.5),
            wdir=(0.05, -1.0, 0.5), lh=(16.0, -6.0, -4.0),
            wr=60.0, ws=-8.0, wb=0.0, wb3=-6.0, r2=20.0, r3=10.0, fb=2.0, fs=6.0, flex=5.0)
PLUNGE = dict(body=(0, 0, 0), torso=(6, 0, 0), head=(18, 0, 0), rh=(-12.0, -6.0, -14.0), r_pole=(-1.0, 0.0, 0.4),
              wdir=(0.0, 0.85, -0.5), lh=(12.0, -2.0, -8.0),
              wr=72.0, ws=-40.0, wb=30.0, wb3=20.0, r2=10.0, r3=6.0, fb=-14.0, fs=1.0, flex=0.0, robe_drag=-20.0)
IMPACT = dict(root_pos=(0, 3.0, 0), body=(8, 0, 0), torso=(26, 0, 0), head=(20, 0, 0), rh=(-10.0, 4.0, -16.0),
              wdir=(0.0, 1.0, -0.35), lh=(16.0, 6.0, -8.0),
              wr=8.0, ws=26.0, wb=-10.0, wb3=-8.0, r2=-6.0, r3=-10.0, fb=-2.0, fs=6.5, flex=0.0, robe_flare=60.0)
DIVE = [
    (0.00, HOVER, "lin"),
    (0.70, REAR, "soft"),
    (DIVE_FALL, dict(REAR, wr=66.0, ws=-20.0, body=(-6, 0, 0)), "out"),
    (DIVE_HIT - 0.12, PLUNGE, "in"),
    (DIVE_HIT, IMPACT, "out3"),
    (1.90, dict(IMPACT, torso=(20, 0, 0), wr=20.0), "soft"),
]


def dive(t):
    pr = layered(DIVE, t, P(HOVER))
    if t > 1.9:
        phi = 2 * math.pi * (t - 1.9) / 1.0 + math.pi * 0.5
        pr = mixp(pr, dict(pr, **flap(phi, wr0=30.0, wra=30.0, flex=10.0)), _soft((t - 1.9) / 0.35))
    return fade_to(pr, t, 2.3, DIVE_T, hover)


REAP_IN, REAP_OUT, REAP_T = 0.8, 2.4, 3.4
BLADES = dict(root=(22, 0, 0), body=(2, 0, 0), torso=(4, 0, 0), head=(-20, 0, 0),
              rh=(-13.0, 2.0, -14.0), r_pole=(-0.8, -0.3, 0.6), wdir=(-0.2, 0.3, -1.0), lh=(12.0, 6.0, -2.0),
              wr=8.0, ws=34.0, wb=-12.0, wb3=-10.0, r2=4.0, r3=2.0, wt=-16.0, fb=18.0, fs=4.0, flex=0.0,
              robe_drag=40.0, tabard=-45.0)
REAP = [
    (0.00, HOVER, "lin"),
    (REAP_IN, BLADES, "soft"),
    (REAP_OUT, dict(BLADES, wr=12.0, ws=30.0, root=(18, 0, 0)), "lin"),
]


def reap(t):
    pr = layered(REAP, t, P(HOVER))
    if REAP_IN <= t <= REAP_OUT:
        pr["flex"] = 2.5
        pr["flexp"] = t * 30.0
    if t > REAP_OUT:
        phi = 2 * math.pi * (t - REAP_OUT) / 1.0
        pr = mixp(pr, P(HOVER, **flap(phi, wr0=30, wra=28, flex=10)), _soft((t - REAP_OUT) / 0.35))
    return fade_to(pr, t, REAP_OUT + 0.4, REAP_T, hover)


QUAKE_LAND, QUAKE_HITS, QUAKE_RISE, QUAKE_T = 1.0, (1.5, 2.7, 3.9), 4.4, 5.3
SET = dict(GROUND, torso=(4, 0, 0), head=(4, 0, 0), body=(-6, 0, 0), wr=10.0, ws=-20.0, wb=10.0, r2=6.0, r3=0.0,
           fb=-4.0, fs=4.0, rh=(-14.0, -4.0, -12.0), wdir=(0.0, -1.0, -0.4))
HOP = dict(SET, root_pos=(0, 4.0, 0), torso=(-8, 0, 0), head=(-14, 0, 0), wr=48.0, ws=-14.0, fs=6.0,
           rh=(-8.0, -30.0, -4.0), wdir=(0.05, -1.0, 0.4), robe_flare=40.0)
SLAM = dict(SET, root_pos=(0, 12.0, 0), torso=(22, 0, 0), head=(14, 0, 0), wr=-6.0, ws=10.0, fs=6.5,
            rh=(-6.0, -2.0, -20.0), wdir=(0.0, 0.75, -1.0), robe_flare=170.0)


def _quake_keys():
    ks = [(0.00, HOVER, "lin"), (0.75, dict(HOVER, wr=50.0, ws=-10.0, root_pos=(0, 0, 0)), "soft"),
          (QUAKE_LAND, dict(SET, root_pos=(0, 12.0, 0)), "out")]
    for i, h in enumerate(QUAKE_HITS):
        big = i == len(QUAKE_HITS) - 1
        up = -10.0 if big else -4.0
        ks.append((h - 0.32, dict(HOP, root_pos=(0, up, 0), wr=60.0 if big else 48.0), "out"))
        ks.append((h, SLAM, "in3"))
        ks.append((h + 0.25, dict(SLAM, torso=(18, 0, 0)), "out"))
    ks.append((QUAKE_RISE, dict(SET, root_pos=(0, 10.0, 0), wr=40.0), "soft"))
    return ks


QUAKE = _quake_keys()


def quake(t):
    pr = layered(QUAKE, t, P(HOVER))
    if t > QUAKE_RISE:
        phi = 2 * math.pi * (t - QUAKE_RISE) / 0.9 - math.pi * 0.4
        pr = mixp(pr, dict(pr, **flap(phi, wr0=30, wra=30, flex=10)), _soft((t - QUAKE_RISE) / 0.35))
    return fade_to(pr, t, QUAKE_RISE + 0.2, QUAKE_T, hover)


CRASH_FALL, CRASH_HIT, CRASH_T = 0.5, 1.1, 2.0
STAGGER = dict(HOVER, root=(0, 0, 14), torso=(-16, 18, 0), head=(-40, -20, 0), rh=(-22.0, -8.0, -10.0),
               wdir=(-0.8, -0.6, -0.5), lh=(22.0, -16.0, 0.0), l_pole=(0.2, -1, 0.5),
               wr=70.0, ws=-30.0, wb=-20.0, wb3=-20.0, r2=30.0, r3=20.0, fb=10.0, fs=8.0, flex=10.0, flexp=0.0)
FALLING = dict(STAGGER, root=(0, 0, -8), torso=(-10, -10, 0), head=(-30, 10, 0), wr=80.0, ws=-46.0, robe_drag=-40.0)
CRASH = [
    (0.00, HOVER, "lin"),
    (0.30, STAGGER, "out"),
    (CRASH_FALL, dict(STAGGER, root=(0, 0, -6)), "ease"),
    (CRASH_HIT, FALLING, "in"),
    (CRASH_HIT + 0.12, dict(GROUND, root_pos=(0, 14.0, 0), torso=(34, 0, 0), head=(30, 0, 0), wr=-10.0, ws=-30.0,
                            robe_flare=180.0), "out3"),
    (CRASH_T, GROUND, "soft"),
]


def crash(t):
    pr = layered(CRASH, t, P(HOVER))
    if t < CRASH_HIT:
        pr["flexp"] = t * 25.0
    return pr


REQ_HITS, REQ_T = (0.9, 1.7, 2.5), 3.0
RING_L = dict(GROUND, torso=(-6, 0, 0), head=(-20, 0, 0), rh=(-10.0, -30.0, -8.0), r_pole=(-1.0, 0.2, 0.4),
              wdir=(-0.45, -1.0, 0.0), lh=(14.0, -10.0, -10.0), l_pole=(0.8, -0.3, 0.4), lhand=(0, 0, -30))
RING_R = dict(RING_L, wdir=(0.35, -1.0, 0.0), rh=(-7.0, -31.0, -8.0))


def _req_keys():
    ks = [(0.00, GROUND, "lin"), (0.55, dict(RING_L, wdir=(-0.1, -1.0, 0.1)), "soft")]
    for i, h in enumerate(REQ_HITS):
        ks.append((h, RING_R if i % 2 == 0 else RING_L, "in"))
        ks.append((h + 0.2, dict(RING_R if i % 2 == 0 else RING_L, torso=(-4, 0, 0)), "out"))
    ks.append((REQ_T, dict(RING_L, wdir=(-0.1, -1.0, 0.15), torso=(-8, 0, 0)), "soft"))
    return ks


REQUIEM = _req_keys()


def requiem(t):
    pr = layered(REQUIEM, t, P(GROUND))
    pr["halo_k"] = 0.85 + 0.6 * smooth01(0.3, 2.8, t)
    pr["halo"] = 30.0 * t + 200.0 * smooth01(0.3, 3.0, t)
    return pr


BREAK_T = 1.3
REQ_BREAK = [
    (0.00, dict(RING_L, wdir=(-0.1, -1.0, 0.15), torso=(-8, 0, 0)), "lin"),
    (0.18, dict(GROUND, torso=(-24, 0, 0), head=(-46, 0, 0), body=(-20, 0, 0), rh=(-22.0, -4.0, 6.0),
                wdir=(-0.7, -0.3, -0.6), lh=(18.0, -4.0, 4.0), halo_k=1.3), "out3"),
    (0.60, dict(GROUND, torso=(30, 0, 0), head=(32, 0, 0), halo_k=0.7), "soft"),
    (BREAK_T, GROUND, "soft"),
]


def req_break(t):
    pr = layered(REQ_BREAK, t, P(GROUND))
    return pr


CAPTIVE_AT, CAPTIVE_T = 1.1, 2.2
CAPTIVE = [
    (0.00, GROUND, "lin"),
    (0.75, dict(GROUND, torso=(-8, 0, 0), head=(-26, 0, 0), lh=(8.0, -32.0, -10.0), l_pole=(0.9, 0.2, 0.3),
                lhand=(0, 0, -20)), "soft"),
    (CAPTIVE_AT, dict(GROUND, torso=(28, 0, 0), head=(20, 0, 0), lh=(10.0, 6.0, -21.0), l_pole=(0.9, -0.3, 0.2),
                      lhand=(-60, 0, 0)), "in"),
    (1.40, dict(GROUND, torso=(24, 0, 0), lh=(10.0, 6.0, -18.0)), "out"),
    (CAPTIVE_T, GROUND, "soft"),
]


def captive(t):
    return layered(CAPTIVE, t, P(GROUND))


MAUL_HIT, MAUL_T = 0.9, 1.8
MAUL_K = [
    (0.00, GROUND, "lin"),
    (0.70, dict(GROUND, torso=(-4, -24, 0), head=(-6, 18, 0), rh=(-15.0, -26.0, 5.0), r_pole=(-1.0, 0.2, 0.3),
                wdir=(-0.25, -0.55, 1.0), lh=(12.0, 0.0, -10.0)), "soft"),
    (MAUL_HIT, dict(GROUND, torso=(30, 14, 0), head=(18, -10, 0), rh=(-4.0, 2.0, -21.0), r_pole=(-1.0, 0.4, 0.2),
                    wdir=(0.15, 0.75, -1.0)), "in"),
    (1.15, dict(GROUND, torso=(28, 10, 0), rh=(-5.0, 3.0, -20.0), wdir=(0.15, 0.8, -1.0)), "out"),
    (MAUL_T, GROUND, "soft"),
]


def maul(t):
    return layered(MAUL_K, t, P(GROUND))


RISE_T = 1.8
CROUCH = dict(GROUND, root_pos=(0, 14.0, 0), torso=(30, 0, 0), head=(20, 0, 0), wr=40.0, ws=-10.0, wb=-4.0,
              r2=16.0, r3=10.0, fb=0.0, fs=6.5)
LAUNCH = dict(HOVER, root_pos=(0, -2.0, 0), torso=(-6, 0, 0), head=(-18, 0, 0), wr=-6.0, ws=30.0, wb=24.0,
              wb3=18.0, r2=-12.0, r3=-14.0, fb=-10.0, fs=4.0, robe_drag=-30.0)
RISE = [
    (0.00, GROUND, "lin"),
    (0.40, CROUCH, "soft"),
    (0.75, LAUNCH, "out3"),
]


def rise(t):
    pr = layered(RISE, t, P(GROUND))
    return fade_to(pr, t, 0.85, RISE_T, hover)


DEATH_FALL, DEATH_HIT, DEATH_T = 0.8, 1.6, 3.6
DEAD = dict(GROUND, root_pos=(0, 13.0, 0), body=(-4, 0, 26), torso=(40, 0, -10), head=(40, 20, 0),
            rh=(-22.0, 10.0, -6.0), r_pole=(-1.0, 0.5, 0.0), wdir=(-1.0, 0.25, -0.3),
            lh=(18.0, 10.0, -8.0), l_pole=(1.0, 0.5, 0.0), lhand=(-40, 0, 0),
            wr=-18.0, ws=-34.0, wb=-8.0, wb3=-6.0, r2=-10.0, r3=-8.0, wt=-20.0, fb=0.0, fs=7.0,
            halo_k=0.55, robe_flare=200.0)
DEATH = [
    (0.00, HOVER, "lin"),
    (0.45, dict(WIDE, torso=(-20, 0, 0), head=(-44, 0, 0), wr=70.0, ws=-20.0, fs=8.0), "out"),
    (DEATH_FALL, dict(WIDE, torso=(-14, 0, 0), wr=80.0, ws=-40.0, robe_drag=-40.0), "ease"),
    (DEATH_HIT, dict(DEAD, root_pos=(0, 15.0, 0), wr=-6.0), "in"),
    (2.10, dict(DEAD, torso=(30, 0, -6)), "out"),
    (DEATH_T, DEAD, "soft"),
]


def death(t):
    pr = layered(DEATH, t, P(HOVER))
    return pr



# =====================================================================
# 4б. Мечник «Отзвук»
# =====================================================================
# Ступни (rf/lf) — лодыжка в системе модели (Java, земля y = 22.5). Кисти — запястье в системе корпуса,
# плечо (∓6.6, −9.5, 0). wdir — клинок (система корпуса). Щит‑колокол — на левом предплечье.
BDEF = dict(root_y=0.0, root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0.4, 0), torso=(0, 0, 0), head=(0, 0, 0),
            rf=(-2.1, 22.5, 0), lf=(2.1, 22.5, 0), toe_r=0.0, toe_l=0.0,
            rh=(-6.0, 1.5, -3.5), lh=(5.2, 0.5, -4.5), r_pole=(-0.3, -0.1, 1), l_pole=(0.3, -0.1, 1),
            wdir=(0.2, -1.0, -0.5), wroll=0.0, lhand=(0, 0, 0), cape=0.0, skirt=0.0)
B_SHOULDER = {"rh": (-6.6, -9.5, 0.0), "lh": (6.6, -9.5, 0.0)}


def BP(base=None, **kw):
    d = dict(BDEF)
    if base:
        d.update(base)
    d.update(kw)
    return d


def bkeyed(keys, t):
    global SHOULDER
    old = SHOULDER
    SHOULDER = B_SHOULDER
    try:
        return keyed(keys, t, BDEF)
    finally:
        SHOULDER = old


B_LAG = {"head": -0.02, "rh": 0.03, "lh": 0.04, "wdir": 0.04, "r_pole": 0.03, "cape": 0.14, "skirt": 0.10}


def blayered(keys, t):
    pr = bkeyed(keys, t)
    for name, lag in B_LAG.items():
        pr[name] = bkeyed(keys, max(0.0, t - lag))[name]
    return pr


def blade_resolve(pr, model):
    pose = {"root": {"rot": (0, pr["root_y"], 0), "pos": pr["root_pos"]},
            "body": {"rot": pr["body"], "pos": pr["body_pos"]},
            "torso": {"rot": pr["torso"]}, "head": {"rot": pr["head"]}}
    Rroot = model.chain_R("root", pose)
    for s_, key, toe in (("right", "rf", "toe_r"), ("left", "lf", "toe_l")):
        tgt = model.world("root", [pr[key][0], pr[key][1] - 24, pr[key][2]], pose)
        limb_ik(model, pose, s_ + "_leg", s_ + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), BTHIGH, BSHIN, -1)
        model.set_world_rot(pose, s_ + "_foot", mm(Rroot, rx_m(math.radians(pr[toe]))))
    Rt = model.chain_R("torso", pose)
    for s_, key, pole in (("right", "rh", "r_pole"), ("left", "lh", "l_pole")):
        tgt = model.world("torso", list(pr[key]), pose)
        out = mv(Rt, [-1.0 if s_ == "right" else 1.0, 0.3, 0.4])
        limb_ik(model, pose, s_ + "_arm", s_ + "_fore", tgt, mv(Rt, list(pr[pole])), BUPPER, BFORE, 1, out)
    Rw = weapon_R(model, pose, pr)
    model.set_world_rot(pose, "right_hand", mm(Rw, tr(GRIP_G)))
    pose["left_hand"] = {"rot": pr["lhand"]}
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    tilt = pr["body"][0]
    pose["tasset0"] = {"rot": (min(rl, 0) * 0.85 - tilt * 0.8 - pr["skirt"] * 0.3, 0, 3)}
    pose["tasset1"] = {"rot": (min(rl, ll, 0) * 0.6 - tilt * 0.8 - pr["skirt"] * 0.3, 0, 0)}
    pose["tasset2"] = {"rot": (min(ll, 0) * 0.85 - tilt * 0.8 - pr["skirt"] * 0.3, 0, -3)}
    pose["tasset3"] = {"rot": (max(rl, ll, 0) * 0.7 - tilt * 0.8 + pr["skirt"] + 3, 0, 0)}
    pose["tasset_s0"] = {"rot": (rl * 0.35, 0, 6 + pr["skirt"] * 0.2)}
    pose["tasset_s1"] = {"rot": (ll * 0.35, 0, -6 - pr["skirt"] * 0.2)}
    lean = pr["body"][0] + pr["torso"][0]
    pose["cape"] = {"rot": (-lean * 0.9 + 6 + pr["cape"], 0, 0)}
    return pose


def gait(w, stride, lift, base_y=22.5, width=2.1, z0=0.0):
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l_ = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l_


GUARD = dict(body_pos=(0, 1.2, 0), body=(4, -14, 0), torso=(6, 8, 0), head=(-6, 8, 0),
             rf=(-2.6, 22.5, 2.4), lf=(2.4, 22.5, -2.8), rh=(-6.4, -1.8, -6.6), r_pole=(-0.8, 0.6, 0.4),
             wdir=(-0.4, -1.0, -0.3), lh=(4.0, -3.2, -6.4), l_pole=(0.6, 0.8, 0.4), lhand=(0, 0, 0))


def b_idle(t):
    w = 2 * math.pi * t / 2.0
    pr = BP(GUARD)
    pr["body_pos"] = (0, 1.2 + 0.3 * math.sin(w), 0)
    pr["torso"] = (6 + 1.2 * math.sin(w + 0.5), 8, 0)
    pr["head"] = (-6 + 1.5 * math.sin(w + 1.0), 8 + 3 * math.sin(w * 0.5), 0)
    pr["rh"] = (-6.4, -1.8 + 0.35 * math.sin(w + 0.7), -6.6)
    pr["lh"] = (4.0, -3.2 + 0.35 * math.sin(w + 1.0), -6.4)
    pr["cape"] = 2 * math.sin(w - 0.6)
    return pr


def b_walk(t):
    w = 2 * math.pi * t / 1.1
    pr = BP(GUARD, body=(3, 0, 0), torso=(5, 4, 0), head=(-3, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 3.6, 2.0)
    pr["body_pos"] = (0, 0.9 + 0.5 * abs(math.sin(w)), 0)
    pr["body"] = (3, 6 * math.sin(w), 0)
    pr["torso"] = (5, 4 - 7 * math.sin(w), 0)
    pr["head"] = (-3, 2 * math.sin(w), 0)
    pr["rh"] = (-5.4, -1.4 + 0.5 * math.sin(w), -5.6)
    pr["lh"] = (4.4, -2.6 - 0.5 * math.sin(w), -5.8)
    pr["toe_r"] = -10 * max(0.0, math.sin(w))
    pr["toe_l"] = -10 * max(0.0, -math.sin(w))
    pr["cape"] = 4 + 3 * math.sin(2 * w)
    pr["skirt"] = 2 * math.sin(2 * w)
    return pr


def b_run(t):
    w = 2 * math.pi * t / 0.7
    pr = BP(GUARD, body=(16, 0, 0), torso=(8, 0, 0), head=(-18, 0, 0))
    pr["rf"], pr["lf"] = gait(w, 6.0, 3.8)
    pr["body_pos"] = (0, 1.4 + 0.9 * abs(math.sin(w)), 0)
    pr["body"] = (16, 8 * math.sin(w), 0)
    pr["torso"] = (8, -10 * math.sin(w), 0)
    pr["rh"] = (-6.0, -0.5 + 1.0 * math.sin(w), 0.5 - 3.5 * math.sin(w))
    pr["wdir"] = (0.1, -0.5, 1.0)                     # клинок назад, вдоль тела
    pr["lh"] = (5.4, -2.0 - 1.0 * math.sin(w), -4.0 + 3.0 * math.sin(w))
    pr["toe_r"] = -20 * max(0.0, math.sin(w))
    pr["toe_l"] = -20 * max(0.0, -math.sin(w))
    pr["cape"] = 22 + 5 * math.sin(2 * w)
    pr["skirt"] = 8 + 3 * math.sin(2 * w)
    return pr


# серия: косой сверху справа (0.55) → обратный горизонтальный (1.0) → рубящий сверху (1.55)
B_HITS = (0.55, 1.0, 1.55)
CUT1_UP = dict(GUARD, body=(-2, 16, 0), torso=(-4, 22, 0), head=(-4, -20, 0), rh=(-7.0, -11.0, 1.5),
               r_pole=(-1.0, 0.2, 0.3), wdir=(-0.4, -1.0, 0.7), lf=(2.4, 22.5, -3.2))
CUT1 = dict(GUARD, body=(10, -24, 0), torso=(14, -24, 0), head=(-10, 24, 0), root_pos=(0, 0, -1.5),
            rh=(2.0, 1.5, -8.0), r_pole=(-0.6, 0.5, 0.6), wdir=(0.9, 0.6, -0.4), rf=(-2.6, 22.5, 3.6), lf=(2.6, 22.5, -4.0))
CUT2 = dict(GUARD, body=(6, 22, 0), torso=(8, 28, 0), head=(-6, -26, 0), root_pos=(0, 0, -2.0),
            rh=(-9.0, -3.5, -5.0), r_pole=(-1.0, -0.2, 0.2), wdir=(-1.0, -0.1, -0.35), rf=(-3.0, 22.5, 3.0), lf=(2.6, 22.5, -4.4))
CUT3_UP = dict(GUARD, body=(-6, -6, 0), torso=(-10, -4, 0), head=(-10, 4, 0), rh=(-3.5, -16.0, -1.0),
               r_pole=(-1.0, 0.0, 0.2), wdir=(0.05, -0.6, 1.0), lh=(3.0, -10.0, -4.0))
CUT3 = dict(GUARD, body=(18, -4, 0), torso=(20, -4, 0), head=(-14, 4, 0), root_pos=(0, 0, -2.5),
            rh=(-2.0, -1.0, -9.5), r_pole=(-1.0, 0.6, 0.3), wdir=(0.05, 0.4, -1.0), rf=(-2.8, 22.5, 4.2), lf=(2.6, 22.5, -4.6),
            toe_r=-16.0)
B_COMBO = [
    (0.00, GUARD, "lin"), (0.40, CUT1_UP, "soft"), (B_HITS[0], CUT1, "in"),
    (0.68, dict(CUT1, wdir=(0.85, 0.75, -0.2)), "out"), (0.85, dict(CUT2, wdir=(0.6, -0.5, -0.7), body=(4, -10, 0)), "soft"),
    (B_HITS[1], CUT2, "in"), (1.12, dict(CUT2, wdir=(-0.9, 0.1, 0.1)), "out"), (1.38, CUT3_UP, "soft"),
    (B_HITS[2], CUT3, "in"), (1.70, dict(CUT3, wdir=(0.05, 0.5, -0.9)), "out"), (2.20, GUARD, "soft"),
]


def b_combo(t):
    return blayered(B_COMBO, t)


B_DASH_GO, B_DASH_HIT, B_DASH_T = 0.5, 0.8, 1.6
COIL = dict(GUARD, body_pos=(0, 3.0, 0), body=(16, 10, 0), torso=(10, 14, 0), head=(-14, -10, 0),
            rh=(-6.0, -1.0, 3.0), r_pole=(-0.5, -0.3, 1.0), wdir=(0.0, 0.3, -1.0), rf=(-3.0, 22.5, 4.4), lf=(2.6, 22.5, -3.6),
            toe_r=-20.0, cape=6.0)
THRUST = dict(GUARD, body_pos=(0, 2.2, 0), body=(22, -12, 0), torso=(12, -16, 0), head=(-20, 12, 0), root_pos=(0, 0, -3.0),
              rh=(-2.0, -5.5, -12.0), r_pole=(-0.6, 0.6, 0.4), wdir=(0.0, 0.05, -1.0), rf=(-2.6, 21.0, 6.5), lf=(2.6, 22.5, -5.0),
              toe_r=-36.0, cape=30.0, skirt=14.0)
B_DASH = [
    (0.00, GUARD, "lin"), (B_DASH_GO, COIL, "soft"), (B_DASH_HIT, THRUST, "in3"),
    (1.00, dict(THRUST, rh=(-2.2, -5.0, -11.4)), "out"), (B_DASH_T, GUARD, "soft"),
]


def b_dash(t):
    return blayered(B_DASH, t)


B_PARRY_T = 0.6
B_PARRY = [
    (0.00, GUARD, "lin"),
    (0.10, dict(GUARD, body=(-6, -10, 0), torso=(-8, -4, 0), head=(-12, 0, 0), lh=(3.5, -7.0, -7.0),
                l_pole=(0.8, 0.6, 0.3), root_pos=(0, 0, 1.2)), "out3"),
    (B_PARRY_T, GUARD, "soft"),
]


def b_parry(t):
    return blayered(B_PARRY, t)


B_ASM_T = 1.6
KNEEL = dict(GUARD, body_pos=(0, 7.0, 0), body=(30, 0, 0), torso=(34, 0, 0), head=(30, 0, 0),
             rf=(-2.4, 22.5, -3.0), lf=(2.4, 22.5, 3.0), rh=(-5.0, 6.0, -6.0), wdir=(0.0, 1.0, -0.2),
             lh=(5.0, 6.0, -3.0), cape=-10.0)
B_ASM = [
    (0.00, KNEEL, "lin"), (0.55, dict(KNEEL, body_pos=(0, 6.5, 0), head=(10, 0, 0)), "soft"),
    (1.10, dict(GUARD, body_pos=(0, 2.0, 0), torso=(-6, 0, 0), head=(-14, 0, 0)), "soft"), (B_ASM_T, GUARD, "soft"),
]


def b_assemble(t):
    return blayered(B_ASM, t)


# =====================================================================
# 5. Запекание, дорожки оружия, предпросмотр
# =====================================================================
def bones_of(model, air_only):
    out = []
    for b_ in model.bones:
        n = b_["name"]
        if air_only and ("_wing" in n or "_p" in n and n.split("_p")[-1].isdigit()):
            continue
        out.append(n)
    return out


def bake_clip(model, fn, length_s, fps, loop, bones, weapon_bone="right_hand"):
    PREV_PN.clear()
    track = []
    poses = {}

    def full(t):
        pr, pose = pose_at(fn, t, model, length_s, loop)
        poses[t] = (pr, pose)
        return pose

    anim = bake(full, length_s, fps, loop, bones, unwrap=True)
    if not loop:
        anchor_end(anim)
    # дорожка оружия: тот же шаг, что у анимации
    n = int(round(length_s * fps))
    prev_q = None
    for k in range(n + 1):
        t = min(k / fps, length_s)
        pr, pose = poses[t]
        q = quat(weapon_R(model, pose, pr))
        if prev_q is not None and sum(q[i] * prev_q[i] for i in range(4)) < 0:
            q = [-v for v in q]
        prev_q = q
        track.append([round(v, 5) for v in q])
    return anim, {"fps": fps, "length": length_s, "loop": loop, "q": track}


def export(model, clips, weapon_name, air_names=()):
    anims, tracks = {}, {}
    for name, fn, ln, fps, loop in clips:
        bones = bones_of(model, name in air_names)
        a, w = bake_clip(model, fn, ln, fps, loop, bones)
        anims["animation.%s.%s" % (model.name, name)] = a
        tracks[name] = w
        print("  %-16s %.2f с" % (name, ln))
    if os.environ.get("ONLY"):        # частичное запекание — дописать к уже запечённым клипам
        try:
            with open(os.path.join(ROOT, "animations", model.name + ".animation.json"), encoding="utf-8") as f:
                old = json.load(f)["animations"]
            with open(os.path.join(ROOT, "animations", model.name + ".weapon.json"), encoding="utf-8") as f:
                old_w = json.load(f)["clips"]
            old.update(anims)
            old_w.update(tracks)
            anims, tracks = old, old_w
        except (OSError, KeyError, ValueError):
            pass
    write_anims(ROOT, model.name, anims)
    with open(os.path.join(ROOT, "animations", model.name + ".weapon.json"), "w", encoding="utf-8") as f:
        json.dump({"weapon": weapon_name, "hand": "right_hand",
                   "grip_offset": [round(v, 4) for v in GRIP_OFFSET], "clips": tracks}, f, separators=(",", ":"))


def _find_tf(parts, name, transform=None):
    """Функция «точка в системе кости name → система модели» для частей mob_preview (с учётом анимации)."""
    import settler_preview as sp
    transform = transform or (lambda v: v)
    for p in parts:
        pitch, yaw, roll = p["rot"]
        px, py, pz = p["pivot"]
        sx, sy, sz = p.get("scale", (1, 1, 1))

        def tf(v, pitch=pitch, yaw=yaw, roll=roll, px=px, py=py, pz=pz, sx=sx, sy=sy, sz=sz, parent=transform):
            v = (v[0] * sx, v[1] * sy, v[2] * sz)
            v = sp.rot_x(v, pitch)
            v = sp.rot_y(v, yaw)
            v = sp.rot_z(v, roll)
            return parent((v[0] + px, v[1] + py, v[2] + pz))

        if p["name"] == name:
            return tf
        r = _find_tf(p["children"], name, tf)
        if r:
            return r
    return None


def _shift_uv(parts, dv):
    for p in parts:
        p["cubes"] = [(c[0], c[1] + dv) + tuple(c[2:]) for c in p["cubes"]]
        _shift_uv(p["children"], dv)


def track_q(track, t):
    fps, q = track["fps"], track["q"]
    if track["loop"]:
        t = t % track["length"]
    x = max(0.0, min(len(q) - 1.0, t * fps))
    i = int(x)
    f = x - i
    a, b_ = q[i], q[min(i + 1, len(q) - 1)]
    v = [a[k] + (b_[k] - a[k]) * f for k in range(4)]
    l_ = math.sqrt(sum(c * c for c in v))
    return [c / l_ for c in v]


def q_to_R(q):
    x, y, z, w = q
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def parts_from_pose(mob, pose):
    """Части mob_preview прямо из позы resolve() (без запекания) — для быстрой подгонки поз."""
    import mob_preview as mp
    parts = mp.build_parts(mp.load_geo(mob))

    def walk(ps):
        for p in ps:
            st = pose.get("root" if p["name"] == "bone_root" else p["name"])
            if st:
                r = st.get("rot", (0, 0, 0))
                p["rot"] = tuple(math.radians(v) for v in r)
                if "pos" in st:
                    px, py, pz = p["pivot"]
                    q = st["pos"]
                    p["pivot"] = (px + q[0], py + q[1], pz + q[2])
                if "scale" in st:
                    p["scale"] = tuple(st["scale"])
            walk(p["children"])
    walk(parts)
    return parts


POSE_SRC = {}


def preview_parts(mob, weapon, clip, t, hide=()):
    """Части модели в момент t клипа + оружие по дорожке (для mob_preview‑рендера)."""
    import mob_preview as mp
    with open(os.path.join(ROOT, "animations", mob + ".weapon.json"), encoding="utf-8") as f:
        wj = json.load(f)
    if POSE_SRC:
        model, fn, ln, loop = POSE_SRC["v"]
        pr, pose = pose_at(fn, t, model, ln, loop)
        parts = parts_from_pose(mob, pose)
        R = weapon_R(model, pose, pr)
    else:
        geo = mp.load_geo(mob)
        parts = mp.build_parts(geo)
        if clip:
            an = mp.load_anims(mob)[clip]
            mp.apply_anim(parts, an, t)
        R = q_to_R(track_q(wj["clips"][clip or next(iter(wj["clips"]))], t))
    tf = _find_tf(parts, wj["hand"])
    wrist = tf((0.0, 0.0, 0.0))
    wparts = mp.build_parts(mp.load_geo(weapon))
    tex_h = mp.load_geo(mob)["description"]["texture_height"]
    _shift_uv(wparts, tex_h)
    go = wj["grip_offset"]
    wroot = dict(name="weapon", pivot=tuple(wrist), rot=tuple(math.radians(v) for v in to_euler(R)),
                 cubes=[], tag=None,
                 children=[dict(name="weapon_off", pivot=(go[0], go[1] - 24.0, go[2]), rot=(0, 0, 0), cubes=[],
                                tag=None, children=wparts)])
    out = [p for p in parts if p["name"] not in hide] + [wroot]
    return out


def atlas(mob, weapon, glow=False):
    from PIL import Image
    suf = "_glowmask" if glow else ""
    a = Image.open(os.path.join(ROOT, "textures", "entity", mob + suf + ".png")).convert("RGBA")
    w = Image.open(os.path.join(ROOT, "textures", "entity", weapon + suf + ".png")).convert("RGBA")
    out = Image.new("RGBA", (max(a.width, w.width), a.height + w.height), (0, 0, 0, 0))
    out.paste(a, (0, 0))
    out.paste(w, (0, a.height))
    if glow:   # непрозрачный чёрный под несветящимися пикселями — для глубины
        base = atlas(mob, weapon, False)
        bp, op = base.load(), out.load()
        for y in range(out.height):
            for x in range(out.width):
                if op[x, y][3] == 0 and bp[x, y][3]:
                    op[x, y] = (0, 0, 0, 255)
    return out


def preview(mob, weapon, clip, times, yaw=0.5, scale=4.2, out_name="tools/mob_preview.png", lift=0, size=(520, 760)):
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
    import settler_preview as sp
    sys.path.insert(0, os.path.dirname(__file__))
    import mob_preview as mp  # noqa: F401  (подменяет sp.collect на версию с масштабом)
    tex, gm = atlas(mob, weapon), atlas(mob, weapon, True)
    frames = []
    W, H = size
    for t in times:
        img = None
        for k, tx in enumerate((tex, gm)):
            sp.PARTS = preview_parts(mob, weapon, clip, t)
            r = sp.render(tx, None, yaw, scale=scale, size=(W, H), baseline=H - 40 - lift)
            if k == 0:
                img = r
            else:
                g = r
        bp, gp = img.load(), g.load()
        for y in range(H):
            for x in range(W):
                c = gp[x, y]
                if c[3] and c[0] + c[1] + c[2] > 30:
                    bp[x, y] = (min(255, int(c[0] * 1.1)), min(255, int(c[1] * 1.1)), min(255, int(c[2] * 1.1)), 255)
        glow = g.convert("RGB")
        bloom = ImageChops.add(glow.filter(ImageFilter.GaussianBlur(scale * 1.2)), glow.filter(ImageFilter.GaussianBlur(scale * 4)))
        bg = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(bg)
        for y in range(H):
            k = y / H
            d.line([(0, y), (W, y)], fill=(int(22 + 46 * k), int(22 + 30 * k), int(40 + 26 * k)))
        d.rectangle([0, H - 40, W, H], fill=(38, 42, 36))
        d.text((8, 6), "%s %.2f" % (clip or "-", t), fill=(230, 220, 200))
        o = ImageChops.add(bg, ImageChops.multiply(bloom, Image.new("RGB", (W, H), (140, 115, 85)))).convert("RGBA")
        o.alpha_composite(img)
        frames.append(ImageChops.add(o.convert("RGB"), ImageChops.multiply(bloom, Image.new("RGB", (W, H), (100, 80, 55)))))
    sheet = Image.new("RGB", (W * len(frames), H))
    for i, f in enumerate(frames):
        sheet.paste(f, (i * W, 0))
    path = os.path.join(os.path.dirname(__file__), "..", out_name) if not os.path.isabs(out_name) else out_name
    sheet.save(path)
    print("saved", path)


# =====================================================================
# 7. Сборка
# =====================================================================
def build_all():
    global NABAT, MAUL, BLADE, SWORD, BELL
    NABAT = build_nabat()
    NABAT.finalize()
    MAUL = build_maul()
    BLADE = build_blade()
    BLADE.finalize()
    SWORD = build_sword()
    BELL = build_bell()
    for M_ in (NABAT, MAUL, BLADE, SWORD, BELL):
        M_.write_geo(ROOT)
    save_textures(NABAT, paint(NABAT, 91))
    save_textures(NABAT, paint(NABAT, 91, phase3=True), "nabat_p3")
    for M_, seed in ((MAUL, 5), (BLADE, 17), (SWORD, 23), (BELL, 29)):
        save_textures(M_, paint(M_, seed))


def seg_dist(p, a, b_):
    ab = sub(b_, a)
    t = max(0.0, min(1.0, dot(sub(p, a), ab) / max(1e-9, dot(ab, ab))))
    return length(sub(p, add(a, scl(ab, t))))


def check_clips(model, clips):
    """Пересечения молота с головой, корпусом и колоколом (по позам, 30 кадров/с). Пусто — чисто."""
    bad = 0
    for name, fn, ln, fps, loop in clips:
        PREV_PN.clear()
        n = int(ln * 30)
        for k in range(n + 1):
            t = min(k / 30.0, ln)
            pr, pose = pose_at(fn, t, model, ln, loop)
            Rw = weapon_R(model, pose, pr)
            wrist = model.world("right_hand", [0, 0, 0], pose)
            grip = add(wrist, mv(Rw, GRIP_OFFSET))
            head_c = add(grip, mv(Rw, [0, -42.0, 0]))
            butt = add(grip, mv(Rw, [0, 24.0, 0]))
            hc = model.world("head", model.local_of("head", (0, 60 + OY, 0)), pose)
            tc = model.world("torso", model.local_of("torso", (0, 47 + OY, 0)), pose)
            bc = model.world("body", model.local_of("body", (0, 26 + OY, 0)), pose)
            probs = []
            if length(sub(head_c, hc)) < 7 + 8:
                probs.append("молот у головы %.1f" % length(sub(head_c, hc)))
            if seg_dist(hc, butt, head_c) < 6.0:
                probs.append("древко сквозь голову %.1f" % seg_dist(hc, butt, head_c))
            if length(sub(head_c, tc)) < 9 + 8:
                probs.append("молот у корпуса %.1f" % length(sub(head_c, tc)))
            if seg_dist(tc, grip, head_c) < 7.0:
                probs.append("древко сквозь корпус %.1f" % seg_dist(tc, grip, head_c))
            if length(sub(head_c, bc)) < 11 + 7:
                probs.append("молот в колоколе %.1f" % length(sub(head_c, bc)))
            # крылья: в воздушных действиях крылья машут циклом парения — проверяем вместе с ним
            wpose = pose
            if name in AIR_CLIPS:
                _, hp = pose_at(hover, t, model, HOVER_T, True)
                wpose = dict(pose)
                for bn in hp:
                    if "_wing" in bn or ("_p" in bn and bn.split("_p")[-1].isdigit()):
                        wpose[bn] = hp[bn]
            for side, sx in (("right", -1), ("left", 1)):
                pts = [model.world(side + "_wing", [0, 0, 0], wpose),
                       model.world(side + "_wing2", [0, 0, 0], wpose),
                       model.world(side + "_wing3", [0, 0, 0], wpose),
                       model.world(side + "_wing3", [sx * 13.0, 0, 0], wpose)]
                for a_, b2 in zip(pts, pts[1:]):
                    for q in [add(grip, mv(Rw, [0, -yy, 0])) for yy in range(-20, 52, 3)]:
                        if seg_dist(q, a_, b2) < 3.5:
                            probs.append("молот задевает крыло (%s)" % side)
                            break
                    else:
                        continue
                    break
            if probs:
                bad += 1
                print("%-14s %.2f с: %s" % (name, t, ", ".join(probs)))
    print("проблемных кадров:", bad)


def main():
    global NABAT, BLADE
    args = sys.argv[1:]
    if args and args[0] == "--check":
        NABAT = build_nabat()
        NABAT.finalize()
        only = set(args[1:])
        check_clips(NABAT, [c for c in NABAT_CLIPS if not only or c[0] in only])
        return
    if args and args[0] == "--pose":
        # быстрый просмотр позы без запекания: --pose <клип> <секунды>
        NABAT = build_nabat()
        NABAT.finalize()
        BLADE = build_blade()
        BLADE.finalize()
        mob = os.environ.get("MOB", "nabat")
        table = {c[0]: c for c in (NABAT_CLIPS if mob == "nabat" else BLADE_CLIPS)}
        name, fn, ln, fps, loop = table[args[1]]
        POSE_SRC["v"] = (NABAT if mob == "nabat" else BLADE, fn, ln, loop)
        args = ["--preview"] + args[1:]
    if args and args[0] == "--preview":
        mob = os.environ.get("MOB", "nabat")
        weapon = "nabat_maul" if mob == "nabat" else "nabat_sword"
        clip = args[1] if len(args) > 1 and args[1] != "-" else None
        times = [float(x) for x in args[2].split(",")] if len(args) > 2 else [0.0]
        preview(mob, weapon, clip, times, yaw=float(os.environ.get("YAW", "0.5")),
                scale=float(os.environ.get("SCALE", "4.2" if mob == "nabat" else "11")),
                lift=float(os.environ.get("LIFT", "0")))
        return
    build_all()
    only = set(os.environ.get("ONLY", "").split(",")) - {""}
    clips = [c for c in NABAT_CLIPS if not only or c[0] in only]
    print("Набат:", len(NABAT.cubes()), "кубов")
    export(NABAT, clips, "nabat_maul", AIR_CLIPS)
    if not only:
        print("Мечник:", len(BLADE.cubes()), "кубов")
        export(BLADE, BLADE_CLIPS, "nabat_sword")


NABAT_CLIPS = [
    ("hover", hover, HOVER_T, 30, True),
    ("fly", fly, FLY_T, 30, True),
    ("glide", glide, GLIDE_T, 30, True),
    ("ground_idle", ground_idle, GROUND_T, 20, True),
    ("toll", toll, TOLL[-1][0], 60, False),
    ("censer", censer, CENSER[-1][0], 60, False),
    ("gather", gather, GATHER[-1][0], 60, False),
    ("bells", bells, BELLS[-1][0], 60, False),
    ("rain", rain, RAIN[-1][0], 60, False),
    ("mark", mark, MARK[-1][0], 60, False),
    ("eggs", eggs, EGGS[-1][0], 60, False),
    ("phantoms", phantoms, PHANTOMS[-1][0], 60, False),
    ("reliquary", reliquary, RELIQ[-1][0], 60, False),
    ("crack", crack, CRACK[-1][0], 60, False),
    ("intro", intro, INTRO_T, 60, False),
    ("dive", dive, DIVE_T, 60, False),
    ("reap", reap, REAP_T, 60, False),
    ("quake", quake, QUAKE_T, 60, False),
    ("crash", crash, CRASH_T, 60, False),
    ("requiem", requiem, REQ_T, 60, False),
    ("requiem_break", req_break, BREAK_T, 60, False),
    ("captive", captive, CAPTIVE_T, 60, False),
    ("maul", maul, MAUL_T, 60, False),
    ("rise", rise, RISE_T, 60, False),
    ("death", death, DEATH_T, 60, False),
]
AIR_CLIPS = {"toll", "censer", "gather", "bells", "rain", "mark", "eggs", "phantoms", "reliquary", "crack"}
BLADE_CLIPS = [
    ("idle", b_idle, 2.0, 20, True),
    ("walk", b_walk, 1.1, 24, True),
    ("run", b_run, 0.7, 30, True),
    ("combo", b_combo, B_COMBO[-1][0], 60, False),
    ("dash", b_dash, B_DASH_T, 60, False),
    ("parry", b_parry, B_PARRY_T, 60, False),
    ("assemble", b_assemble, B_ASM_T, 60, False),
]

if __name__ == "__main__":
    main()
