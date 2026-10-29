"""
Текстуры артефактов: иконки амулетов (рывок, взлёт, вампиризм/скорость/жизнь I–III) и кнопка вкладки артефактов в инвентаре.
Запуск: python tools/artifact_textures.py
  → textures/item/dash_amulet.png (16×16), textures/gui/artifact_button.png (20×36: обычная и под мышью)
"""
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures")


def amulet(name="dash_amulet", gem=((40, 150, 200), (60, 80, 50)), glyph=((6, 9), (7, 9), (8, 10), (7, 11), (9, 9)), ink=(236, 252, 255, 255)):
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
    px[6, 8] = (255, 255, 255, 255)
    for x, y in glyph:
        px[x, y] = ink
    img.save(os.path.join(ROOT, "item", name + ".png"))


def cloak(name="vampire_cloak", cloth=(58, 14, 24), fold=(30, 6, 8), lining=(150, 18, 30),
          outline=(24, 10, 14, 255), collar=((96, 22, 34), (120, 28, 40)), gem=((210, 30, 40), (160, 10, 20)), collar_edge=(40, 14, 18)):
    """Плащ‑накидка с подкладкой в разрезе и золотой застёжкой с камнем (по умолчанию — плащ вампиризма)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 16):
        half = 2 + (y - 2) * 0.48            # расширяется книзу
        for x in range(16):
            dx = x + 0.5 - 8
            if abs(dx) <= half:
                edge = abs(dx) > half - 1 or y == 15
                hem = y == 15 and int(x) % 3 == 0     # зубчатый низ
                if hem:
                    continue
                if edge:
                    px[x, y] = outline
                elif abs(dx) < 1.0 and y > 4:
                    px[x, y] = lining + (255,)                  # подкладка в разрезе
                else:
                    t = 0.5 + 0.5 * math.sin(dx * 1.3)          # складки
                    k = 1 - (y - 2) / 20
                    base = (cloth[0] + fold[0] * t * k, cloth[1] + fold[1] * t, cloth[2] + fold[2] * t)
                    if dx < 0:
                        base = tuple(c * 1.15 for c in base)    # свет слева
                    px[x, y] = tuple(int(min(255, c)) for c in base) + (255,)
    # воротник
    for x in range(4, 12):
        px[x, 2] = (collar_edge + (255,)) if x in (4, 11) else collar[0] + (255,)
        px[x, 1] = outline if x in (4, 11) else collar[1] + (255,)
    # застёжка: золото + алый камень
    for x, y in ((7, 3), (8, 3), (7, 4), (8, 4)):
        px[x, y] = (236, 196, 88, 255)
    px[6, 3] = (176, 130, 46, 255)
    px[9, 3] = (176, 130, 46, 255)
    px[7, 4] = gem[0] + (255,)
    px[8, 4] = gem[1] + (255,)
    px[7, 3] = (255, 230, 160, 255)
    img.save(os.path.join(ROOT, "item", name + ".png"))


def ring(name, gem, dots):
    """Кольцо: золотой ободок (вид спереди под наклоном) и камень сверху; dots — точки уровня на ободке."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 7.5, 10.0
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - 8) / 6.0
            dy = (y + 0.5 - cy - 0.5) / 4.6
            d = math.hypot(dx, dy)
            if 0.62 <= d <= 1.0:
                edge = d > 0.88 or d < 0.72
                light = y < cy
                px[x, y] = (40, 30, 22, 255) if edge else ((236, 196, 88, 255) if light else (170, 124, 44, 255))
    # оправа камня
    for y in range(1, 7):
        for x in range(4, 12):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 4)
            if d <= 3.4:
                if d > 2.5:
                    px[x, y] = (40, 30, 22, 255)
                else:
                    k = max(0.0, 1 - d / 2.5)
                    (r0, g0, b0), (dr, dg, db) = gem
                    px[x, y] = (int(r0 + dr * k), int(g0 + dg * k), int(b0 + db * k), 255)
    px[7, 3] = (255, 255, 255, 255)
    for x, y in dots:
        px[x, y] = (255, 240, 200, 255)
    img.save(os.path.join(ROOT, "item", name + ".png"))


