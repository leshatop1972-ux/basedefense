"""
Генератор текстур жителей basedefense.
Запуск:  python tools/settler_textures.py      (нужен Pillow: pip install pillow)

Пишет:
  textures/entity/settler/male_1..5.png, female_1..3.png  — внешность (64x64, раскладка скина игрока).
      Выдаётся жителю случайно и не зависит от профессии.
  textures/entity/outfit/<профессия>[ _2 | _3 ].png       — наряд профессии (128x64): одежда поверх
      (левая половина, раскладка скина) и головной убор (правая половина). _2/_3 — для 2-го и 3-го уровня.
Правь списки SKINS и OUTFITS ниже и перезапусти.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "basedefense", "textures", "entity")
T = (0, 0, 0, 0)
GOLD = (235, 190, 60, 255)


def clamp(v):
    return max(0, min(255, int(v)))


def rgba(c):
    return c if len(c) == 4 else (c[0], c[1], c[2], 255)


def shade(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t),
            clamp(a[2] + (b[2] - a[2]) * t), 255)


class Canvas:
    def __init__(self, w, h, seed):
        self.im = Image.new("RGBA", (w, h), T)
        self.px = self.im.load()
        self.w, self.h = w, h
        self.rnd = random.Random(seed)

    def put(self, x, y, c):
        if c is not None and 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = rgba(c)

    def noisy(self, c, a=5):
        n = self.rnd.randint(-a, a)
        return (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)

    @staticmethod
    def faces(u, v, w, h, d):
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def box(self, u, v, w, h, d, fns):
        """fns: {'front': fn, ..., '*': fn}; fn(col, row, width, height, face) -> цвет или None."""
        for name, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            fn = fns.get(name, fns.get("*"))
            if fn is None:
                continue
            for r in range(fh):
                for c in range(fw):
                    self.put(x + c, y + r, fn(c, r, fw, fh, name))


# ---------------------------------------------------------------------------
# Ткани
# ---------------------------------------------------------------------------

def plain(cv, base, a=5):
    return lambda c, r, w, h, f: cv.noisy(base, a)


def plaid(cv, a, b):
    def f(c, r, w, h, face):
        sc, sr = c % 4 == 1, r % 4 == 1
        col = b if sc and sr else (mix(a, b, 0.55) if sc or sr else rgba(a))
        return cv.noisy(col, 4)
    return f


def stripes(cv, a, b):
    return lambda c, r, w, h, f: cv.noisy(a if r % 3 else b, 3)


def denim(cv, base):
    def f(c, r, w, h, face):
        k = 1.06 if (c + r) % 3 == 0 else 0.97
        return cv.noisy(shade(base, k), 4)
    return f


# ---------------------------------------------------------------------------
# Внешность (64x64)
# ---------------------------------------------------------------------------

SKINS = {
    # мужчины
    "male_1": dict(skin=(232, 186, 148), hair=(110, 72, 40), eyes=(70, 110, 160), shirt=(214, 196, 160),
                   pants=(96, 78, 58), shoes=(70, 48, 30), style="short"),
    "male_2": dict(skin=(196, 140, 98), hair=(36, 28, 24), eyes=(70, 45, 30), shirt=(110, 128, 150),
                   pants=(60, 64, 80), shoes=(40, 34, 30), style="short", mustache=True),
    "male_3": dict(skin=(124, 84, 58), hair=(26, 22, 20), eyes=(45, 30, 22), shirt=(225, 222, 210),
                   pants=(70, 70, 76), shoes=(45, 38, 34), style="crop"),
    "male_4": dict(skin=(240, 200, 170), hair=(178, 80, 36), eyes=(80, 130, 70), shirt=(110, 140, 80),
                   pants=(110, 90, 60), shoes=(90, 60, 35), style="short", beard=True),
    "male_5": dict(skin=(214, 162, 122), hair=(165, 165, 160), eyes=(80, 90, 100), shirt=(140, 100, 70),
                   pants=(80, 70, 60), shoes=(60, 45, 35), style="short", beard=True),
    # женщины
    "female_1": dict(skin=(240, 198, 166), hair=(226, 190, 104), eyes=(70, 120, 180), shirt=(170, 200, 225),
                     skirt=(70, 100, 160), shoes=(110, 70, 45), style="braid", female=True),
    "female_2": dict(skin=(206, 150, 112), hair=(82, 50, 30), eyes=(90, 60, 35), shirt=(235, 232, 225),
                     skirt=(150, 60, 50), shoes=(70, 45, 30), style="long", female=True, pattern="stripes"),
    "female_3": dict(skin=(132, 90, 62), hair=(28, 22, 20), eyes=(50, 34, 24), shirt=(230, 195, 90),
                     skirt=(80, 120, 80), shoes=(60, 40, 30), style="bun", female=True),
}


def paint_skin(name, d):
    cv = Canvas(64, 64, name)
    skin, hair = rgba(d["skin"]), rgba(d["hair"])
    dark_skin = shade(skin, 0.86)
    eye = rgba(d["eyes"])
    female = d.get("female", False)
    style = d["style"]
    brow = shade(hair, 0.7) if hair[0] < 150 else shade(hair, 0.55)
    beard = shade(hair, 0.95) if d.get("beard") else None
    long_hair = style in ("long", "braid")

    # ----- голова -----
    def front(c, r, w, h, f):
        if r == 0:
            return cv.noisy(hair, 4)
        if r == 1:
            if style == "crop":
                return cv.noisy(hair, 4) if c in (0, 7) else cv.noisy(skin, 3)
            return cv.noisy(hair, 4) if (c not in (3, 4) or style == "short") else cv.noisy(skin, 3)
        if r == 2:
            return cv.noisy(hair, 4) if c in (0, 7) else cv.noisy(skin, 3)
        if r == 3:
            if female:
                return shade(brow, 1.1) if c in (2, 5) else (cv.noisy(hair, 4) if c in (0, 7) else cv.noisy(skin, 3))
            return brow if c in (1, 2, 5, 6) else cv.noisy(skin, 3)
        if r == 4:
            if c in (1, 6):
                return (245, 245, 245, 255)
            if c in (2, 5):
                return eye
            if female and c in (0, 7):
                return cv.noisy(hair, 4) if long_hair else (40, 30, 30, 255)
            return cv.noisy(skin, 3)
        if beard is not None:
            if r == 5 and c in (2, 3, 4, 5):
                return shade(beard, 0.9)
            if r == 6 and c in (3, 4):
                return (120, 60, 50, 255)
            if r >= 6 or (r == 5 and c in (0, 7)):
                return cv.noisy(beard, 4)
        if d.get("mustache") and r == 5 and c in (2, 3, 4, 5):
            return shade(hair, 1.0)
        if r == 5:
            if c in (3, 4):
                return dark_skin
            if c in (1, 6):
                return mix(skin, (225, 120, 115), 0.28 if female else 0.15)
            if female and long_hair and c in (0, 7):
                return cv.noisy(hair, 4)
            return cv.noisy(skin, 3)
        if r == 6:
            if c in (3, 4):
                return (196, 92, 96, 255) if female else (150, 82, 72, 255)
            if female and long_hair and c in (0, 7):
                return cv.noisy(hair, 4)
            return cv.noisy(skin, 3)
        return cv.noisy(dark_skin, 3)

    def side(c, r, w, h, f):
        back_cols = (0, 1, 2) if f == "right" else (5, 6, 7)
        face_col = 7 if f == "right" else 0
        if r <= 1:
            return cv.noisy(hair, 4)
        if long_hair or style == "bun":
            if c in back_cols or r <= 2 or (c != face_col and r <= 7 and long_hair):
                return cv.noisy(hair, 4)
        if c in back_cols[:2] and r <= 5:
            return cv.noisy(hair, 4)
        if r == 2 and c != face_col:
            return cv.noisy(hair, 4)
        if beard is not None and r >= 5 and ((f == "right" and c >= 4) or (f == "left" and c <= 3)):
            return cv.noisy(beard, 4)
        if r == 4 and c in (3, 4):
            return dark_skin
        return cv.noisy(skin, 3)

    def back(c, r, w, h, f):
        if long_hair or r <= 5:
            return cv.noisy(hair, 4)
        return cv.noisy(dark_skin, 3)

    cv.box(0, 0, 8, 8, 8, {"top": lambda c, r, w, h, f: cv.noisy(hair, 4),
                            "bottom": lambda c, r, w, h, f: cv.noisy(dark_skin, 3),
                            "front": front, "right": side, "left": side, "back": back})

    # ----- второй слой головы: объём волос -----
    hl = shade(hair, 1.1)

    def h_front(c, r, w, h, f):
        if r == 0:
            return cv.noisy(hl, 4)
        if r == 1 and c in (0, 1, 6, 7) and style != "crop":
            return cv.noisy(hl, 4)
        if long_hair and c in (0, 7) and r <= 6:
            return cv.noisy(hl, 4)
        return None

    def h_side(c, r, w, h, f):
        back_cols = (0, 1, 2, 3) if f == "right" else (4, 5, 6, 7)
        if r <= 1:
            return cv.noisy(hl, 4)
        if long_hair and r <= 7:
            return cv.noisy(hl, 4) if c in back_cols or c in ((6, 7) if f == "right" else (0, 1)) else None
        if c in back_cols[:2] and r <= 3 and style != "crop":
            return cv.noisy(hl, 4)
        return None

    def h_back(c, r, w, h, f):
        if style == "bun" and r <= 3 and 2 <= c <= 5:
            return cv.noisy(shade(hair, 0.9), 4)      # пучок
        if long_hair:
            return cv.noisy(hl, 4)
        return cv.noisy(hl, 4) if r <= 3 else None

    cv.box(32, 0, 8, 8, 8, {"top": lambda c, r, w, h, f: cv.noisy(hl, 4),
                             "front": h_front, "right": h_side, "left": h_side, "back": h_back})

    # ----- туловище -----
    shirt = (stripes(cv, rgba(d["shirt"]), shade(d["shirt"], 0.75)) if d.get("pattern") == "stripes"
             else plain(cv, rgba(d["shirt"])))
    lower = rgba(d["skirt"]) if female else rgba(d["pants"])

    def body(c, r, w, h, f):
        if f == "top":
            return shirt(c, r, w, h, f)
        if f == "bottom":
            return cv.noisy(lower, 4)
        if f == "front" and r == 0 and c in (3, 4):
            return skin if not female else mix(skin, shade(d["shirt"], 0.9), 0.0)
        if f == "front" and not female and c in (3, 4) and r in (2, 4, 6):
            return shade(d["shirt"], 0.75)            # пуговицы
        if r == 8:
            return shade(lower, 0.75) if not female else cv.noisy(shade(d["shirt"], 0.85), 3)  # пояс
        if r >= 9:
            return cv.noisy(lower, 4)
        return shirt(c, r, w, h, f)

    cv.box(16, 16, 8, 12, 4, {"*": body})

    # второй слой туловища: длинные волосы по спине / коса
    if long_hair:
        def jb(c, r, w, h, f):
            if f != "back":
                return None
            if style == "braid":
                if c in (3, 4) and r <= 7:
                    return cv.noisy(shade(hair, 0.95 if r % 2 else 1.08), 3)
                if r == 8 and c in (3, 4):
                    return (200, 60, 60, 255)          # лента на косе
                return None
            return cv.noisy(hl, 4) if r <= 4 and 1 <= c <= 6 else None
        cv.box(16, 32, 8, 12, 4, {"*": jb})

    # ----- руки -----
    sleeve = 9 if female else 8

    def arm(c, r, w, h, f):
        if f == "top":
            return shirt(c, r, w, h, f)
        if f == "bottom":
            return dark_skin
        if r < sleeve:
            return shirt(c, r, w, h, f)
        return cv.noisy(dark_skin if r >= 10 else skin, 3)

    cv.box(40, 16, 4, 12, 4, {"*": arm})
    cv.box(32, 48, 4, 12, 4, {"*": arm})
    # закатанный рукав (второй слой)
    cuff = shade(d["shirt"], 0.85)
    cv.box(40, 32, 4, 12, 4, {"*": lambda c, r, w, h, f: cuff if f not in ("top", "bottom") and r == sleeve - 1 else None})
    cv.box(48, 48, 4, 12, 4, {"*": lambda c, r, w, h, f: cuff if f not in ("top", "bottom") and r == sleeve - 1 else None})

    # ----- ноги -----
    shoes = rgba(d["shoes"])

    def leg(c, r, w, h, f):
        if f == "top":
            return cv.noisy(lower, 4)
        if f == "bottom":
            return shade(shoes, 0.6)
        if r >= 10:
            return cv.noisy(shade(shoes, 0.7) if r == 11 else shoes, 3)
        if female:
            if r <= 6:
                return cv.noisy(lower, 4)            # юбка
            return cv.noisy(shade(d["skin"], 0.95) if r != 9 else (235, 235, 230), 3)  # ноги / носки
        return cv.noisy(lower, 4)

    cv.box(0, 16, 4, 12, 4, {"*": leg})
    cv.box(16, 48, 4, 12, 4, {"*": leg})
    if female:
        # юбка «расширяется» вторым слоем
        def flare(c, r, w, h, f):
            if f in ("top", "bottom"):
                return None
            if r <= 6:
                return cv.noisy(shade(lower, 1.05 if r < 6 else 0.8), 4)
            return None
        cv.box(0, 32, 4, 12, 4, {"*": flare})
        cv.box(0, 48, 4, 12, 4, {"*": flare})
    return cv.im


# ---------------------------------------------------------------------------
# Наряды профессий (128x64)
# ---------------------------------------------------------------------------

def hat_brim(cv, base, band, weave, trim=None):
    dark = shade(base, 0.8)

    def brim(c, r, w, h, f):
        if f == "top":
            col = shade(base, 0.92) if c in (0, 13) or r in (0, 13) else rgba(base)
            if weave and (c + r) % 2 == 0:
                col = shade(col, 1.07)
            return cv.noisy(col, 3)
        return cv.noisy(dark if f == "bottom" else shade(base, 0.85), 3)
    cv.box(64, 0, 14, 1, 14, {"*": brim})

    def crown(c, r, w, h, f):
        if f == "top":
            col = shade(base, 1.05)
            if weave and (c + r) % 2 == 0:
                col = shade(col, 1.06)
            if not weave and 2 <= c <= 5 and 2 <= r <= 5:
                col = shade(base, 0.9)
            return cv.noisy(col, 3)
        if f == "bottom":
            return cv.noisy(dark, 3)
        if r == 2:
            return trim if trim and f == "front" and c in (3, 4) else cv.noisy(band, 3)
        col = rgba(base)
        if weave and (c + r) % 2 == 0:
            col = shade(col, 1.06)
        return cv.noisy(col, 3)
    cv.box(64, 16, 8, 3, 8, {"*": crown})


def hat_toque(cv, base, trim=None):
    def f(c, r, w, h, face):
        if face == "top":
            return cv.noisy(shade(base, 1.02), 2)
        if face == "bottom":
            return cv.noisy(shade(base, 0.85), 2)
        if r == 5:
            return trim or shade(base, 0.88)
        return cv.noisy(shade(base, 0.92) if c % 2 else rgba(base), 2)
    cv.box(96, 16, 8, 6, 8, {"*": f})


def hat_helmet(cv, base, stripe=None, knit=False, visor=True, lamp=False):
    def f(c, r, w, h, face):
        if face == "top":
            if stripe and c in (3, 4):
                return stripe
            col = shade(base, 1.08) if (c in (3, 4) and not knit) else rgba(base)
            if knit and r % 2 == 0:
                col = shade(col, 0.92)
            return cv.noisy(col, 4)
        if face == "bottom":
            return cv.noisy(shade(base, 0.7), 3)
        if knit:
            col = shade(base, 0.9) if c % 2 == 0 else rgba(base)
            if r == 2:
                col = shade(base, 0.8)
            if stripe and r == 1:
                col = stripe
            return cv.noisy(col, 3)
        if stripe and face in ("front", "back") and c in (3, 4):
            return stripe
        return cv.noisy(shade(base, 0.95) if r == 2 else rgba(base), 4)
    cv.box(64, 32, 8, 3, 8, {"*": f})
    if visor:
        cv.box(96, 32, 8, 1, 3, {"*": lambda c, r, w, h, face: cv.noisy(shade(base, 0.88), 3)})
    if lamp:
        cv.box(96, 40, 2, 2, 1, {"*": lambda c, r, w, h, face:
                                 (255, 250, 200, 255) if face == "front" else (110, 110, 115, 255)})


def body_vest(cv, color, stripe=None, trim=None):
    def f(c, r, w, h, face):
        if face in ("top", "bottom") or r > 8:
            return None
        if face == "front" and c in (3, 4):
            return None
        if stripe and r in (4, 7):
            return stripe
        if trim and face == "front" and c == 2 and r in (2, 5):
            return trim
        return cv.noisy(color, 4)
    cv.box(16, 16, 8, 12, 4, {"*": f})


def body_apron(cv, color, strap, legs=True, pocket=True):
    def f(c, r, w, h, face):
        if face == "front":
            if r <= 1:
                return cv.noisy(strap, 3) if c in (2, 5) else None
            if 1 <= c <= 6:
                if pocket and r == 6 and c in (3, 4):
                    return shade(color, 0.85)
                return cv.noisy(color, 3)
            return None
        if face in ("back", "left", "right"):
            return shade(strap, 0.9) if r == 8 else None
        return None
    cv.box(16, 16, 8, 12, 4, {"*": f})
    if legs:
        for u, v in ((0, 16), (16, 48)):
            cv.box(u, v, 4, 12, 4, {"front": lambda c, r, w, h, face: cv.noisy(color, 3) if r <= 5 else None})


def body_straps(cv, color, buckle):
    def f(c, r, w, h, face):
        if face == "front" and c in (1, 6) and r <= 8:
            return buckle if r == 8 else cv.noisy(color, 3)
        if face == "back" and r <= 8 and c in (1 + r // 3, 6 - r // 3):
            return cv.noisy(color, 3)
        if face in ("front", "back", "left", "right") and r == 8:
            return cv.noisy(shade(color, 0.9), 3)
        return None
    cv.box(16, 16, 8, 12, 4, {"*": f})


def body_bib(cv, color, buttons):
    def f(c, r, w, h, face):
        if face == "front":
            if r <= 2:
                return cv.noisy(color, 4) if c in (1, 6) else None
            if r == 3 and c in (1, 6):
                return buttons
            return cv.noisy(color, 4) if 1 <= c <= 6 and r <= 8 else None
        if face == "back" and r <= 6 and c in (1 + r // 2, 6 - r // 2):
            return cv.noisy(color, 4)
        return None
    cv.box(16, 16, 8, 12, 4, {"*": f})


def neckerchief(cv, color):
    for x in range(20, 28):
        cv.put(x, 20, color)
    for x in (22, 23, 24, 25):
        cv.put(x, 21, shade(color, 0.9))
    cv.put(23, 22, shade(color, 0.8))
    cv.put(24, 22, shade(color, 0.8))


def gloves(cv, color):
    for u, v in ((40, 16), (32, 48)):
        cv.box(u, v, 4, 12, 4, {"*": lambda c, r, w, h, face:
                                cv.noisy(color, 4) if face == "bottom" or (face != "top" and r >= 8) else None})


def belt(cv, color, buckle):
    def f(c, r, w, h, face):
        if face in ("top", "bottom") or r != 8:
            return None
        return buckle if face == "front" and c in (3, 4) else cv.noisy(color, 3)
    cv.box(16, 16, 8, 12, 4, {"*": f})


def bandana(cv, color):
    def f(c, r, w, h, face):
        if face == "top":
            return cv.noisy(color, 4)
        if face == "bottom":
            return None
        if r <= 1:
            return cv.noisy(color, 4)
        if face == "back" and r in (2, 3) and c in (3, 4):
            return shade(color, 0.85)
        return None
    cv.box(0, 0, 8, 8, 8, {"*": f})


def paint_outfit(prof, level):
    cv = Canvas(128, 64, f"{prof}-{level}")
    gold = GOLD if level == 3 else None
    if prof == "builder":
        # жёлтая каска + оранжевый жилет (со 2 ур. — светоотражающие полосы, на 3 — пояс с золотой пряжкой)
        hat_helmet(cv, (240, 200, 50), stripe=gold)
        body_vest(cv, (240, 130, 30), stripe=(230, 230, 130, 255) if level >= 2 else None)
        if level == 3:
            belt(cv, (100, 65, 35), GOLD)
    elif prof == "farmer":
        band = {1: (205, 175, 90), 2: (120, 80, 45), 3: (190, 45, 40)}[level]
        hat_brim(cv, (228, 196, 105), band, weave=True, trim=gold)
        body_bib(cv, (70, 100, 160), gold or (200, 200, 205, 255))
        if level >= 2:
            neckerchief(cv, (190, 50, 45, 255))
    elif prof == "rancher":
        hat_brim(cv, (125, 85, 48), GOLD[:3] if level == 3 else (60, 40, 25), weave=False)
        neckerchief(cv, (175, 45, 40, 255))
        if level >= 2:
            body_vest(cv, (125, 85, 50), trim=gold)
        belt(cv, (70, 45, 25), gold or (180, 180, 185, 255))
    elif prof == "cook":
        hat_toque(cv, (246, 246, 243), trim=gold)
        body_apron(cv, (236, 236, 232), (210, 210, 206))
        if level >= 2:
            neckerchief(cv, (200, 50, 45, 255))
    elif prof == "miner":
        hat_helmet(cv, (230, 170, 40), stripe=gold, lamp=True)
        body_straps(cv, (45, 60, 95), gold or (170, 170, 175, 255))
        if level >= 2:
            gloves(cv, (90, 70, 45))
    elif prof == "lumberjack":
        beanie = {1: (50, 90, 60), 2: (160, 45, 40), 3: (50, 90, 60)}[level]
        hat_helmet(cv, beanie, stripe=gold or (200, 195, 180, 255), knit=True, visor=False)
        body_straps(cv, (70, 45, 25), gold or (160, 160, 165, 255))
    elif prof == "blacksmith":
        bandana(cv, {1: (170, 45, 40), 2: (50, 80, 150), 3: (30, 30, 30)}[level])
        body_apron(cv, (110, 70, 40), (80, 50, 30))
        gloves(cv, (80, 55, 35))
        if level == 3:
            belt(cv, (60, 40, 25), GOLD)
    return cv.im


def main():
    skin_dir = os.path.join(ROOT, "settler")
    outfit_dir = os.path.join(ROOT, "outfit")
    os.makedirs(skin_dir, exist_ok=True)
    os.makedirs(outfit_dir, exist_ok=True)
    for name, d in SKINS.items():
        paint_skin(name, d).save(os.path.join(skin_dir, name + ".png"))
        print("skin", name)
    for prof in ("builder", "farmer", "rancher", "cook", "miner", "lumberjack", "blacksmith"):
        for level in (1, 2, 3):
            suffix = "" if level == 1 else f"_{level}"
            paint_outfit(prof, level).save(os.path.join(outfit_dir, prof + suffix + ".png"))
        print("outfit", prof)


if __name__ == "__main__":
    main()
