"""
Генератор текстур жителей basedefense (ориентир качества — жители MineColonies, но рисунок свой).
Запуск:  python tools/settler_textures.py      (нужен Pillow: pip install pillow)
Предпросмотр 3D без игры:  python tools/settler_preview.py

Пишет textures/entity/settler/<имя>.png (128x64) для каждого персонажа из CHARACTERS.
Раскладка совпадает с SettlerEntityModel:
  левая половина — как скин игрока (голова, тело, широкие руки, ноги + вторые слои);
  (64,0) пряди по спине, (82,0) хвост, (96,0) юбка, (96,13) низ юбки,
  (64,16)/(64,32) тонкая правая рука и рукав, (80,16)/(80,32) тонкая левая, (64,48)/(64,52) грудь;
  (64,9) козырёк кепки, (86,9) сумочка на поясе,
  (96,24)/(96,27) поля шляпы спереди/сзади, (96,30)/(96,39) поля справа/слева, (96,48) тулья шляпы.
Внешность от профессии не зависит. Чтобы поменять жителя — правь словари в CHARACTERS.
Порядок персонажей в CHARACTERS = номер внешности в игре (SettlerLooks.NAMES).

Приёмы «качества»:
  - свет сверху-спереди: у каждой грани своя яркость, тени уходят в холодный оттенок, блики — в тёплый;
  - фактура материала: саржа, лён, шерсть, вязка, кожа (узор, а не случайный шум);
  - «затенение»: под головой, в подмышках, между ногами, на гранях, прижатых к телу;
  - детали: швы, подгибки, заплатки, карманы, пуговицы с бликом, шнуровка, пряжки;
  - лицо: глаза в 2 пикселя (белок + радужка), брови, нос со светом и тенью, румянец, тень подбородка.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "basedefense", "textures", "entity", "settler")
T = (0, 0, 0, 0)

# Яркость граней (свет сверху и спереди). Имена граней: right = сторона x− (правый бок персонажа),
# left = сторона x+ (левый бок), front — лицо/перед, back — спина.
FACE_LIGHT = {"top": 1.12, "front": 1.0, "right": 0.9, "left": 0.9, "back": 0.84, "bottom": 0.64}
SIDES = ("front", "back", "left", "right")


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k))


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t))


def shade(c, k):
    """Яркость k: тени холоднее (чуть в синеву), блики теплее (чуть в жёлтый)."""
    d = mul(c, k)
    if k < 1.0:
        t = min(1.0, (1.0 - k) * 1.6)
        return (clamp(d[0] * (1 - 0.05 * t)), clamp(d[1] * (1 - 0.02 * t)), clamp(d[2] * (1 + 0.07 * t)))
    t = min(1.0, (k - 1.0) * 2.0)
    return (clamp(d[0] * (1 + 0.03 * t)), clamp(d[1] * (1 + 0.015 * t)), clamp(d[2] * (1 - 0.03 * t)))


def noise(x, y, seed=0):
    """Упорядоченный шум -1..1 (одинаковый при каждом запуске)."""
    h = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h >> 8) % 1000) / 500.0 - 1.0


def smooth(x, y, seed=0):
    """Мягкий шум: среднее соседей (для шерсти и кожи)."""
    return (noise(x, y, seed) * 2 + noise(x + 1, y, seed) + noise(x, y + 1, seed) + noise(x - 1, y - 1, seed)) / 5


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

    def box(self, u, v, w, h, d, fn, only_empty=False):
        """fn(face, col, row, fw, fh, X, Y) -> цвет или None; X, Y — абсолютные координаты пикселя."""
        for face, (x, y, fw, fh) in self.faces(u, v, w, h, d).items():
            for r in range(fh):
                for c in range(fw):
                    if only_empty and self.get(x + c, y + r)[3] != 0:
                        continue
                    self.put(x + c, y + r, fn(face, c, r, fw, fh, x + c, y + r))


# ---------------------------------------------------------------------------
# Материалы
# ---------------------------------------------------------------------------

def material(base, face, c, r, fw, fh, X, Y, seed, kind="twill", grad=0.12, rim=0.9, rough=0.03):
    """Цвет пикселя ткани/кожи с учётом света, градиента, краёв и узора материала."""
    k = FACE_LIGHT[face]
    if face in SIDES:
        k *= 1.05 - grad * (r / max(1, fh - 1))
        if c == 0 or c == fw - 1:
            k *= rim
    if kind == "twill":                       # саржа: диагональные рубчики
        k *= (1.035, 1.0, 0.965, 1.0)[(X + Y) % 4]
    elif kind == "linen":                     # лён: мелкая клетка
        k *= 1.02 if (X % 2 == 0 and Y % 2 == 0) else (0.985 if (X + Y) % 2 else 1.0)
        rough *= 0.5
    elif kind == "wool":                      # шерсть: мягкие пятна
        k *= 1.0 + 0.07 * smooth(X, Y, seed)
    elif kind == "knit":                      # вязка: «косички» по столбцам
        k *= 1.04 if X % 2 == 0 else 0.95
        k *= 0.96 if Y % 3 == 0 else 1.0
    elif kind == "leather":                   # кожа: зерно и блик
        k *= 1.0 + 0.05 * smooth(X, Y, seed)
        if face == "front" and c == 1:
            k *= 1.08
    elif kind == "straw":                     # солома: горизонтальное плетение
        k *= 1.08 if (Y + (X // 2)) % 2 == 0 else 0.9
    k *= 1.0 + rough * noise(X, Y, seed)
    return shade(base, k)


def skin_px(base, face, c, r, fw, fh, X, Y, seed):
    k = FACE_LIGHT[face] * (1.0 + 0.018 * noise(X, Y, seed))
    if face in SIDES:
        k *= 1.03 - 0.06 * (r / max(1, fh - 1))
    return shade(base, k)


def hair_px(base, face, c, r, fw, fh, X, Y, seed):
    """Волосы: вертикальные пряди трёх тонов, блик-«сияние» вверху, корни темнее внизу."""
    strand = noise(X, 0, seed + 31)                 # одна прядь — один тон по всей высоте
    k = FACE_LIGHT[face] * (1.0 + 0.10 * strand)
    if face in SIDES:
        if r in (1, 2):
            k *= 1.09                               # сияние
        k *= 1.0 - 0.025 * r
    if face == "top":
        k *= 1.0 + 0.06 * noise(X, Y, seed + 5)
    k *= 1.0 + 0.025 * noise(X, Y, seed + 7)
    return shade(base, k)


def ao(part, face, c, r, fw, fh):
    """Затенение в «щелях»: под головой, у тела, между ногами. Возвращает множитель яркости."""
    k = 1.0
    if part == "body":
        if face in SIDES and r == 0:
            k *= 0.82                               # тень от головы
        elif face in SIDES and r == 1:
            k *= 0.93
        if face in ("left", "right"):
            k *= 0.86                               # бока прижаты руками
    elif part in ("arm_r", "arm_l"):
        inner = "left" if part == "arm_r" else "right"
        if face == inner:
            k *= 0.8
        if face in SIDES and r == 0:
            k *= 0.9
    elif part in ("leg_r", "leg_l"):
        inner = "left" if part == "leg_r" else "right"
        if face == inner:
            k *= 0.78
        if face in SIDES and r == 0:
            k *= 0.8                                # тень от туловища
        elif face in SIDES and r == 1:
            k *= 0.92
    return k


def apply_ao(px, k):
    return None if px is None else (px if k >= 0.999 else shade(px, k))


# ---------------------------------------------------------------------------
# Персонажи (порядок = номер внешности в игре: сначала 5 мужчин, 3 женщины, потом новые)
# ---------------------------------------------------------------------------

CHARACTERS = {
    "male_1": dict(skin=(233, 190, 155), hair=(122, 76, 44), hair_style="wavy", eyes=(64, 120, 176),
                   shirt=(232, 222, 200), outer="coat", outer_color=(70, 88, 112), buttons=(214, 176, 88),
                   trousers=(74, 58, 46), boots=(58, 42, 32), boot_style="tall"),
    "male_2": dict(skin=(116, 78, 56), hair=(30, 25, 23), hair_style="short", eyes=(58, 38, 26), beard="full",
                   shirt=(226, 214, 186), sleeves="rolled", outer="waistcoat", outer_color=(104, 66, 42),
                   buttons=(214, 180, 96), belt=(48, 36, 28), buckle=(212, 176, 86),
                   trousers=(62, 62, 70), boots=(46, 36, 30), boot_style="socks", socks=(226, 218, 200),
                   pouch=(120, 82, 50)),
    "male_3": dict(skin=(206, 152, 114), hair=(66, 44, 32), hair_style="side", eyes=(86, 60, 38),
                   shirt=(240, 236, 226), outer="vest", outer_color=(116, 80, 52), buttons=(216, 180, 96),
                   cravat=(140, 42, 40), trousers=(100, 74, 50), boots=(34, 30, 28), boot_style="shoes",
                   socks=(232, 228, 216)),
    "male_4": dict(skin=(243, 206, 178), hair=(192, 92, 42), hair_style="messy", eyes=(84, 134, 72),
                   beard="short", freckles=True, shirt=(98, 120, 74), shirt_kind="wool", sleeves="rolled",
                   belt=(180, 152, 104), rope_belt=True, trousers=(156, 128, 92), boots=(102, 70, 44),
                   boot_style="tall", patch=(118, 92, 64), pouch=(140, 104, 64)),
    "male_5": dict(skin=(216, 166, 126), hair=(176, 174, 168), hair_style="receding", eyes=(96, 110, 124),
                   beard="mustache", shirt=(124, 142, 158), outer="suspenders", outer_color=(64, 46, 34),
                   buttons=(196, 162, 96), scarf=(156, 50, 42), trousers=(94, 78, 62), boots=(52, 42, 36),
                   boot_style="tall", hat="cap", hat_color=(98, 90, 80)),
    "female_1": dict(skin=(244, 206, 176), hair=(232, 196, 110), hair_style="ponytail", eyes=(70, 126, 190),
                     female=True, shirt=(240, 232, 214), outer="bodice", outer_color=(102, 128, 84),
                     lacing=(236, 226, 200), skirt=(126, 90, 62), apron=(232, 224, 206),
                     boots=(104, 72, 46), socks=(216, 206, 190), ribbon=(196, 70, 64)),
    "female_2": dict(skin=(210, 154, 116), hair=(82, 50, 32), hair_style="long", eyes=(98, 66, 40),
                     female=True, shirt=(242, 240, 234), outer="bodice", outer_color=(66, 94, 132),
                     lacing=(220, 212, 190), skirt=(52, 66, 100), hem=(164, 174, 200),
                     boots=(40, 32, 30), socks=(74, 68, 64)),
    "female_3": dict(skin=(128, 88, 62), hair=(28, 23, 21), hair_style="bun", eyes=(58, 38, 28),
                     female=True, shirt=(232, 220, 194), outer="bodice", outer_color=(164, 86, 62),
                     lacing=(236, 214, 172), skirt=(128, 72, 54), apron=(220, 202, 164), scarf=(206, 166, 72),
                     boots=(66, 46, 34), socks=(190, 174, 154)),
    "male_6": dict(skin=(222, 172, 136), hair=(226, 224, 218), hair_style="receding", eyes=(84, 112, 140),
                   beard="long", shirt=(214, 200, 168), shirt_kind="linen", tunic=True,
                   belt=(164, 140, 96), rope_belt=True, trousers=(104, 84, 62), boots=(84, 60, 42),
                   boot_style="tall", hat="brim", hat_color=(214, 182, 112), hat_kind="straw",
                   hat_band=(150, 58, 44), pouch=(112, 80, 52)),
    "male_7": dict(skin=(236, 194, 160), hair=(92, 60, 38), hair_style="short", eyes=(78, 104, 64),
                   freckles=True, shirt=(236, 230, 214), stripes=(176, 64, 58), sleeves="rolled",
                   outer="suspenders", outer_color=(88, 58, 38), buttons=(206, 172, 96),
                   trousers=(70, 80, 104), boots=(44, 36, 32), boot_style="shoes", socks=(206, 196, 180),
                   patch=(150, 128, 92), hat="cap", hat_color=(52, 62, 88)),
    "female_4": dict(skin=(246, 212, 186), hair=(176, 70, 34), hair_style="ponytail", eyes=(80, 132, 96),
                     female=True, freckles=True, shirt=(238, 230, 212), outer="bodice",
                     outer_color=(110, 64, 104), lacing=(232, 216, 196), skirt=(84, 54, 86),
                     hem=(200, 170, 100), boots=(78, 54, 40), socks=(170, 150, 160),
                     ribbon=(70, 120, 90), pouch=(128, 92, 58)),
    "female_5": dict(skin=(186, 128, 92), hair=(36, 28, 26), hair_style="bob", eyes=(72, 48, 32),
                     female=True, shirt=(236, 228, 208), outer="bodice", outer_color=(196, 152, 62),
                     lacing=(86, 58, 40), skirt=(54, 88, 70), apron=(228, 216, 190),
                     boots=(54, 40, 32), socks=(120, 110, 100), hat="brim",
                     hat_color=(60, 92, 72), hat_kind="wool", hat_band=(214, 176, 82)),
    "male_8": dict(skin=(200, 146, 108), hair=(112, 58, 34), hair_style="messy", eyes=(70, 96, 120),
                   beard="full", shirt=(222, 214, 196), outer="coat", outer_color=(54, 86, 62),
                   buttons=(206, 168, 86), trousers=(84, 84, 90), boots=(66, 48, 36), boot_style="tall",
                   hat="brim", hat_color=(104, 74, 50), hat_kind="wool", hat_band=(44, 34, 30),
                   pouch=(132, 92, 56)),
    "female_6": dict(skin=(236, 198, 170), hair=(214, 212, 206), hair_style="bun", eyes=(88, 108, 132),
                     female=True, shirt=(240, 236, 226), outer="bodice", outer_color=(124, 96, 150),
                     lacing=(232, 220, 236), skirt=(62, 54, 80), hem=(196, 180, 120),
                     apron=(226, 222, 212), boots=(46, 36, 32), socks=(150, 140, 156)),
}


# ---------------------------------------------------------------------------
# Голова
# ---------------------------------------------------------------------------

def paint_head(cv, ch):
    skin, hair, eyes = ch["skin"], ch["hair"], ch["eyes"]
    style = ch["hair_style"]
    female = ch.get("female", False)
    beard = ch.get("beard")
    hat = ch.get("hat")
    s = cv.seed
    shadow_skin = shade(skin, 0.8)
    light_skin = shade(skin, 1.06)
    dark_hair = sum(hair) < 300
    brow = mul(hair, 0.6) if not dark_hair else mul(hair, 0.8)
    if sum(hair) > 600:
        brow = mul(hair, 0.7)                          # седые брови
    beard_col = mul(hair, 0.96)
    iris_dark = mul(eyes, 0.62)
    lash = (44, 32, 30)
    blush = (226, 118, 110)

    def hairline(c, r):
        if r == 0:
            return True
        if style == "receding":
            return r == 1 and c in (0, 7)
        if style == "short":
            return r == 1 and c not in (3, 4)
        if style == "side":
            return (r == 1 and c not in (6,)) or (r == 2 and c in (0, 1, 2))
        if style == "wavy":
            return r == 1 or (r == 2 and c in (0, 1, 6, 7))
        if style == "messy":
            return r == 1 or (r == 2 and c in (0, 2, 5, 7))
        if style in ("ponytail", "bun"):
            return r == 1 and c in (0, 1, 2, 5, 6, 7)
        if style == "long":
            return r == 1 or (r == 2 and c in (0, 7))
        if style == "bob":
            return r == 1 or (r == 2 and c in (0, 1, 2, 3, 4))   # косая чёлка
        return r == 1

    def face_px(c, r, X, Y):
        if hairline(c, r):
            return hair_px(hair, "front", c, r, 8, 8, X, Y, s)
        if style in ("long", "bob") and c in (0, 7) and r <= (6 if style == "long" else 5):
            return hair_px(mul(hair, 0.9), "front", c, r, 8, 8, X, Y, s)
        base = skin_px(skin, "front", c, r, 8, 8, X, Y, s)
        if r == 2 or (r == 3 and c in (3, 4)):
            base = mix(base, light_skin, 0.5)          # светлый лоб
        if c in (0, 7):
            base = mix(base, shadow_skin, 0.5)         # скулы в тени
        if r == 7:
            base = mix(base, shadow_skin, 0.6 if c not in (3, 4) else 0.4)   # подбородок
        if r == 2 and hat:
            base = mix(base, shadow_skin, 0.45)        # тень от козырька/полей

        # Брови
        if r == 3 and c in (1, 2, 5, 6):
            if female:
                return mix(brow, skin, 0.15) if c in (2, 5) else mix(brow, skin, 0.5)
            return brow if c in (1, 2, 5, 6) and style != "receding" or c in (2, 5) else mix(brow, skin, 0.35)
        # Глаза: белок снаружи, радужка внутри (2 пикселя высотой: сверху темнее)
        if r == 4:
            if c in (1, 6):
                return (236, 236, 232) if not female else (240, 240, 236)
            if c in (2, 5):
                return iris_dark
            if female and c in (0, 7):
                return lash
        if r == 5:
            if c in (2, 5):
                return mix(eyes, (255, 255, 255), 0.12)
            if c in (1, 6):
                return mix(base, shadow_skin, 0.35)    # тень под глазом
            if c == 3:
                return mix(base, light_skin, 0.6)      # спинка носа
            if c == 4:
                return mix(base, shadow_skin, 0.45)
        # Рот и щёки
        if r == 6:
            if c in (1, 6) and not (beard in ("full",)):
                return mix(base, blush, 0.32 if female else 0.16)
            if c in (3, 4):
                if female:
                    return mix(skin, (190, 80, 86), 0.62 if c == 3 else 0.72)
                return mix(skin, (124, 60, 52), 0.42 if c == 3 else 0.55)
        if ch.get("freckles") and (c, r) in ((1, 5), (6, 5), (2, 6), (5, 6), (0, 5), (7, 5)):
            if not (r == 5 and c in (1, 6)) or True:
                base = mix(base, (176, 96, 60), 0.35)
        # Борода и усы
        if beard == "mustache":
            if r == 6 and c in (2, 3, 4, 5):
                return mul(beard_col, 0.95 if c in (3, 4) else 1.0)
        if beard in ("full", "short", "long"):
            if r == 6 and c in (2, 5):
                return mul(beard_col, 0.95)            # усы
            if r == 6 and c in (3, 4):
                return mix(skin, (124, 60, 52), 0.5)
            if r == 7 or (r == 6 and c in (0, 1, 6, 7) and beard != "short"):
                return hair_px(beard_col, "front", c, r, 8, 8, X, Y, s + 3)
            if beard in ("full", "long") and r == 5 and c in (0, 7):
                return hair_px(beard_col, "front", c, r, 8, 8, X, Y, s + 3)
        return base

    def side_px(face, c, r, X, Y):
        back_cols = (0, 1, 2) if face == "right" else (5, 6, 7)
        face_col = 7 if face == "right" else 0
        near = (5, 6, 7) if face == "right" else (0, 1, 2)
        if r <= 1:
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if style == "long" and r <= 7 or style == "bob" and r <= 5:
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if style == "receding":
            if c in back_cols and r <= 4:
                return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        elif c in back_cols and r <= 5 or (r == 2 and c != face_col) or (c not in near and r <= 3):
            return hair_px(hair, face, c, r, 8, 8, X, Y, s)
        if beard in ("full", "short", "long") and r >= 4 and c in near:
            return hair_px(beard_col, face, c, r, 8, 8, X, Y, s + 3)
        px = skin_px(skin, face, c, r, 8, 8, X, Y, s)
        if r in (3, 4, 5) and c in (3, 4):
            px = mix(px, shadow_skin, 0.45 if (c == 3) == (face == "right") else 0.2)   # ухо
            if r == 4:
                px = mix(px, blush, 0.12)
        if c == face_col:
            px = mix(px, shadow_skin, 0.25)
        return mix(px, shadow_skin, 0.35) if r >= 6 else px

    def head_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return hair_px(hair, face, c, r, fw, fh, X, Y, s)
        if face == "bottom":
            return shade(skin, 0.62)
        if face == "front":
            return face_px(c, r, X, Y)
        if face == "back":
            if style in ("long", "bob") and (style == "long" or r <= 6) or r <= (4 if style == "receding" else 5):
                return hair_px(hair, face, c, r, fw, fh, X, Y, s)
            return mix(skin, shadow_skin, 0.6)
        return side_px(face, c, r, X, Y)

    cv.box(0, 0, 8, 8, 8, head_fn)

    # ----- второй слой головы: объём волос, платок или кепка -----
    hl = shade(hair, 1.06)
    scarf = ch.get("scarf") if female else None
    hat_col = ch.get("hat_color")

    def layer_fn(face, c, r, fw, fh, X, Y):
        if hat == "cap":
            # Кепка: верх и два ряда по кругу, околыш темнее
            if face == "top":
                return material(hat_col, face, c, r, fw, fh, X, Y, s + 21, "wool", rough=0.04)
            if face != "bottom" and r <= 1:
                px = material(hat_col, face, c, r, fw, fh, X, Y, s + 21, "wool", grad=0.0)
                return shade(px, 0.82) if r == 1 else px
            if face != "bottom" and face != "front" and r == 2 and style not in ("receding",):
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            return None
        if hat == "brim":
            if face == "top" or face == "bottom":
                return None
            if face != "front" and r <= 2 and style != "receding":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            return None
        if scarf is not None:
            if face == "top" or (face != "bottom" and r <= 1) or (face == "back" and r <= 3):
                k = 1.0 if (c + r) % 4 else 0.86
                return material(mul(scarf, k), face, c, r, fw, fh, X, Y, s + 11, "linen", rim=1.0)
            if face == "back" and r in (4, 5) and c in (3, 4):
                return shade(scarf, 0.82)              # узелок
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
            if style == "bob" and (r == 1 and c <= 4 or c in (0, 7) and r <= 5):
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "long" and c in (0, 7) and r <= 6:
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            return None
        if face == "back":
            if style == "bun" and 1 <= r <= 4 and 2 <= c <= 5:
                return hair_px(mul(hair, 0.95), face, c, r, fw, fh, X, Y, s + 2)
            if style == "ponytail" and r <= 3 and 3 <= c <= 4:
                return ch.get("ribbon", (170, 60, 60)) if r == 3 else hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "long":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
            if style == "bob":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if r <= 5 else None
            if style == "receding":
                return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if 2 <= r <= 3 else None
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if r <= 3 else None
        back_cols = (0, 1, 2, 3) if face == "right" else (4, 5, 6, 7)
        if style == "receding":
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1) if c in back_cols and 2 <= r <= 3 else None
        if r == 0 or (r == 1 and style != "short"):
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        if style == "long" and r <= 7 or style == "bob" and r <= 5:
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        if style in ("wavy", "messy") and c in back_cols and r <= 3:
            return hair_px(hl, face, c, r, fw, fh, X, Y, s + 1)
        return None

    cv.box(32, 0, 8, 8, 8, layer_fn)

    # Борода «long» — свисает ниже подбородка: рисуем на втором слое лица
    if beard == "long":
        for c in range(8):
            for r in (5, 6, 7):
                if r >= 6 or c in (0, 1, 6, 7):
                    cv.put(40 + c, 8 + r, hair_px(shade(beard_col, 1.05), "front", c, r, 8, 8, 40 + c, 8 + r, s + 4))

    # Пряди по спине и хвост (отдельные части модели)
    cv.box(64, 0, 8, 7, 1, lambda face, c, r, fw, fh, X, Y: hair_px(
        shade(hair, 1.0 - 0.025 * r), face if face != "front" else "back", c, r, fw, fh, X, Y, s + 4))

    def tail(face, c, r, fw, fh, X, Y):
        if r == 0 and face not in ("top", "bottom"):
            return ch.get("ribbon", (176, 64, 60))      # лента
        if r == 2 and face in SIDES:
            return shade(hair, 0.82)                   # перехват косы
        return hair_px(shade(hair, 1.0 - 0.03 * r), face, c, r, fw, fh, X, Y, s + 5)
    cv.box(82, 0, 2, 5, 2, tail)

    # ----- головные уборы (отдельные части модели) -----
    if hat == "cap":
        def visor(face, c, r, fw, fh, X, Y):
            px = material(hat_col, face, c, r, fw, fh, X, Y, s + 21, "wool", grad=0.0, rim=0.94)
            if face == "bottom":
                return shade(hat_col, 0.55)
            if face == "top" and r == fh - 1:
                return shade(px, 1.08)                 # кромка
            return px
        cv.box(64, 9, 8, 1, 3, visor)
    if hat == "brim":
        kind = ch.get("hat_kind", "wool")
        band = ch.get("hat_band", mul(hat_col, 0.6))

        def brim(face, c, r, fw, fh, X, Y):
            if face == "bottom":
                return shade(hat_col, 0.6)
            px = material(hat_col, face, c, r, fw, fh, X, Y, s + 22, kind, grad=0.0, rim=0.95)
            return shade(px, 0.88) if face in SIDES else px
        cv.box(96, 24, 12, 1, 2, brim)
        cv.box(96, 27, 12, 1, 2, brim)
        cv.box(96, 30, 2, 1, 8, brim)
        cv.box(96, 39, 2, 1, 8, brim)

        def crown(face, c, r, fw, fh, X, Y):
            if face == "bottom":
                return shade(hat_col, 0.5)
            if face == "top":
                px = material(hat_col, face, c, r, fw, fh, X, Y, s + 23, kind)
                return shade(px, 0.9) if c in (0, 7) or r in (0, 7) else px   # вмятина по краю
            if r == fh - 2:
                return material(band, face, c, r, fw, fh, X, Y, s + 24, "linen", grad=0.0)   # лента
            if r == fh - 1:
                return shade(material(hat_col, face, c, r, fw, fh, X, Y, s + 23, kind, grad=0.0), 0.85)
            return material(hat_col, face, c, r, fw, fh, X, Y, s + 23, kind, grad=0.0)
        cv.box(96, 48, 8, 3, 8, crown)


# ---------------------------------------------------------------------------
# Туловище
# ---------------------------------------------------------------------------

def button_px(col, top):
    """Пуговица из двух пикселей: блик сверху, тень снизу."""
    return shade(col, 1.18) if top else shade(col, 0.78)


def paint_body(cv, ch):
    s = cv.seed
    skin = ch["skin"]
    female = ch.get("female", False)
    shirt = ch["shirt"]
    shirt_kind = ch.get("shirt_kind", "linen")
    outer = ch.get("outer")
    oc = ch.get("outer_color")
    trousers = ch.get("trousers", ch.get("skirt"))
    belt = ch.get("belt")
    buckle = ch.get("buckle")
    buttons = ch.get("buttons")
    stripes = ch.get("stripes")
    tunic = ch.get("tunic")

    def shirt_px(face, c, r, fw, fh, X, Y):
        base = shirt
        if stripes and X % 3 == 0:
            base = stripes                               # рубашка в полоску
        return material(base, face, c, r, fw, fh, X, Y, s, shirt_kind, grad=0.1)

    def base_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return shirt_px(face, c, r, fw, fh, X, Y)
        if face == "bottom":
            return material(trousers, face, c, r, fw, fh, X, Y, s, "twill")
        # Вырез и шея
        if face == "front" and r == 0 and c in (3, 4):
            return shade(skin, 0.78)
        if face == "front" and r == 1 and c in (3, 4) and not ch.get("cravat") and not tunic:
            return shade(skin, 0.7)
        if face == "front" and r == 1 and c in (2, 5) and tunic:
            return shade(material(shirt, face, c, r, fw, fh, X, Y, s, shirt_kind), 0.8)   # шнурок туники
        # Низ: брюки (у мужчин без туники) — с ремнём
        if not female and r >= 9 and not tunic:
            px = material(trousers, face, c, r, fw, fh, X, Y, s + 1, "twill")
            if face == "front" and r >= 10 and c == 4:
                return shade(px, 0.84)                   # шов
            if face == "front" and r == 9 and c in (1, 6):
                return shade(px, 0.86)                   # карманы
            return px
        if belt is not None and r == 8:
            if ch.get("rope_belt"):
                rope = shade(belt, 1.1 if (X + Y) % 2 else 0.84)
                if face == "front" and c == 2:
                    return shade(belt, 0.7)              # узел
                return rope
            if face == "front" and buckle and c in (3, 4):
                return shade(buckle, 1.15) if c == 3 else shade(buckle, 0.8)
            return material(belt, face, c, r, fw, fh, X, Y, s + 2, "leather", grad=0.0)
        px = shirt_px(face, c, r, fw, fh, X, Y)
        # Складки
        if face == "front" and (c, r) in ((2, 6), (5, 5), (2, 7), (5, 6), (1, 3), (6, 4)):
            px = shade(px, 0.88)
        if face == "front" and c == 3 and 2 <= r <= 7 and not female and not tunic:
            px = shade(px, 0.93)                         # планка рубашки
            if r % 2 == 0:
                px = mix(px, (250, 248, 240), 0.5)      # пуговички рубашки
        if tunic and r >= 9:
            px = shade(px, 0.94 if (c % 3) else 0.86)    # подол туники — складками
            if r == 11:
                px = shade(px, 0.8)
        if female and r == 9:
            return shade(px, 0.86)
        return px

    cv.box(16, 16, 8, 12, 4, lambda f, c, r, fw, fh, X, Y: apply_ao(base_fn(f, c, r, fw, fh, X, Y),
                                                                         ao("body", f, c, r, fw, fh)))

    # Шейный платок / галстук
    if ch.get("cravat"):
        cr = ch["cravat"]
        for (x, y) in ((23, 21), (24, 21), (23, 22), (24, 22), (23, 23)):
            cv.put(x, y, shade(cr, 1.08 if x == 23 and y == 21 else (1.0 if y < 23 else 0.8)))
    if ch.get("scarf") and not female:
        sc = ch["scarf"]
        for x in range(20, 28):
            cv.put(x, 20, shade(sc, 0.82 if x in (20, 27) else (1.04 if x % 2 else 0.92)))
        for (x, y) in ((22, 21), (23, 21), (24, 21), (25, 21), (23, 22), (24, 22), (24, 23)):
            cv.put(x, y, shade(sc, 0.9 if y > 21 else 1.0))

    # ----- второй слой туловища -----
    def coat_fn(face, c, r, fw, fh, X, Y):
        """Сюртук нараспашку: полы до бёдер, лацканы, медные пуговицы, карманы с клапанами."""
        if face == "bottom":
            return None
        if face == "top":
            return material(oc, face, c, r, fw, fh, X, Y, s + 6, "wool")
        if face == "front":
            if c in (3, 4):
                return None                              # рубашка видна
            px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "wool")
            if r <= 3 and c in (2, 5):
                return shade(px, 1.2)                    # лацкан
            if r <= 1 and c in (1, 6):
                return shade(px, 1.1)
            if c == 2 and r in (5, 7) and buttons:
                return button_px(buttons, True)
            if c == 2 and r in (6, 8) and buttons:
                return shade(px, 0.9)
            if r == 9 and c in (0, 1, 6, 7):
                return shade(px, 0.78)                   # клапан кармана
            if r == 10 and c in (0, 1, 6, 7):
                return shade(px, 0.9)
            return px
        px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "wool")
        if face == "back" and c in (3, 4) and r >= 8:
            return shade(px, 0.78)                       # разрез сзади
        if face == "back" and r == 7 and c in (2, 5) and buttons:
            return button_px(buttons, True)
        return px

    def waistcoat_fn(face, c, r, fw, fh, X, Y):
        """Жилет: V-вырез, пуговицы с бликом, кармашки, хлястик сзади."""
        if face in ("top", "bottom") or r > 8:
            return None
        if face == "front":
            if r <= 2 and c in (3, 4) or (r == 0 and c in (2, 5)):
                return None
            px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")
            if c == 3 and r in (3, 5, 7) and buttons:
                return button_px(buttons, True)
            if c == 4 and r >= 3:
                return shade(px, 0.86)                   # край полочки
            if (c, r) in ((1, 5), (6, 5)):
                return shade(px, 0.76)                   # кармашки
            if r == 8:
                return shade(px, 0.88)
            return px
        if face == "back":
            px = material(mix(oc, (40, 36, 34), 0.35), face, c, r, fw, fh, X, Y, s + 6, "linen")
            return shade(px, 0.86) if r == 6 else px     # хлястик
        return material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")

    def vest_fn(face, c, r, fw, fh, X, Y):
        """Строгий жилет: окантовка выреза, 3 пуговицы, до пояса."""
        if face in ("top", "bottom") or r > 8:
            return None
        if face == "front":
            if r <= 1 and c in (2, 3, 4, 5) or r == 2 and c in (3, 4):
                return None
            px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")
            if c in (2, 5) and r <= 4 or (c == 3 and r == 3) or (c == 4 and r == 3):
                return shade(px, 0.78)                   # окантовка
            if c == 4 and r in (4, 6, 8) and buttons:
                return button_px(buttons, True)
            return px
        return material(shade(oc, 0.9), face, c, r, fw, fh, X, Y, s + 6, "linen")

    def suspenders_fn(face, c, r, fw, fh, X, Y):
        if face in ("top", "bottom"):
            return None
        if face == "front" and c in (1, 6) and r <= 8:
            if r == 8 and buttons:
                return button_px(buttons, True)
            px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "leather", rim=1.0)
            return shade(px, 1.1) if r == 4 else px      # пряжка-регулятор
        if face == "back" and r <= 8 and c in (1 + r // 3, 6 - r // 3):
            return material(oc, face, c, r, fw, fh, X, Y, s + 6, "leather", rim=1.0)
        if face in ("top",) and c in (1, 6):
            return material(oc, face, c, r, fw, fh, X, Y, s + 6, "leather")
        return None

    def bodice_fn(face, c, r, fw, fh, X, Y):
        """Корсаж: от груди до талии, шнуровка спереди, светлый кант."""
        if face in ("top", "bottom") or r < 2 or r > 9:
            if face == "front" and r == 1 and c in (1, 6):
                return material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")   # бретели
            if face == "back" and r == 1 and c in (1, 6):
                return material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")
            return None
        px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "twill")
        if face == "front":
            if c in (3, 4):
                lace = ch.get("lacing", (230, 220, 200))
                return lace if (r + c) % 2 == 0 else shade(px, 0.7)
            if c in (2, 5):
                px = shade(px, 0.92)
            if r == 9:
                return shade(px, 0.8)
        if r == 2:
            return mix(px, ch.get("lacing", (230, 220, 200)), 0.3)   # кант
        if face == "back" and c in (3, 4) and r >= 4:
            return shade(px, 0.88)                       # шов
        return px

    fn = {"coat": coat_fn, "waistcoat": waistcoat_fn, "vest": vest_fn,
          "suspenders": suspenders_fn, "bodice": bodice_fn}.get(outer)
    collar = None if female or tunic else shade(shirt if not stripes else ch["shirt"], 1.05)

    def jacket_fn(face, c, r, fw, fh, X, Y):
        px = fn(face, c, r, fw, fh, X, Y) if fn else None
        if px is not None:
            return apply_ao(px, 0.96 if face in ("left", "right") else 1.0)
        if collar and face == "front" and r == 0 and c in (2, 5):
            return collar
        if collar and face == "front" and r == 1 and c in (2, 5) and outer not in ("vest", "waistcoat"):
            return shade(collar, 0.9)
        if female and face == "front" and r == 0 and c in (1, 2, 5, 6):
            return shade(shirt, 1.06)                    # пышный вырез блузы
        if ch.get("patch_body") and face == "back" and (c, r) in ((5, 4), (6, 4), (5, 5), (6, 5)):
            return ch["patch_body"]
        return None

    cv.box(16, 32, 8, 12, 4, jacket_fn)

    # Полы сюртука и туники на бёдрах — второй слой ног
    if outer == "coat" or tunic:
        long_col = oc if outer == "coat" else shirt

        def tails(face, c, r, fw, fh, X, Y):
            depth = 2 if outer == "coat" else 1
            if face in ("top", "bottom") or r > depth:
                return None
            if face == "front" and outer == "coat" and (c == 3 if X < 8 else c == 0):
                return None
            px = material(long_col, face, c, r, fw, fh, X, Y, s + 6, "wool" if outer == "coat" else shirt_kind,
                          grad=0.0)
            return shade(px, 0.82) if r == depth else px
        cv.box(0, 32, 4, 12, 4, tails)
        cv.box(0, 48, 4, 12, 4, tails)

    # Грудь (женская модель)
    if female:
        def breast_fn(face, c, r, fw, fh, X, Y):
            if face == "top":
                return material(shirt, face, c, r, fw, fh, X, Y, s, shirt_kind)
            base = shirt if r == 0 else (oc if outer == "bodice" else shirt)
            px = material(base, face, c, r, fw, fh, X, Y, s + 6, "twill" if r else shirt_kind, grad=0.0)
            if face == "front" and outer == "bodice" and r >= 1 and c == 3:
                return ch.get("lacing", (230, 220, 200)) if r == 1 else shade(px, 0.75)
            if face == "front" and r == 2:
                return shade(px, 0.9)
            return shade(px, 0.7) if face == "bottom" else px
        cv.box(64, 48, 7, 3, 1, breast_fn)
        cv.box(64, 52, 7, 3, 1, lambda face, c, r, fw, fh, X, Y:
               shade(shirt, 1.07) if face == "front" and r == 0 and c in (0, 1, 5, 6) else None)

    # Сумочка на поясе (отдельная часть модели)
    if ch.get("pouch"):
        pc = ch["pouch"]

        def pouch(face, c, r, fw, fh, X, Y):
            px = material(pc, face, c, r, fw, fh, X, Y, s + 30, "leather", grad=0.15, rim=0.85)
            if face == "front" and r == 0:
                return shade(px, 1.12)                   # клапан
            if face == "front" and r == 1 and c == 1:
                return button_px((210, 176, 96), True)   # застёжка
            if face == "bottom":
                return shade(pc, 0.55)
            return px
        cv.box(86, 9, 3, 3, 1, pouch)


# ---------------------------------------------------------------------------
# Руки
# ---------------------------------------------------------------------------

def paint_arm(cv, ch, u, v, lu, lv, width, part):
    s = cv.seed
    skin = ch["skin"]
    shirt = ch["shirt"]
    shirt_kind = ch.get("shirt_kind", "linen")
    stripes = ch.get("stripes")
    female = ch.get("female", False)
    outer = ch.get("outer")
    oc = ch.get("outer_color")
    rolled = ch.get("sleeves") == "rolled"
    sleeve_end = 6 if rolled else (9 if female else 10)
    coat = outer == "coat"
    thumb_face = "left" if part == "arm_r" else "right"   # большой палец — к телу

    def arm_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return material(oc if coat else shirt, face, c, r, fw, fh, X, Y, s + 6, "wool" if coat else shirt_kind)
        if face == "bottom":
            return shade(skin, 0.74)
        if r < sleeve_end:
            base = stripes if stripes and X % 3 == 0 else shirt
            px = material(base, face, c, r, fw, fh, X, Y, s + 8, shirt_kind, grad=0.1)
            if female and r in (0, 1, 2) and not rolled:
                px = shade(px, 1.05 if r != 2 else 0.88)    # пышный рукав
            if r == sleeve_end - 3 and 0 < c < fw - 1 or (r == sleeve_end - 2 and c == fw // 2):
                px = shade(px, 0.88)                    # складка на локте
            if r == 1 and face in ("left", "right"):
                px = shade(px, 0.92)                    # плечевой шов
            return px
        px = skin_px(skin, face, c, r, fw, fh, X, Y, s)
        if r == sleeve_end:
            px = shade(px, 0.86)                        # тень от рукава
        if r >= 10:
            px = shade(px, 0.95)                        # кисть
            if face == "front" and r == 10 and c in (1, fw - 2):
                px = shade(px, 0.9)                     # костяшки
        if face == thumb_face and r == 10 and c in (1, 2):
            px = shade(px, 1.06)                        # большой палец
        if r == 11:
            px = shade(px, 0.86)
        return px

    cv.box(u, v, width, 12, 4, lambda f, c, r, fw, fh, X, Y: apply_ao(arm_fn(f, c, r, fw, fh, X, Y),
                                                                         ao(part, f, c, r, fw, fh)))

    def sleeve_fn(face, c, r, fw, fh, X, Y):
        if face == "bottom":
            return None
        if coat:
            if face == "top" or r <= 8:
                px = material(oc, face, c, r, fw, fh, X, Y, s + 6, "wool")
                if face == "front" and r == 8 and c == 1 and ch.get("buttons"):
                    return button_px(ch["buttons"], True)
                return shade(px, 1.12) if r == 8 else apply_ao(px, ao(part, face, c, r, fw, fh))   # обшлаг
            if r == 9:
                return shade(shirt, 1.04)               # манжета рубашки
            return None
        if face == "top":
            if outer == "suspenders":
                return None
            return None
        if rolled and r == sleeve_end - 1:
            return shade(material(shirt, face, c, r, fw, fh, X, Y, s + 8, shirt_kind), 0.9 if c % 2 else 1.04)
        if not rolled and r == sleeve_end - 1:
            return shade(shirt, 1.05)                   # манжета
        if female and r == 2 and not rolled:
            return shade(shirt, 0.9)                    # перехват пышного рукава
        return None

    cv.box(lu, lv, width, 12, 4, sleeve_fn)


# ---------------------------------------------------------------------------
# Ноги и юбка
# ---------------------------------------------------------------------------

def paint_leg(cv, ch, u, v, lu, lv, part):
    s = cv.seed
    female = ch.get("female", False)
    boots = ch["boots"]
    style = ch.get("boot_style", "tall")
    socks = ch.get("socks")
    trousers = ch.get("trousers", ch.get("skirt"))
    patch = ch.get("patch")
    top_boot = {"tall": 6, "socks": 9, "shoes": 10}.get(style, 6)
    if female:
        top_boot = 9

    def leg_fn(face, c, r, fw, fh, X, Y):
        if face == "top":
            return material(trousers, face, c, r, fw, fh, X, Y, s + 1, "twill")
        if face == "bottom":
            return shade(boots, 0.45)
        if r >= top_boot:
            px = material(boots, face, c, r, fw, fh, X, Y, s + 9, "leather", grad=0.0)
            if r == 11:
                return shade(px, 0.62)                  # подошва
            if face == "front" and r == 10:
                px = shade(px, 1.12 if c in (1, 2) else 1.0)   # носок блестит
            if face == "front" and r == top_boot and style == "shoes":
                return (214, 186, 104) if c in (1, 2) else px  # пряжка
            if face == "front" and style == "socks" and r in (top_boot, top_boot + 1) and c in (1, 2):
                return shade(boots, 1.45) if (r + c) % 2 else px   # шнурки
            if r == top_boot and style == "tall" and not female:
                return shade(px, 0.8)                   # шов голенища
            return px
        if socks and (female or style in ("socks", "shoes")) and r >= top_boot - (3 if female else 2):
            return material(socks, face, c, r, fw, fh, X, Y, s + 10, "knit", grad=0.0)
        px = material(trousers, face, c, r, fw, fh, X, Y, s + 1, "twill", grad=0.08)
        if face == "front" and (c, r) in ((1, 3), (2, 6), (1, 7), (2, 4)):
            px = shade(px, 0.86)                        # складки на коленях
        if face == "right" and part == "leg_r" and c == 2 or face == "left" and part == "leg_l" and c == 1:
            px = shade(px, 0.9)                         # боковой шов
        if patch and part == "leg_l" and face == "front" and 4 <= r <= 6 and 1 <= c <= 2:
            pp = material(patch, face, c, r, fw, fh, X, Y, s + 40, "linen", grad=0.0)
            return shade(pp, 0.8) if (r in (4, 6) and (c + r) % 2) else pp   # заплатка со стежками
        return px

    cv.box(u, v, 4, 12, 4, lambda f, c, r, fw, fh, X, Y: apply_ao(leg_fn(f, c, r, fw, fh, X, Y),
                                                                     ao(part, f, c, r, fw, fh)))

    def layer_fn(face, c, r, fw, fh, X, Y):
        if face in ("top", "bottom"):
            return None
        if style == "tall" and not female and r == 6:
            return shade(boots, 1.3 if c in (1, 2) else 1.15)   # отворот сапога
        if not female and style == "tall" and r == 7 and face == "front" and c in (0, 3):
            return shade(boots, 0.9)
        return None

    # (у сюртука/туники на этом слое уже нарисованы полы — дорисовываем только пустые клетки)
    cv.box(lu, lv, 4, 12, 4, layer_fn, only_empty=True)


def paint_skirt(cv, ch):
    s = cv.seed
    skirt = ch["skirt"]
    apron = ch.get("apron")
    hem = ch.get("hem")

    def fold(c):
        return 0.82 if c % 3 == 2 else (1.06 if c % 3 == 0 else 1.0)

    def upper(face, c, r, fw, fh, X, Y):
        if face == "bottom":
            return shade(skirt, 0.45)
        px = material(skirt, face, c, r, fw, fh, X, Y, s + 12, "wool", grad=0.14)
        px = shade(px, fold(c))
        if face == "front" and apron and 2 <= c <= 6:
            ap = material(apron, face, c, r, fw, fh, X, Y, s + 13, "linen", grad=0.08, rim=1.0)
            if r == 0:
                return shade(ap, 0.82)                  # завязки
            return shade(ap, 0.88) if c in (2, 6) else (shade(ap, 0.95) if c == 4 else ap)
        if r == 0:
            return shade(px, 0.78)                      # пояс юбки
        return px

    cv.box(96, 0, 9, 8, 5, upper)

    def lower(face, c, r, fw, fh, X, Y):
        if face == "top":
            return shade(skirt, 0.75)
        if face == "bottom":
            return shade(skirt, 0.4)
        px = material(skirt, face, c, r, fw, fh, X, Y, s + 14, "wool", grad=0.16)
        px = shade(px, fold(c + 1))
        if face == "front" and apron and 2 <= c <= 7 and r <= 2:
            ap = material(apron, face, c, r, fw, fh, X, Y, s + 13, "linen", grad=0.0, rim=1.0)
            return shade(ap, 0.84) if r == 2 or c in (2, 7) else ap
        if r == 3:
            return shade(hem, 1.0 if c % 2 else 0.9) if hem else shade(px, 0.74)   # кайма
        return px

    cv.box(96, 13, 10, 4, 6, lower)


# ---------------------------------------------------------------------------

def paint(name, ch):
    cv = Canvas(sum(map(ord, name)))
    paint_head(cv, ch)
    paint_body(cv, ch)
    if ch.get("female", False):
        paint_arm(cv, ch, 64, 16, 64, 32, 3, "arm_r")
        paint_arm(cv, ch, 80, 16, 80, 32, 3, "arm_l")
        paint_skirt(cv, ch)
    else:
        paint_arm(cv, ch, 40, 16, 40, 32, 4, "arm_r")
        paint_arm(cv, ch, 32, 48, 48, 48, 4, "arm_l")
    paint_leg(cv, ch, 0, 16, 0, 32, "leg_r")
    paint_leg(cv, ch, 16, 48, 0, 48, "leg_l")
    return cv.im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, ch in CHARACTERS.items():
        paint(name, ch).save(os.path.join(OUT, name + ".png"))
        print("saved", name)


if __name__ == "__main__":
    main()