def sword(name="god_sword"):
    """Меч богов: золотой клинок по диагонали с белым бликом, гарда с голубым камнем."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    out = (60, 40, 10, 255)
    for i in range(2, 12):                       # клинок от (13,2) к (4,11)
        x, y = 15 - i, i
        px[x, y] = (255, 236, 150, 255)
        px[x - 1, y] = (226, 180, 70, 255)
        px[x, y - 1] = (255, 250, 220, 255)
        px[x + 1, y] = out
        px[x - 1, y + 1] = out
    px[13, 1] = out
    px[14, 2] = out
    for x, y in ((2, 10), (3, 11), (4, 12), (5, 13), (6, 14)):   # гарда поперёк
        px[x, y] = (200, 150, 50, 255)
    px[4, 12] = (90, 200, 255, 255)
    for x, y in ((2, 13), (1, 14)):              # рукоять
        px[x, y] = (110, 70, 40, 255)
    px[0, 15] = (236, 196, 88, 255)
    img.save(os.path.join(ROOT, "item", name + ".png"))


def book(name="summoner_chronicle"):
    """Летопись призывателя: толстая книга в синей коже, золотые уголки, на обложке меч."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 15):
        for x in range(3, 14):
            edge = x in (3, 13) or y in (2, 14)
            if edge:
                c = (20, 24, 50)
            elif x == 4:
                c = (50, 60, 120)                     # корешок
            elif y == 13 and x > 4:
                c = (235, 225, 200)                   # страницы снизу
            else:
                t = (x + y) % 5 == 0
                c = (40, 60, 150) if not t else (46, 68, 160)
            px[x, y] = c + (255,)
    for x, y in ((12, 3), (12, 12), (5, 3), (5, 12)):
        px[x, y] = (236, 196, 88, 255)
    for y in range(4, 11):                        # меч на обложке
        px[9, y] = (220, 225, 235, 255)
    px[8, 9] = px[10, 9] = (236, 196, 88, 255)
    px[9, 11] = (236, 196, 88, 255)
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
    # уворот: серебристо‑лиловый камень, римская цифра
    for lvl, xs in numerals.items():
        amulet(f"dodge_amulet_{lvl}", ((150, 150, 200), (70, 70, 50)), tuple((x, y) for x in xs for y in (9, 10, 11)),
               (52, 40, 96, 255))
    # мудрость: глубокий синий камень с «глазом»
    amulet("wisdom_amulet", ((30, 40, 150), (60, 90, 90)),
           ((6, 10), (7, 9), (8, 9), (9, 10), (7, 11), (8, 11), (5, 10), (10, 10)))
    cloak()
    # вторая партия
    amulet("memory_amulet", ((30, 120, 130), (70, 110, 90)),                     # память: бирюзовый, «глаз»
           ((6, 10), (7, 9), (8, 9), (9, 10), (7, 11), (8, 11), (7, 10)), (230, 255, 250, 255))
    amulet("eternity_amulet", ((200, 170, 60), (55, 70, 120)),                   # вечность: золотой, ∞
           ((5, 9), (6, 10), (5, 11), (7, 10), (8, 10), (9, 9), (10, 10), (9, 11)), (255, 255, 240, 255))
    amulet("greed_amulet", ((30, 130, 50), (60, 100, 60)),                      # жадность: изумруд, монета
           ((7, 9), (8, 9), (6, 10), (9, 10), (7, 11), (8, 11)), (255, 220, 90, 255))
    amulet("fury_amulet", ((170, 40, 10), (80, 90, 30)),                        # ярость: огненный, клык
           ((6, 9), (7, 10), (8, 11), (9, 10), (10, 9)), (255, 230, 150, 255))
    for lvl, xs in ((1, (7,)), (2, (6, 9))):
        amulet(f"purity_amulet_{lvl}", ((170, 210, 240), (80, 40, 15)),        # чистота: светло‑голубой
               tuple((x, y) for x in xs for y in (9, 10, 11)), (40, 70, 120, 255))
    # третья партия
    amulet("crusher", ((90, 90, 100), (90, 90, 90)),                            # дробитель: стальной, трещина
           ((8, 8), (7, 9), (8, 10), (7, 11), (8, 12)), (30, 30, 36, 255))
    sword()
    cloak("ice_cloak", cloth=(40, 90, 140), fold=(40, 60, 60), lining=(200, 240, 255), outline=(14, 30, 50, 255),
          collar=((90, 160, 210), (150, 210, 240)), gem=((170, 240, 255), (90, 190, 240)))
    cloak("holy_cloak", cloth=(190, 185, 170), fold=(50, 50, 40), lining=(240, 200, 80), outline=(70, 60, 40, 255),
          collar=((236, 196, 88), (255, 230, 150)), gem=((255, 250, 220), (240, 200, 80)))
    book()
    ring("greed_ring_1", ((30, 130, 50), (60, 100, 60)), ((7, 14),))
    ring("greed_ring_2", ((30, 130, 50), (60, 100, 60)), ((5, 14), (9, 14)))
    # пустой кулон: золотая оправа с пустым тёмным гнездом (для стола амулетов)
    amulet("empty_pendant", ((34, 30, 38), (26, 24, 30)), ((7, 10), (8, 10)), (70, 64, 78, 255))
    button()
    print("ok")
