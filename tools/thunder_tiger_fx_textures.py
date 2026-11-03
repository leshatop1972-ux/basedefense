"""
Значки эффектов «Громового тигра» 18×18: textures/mob_effect/tremor, scorch, prey, inner_strength, tiger_rounds,
savage_rounds. Запуск: python tools/thunder_tiger_fx_textures.py
"""
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures",
                    "mob_effect")
OUT = (20, 14, 10, 255)


def new():
    return Image.new("RGBA", (18, 18), (0, 0, 0, 0))


def outline(img):
    px = img.load()
    src = img.copy().load()
    for y in range(18):
        for x in range(18):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < 18 and 0 <= yy < 18 and src[xx, yy][3] and src[xx, yy] != OUT:
                    px[x, y] = OUT
                    break
    return img


def save(img, name):
    os.makedirs(ROOT, exist_ok=True)
    outline(img).save(os.path.join(ROOT, name + ".png"))


def tremor():
    img = new()
    px = img.load()
    for x in range(2, 16):
        for k, (col, off) in enumerate((((232, 160, 70, 255), 5), ((255, 214, 130, 255), 9), ((200, 120, 50, 255), 13))):
            y = off + round(1.6 * math.sin(x * 1.3 + k))
            if 0 <= y < 18:
                px[x, y] = col
    save(img, "tremor")


def scorch():
    img = new()
    px = img.load()
    for y in range(18):
        for x in range(18):
            dx = (x - 8.5) / 6.0
            h = (17 - y) / 15.0
            w = (1 - h) * 0.9 + 0.1 * math.sin(h * 9)
            if abs(dx) < w and h < 1.0 and y > 1:
                t = abs(dx) / max(w, 1e-3)
                col = (255, 240, 170, 255) if t < 0.3 and h < 0.6 else ((255, 150, 40, 255) if t < 0.7 else (200, 60, 20, 255))
                px[x, y] = col
    save(img, "scorch")


def prey():
    img = new()
    px = img.load()
    for a in range(0, 360, 6):
        r = math.radians(a)
        x, y = round(8.5 + 6.5 * math.cos(r)), round(8.5 + 6.5 * math.sin(r))
        px[x, y] = (224, 176, 64, 255)
    for k in (-3, 0, 3):                                    # три когтя
        for i in range(9):
            x, y = 6 + k // 1 + i // 3 + 2, 4 + i
            if 0 <= x < 18:
                px[min(17, x + k), y] = (200, 40, 30, 255)
    save(img, "prey")


def inner():
    img = new()
    px = img.load()
    for a in range(0, 360, 45):
        r = math.radians(a)
        for d in range(2, 8 if a % 90 == 0 else 6):
            px[round(8.5 + d * math.cos(r)), round(8.5 + d * math.sin(r))] = (255, 222, 110, 255)
    for y in range(6, 12):
        for x in range(6, 12):
            if (x - 8.5) ** 2 + (y - 8.5) ** 2 < 8:
                px[x, y] = (255, 250, 210, 255)
    save(img, "inner_strength")


def bullet(name, case, tip):
    img = new()
    px = img.load()
    for y in range(2, 16):
        for x in range(6, 12):
            if y < 6:
                if abs(x - 8.5) <= (y - 1) * 0.8:
                    px[x, y] = tip
            elif y == 13:
                px[x, y] = (110, 80, 40, 255)
            else:
                px[x, y] = (255, 236, 160, 255) if x == 7 else case
    save(img, name)


if __name__ == "__main__":
    tremor()
    scorch()
    prey()
    inner()
    bullet("tiger_rounds", (200, 160, 72, 255), (150, 110, 70, 255))
    bullet("savage_rounds", (200, 160, 72, 255), (210, 50, 30, 255))
    print("готово")
