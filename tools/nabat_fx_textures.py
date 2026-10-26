"""
Текстуры эффектов «Набата» (запуск: python tools/nabat_fx_textures.py):
  textures/mob_effect/noise.png, dome.png, requiem.png, crack.png, bell_mark.png, prisoner.png  — значки 18×18
  textures/misc/nabat_ring.png     — кольцо‑тень под падающим колоколом (в игре окрашивается кодом)
  textures/misc/nabat_ripple.png   — рябь «Купола» в месте попадания
  textures/misc/nabat_captive.png  — темнота изнутри «Колокольного плена» (янтарный отсвет по краям)
  textures/entity/nabat_soul.png   — огонёк души (мягкое ядро)
  textures/block/resonant_stone.png — «Гудящий камень» (тёмный камень с янтарными прожилками)
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures")

BRONZE = (150, 108, 60, 255)
BRONZE_D = (92, 64, 36, 255)
BRONZE_L = (206, 160, 92, 255)
AMBER = (255, 190, 80, 255)
AMBER_HI = (255, 240, 190, 255)
TEAL = (70, 224, 212, 255)
OUT = (30, 20, 12, 255)


def save(img, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def bell_shape(d, cx, top, h, w0, w1, fill, outline=OUT):
    pts = []
    for i in range(9):
        t = i / 8
        w = w0 + (w1 - w0) * (t ** 1.6)
        pts.append((cx - w / 2, top + t * h))
    for i in range(8, -1, -1):
        t = i / 8
        w = w0 + (w1 - w0) * (t ** 1.6)
        pts.append((cx + w / 2, top + t * h))
    d.polygon(pts, fill=fill, outline=outline)


def icon_noise():
    """Шум: янтарные звуковые дуги от точки."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((2, 7, 6, 11), fill=AMBER_HI, outline=OUT)
    for k, r in enumerate((5, 8, 11)):
        col = AMBER if k < 2 else (230, 150, 50, 255)
        d.arc((4 - r, 9 - r, 4 + r, 9 + r), -50, 50, fill=OUT, width=3)
        d.arc((4 - r, 9 - r, 4 + r, 9 + r), -45, 45, fill=col, width=1)
    save(img, "mob_effect", "noise.png")


def icon_dome():
    """Купол: светлая полусфера над колоколом."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.pieslice((1, 2, 17, 26), 180, 360, fill=(255, 226, 150, 90), outline=AMBER)
    d.arc((3, 4, 15, 24), 200, 260, fill=AMBER_HI)
    bell_shape(d, 9, 7, 8, 4, 9, BRONZE)
    d.line([(5, 15), (13, 15)], fill=BRONZE_L)
    d.point((9, 16), fill=AMBER_HI)
    save(img, "mob_effect", "dome.png")


def icon_requiem():
    """Реквием: колокол и поднимающиеся к нему огоньки душ."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bell_shape(d, 9, 1, 9, 4, 10, BRONZE)
    d.line([(4, 10), (14, 10)], fill=BRONZE_L)
    for x, y in ((4, 15), (9, 13), (14, 16)):
        d.ellipse((x - 1.6, y - 1.6, x + 1.6, y + 1.6), fill=AMBER, outline=(120, 70, 20, 255))
        d.point((x, y), fill=AMBER_HI)
    save(img, "mob_effect", "requiem.png")


def icon_crack():
    """Трещина: колокол с ломаной светящейся трещиной."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bell_shape(d, 9, 1, 14, 5, 15, BRONZE)
    d.line([(1, 15), (17, 15)], fill=BRONZE_L, width=1)
    d.line([(9, 3), (8, 6), (10, 8), (7, 11), (9, 14)], fill=OUT, width=2)
    d.line([(9, 3), (8, 6), (10, 8), (7, 11), (9, 14)], fill=AMBER_HI, width=1)
    save(img, "mob_effect", "crack.png")


def icon_mark():
    """Метка набата: колокол над кольцом‑мишенью."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 11, 17, 17), outline=AMBER, width=1)
    d.ellipse((5, 12.5, 13, 15.5), outline=(230, 150, 50, 255))
    bell_shape(d, 9, 0, 9, 3.5, 9, BRONZE)
    d.line([(9, 9), (9, 11)], fill=AMBER_HI)
    save(img, "mob_effect", "bell_mark.png")


