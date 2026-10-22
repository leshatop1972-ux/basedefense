"""
Генератор текстур жителей basedefense (стиль — как у жителей MineColonies).
Запуск:  python tools/settler_textures.py      (нужен Pillow: pip install pillow)

Пишет textures/entity/settler/male_1..5.png и female_1..3.png (128x64).
Раскладка совпадает с SettlerEntityModel:
  левая половина — как скин игрока (голова, тело, широкие руки, ноги + вторые слои);
  (64,0) пряди по спине, (82,0) хвост, (96,0) юбка, (96,13) низ юбки,
  (64,16)/(64,32) тонкая правая рука и рукав, (80,16)/(80,32) тонкая левая, (64,48)/(64,52) грудь.
Внешность от профессии не зависит. Чтобы поменять жителя — правь словари в CHARACTERS.

Приёмы «качества»: свет сверху-спереди (у каждой грани своя яркость), вертикальный градиент,
упорядоченный шум (ткань), затемнение краёв, складки, швы, пуговицы, второй слой для объёма.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "basedefense", "textures", "entity", "settler")
T = (0, 0, 0, 0)

# Яркость граней (свет сверху и спереди)
FACE_LIGHT = {"top": 1.10, "front": 1.0, "right": 0.93, "left": 0.93, "back": 0.86, "bottom": 0.72}


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(c):
    return (c[0], c[1], c[2])


def mul(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k))


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t))


def hue_shift_dark(c, k):
    """Тень с лёгким уходом в холодный оттенок (живее, чем просто темнее)."""
    d = mul(c, k)
    return (clamp(d[0] * 0.97), clamp(d[1] * 0.98), clamp(d[2] * 1.04))


def noise(x, y, seed=0):
    """Упорядоченный шум -1..1 (одинаковый при каждом запуске)."""
    h = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h >> 8) % 1000) / 500.0 - 1.0


class Canvas:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (128, 64), T)
        self.px = self.im.load()
        self.seed = seed

    def put(self, x, y, c):
        if c is not None and 0 <= x < 128 and 0 <= y < 64:
            self.px[x, y] = (c[0], c[1], c[2], 255)

    def get(self, x, y):
        return self.px[x, y]

    @staticmethod
    def faces(u, v, w, h, d):
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def box(self, u, v, w, h, d, fn):
        """fn(face, col, row, fw, fh, X, Y) -> цвет или None; X, Y — абсолютные координаты пикселя."""
        for face, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            for r in range(fh):
                for c in range(fw):
                    self.put(x + c, y + r, fn(face, c, r, fw, fh, x + c, y + r))


# ---------------------------------------------------------------------------
# Материалы: возвращают цвет пикселя с учётом света, градиента и фактуры
# ---------------------------------------------------------------------------

def cloth(base, face, c, r, fw, fh, X, Y, seed, rough=0.045, grad=0.08, edge=True):
    k = FACE_LIGHT[face]
    if face in ("front", "back", "left", "right"):
        k *= 1.0 - grad * (r / max(1, fh - 1))
        if edge and (c == 0 or c == fw - 1):
            k *= 0.93
    k *= 1.0 + rough * noise(X, Y, seed)
    return hue_shift_dark(base, k) if k < 0.97 else mul(base, k)


def skin_px(base, face, c, r, fw, fh, X, Y, seed):
    k = FACE_LIGHT[face] * (1.0 + 0.02 * noise(X, Y, seed))
    return mul(base, k)


def hair_px(base, face, c, r, fw, fh, X, Y, seed):
    """Волосы: пряди (вертикальные полосы разной яркости) и блик сверху."""
    strand = ((c * 3 + (r // 2)) % 4)
    k = FACE_LIGHT[face] * (0.92 + 0.06 * strand)
    if face == "top":
        k *= 1.05 if (c + r) % 3 else 1.12
    k *= 1.0 + 0.03 * noise(X, Y, seed + 7)
    return mul(base, k)


# ---------------------------------------------------------------------------
# Персонажи
# ---------------------------------------------------------------------------

CHARACTERS = {
    "male_1": dict(skin=(233, 190, 155), hair=(122, 76, 44), hair_style="wavy", eyes=(64, 110, 160),
                   shirt=(226, 214, 190), outer="coat", outer_color=(76, 90, 108), buttons=(196, 160, 82),
                   trousers=(72, 54, 42), boots=(46, 36, 30), boot_style="tall"),
    "male_2": dict(skin=(112, 76, 54), hair=(30, 25, 23), hair_style="short", eyes=(52, 34, 24), beard="full",
                   shirt=(222, 210, 182), sleeves="rolled", outer="waistcoat", outer_color=(98, 64, 42),
                   buttons=(70, 50, 34), belt=(44, 34, 28), buckle=(200, 164, 80),
                   trousers=(60, 58, 62), boots=(42, 34, 30), boot_style="socks", socks=(222, 214, 196)),
    "male_3": dict(skin=(204, 150, 112), hair=(64, 44, 32), hair_style="side", eyes=(80, 56, 36),
                   shirt=(236, 232, 222), outer="vest", outer_color=(120, 84, 54), buttons=(206, 170, 90),
                   cravat=(130, 44, 40), trousers=(98, 72, 48), boots=(32, 28, 26), boot_style="shoes",
                   socks=(230, 226, 214)),
    "male_4": dict(skin=(241, 204, 176), hair=(184, 88, 42), hair_style="messy", eyes=(84, 128, 72), beard="short",
                   shirt=(94, 114, 72), sleeves="rolled", belt=(176, 150, 102), rope_belt=True,
                   trousers=(152, 126, 92), boots=(94, 64, 40), boot_style="tall"),
    "male_5": dict(skin=(214, 164, 124), hair=(172, 170, 164), hair_style="receding", eyes=(90, 100, 112),
                   beard="mustache", shirt=(122, 138, 152), outer="suspenders", outer_color=(62, 44, 32),
                   buttons=(180, 150, 90), scarf=(150, 52, 42), trousers=(92, 76, 60), boots=(50, 40, 34),
                   boot_style="tall"),
    "female_1": dict(skin=(242, 204, 174), hair=(230, 196, 112), hair_style="ponytail", eyes=(70, 120, 180),
                     female=True, shirt=(234, 226, 208), outer="bodice", outer_color=(108, 126, 88),
                     lacing=(230, 220, 196), skirt=(122, 88, 62), apron=(226, 218, 200),
                     boots=(96, 66, 44), socks=(212, 202, 188)),
    "female_2": dict(skin=(208, 152, 114), hair=(80, 50, 32), hair_style="long", eyes=(92, 62, 38),
                     female=True, shirt=(238, 236, 230), outer="bodice", outer_color=(70, 96, 128),
                     lacing=(214, 206, 186), skirt=(54, 68, 98), hem=(150, 160, 184),
                     boots=(36, 30, 28), socks=(70, 64, 60)),
    "female_3": dict(skin=(126, 86, 60), hair=(28, 23, 21), hair_style="bun", eyes=(54, 36, 26),
                     female=True, shirt=(228, 216, 192), outer="bodice", outer_color=(158, 86, 64),
                     lacing=(232, 210, 170), skirt=(126, 72, 54), apron=(214, 196, 160), scarf=(196, 160, 72),
                     boots=(62, 44, 32), socks=(186, 170, 150)),
}


# ---------------------------------------------------------------------------
# Голова
# ---------------------------------------------------------------------------

def paint_head(cv, ch):
    skin, hair, eyes = ch["skin"], ch["hair"], ch["eyes"]
    style = ch["hair_style"]
    female = ch.get("female", False)
    beard = ch.get("beard")
    s = cv.seed
    shadow_skin = hue_shift_dark(skin, 0.86)
    brow = mul(hair, 0.62) if sum(hair) < 450 else mul(hair, 0.5)
    beard_col = mul(hair, 0.95)
    long_sides = style in ("long",)

    def hairline(c, r):
        """Есть ли волосы на лбу в этой клетке лица."""
        if r == 0:
            return True
        if style == "receding":
            return r == 0 or (r == 1 and c in (0, 7))
        if style == "short":
            return r == 1 and c not in (3, 4)
        if style == "side":
            return r == 1 and c not in (5, 6) or (r == 2 and c in (0, 1))
        if style == "wavy":
            return r == 1 or (r == 2 and c in (0, 1, 7))
        if style == "messy":
            return r == 1 or (r == 2 and c in (0, 2, 5, 7))
        if style in ("ponytail", "bun"):
            return r == 1 and c in (0, 1, 2, 5, 6, 7)
        if style == "long":
            return r == 1 or (r == 2 and c in (0, 7))
        return r == 1

    def face_px(c, r, X, Y):
        if hairline(c, r):
            return hair_px(hair, "front", c, r, 8, 8, X, Y, s)
        if long_sides and c in (0, 7) and r <= 6:
            return hair_px(hair, "front", c, r, 8, 8, X, Y, s)
        base = skin_px(skin, "front", c, r, 8, 8, X, Y, s)
        # Боковые колонки лица и подбородок — в тени
        if c in (0, 7):
            base = mix(base, shadow_skin, 0.45)
        if r == 7:
            base = mix(base, shadow_skin, 0.55)
        # Брови
        if r == 3:
            if female and c in (1, 2, 5, 6):
                return mix(brow, skin, 0.25) if c in (2, 5) else mix(brow, skin, 0.55)
            if not female and c in (1, 2, 5, 6):
                return brow if c in (2, 5) or style != "receding" else mix(brow, skin, 0.3)
        # Глаза: белок + радужка (внутри), у женщин — ресницы снаружи
        if r == 4:
            if c in (1, 6):
                return (238, 238, 236)
            if c in (2, 5):
                return eyes
            if female and c in (0, 7):
                return (52, 38, 34)
        # Нос: свет слева, тень справа; румянец
        if r == 5:
            if c == 3:
                return mix(base, (255, 230, 210), 0.12)
            if c == 4:
                return mix(base, shadow_skin, 0.55)
            if c in (1, 6):
                return mix(base, (222, 120, 112), 0.30 if female else 0.14)
        # Рот
        if r == 6 and c in (3, 4):
            return mix(skin, (176, 82, 86), 0.7) if female else mix(skin, (120, 62, 54), 0.5)
        # Борода и усы
        if beard == "mustache" and r == 5 and c in (2, 5) or beard == "mustache" and r == 6 and c in (2, 5):
            return beard_col
        if beard == "mustache" and r == 5 and c in (3, 4):
            return mul(beard_col, 0.95)
        if beard in ("full", "short"):
            if r == 5 and c in (2, 3, 4, 5):
                return mul(beard_col, 0.9 if c in (3, 4) else 1.0)
            if r == 6 and c in (3, 4):
                return (110, 58, 50)
            if r == 6 or r == 7:
                if beard == "full" or c not in (1, 2, 5, 6) or r == 7:
                    return hair_px(beard_col, "front", c, r, 8, 8, X, Y, s + 3)
            if beard == "full" and r == 5 and c in (0, 1, 6, 7):
                return hair_px(beard_col, "front", c, r, 8, 8, X, Y, s + 3)
        return base

    def side_px(face, c, r, X, Y):
        back_cols = (0, 1, 2) if face == "right" else (5, 6, 7)
        face_col = 7 if face == "right" else 0
        near = (5, 6, 7) if face == "right" else (0, 1, 2)
        if r <= 1:
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if style == "long" and r <= 7:
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if style == "receding":
            if c in back_cols and r <= 4:
                return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        elif c in back_cols and r <= 5 or (r == 2 and c != face_col) or (c not in near and r <= 3):
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if beard in ("full", "short") and r >= 4 and c in near:
            return hair_px(beard_col, face, c, r, 8, 8, X, Y, s + 3)
        if r in (4, 5) and c in (3, 4):
            return mix(skin_px(skin, face, c, r, 8, 8, X, Y, s), shadow_skin, 0.6)   # ухо
        px = skin_px(skin, face, c, r, 8, 8, X, Y, s)
        return mix(px, shadow_skin, 0.3) if r >= 6 else px

    def head_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return hair_px(hair, face, c, r, fw, fh, X, Y, s)
        if face == "bottom":
            return shadow_skin
        if face == "front":
            return face_px(c, r, X, Y)
        if face == "back":
            if style == "long" or r <= (4 if style == "receding" else 5):
                return hair_px(hair, face, c, r, fw, fh, X, Y, s)
            return mix(skin, shadow_skin, 0.6)
        return side_px(face, c, r, X, Y)

    cv.box(0, 0, 8, 8, 8, head_fn)

    # Второй слой: объём волос (и платок/пучок)
    hl = mul(hair, 1.08)
    scarf = ch.get("scarf") if female else None

    def layer_fn(face, c, r, fw, fh, X, Y):
        if scarf is not None:
            # Платок на голове у женщины
            if face == "top" or (face != "bottom" and r <= 1) or (face == "back" and r <= 3):
                k = 1.0 if (c + r) % 4 else 0.9
                return cloth(mul(scarf, k), face, c, r, fw, fh, X, Y, s + 11, edge=False)
            if face == "back" and r in (4, 5) and c in (3, 4):
                return mul(scarf, 0.85)               # узелок
            return None
        if face == "top":
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        if face == "bottom":
            return None
        if face == "front":
            if r == 0:
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style in ("wavy", "messy") and r == 1 and c in (0, 1, 3, 6, 7):
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "side" and r == 1 and c in (0, 1, 2, 3):
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "long" and c in (0, 7) and r <= 6:
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            return None
        if face == "back":
            if style == "bun" and 1 <= r <= 4 and 2 <= c <= 5:
                return hair_px(mul(hair, 0.95), face, c, r, fw, fh, X, Y, s + 2)
            if style == "ponytail" and r <= 3 and 3 <= c <= 4:
                return (170, 60, 60) if r == 3 else hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "long":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "receding":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if 2 <= r <= 3 else None
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if r <= 3 else None
        # бока
        back_cols = (0, 1, 2, 3) if face == "right" else (4, 5, 6, 7)
        if style == "receding":
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if c in back_cols and 2 <= r <= 3 else None
        if r == 0 or (r == 1 and style != "short"):
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        if style == "long" and r <= 7:
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        if style in ("wavy", "messy") and c in back_cols and r <= 3:
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        return None

    cv.box(32, 0, 8, 8, 8, layer_fn)

    # Пряди по спине и хвост (отдельные части модели)
    cv.box(64, 0, 8, 7, 1, lambda face, c, r, fw, fh, X, Y: hair_px(
        mul(hair, 1.0 - 0.02 * r), face if face != "front" else "back", c, r, fw, fh, X, Y, s + 4))

    def tail(face, c, r, fw, fh, X, Y):
        if r == 0 and face not in ("top", "bottom"):
            return (176, 64, 60)                       # лента
        return hair_px(mul(hair, 1.0 - 0.03 * r), face, c, r, fw, fh, X, Y, s + 5)
    cv.box(82, 0, 2, 5, 2, tail)


# ---------------------------------------------------------------------------
# Туловище
# ---------------------------------------------------------------------------

def paint_body(cv, ch):
    s = cv.seed
    skin = ch["skin"]
    female = ch.get("female", False)
    shirt = ch["shirt"]
    outer = ch.get("outer")
    oc = ch.get("outer_color")
    trousers = ch.get("trousers", ch.get("skirt"))
    belt = ch.get("belt")
    buckle = ch.get("buckle")
    buttons = ch.get("buttons")

    def base_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return cloth(shirt, face, c, r, fw, fh, X, Y, s)
        if face == "bottom":
            return cloth(trousers, face, c, r, fw, fh, X, Y, s)
        # Ворот: открытая шея
        if face == "front" and r == 0 and c in (3, 4):
            return mul(skin, 0.9)
        if face == "front" and r == 1 and c in (3, 4) and not ch.get("cravat"):
            return mul(skin, 0.82)
        # Низ: брюки (у мужчин) — с ремнём
        if not female and r >= 9:
            px = cloth(trousers, face, c, r, fw, fh, X, Y, s + 1)
            if face == "front" and r >= 10 and c in (3, 4):
                return mul(px, 0.9)                     # шов ширинки
            return px
        if belt is not None and r == 8:
            if ch.get("rope_belt"):
                return mul(belt, 1.06 if (c + X) % 2 else 0.88)
            if face == "front" and buckle and c in (3, 4):
                return buckle if c == 3 else mul(buckle, 0.8)
            return cloth(belt, face, c, r, fw, fh, X, Y, s + 2, rough=0.03)
        px = cloth(shirt, face, c, r, fw, fh, X, Y, s, rough=0.055, grad=0.07)
        # Складки рубашки
        if face == "front" and (c, r) in ((2, 6), (5, 5), (2, 7), (5, 6)):
            px = hue_shift_dark(px, 0.9)
        if face == "front" and c in (3, 4) and r >= 2 and r % 2 == 0 and not female:
            px = mul(px, 0.92)                          # планка с пуговицами
        if female and r == 9:
            return hue_shift_dark(px, 0.88)
        return px

    cv.box(16, 16, 8, 12, 4, base_fn)

    # Шейный платок у мужчины / галстук
    if ch.get("cravat"):
        cr = ch["cravat"]
        for (x, y) in ((23, 21), (24, 21), (23, 22), (24, 22), (23, 23)):
            cv.put(x, y, mul(cr, 1.0 if y < 23 else 0.85))
    if ch.get("scarf") and not female:
        sc = ch["scarf"]
        for x in range(20, 28):
            cv.put(x, 20, mul(sc, 1.0 if x % 2 else 0.9))
        for (x, y) in ((22, 21), (23, 21), (24, 21), (25, 21), (23, 22), (24, 22)):
            cv.put(x, y, mul(sc, 0.92))

    # ----- второй слой туловища -----
    def coat_fn(face, c, r, fw, fh, X, Y):
        """Сюртук нараспашку: полы до бёдер, лацканы, медные пуговицы."""
        if face == "bottom":
            return None
        if face == "top":
            return cloth(oc, face, c, r, fw, fh, X, Y, s + 6)
        if face == "front":
            if c in (3, 4):
                return None                              # рубашка видна
            px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6)
            if r <= 3 and c in (2, 5):
                return mul(px, 1.18)                     # лацкан
            if r <= 1 and c in (1, 6):
                return mul(px, 1.1)
            if c == 2 and r in (4, 6, 8) and buttons:
                return buttons
            if r == 8 and c in (0, 1, 6, 7):
                return mul(px, 0.85)                     # карманы
            return px
        px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6)
        if face == "back" and c in (3, 4) and r >= 8:
            return mul(px, 0.82)                         # разрез сзади
        return px

    def waistcoat_fn(face, c, r, fw, fh, X, Y):
        """Жилет: закрыт, V-вырез, пуговицы, кончается на поясе."""
        if face in ("top", "bottom") or r > 8:
            return None
        if face == "front":
            if r <= 2 and c in (3, 4) or (r == 0 and c in (2, 5)):
                return None
            px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6, rough=0.06)
            if c in (3, 4) and r % 2 == 1 and buttons:
                return buttons if c == 3 else mul(buttons, 0.8)
            if (c, r) in ((1, 5), (6, 5)):
                return mul(px, 0.82)                     # кармашки
            return px
        if face == "back":
            px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6, rough=0.02)
            return mul(px, 0.9) if r == 6 else px        # хлястик
        return cloth(oc, face, c, r, fw, fh, X, Y, s + 6)

    def vest_fn(face, c, r, fw, fh, X, Y):
        """Строгий жилет: тёмная окантовка, 4 пуговицы, до пояса."""
        if face in ("top", "bottom") or r > 8:
            return None
        if face == "front":
            if r <= 1 and c in (2, 3, 4, 5):
                return None
            if r == 2 and c in (3, 4):
                return None
            px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6, rough=0.035)
            if c in (2, 5) and r <= 4:
                return mul(px, 0.8)                      # окантовка выреза
            if c == 4 and r in (3, 5, 7) and buttons:
                return buttons
            return px
        return cloth(mul(oc, 0.92), face, c, r, fw, fh, X, Y, s + 6)

    def suspenders_fn(face, c, r, fw, fh, X, Y):
        if face in ("top", "bottom"):
            return None
        if face == "front" and c in (1, 6) and r <= 8:
            return buttons if r == 8 and buttons else cloth(oc, face, c, r, fw, fh, X, Y, s + 6, edge=False)
        if face == "back" and r <= 8 and c in (1 + r // 3, 6 - r // 3):
            return cloth(oc, face, c, r, fw, fh, X, Y, s + 6, edge=False)
        return None

    def bodice_fn(face, c, r, fw, fh, X, Y):
        """Корсаж: от груди до талии, шнуровка спереди."""
        if face in ("top", "bottom") or r < 2 or r > 9:
            if face == "front" and r == 1 and c in (1, 6):
                return cloth(oc, face, c, r, fw, fh, X, Y, s + 6)   # бретели
            return None
        px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6, rough=0.04)
        if face == "front":
            if c in (3, 4):
                lace = ch.get("lacing", (230, 220, 200))
                return lace if (r + c) % 2 == 0 else mul(px, 0.8)
            if r == 9:
                return mul(px, 0.85)
        if r == 2:
            return mul(px, 1.1)                          # светлый край корсажа
        return px

    fn = {"coat": coat_fn, "waistcoat": waistcoat_fn, "vest": vest_fn,
          "suspenders": suspenders_fn, "bodice": bodice_fn}.get(outer)
    collar = None if female else mul(shirt, 1.04)

    def jacket_fn(face, c, r, fw, fh, X, Y):
        px = fn(face, c, r, fw, fh, X, Y) if fn else None
        if px is not None:
            return px
        # Воротник рубашки — уголки поверх
        if collar and face == "front" and r == 0 and c in (2, 5):
            return collar
        if collar and face == "front" and r == 1 and c in (2, 5) and outer not in ("vest", "waistcoat"):
            return mul(collar, 0.93)
        # Женская блуза: пышный край выреза
        if female and face == "front" and r == 0 and c in (1, 2, 5, 6):
            return mul(shirt, 1.05)
        return None

    cv.box(16, 32, 8, 12, 4, jacket_fn)

    # Полы сюртука на бёдрах — второй слой ног
    if outer == "coat":
        def tails(face, c, r, fw, fh, X, Y):
            if face in ("top", "bottom") or r > 2:
                return None
            if face == "front" and (c == 3 if X < 8 else c == 0):
                return None
            return cloth(oc, face, c, r, fw, fh, X, Y, s + 6, grad=0.0)
        cv.box(0, 32, 4, 12, 4, tails)
        cv.box(0, 48, 4, 12, 4, tails)

    # Грудь (женская модель): цвет как у корсажа/блузы
    if female:
        def breast_fn(face, c, r, fw, fh, X, Y):
            if face == "top":
                return cloth(shirt, face, c, r, fw, fh, X, Y, s)
            base = shirt if r == 0 else (oc if outer == "bodice" else shirt)
            px = cloth(base, face, c, r, fw, fh, X, Y, s + 6, grad=0.0)
            if face == "front" and outer == "bodice" and r >= 1 and c == 3:
                return ch.get("lacing", (230, 220, 200)) if r == 1 else mul(px, 0.8)
            return mul(px, 0.9) if face == "bottom" else px
        cv.box(64, 48, 7, 3, 1, breast_fn)
        cv.box(64, 52, 7, 3, 1, lambda face, c, r, fw, fh, X, Y:
               mul(shirt, 1.06) if face == "front" and r == 0 and c in (0, 1, 5, 6) else None)


# ---------------------------------------------------------------------------
# Руки
# ---------------------------------------------------------------------------

def paint_arm(cv, ch, u, v, lu, lv, width):
    s = cv.seed
    skin = ch["skin"]
    shirt = ch["shirt"]
    female = ch.get("female", False)
    outer = ch.get("outer")
    oc = ch.get("outer_color")
    rolled = ch.get("sleeves") == "rolled"
    sleeve_end = 6 if rolled else (9 if female else 10)
    coat = outer == "coat"

    def arm_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return cloth(oc if coat else shirt, face, c, r, fw, fh, X, Y, s + 6)
        if face == "bottom":
            return mul(skin, 0.8)
        if r < sleeve_end:
            px = cloth(shirt, face, c, r, fw, fh, X, Y, s + 8, rough=0.06, grad=0.08)
            if female and r in (0, 1, 2) and not rolled:
                px = mul(px, 1.04 if r != 2 else 0.9)    # пышный рукав
            if (c + r * 2) % 7 == 3 or (c, r) in ((1, 4), (2, 7)):
                px = hue_shift_dark(px, 0.88)            # складки ткани
            return px
        px = skin_px(skin, face, c, r, fw, fh, X, Y, s)
        if r >= 10:
            px = mul(px, 0.94)                           # кисть
        if r == 11 and face == "front":
            px = mul(px, 0.9)
        return px

    cv.box(u, v, width, 12, 4, arm_fn)

    def sleeve_fn(face, c, r, fw, fh, X, Y):
        if face == "bottom":
            return None
        if coat:
            if face == "top" or r <= 8:
                px = cloth(oc, face, c, r, fw, fh, X, Y, s + 6)
                return mul(px, 1.12) if r == 8 else px   # обшлаг
            if r == 9:
                return mul(shirt, 1.04)                  # манжета рубашки
            return None
        if face == "top":
            return None
        if rolled and r == sleeve_end - 1:
            return mul(shirt, 0.88)                      # закатанный рукав
        if not rolled and r == sleeve_end - 1:
            return mul(shirt, 1.05)                      # манжета
        if female and r == 2 and not rolled:
            return mul(shirt, 0.92)                      # перехват пышного рукава
        return None

    cv.box(lu, lv, width, 12, 4, sleeve_fn)


# ---------------------------------------------------------------------------
# Ноги и юбка
# ---------------------------------------------------------------------------

def paint_leg(cv, ch, u, v, lu, lv):
    s = cv.seed
    female = ch.get("female", False)
    boots = ch["boots"]
    style = ch.get("boot_style", "tall")
    socks = ch.get("socks")
    trousers = ch.get("trousers", ch.get("skirt"))
    top_boot = {"tall": 6, "socks": 9, "shoes": 9}.get(style, 6)
    if female:
        top_boot = 9

    def leg_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return cloth(trousers, face, c, r, fw, fh, X, Y, s + 1)
        if face == "bottom":
            return mul(boots, 0.55)
        if r >= top_boot:
            px = cloth(boots, face, c, r, fw, fh, X, Y, s + 9, rough=0.03, grad=0.0)
            if r == 11:
                return mul(px, 0.7)                      # подошва
            if face == "front" and r == top_boot + 1 and style == "shoes":
                return (196, 170, 96) if c in (1, 2) else px   # пряжка
            if face == "front" and style == "socks" and r == top_boot + 1 and c in (1, 2):
                return mul(boots, 1.3)                   # шнурки
            return mul(px, 1.08) if c in (1, 2) and face == "front" else px
        if socks and (female or style in ("socks", "shoes")) and r >= top_boot - (3 if female else 2):
            px = cloth(socks, face, c, r, fw, fh, X, Y, s + 10, rough=0.03, grad=0.0)
            return mul(px, 0.92) if r % 2 else px        # вязка
        px = cloth(trousers, face, c, r, fw, fh, X, Y, s + 1, grad=0.06)
        if face == "front" and (c, r) in ((1, 3), (2, 6), (1, 7)):
            px = hue_shift_dark(px, 0.9)                 # складки на коленях
        return px

    cv.box(u, v, 4, 12, 4, leg_fn)

    def layer_fn(face, c, r, fw, fh, X, Y):
        if face in ("top", "bottom"):
            return None
        if style == "tall" and not female and r == 6:
            return mul(boots, 1.25)                      # отворот сапога
        return None

    # (у сюртука на этом слое уже нарисованы полы — дорисовываем только пустые клетки)
    for face, (x, y, fw, fh) in Canvas.faces(lu, lv, 4, 12, 4).items():
        for r in range(fh):
            for c in range(fw):
                if cv.get(x + c, y + r)[3] == 0:
                    cv.put(x + c, y + r, layer_fn(face, c, r, fw, fh, x + c, y + r))


def paint_skirt(cv, ch):
    s = cv.seed
    skirt = ch["skirt"]
    apron = ch.get("apron")
    hem = ch.get("hem")

    def fold(c):
        return 0.86 if c % 3 == 2 else (1.04 if c % 3 == 0 else 1.0)

    def upper(face, c, r, fw, fh, X, Y):
        if face == "bottom":
            return mul(skirt, 0.5)
        px = cloth(mul(skirt, fold(c)), face, c, r, fw, fh, X, Y, s + 12, grad=0.1)
        if face == "front" and apron and 2 <= c <= 6:
            ap = cloth(apron, face, c, r, fw, fh, X, Y, s + 13, rough=0.03, grad=0.06)
            return mul(ap, 0.9) if c in (2, 6) else ap
        if r == 0:
            return mul(px, 0.8)                          # пояс юбки
        return px

    cv.box(96, 0, 9, 8, 5, upper)

    def lower(face, c, r, fw, fh, X, Y):
        if face == "top":
            return mul(skirt, 0.8)
        if face == "bottom":
            return mul(skirt, 0.45)
        px = cloth(mul(skirt, fold(c + 1)), face, c, r, fw, fh, X, Y, s + 14, grad=0.12)
        if face == "front" and apron and 2 <= c <= 7 and r <= 2:
            ap = cloth(apron, face, c, r, fw, fh, X, Y, s + 13, rough=0.03, grad=0.0)
            return mul(ap, 0.88) if r == 2 or c in (2, 7) else ap
        if r == 3:
            return hem if hem else mul(px, 0.78)         # кайма по подолу
        return px

    cv.box(96, 13, 10, 4, 6, lower)


# ---------------------------------------------------------------------------

def paint(name, ch):
    cv = Canvas(sum(map(ord, name)))
    paint_head(cv, ch)
    paint_body(cv, ch)
    female = ch.get("female", False)
    if female:
        paint_arm(cv, ch, 64, 16, 64, 32, 3)
        paint_arm(cv, ch, 80, 16, 80, 32, 3)
        paint_skirt(cv, ch)
    else:
        paint_arm(cv, ch, 40, 16, 40, 32, 4)
        paint_arm(cv, ch, 32, 48, 48, 48, 4)
    paint_leg(cv, ch, 0, 16, 0, 32)
    paint_leg(cv, ch, 16, 48, 0, 48)
    return cv.im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, ch in CHARACTERS.items():
        paint(name, ch).save(os.path.join(OUT, name + ".png"))
        print("saved", name)


if __name__ == "__main__":
    main()
