"""
Текстуры артефактов: иконки амулетов (рывок, взлёт, вампиризм/скорость/жизнь I–III) и кнопка вкладки артефактов в инвентаре.
Запуск: python tools/artifact_textures.py
  → textures/item/dash_amulet.png (16×16), textures/gui/artifact_button.png (20×36: обычная и под мышью)
"""
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures")


def amulet(name="dash_amulet", gem=((40, 150, 200), (60, 80, 50)), glyph=((6, 9), (7, 9), (8, 10), (7, 11), (9, 9))):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # цепочка: дуга сверху, звенья через пиксель
    seen = []
    for i in range(60):
        a = math.pi * i / 59
        x = int(round(7.5 + 4.6 * math.cos(a)))
        y = int(round(1.0 + 4.6 * (1 - math.sin(a))))
        if (x, y) not in seen:
            seen.append((x, y))
    for k, (x, y) in enumerate(seen):
        px[x, y] = (226, 186, 80, 255) if k % 2 else (140, 104, 36, 255)
    # оправа и камень
    cx, cy, r = 7.5, 10.0, 4.6
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy)
            if d <= r:
                if d > r - 1.0:
                    px[x, y] = (40, 34, 30, 255)                      # контур
                elif d > r - 2.0:
                    t = (x - y + 8) / 16
                    px[x, y] = (236, 196, 88, 255) if t > 0.5 else (176, 130, 46, 255)   # золото
                else:
                    k = max(0.0, 1 - d / (r - 2))
                    (r0, g0, b0), (dr, dg, db) = gem
                    px[x, y] = (int(r0 + dr * k), int(g0 + dg * k), int(b0 + db * k), 255)  # камень
    # завиток ветра и блик
    for x, y in glyph:
        px[x, y] = (236, 252, 255, 255)
    px[6, 8] = (255, 255, 255, 255)
    img.save(os.path.join(ROOT, "item", name + ".png"))


def button():
    img = Image.new("RGBA", (20, 36), (0, 0, 0, 0))
    px = img.load()
    for row, hover in ((0, False), (18, True)):
        for y in range(18):
            for x in range(20):
                edge = x in (0, 19) or y in (0, 17)
                if edge:
                    c = (255, 255, 255) if hover else (0, 0, 0)
                elif x == 1 or y == 1:
                    c = (255, 255, 255) if not hover else (230, 230, 255)
                elif x == 18 or y == 16:
                    c = (85, 85, 85)
                else:
                    c = (198, 198, 198) if not hover else (214, 214, 236)
                px[x, row + y] = c + (255,)
        # ромб‑артефакт в центре
        for y in range(-4, 5):
            for x in range(-4, 5):
                d = abs(x) + abs(y)
                if d <= 4:
                    if d == 4:
                        c = (40, 20, 60)
                    elif x + y < -1:
                        c = (236, 190, 255)
                    else:
                        c = (150, 70, 210) if d > 1 else (200, 120, 250)
                    px[10 + x, row + 9 + y] = c + (255,)
    os.makedirs(os.path.join(ROOT, "gui"), exist_ok=True)
    img.save(os.path.join(ROOT, "gui", "artifact_button.png"))


if __name__ == "__main__":
    amulet()                                                     # рывок: бирюзовый камень, завиток ветра
    amulet("leap_amulet", ((70, 150, 40), (90, 90, 40)),         # взлёт: зелёный камень, стрелка вверх
           ((8, 8), (7, 9), (8, 9), (9, 9), (8, 10), (8, 11), (8, 12)))
    # пассивные амулеты: римская цифра (палочки) на камне
    numerals = {1: (7,), 2: (6, 9), 3: (5, 7, 9)}
    for kind, gem in (("vampire", ((110, 8, 18), (120, 20, 30))),     # тёмно‑алый
                      ("speed", ((200, 90, 10), (55, 80, 30))),        # оранжевый
                      ("life", ((200, 60, 120), (55, 80, 60)))):      # розовый
        for lvl, xs in numerals.items():
            amulet(f"{kind}_amulet_{lvl}", gem, tuple((x, y) for x in xs for y in (9, 10, 11)))
    button()
    print("ok")