def icon_prisoner():
    """Узник: бирюзовый реликварий и звено цепи."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((5, 5, 12, 15), outline=OUT, fill=(30, 60, 60, 255))
    d.rectangle((7, 7, 10, 13), fill=TEAL)
    d.point((8, 9), fill=(220, 255, 250, 255))
    d.line([(5, 4), (12, 4)], fill=(212, 166, 78, 255), width=2)
    d.line([(5, 16), (12, 16)], fill=(212, 166, 78, 255), width=1)
    d.ellipse((7, 0, 11, 4), outline=(160, 160, 170, 255))
    save(img, "mob_effect", "prisoner.png")


def ring():
    """Кольцо‑тень: яркий тонкий обод + размытый внутренний свет (белое — цвет задаёт код)."""
    S = 128
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    d.ellipse((c - 60, c - 60, c + 60, c + 60), outline=(255, 255, 255, 255), width=4)
    d.ellipse((c - 50, c - 50, c + 50, c + 50), outline=(255, 255, 255, 120), width=2)
    # засечки по кругу
    for k in range(16):
        a = k / 16 * 2 * math.pi
        r0, r1 = 52, 58 if k % 2 else 62
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)],
               fill=(255, 255, 255, 200), width=2)
    fill = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fill)
    fd.ellipse((c - 56, c - 56, c + 56, c + 56), fill=(255, 255, 255, 40))
    fill = fill.filter(ImageFilter.GaussianBlur(6))
    out = Image.alpha_composite(fill, img.filter(ImageFilter.GaussianBlur(0.6)))
    save(out, "misc", "nabat_ring.png")


def ripple():
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    for r, a, w in ((28, 255, 3), (22, 140, 2), (15, 70, 1)):
        d.ellipse((c - r, c - r, c + r, c + r), outline=(255, 255, 255, a), width=w)
    save(img.filter(ImageFilter.GaussianBlur(0.8)), "misc", "nabat_ripple.png")


def captive():
    W, H = 256, 144
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    p = img.load()
    for y in range(H):
        for x in range(W):
            dx = (x - W / 2) / (W / 2)
            dy = (y - H / 2) / (H / 2)
            r = math.sqrt(dx * dx + dy * dy)
            k = max(0.0, min(1.0, (r - 0.35) / 0.75))
            a = int(255 * (k ** 1.5))
            glow = max(0.0, 1.0 - abs(r - 1.05) / 0.35)
            p[x, y] = (int(40 + 160 * glow), int(20 + 90 * glow), int(6 + 20 * glow), a)
    save(img.filter(ImageFilter.GaussianBlur(2)), "misc", "nabat_captive.png")


def soul():
    S = 32
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    p = img.load()
    for y in range(S):
        for x in range(S):
            dx = (x - S / 2 + 0.5) / (S / 2)
            dy = (y - S / 2 + 0.5) / (S / 2)
            r = math.sqrt(dx * dx + dy * dy * 0.8)
            a = max(0.0, 1.0 - r) ** 1.8
            core = max(0.0, 1.0 - r * 2.4)
            v = int(200 + 55 * core)
            p[x, y] = (v, v, v, int(255 * min(1.0, a + core)))
    save(img, "entity", "nabat_soul.png")


def resonant_stone():
    rnd = random.Random(1972)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    p = img.load()
    for y in range(16):
        for x in range(16):
            n = rnd.random()
            base = (62, 48, 40) if n > 0.2 else (48, 36, 30)
            if n > 0.92:
                base = (84, 66, 52)
            p[x, y] = base + (255,)
    # янтарные прожилки
    for path in ([(0, 4), (3, 5), (6, 4), (9, 7), (12, 7), (15, 9)], [(2, 15), (4, 12), (7, 11), (8, 13), (11, 14)],
                 [(13, 0), (12, 2), (14, 4)]):
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            n = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(n + 1):
                x = round(x0 + (x1 - x0) * i / n)
                y = round(y0 + (y1 - y0) * i / n)
                if 0 <= x < 16 and 0 <= y < 16:
                    p[x, y] = (255, 186, 74, 255) if (x + y) % 3 else (255, 230, 170, 255)
    save(img, "block", "resonant_stone.png")


if __name__ == "__main__":
    icon_noise()
    icon_dome()
    icon_requiem()
    icon_crack()
    icon_mark()
    icon_prisoner()
    ring()
    ripple()
    captive()
    soul()
    resonant_stone()
    print("готово")
