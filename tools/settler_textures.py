"""
Генератор текстур жителей basedefense (128x64, раскладка как у скина игрока + головные уборы справа).
Запуск:  python tools/settler_textures.py
Нужен Pillow (pip install pillow). Текстуры пишутся в src/main/resources/assets/basedefense/textures/entity/.
Правь цвета в CHARACTERS ниже и перезапусти.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "basedefense", "textures", "entity")

T = (0, 0, 0, 0)  # прозрачный


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, k):
    """k > 1 светлее, k < 1 темнее."""
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


class Canvas:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (128, 64), T)
        self.px = self.im.load()
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        if 0 <= x < 128 and 0 <= y < 64:
            self.px[x, y] = c if len(c) == 4 else (c[0], c[1], c[2], 255)

    def noisy(self, c, amount=6):
        n = self.rnd.randint(-amount, amount)
        return (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)

    def rect(self, x, y, w, h, c, noise=5):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, self.noisy(c, noise) if noise else c)

    def pattern(self, x, y, w, h, fn):
        """fn(col, row, w, h) -> цвет или None (не рисовать)."""
        for row in range(h):
            for col in range(w):
                c = fn(col, row, w, h)
                if c is not None:
                    self.put(x + col, y + row, c)

    @staticmethod
    def faces(u, v, w, h, d):
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }

    def box(self, u, v, w, h, d, fn_by_face):
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            fn = fn_by_face.get(name, fn_by_face.get("*"))
            if fn is not None:
                self.pattern(x, y, fw, fh, lambda c, r, ww, hh, f=fn, n=name: f(c, r, ww, hh, n))


# ---------------------------------------------------------------------------
# Узоры тканей
# ---------------------------------------------------------------------------

def plain(base, rnd, noise=5):
    def f(c, r, w, h, face=None):
        n = rnd.randint(-noise, noise)
        return (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n), 255)
    return f


def plaid(a, b, rnd):
    def f(c, r, w, h, face=None):
        sc = (c % 4 == 1)
        sr = (r % 4 == 1)
        if sc and sr:
            col = b
        elif sc or sr:
            col = mix(a, b, 0.55)
        else:
            col = a
        n = rnd.randint(-4, 4)
        return (clamp(col[0] + n), clamp(col[1] + n), clamp(col[2] + n), 255)
    return f


def denim(base, rnd):
    def f(c, r, w, h, face=None):
        k = 1.06 if (c + r) % 3 == 0 else 0.97
        n = rnd.randint(-5, 5)
        return (clamp(base[0] * k + n), clamp(base[1] * k + n), clamp(base[2] * k + n), 255)
    return f


def checker(a, b):
    def f(c, r, w, h, face=None):
        return a if (c + r) % 2 == 0 else b
    return f


# ---------------------------------------------------------------------------
# Части тела
# ---------------------------------------------------------------------------

def paint_head(cv, ch):
    skin, hair = ch["skin"], ch["hair"]
    dark_skin = shade(skin, 0.85)
    eye, brow = ch["eyes"], shade(hair, 0.7)
    beard = ch.get("beard")

    def side(c, r, w, h, face):
        back_cols = (0, 1) if face == "right" else (6, 7)
        face_col = 7 if face == "right" else 0
        if r <= 1:
            return cv.noisy(hair, 4)
        if c in back_cols and r <= 5:
            return cv.noisy(hair, 4)
        if r == 2 and c != face_col:
            return cv.noisy(hair, 4)
        if beard and r >= 5 and c >= 3 if face == "right" else (beard and r >= 5 and c <= 4):
            return cv.noisy(beard, 4)
        if r == 4 and c in (3, 4):
            return dark_skin  # ухо
        return cv.noisy(skin, 3)

    def front(c, r, w, h, face):
        if r <= 1:
            return cv.noisy(hair, 4)
        if r == 2:
            return cv.noisy(hair, 4) if c in (0, 7) else cv.noisy(skin, 3)
        if r == 3:
            return brow if c in (1, 2, 5, 6) else cv.noisy(skin, 3)
        if r == 4:
            if c in (1, 6):
                return (240, 240, 240, 255)
            if c in (2, 5):
                return eye
            return cv.noisy(skin, 3)
        if beard:
            if r == 5 and c in (2, 3, 4, 5):
                return shade(beard, 0.9)            # усы
            if r == 6 and c in (3, 4):
                return ch.get("mouth", (120, 60, 50, 255))
            if r >= 6 or (r == 5 and c in (0, 1, 6, 7) and ch.get("full_beard")):
                return cv.noisy(beard, 4)
        if r == 5:
            if c in (3, 4):
                return dark_skin                      # нос
            if c in (1, 6):
                return mix(skin, (220, 120, 110, 255), 0.18)  # щёки
            return cv.noisy(skin, 3)
        if r == 6:
            return ch.get("mouth", (150, 80, 70, 255)) if c in (3, 4) else cv.noisy(skin, 3)
        return cv.noisy(dark_skin if r == 7 else skin, 3)

    def back(c, r, w, h, face):
        if r <= 5:
            return cv.noisy(hair, 4)
        return cv.noisy(dark_skin, 3)

    cv.box(0, 0, 8, 8, 8, {
        "top": lambda c, r, w, h, f: cv.noisy(hair, 4),
        "bottom": lambda c, r, w, h, f: cv.noisy(dark_skin, 3),
        "front": front, "right": side, "left": side, "back": back,
    })
    # Чумазые щёки (у шахтёра 1 ур.)
    if ch.get("soot"):
        for x, y in ((9, 13), (14, 14), (13, 13)):
            cv.put(x, y, shade(skin, 0.6))

    # Второй слой: объёмные волосы / бандана
    hair_light = shade(hair, 1.12)
    bandana = ch.get("bandana")

    def ov_top(c, r, w, h, f):
        return cv.noisy(bandana or hair_light, 4)

    def ov_front(c, r, w, h, f):
        if bandana:
            return cv.noisy(bandana, 4) if r <= 1 else (shade(bandana, 0.8) if r == 2 and c in (0, 7) else None)
        if r == 0:
            return cv.noisy(hair_light, 4)
        if r == 1 and c in (0, 1, 2, 5, 6, 7):
            return cv.noisy(hair_light, 4)
        return None

    def ov_side(c, r, w, h, f):
        if bandana:
            return cv.noisy(bandana, 4) if r <= 1 else None
        back_cols = (0, 1, 2) if f == "right" else (5, 6, 7)
        if r <= 1 or (c in back_cols and r <= 3):
            return cv.noisy(hair_light, 4)
        return None

    def ov_back(c, r, w, h, f):
        if bandana:
            if r <= 1:
                return cv.noisy(bandana, 4)
            if r in (2, 3) and c in (3, 4):
                return shade(bandana, 0.85)           # узелок
            return None
        return cv.noisy(hair_light, 4) if r <= 4 else None

    cv.box(32, 0, 8, 8, 8, {"top": ov_top, "front": ov_front, "right": ov_side, "left": ov_side, "back": ov_back})


def paint_body(cv, ch):
    shirt = ch["shirt_fn"]
    pants = ch["pants_fn"]
    skin = ch["skin"]
    belt = ch.get("belt")

    def front(c, r, w, h, f):
        if r == 0 and c in (3, 4) and ch.get("collar_open", True):
            return skin
        if belt and r == 9:
            if c in (3, 4) and ch.get("buckle"):
                return ch["buckle"]
            return cv.noisy(belt, 3)
        if r >= 10:
            return pants(c, r, w, h, f)
        col = shirt(c, r, w, h, f)
        if ch.get("buttons") and c in (2, 5) and r in (2, 4, 6, 8):
            return ch["buttons"]
        return col

    def other(c, r, w, h, f):
        if f in ("top", "bottom"):
            return shirt(c, r, w, h, f) if f == "top" else pants(c, r, w, h, f)
        if belt and r == 9:
            return cv.noisy(belt, 3)
        if r >= 10:
            return pants(c, r, w, h, f)
        return shirt(c, r, w, h, f)

    cv.box(16, 16, 8, 12, 4, {"front": front, "*": other})
    if ch.get("neckerchief"):
        nc = ch["neckerchief"]
        for x in range(20, 28):
            cv.put(x, 20, nc)
        for x in (22, 23, 24, 25):
            cv.put(x, 21, shade(nc, 0.9))
        cv.put(23, 22, shade(nc, 0.8))
        cv.put(24, 22, shade(nc, 0.8))

    jacket = ch.get("jacket")
    if jacket:
        cv.box(16, 32, 8, 12, 4, jacket(cv))


def paint_arm(cv, u, v, ch, sleeve_len, overlay_u=None, overlay_v=None):
    shirt = ch["shirt_fn"]
    skin = ch["skin"]

    def f(c, r, w, h, face):
        if face == "top":
            return shirt(c, r, w, h, face)
        if face == "bottom":
            return shade(skin, 0.85)
        if r < sleeve_len:
            return shirt(c, r, w, h, face)
        return cv.noisy(shade(skin, 0.93) if r >= 10 else skin, 3)

    cv.box(u, v, 4, 12, 4, {"*": f})
    gloves = ch.get("gloves")
    if gloves and overlay_u is not None:
        def g(c, r, w, h, face):
            if face == "bottom" or (face != "top" and r >= 8):
                return cv.noisy(gloves, 4) if not (r == 8 and face != "bottom") else shade(gloves, 1.15)
            return None
        cv.box(overlay_u, overlay_v, 4, 12, 4, {"*": g})
    elif ch.get("cuff") and overlay_u is not None:
        cuff = ch["cuff"]

        def k(c, r, w, h, face):
            if face in ("top", "bottom"):
                return None
            return cuff if r == sleeve_len - 1 else None
        cv.box(overlay_u, overlay_v, 4, 12, 4, {"*": k})


def paint_leg(cv, u, v, ch):
    pants = ch["pants_fn"]
    boots = ch["boots"]

    def f(c, r, w, h, face):
        if face == "top":
            return pants(c, r, w, h, face)
        if face == "bottom":
            return shade(boots, 0.6)
        if r >= 9:
            return cv.noisy(shade(boots, 0.7) if r == 11 else boots, 4)
        return pants(c, r, w, h, face)

    cv.box(u, v, 4, 12, 4, {"*": f})


# ---------------------------------------------------------------------------
# Одежда второго слоя (куртки, фартуки, жилеты)
# ---------------------------------------------------------------------------

def overalls(color, buttons):
    def make(cv):
        def front(c, r, w, h, f):
            if r <= 2:
                return cv.noisy(color, 4) if c in (1, 6) else None      # лямки
            if c in (0, 7) and r < 9:
                return None
            if r == 3 and c in (1, 6):
                return buttons
            return cv.noisy(color, 4) if r >= 3 else None
        def back(c, r, w, h, f):
            if r <= 6:
                return cv.noisy(color, 4) if c in (1 + r // 2, 6 - r // 2) else None
            return cv.noisy(color, 4)
        def side(c, r, w, h, f):
            return cv.noisy(color, 4) if r >= 9 else None
        return {"front": front, "back": back, "right": side, "left": side}
    return make


def apron(color, strap=None):
    def make(cv):
        def front(c, r, w, h, f):
            if r == 0 or r == 1:
                return cv.noisy(strap or color, 3) if c in (2, 5) else None
            if 1 <= c <= 6 and r >= 2:
                return cv.noisy(color, 3) if not (r == 6 and c in (3, 4)) else shade(color, 0.85)  # карман
            return None
        def back(c, r, w, h, f):
            return shade(strap or color, 0.9) if r == 8 else None   # завязки
        def side(c, r, w, h, f):
            return shade(strap or color, 0.9) if r == 8 else None
        return {"front": front, "back": back, "right": side, "left": side}
    return make


def vest(color, stripes=None, trim=None):
    def make(cv):
        def front(c, r, w, h, f):
            if r > 9:
                return None
            if c in (3, 4):
                return None                              # жилет расстёгнут
            if stripes and r in (4, 7):
                return stripes
            if trim and c == 2 and r in (2, 5, 8):
                return trim                              # пуговицы
            return cv.noisy(color, 4)
        def other(c, r, w, h, f):
            if f in ("top", "bottom") or r > 9:
                return None
            if stripes and r in (4, 7):
                return stripes
            return cv.noisy(color, 4)
        return {"front": front, "*": other}
    return make


def suspenders(color, buckle):
    def make(cv):
        def front(c, r, w, h, f):
            if c in (1, 6) and r <= 9:
                return buckle if r == 8 else cv.noisy(color, 3)
            return None
        def back(c, r, w, h, f):
            if r <= 9 and c in (1 + r // 3, 6 - r // 3):
                return cv.noisy(color, 3)
            return None
        return {"front": front, "back": back}
    return make


# ---------------------------------------------------------------------------
# Головные уборы (правая половина)
# ---------------------------------------------------------------------------

def paint_brim_hat(cv, base, band, weave=True, band_trim=None):
    dark = shade(base, 0.8)

    def brim(c, r, w, h, f):
        if f == "top":
            edge = c in (0, 13) or r in (0, 13)
            col = shade(base, 0.92) if edge else base
            if weave and (c + r) % 2 == 0:
                col = shade(col, 1.07)
            return cv.noisy(col, 3)
        if f == "bottom":
            return cv.noisy(dark, 3)
        return cv.noisy(shade(base, 0.85), 3)
    cv.box(64, 0, 14, 1, 14, {"*": brim})

    def crown(c, r, w, h, f):
        if f == "top":
            col = shade(base, 1.05)
            if weave and (c + r) % 2 == 0:
                col = shade(col, 1.06)
            if 2 <= c <= 5 and 2 <= r <= 5 and not weave:
                col = shade(base, 0.9)              # вмятина на ковбойской шляпе
            return cv.noisy(col, 3)
        if f == "bottom":
            return cv.noisy(dark, 3)
        if r == 2:
            if band_trim and c in (3, 4) and f == "front":
                return band_trim
            return cv.noisy(band, 3)
        col = base
        if weave and (c + r) % 2 == 0:
            col = shade(col, 1.06)
        return cv.noisy(col, 3)
    cv.box(64, 16, 8, 3, 8, {"*": crown})


def paint_toque(cv, base, trim=None):
    def f(c, r, w, h, face):
        if face == "top":
            return cv.noisy(shade(base, 1.02), 3)
        if face == "bottom":
            return cv.noisy(shade(base, 0.85), 3)
        if r == 5:
            return trim or shade(base, 0.88)            # ободок
        if c % 2 == 1:
            return cv.noisy(shade(base, 0.92), 2)        # складки
        return cv.noisy(base, 2)
    cv.box(96, 16, 8, 6, 8, {"*": f})


def paint_helmet(cv, base, stripe=None, knit=False, visor=True, lamp=False):
    def f(c, r, w, h, face):
        if face == "top":
            if stripe and c in (3, 4):
                return stripe
            col = shade(base, 1.08) if (c in (3, 4) and not knit) else base   # гребень каски
            if knit and r % 2 == 0:
                col = shade(col, 0.92)
            return cv.noisy(col, 4)
        if face == "bottom":
            return cv.noisy(shade(base, 0.7), 3)
        if knit:
            col = shade(base, 0.9) if c % 2 == 0 else base
            if r == 2:
                col = shade(base, 0.8)                   # отворот шапки
            if stripe and r == 1:
                col = stripe
            return cv.noisy(col, 3)
        if stripe and face in ("front", "back") and c in (3, 4):
            return stripe
        return cv.noisy(shade(base, 0.95) if r == 2 else base, 4)
    cv.box(64, 32, 8, 3, 8, {"*": f})
    if visor:
        cv.box(96, 32, 8, 1, 3, {"*": lambda c, r, w, h, face: cv.noisy(shade(base, 0.88), 3)})
    if lamp:
        def lf(c, r, w, h, face):
            if face == "front":
                return (255, 250, 200, 255) if (c + r) % 3 else (255, 255, 240, 255)
            return (110, 110, 115, 255)
        cv.box(96, 40, 2, 2, 1, {"*": lf})


# ---------------------------------------------------------------------------
# Персонажи: (имя, уровень) -> описание
# ---------------------------------------------------------------------------

GOLD = (235, 190, 60, 255)


def character(name, level, rnd):
    ch = {}
    if name == "builder":
        ch.update(skin=(198, 143, 108), hair=(92, 60, 35), eyes=(70, 45, 30, 255))
        ch["shirt_fn"] = plain((110, 150, 200), rnd)
        ch["pants_fn"] = denim((60, 85, 130), rnd)
        ch["boots"] = (95, 62, 35)
        vest_col = (240, 130, 30) if level < 3 else (245, 150, 30)
        ch["jacket"] = vest(vest_col, stripes=(225, 225, 120, 255) if level >= 2 else None)
        if level >= 3:
            ch["belt"] = (100, 65, 35)
            ch["buckle"] = GOLD
        hat = {1: (240, 200, 50), 2: (245, 150, 40), 3: (238, 238, 232)}[level]
        ch["headwear"] = lambda cv: paint_helmet(cv, hat, stripe=GOLD if level == 3 else None)
        ch["cuff"] = shade((110, 150, 200), 0.85)
    elif name == "farmer":
        ch.update(skin=(235, 190, 150), hair=(220, 180, 90), eyes=(70, 110, 180, 255))
        ch["shirt_fn"] = plaid((120, 160, 80), (80, 115, 55), rnd) if level >= 2 else plain((115, 155, 75), rnd)
        ch["pants_fn"] = denim((70, 100, 160), rnd)
        ch["boots"] = (110, 75, 45)
        ch["jacket"] = overalls((70, 100, 160), GOLD if level == 3 else (200, 200, 205, 255))
        if level >= 2:
            ch["neckerchief"] = (190, 50, 45, 255)
        band = {1: (205, 175, 90), 2: (120, 80, 45), 3: (190, 45, 40)}[level]
        ch["headwear"] = lambda cv: paint_brim_hat(cv, (228, 196, 105), band, weave=True,
                                                   band_trim=GOLD if level == 3 else None)
        ch["cuff"] = shade((115, 155, 75), 0.8)
    elif name == "rancher":
        ch.update(skin=(190, 130, 90), hair=(40, 30, 25), eyes=(60, 40, 25, 255), beard=(45, 32, 26))
        ch["shirt_fn"] = plaid((175, 55, 45), (110, 30, 25), rnd)
        ch["pants_fn"] = denim((75, 90, 120), rnd) if level < 3 else plain((110, 80, 50), rnd)
        ch["boots"] = (85, 55, 30)
        ch["belt"] = (70, 45, 25)
        ch["buckle"] = GOLD if level == 3 else (180, 180, 185, 255)
        if level >= 2:
            ch["jacket"] = vest((125, 85, 50), trim=GOLD if level == 3 else None)
        band = (60, 40, 25) if level < 3 else GOLD[:3]
        ch["headwear"] = lambda cv: paint_brim_hat(cv, (125, 85, 48), band, weave=False)
    elif name == "cook":
        ch.update(skin=(240, 200, 170), hair=(190, 90, 40), eyes=(80, 140, 70, 255))
        ch["shirt_fn"] = plain((238, 238, 235), rnd, noise=3)
        ch["pants_fn"] = checker((45, 45, 50, 255), (220, 220, 220, 255))
        ch["boots"] = (40, 40, 45)
        ch["buttons"] = GOLD if level == 3 else (150, 150, 155, 255)
        ch["collar_open"] = False
        if level >= 2:
            ch["neckerchief"] = (200, 50, 45, 255)
        ch["jacket"] = apron((225, 225, 222), strap=(200, 200, 198))
        ch["headwear"] = lambda cv: paint_toque(cv, (245, 245, 242), trim=GOLD if level == 3 else None)
        ch["cuff"] = (215, 215, 212, 255)
    elif name == "miner":
        ch.update(skin=(120, 80, 55), hair=(30, 25, 22), eyes=(40, 30, 25, 255))
        ch["shirt_fn"] = plain((120, 120, 125), rnd, noise=8)
        ch["pants_fn"] = denim((45, 60, 95), rnd)
        ch["boots"] = (40, 38, 38)
        ch["jacket"] = overalls((45, 60, 95), GOLD if level == 3 else (170, 170, 175, 255))
        ch["soot"] = level == 1
        hat = (230, 170, 40) if level < 3 else (240, 185, 50)
        ch["headwear"] = lambda cv: paint_helmet(cv, hat, stripe=GOLD if level == 3 else None, lamp=True)
        ch["cuff"] = shade((120, 120, 125), 0.8)
    elif name == "lumberjack":
        ch.update(skin=(225, 170, 130), hair=(160, 70, 35), eyes=(60, 90, 60, 255),
                  beard=(165, 75, 35), full_beard=True)
        ch["shirt_fn"] = plaid((180, 40, 35), (45, 30, 30), rnd)
        ch["pants_fn"] = plain((85, 80, 60), rnd)
        ch["boots"] = (100, 65, 35)
        if level >= 2:
            ch["jacket"] = suspenders((70, 45, 25), GOLD if level == 3 else (160, 160, 165, 255))
        beanie = {1: (50, 90, 60), 2: (60, 70, 120), 3: (50, 90, 60)}[level]
        ch["headwear"] = lambda cv: paint_helmet(cv, beanie, stripe=GOLD if level == 3 else (200, 195, 180, 255),
                                                 knit=True, visor=False)
        ch["cuff"] = (45, 30, 30, 255)
    elif name == "blacksmith":
        ch.update(skin=(150, 100, 70), hair=(35, 25, 20), eyes=(45, 30, 20, 255), beard=(40, 28, 22))
        ch["shirt_fn"] = plain((70, 70, 75), rnd)
        ch["pants_fn"] = plain((55, 50, 45), rnd)
        ch["boots"] = (35, 33, 33)
        ch["jacket"] = apron((110, 70, 40), strap=(80, 50, 30))
        ch["gloves"] = (80, 55, 35)
        ch["bandana"] = {1: (170, 45, 40, 255), 2: (50, 80, 150, 255), 3: (30, 30, 30, 255)}[level]
        ch["short_sleeves"] = True
        if level == 3:
            ch["belt"] = (60, 40, 25)
            ch["buckle"] = GOLD
    # Привести цвета к RGBA
    for key in ("skin", "hair", "beard", "boots", "belt"):
        if key in ch and ch[key] is not None and len(ch[key]) == 3:
            ch[key] = (*ch[key], 255)
    return ch


def generate(name, level):
    cv = Canvas(f"{name}-{level}")
    ch = character(name, level, cv.rnd)
    paint_head(cv, ch)
    paint_body(cv, ch)
    sleeve = 4 if ch.get("short_sleeves") else 8
    paint_arm(cv, 40, 16, ch, sleeve, 40, 32)
    paint_arm(cv, 32, 48, ch, sleeve, 48, 48)
    paint_leg(cv, 0, 16, ch)
    paint_leg(cv, 16, 48, ch)
    if "headwear" in ch:
        ch["headwear"](cv)
    return cv.im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in ("builder", "farmer", "rancher", "cook", "miner", "lumberjack", "blacksmith"):
        for level in (1, 2, 3):
            suffix = "" if level == 1 else f"_{level}"
            path = os.path.join(OUT, f"{name}{suffix}.png")
            generate(name, level).save(path)
            print("saved", os.path.normpath(path))


if __name__ == "__main__":
    main()
