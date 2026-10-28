"""
Текстуры казармы и военкомата (16×16, пиксель-арт).
Запуск из корня проекта:  python tools/barracks_textures.py   (нужен Pillow)

Пишет в assets/basedefense/textures/block/:
  barracks_front/side/top/bottom.png  — блок-стройплощадка казармы (в стиле других домов:
      стена с цоколем, окно или дверь, сверху черепица); у казармы — серый камень, тёмно-красная
      крыша, на фасаде красные знамёна по бокам двери;
  recruitment_office_*.png — стол вербовщика: доски, зелёное сукно столешницы, на лицевой
      стороне красный щит со скрещёнными мечами, бумаги и чернильница.
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense/textures/block"
rnd = random.Random(1972)


def jitter(c, amount=10):
    d = rnd.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise_fill(img, box, base, amount=10):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), jitter(base, amount) + (255,))


def put(img, x, y, c):
    img.putpixel((x, y), tuple(c) + (255,))


# ---------- Палитра ----------
STONE = (128, 132, 136)
STONE_D = (92, 96, 102)
STONE_L = (160, 164, 168)
COBBLE = (112, 112, 112)
ROOF = (122, 44, 40)
ROOF_D = (86, 28, 28)
ROOF_L = (156, 66, 56)
WOOD = (104, 74, 46)
WOOD_D = (70, 48, 30)
WOOD_L = (136, 100, 62)
GLASS = (150, 200, 226)
GLASS_D = (96, 140, 170)
RED = (168, 34, 34)
RED_D = (112, 20, 22)
RED_L = (206, 70, 60)
GOLD = (222, 180, 70)
IRON = (196, 202, 210)
IRON_D = (120, 126, 136)
CLOTH = (52, 96, 64)
CLOTH_D = (36, 70, 46)
PAPER = (232, 226, 206)
PAPER_D = (196, 186, 160)
INK = (30, 30, 40)


def stone_wall(img, y0, y1):
    """Каменная кладка: ряды по 4 пикселя, швы темнее, кирпичи со сдвигом."""
    noise_fill(img, (0, y0, 16, y1), STONE, 8)
    for y in range(y0, y1):
        if (y - y0) % 4 == 3:
            for x in range(16):
                put(img, x, y, jitter(STONE_D, 4))
        else:
            row = (y - y0) // 4
            for x in range(16):
                if (x + (4 if row % 2 else 0)) % 8 == 7:
                    put(img, x, y, jitter(STONE_D, 4))
                elif (y - y0) % 4 == 0:
                    put(img, x, y, jitter(STONE_L, 6))


def roof_band(img):
    """Верхняя полоса — край черепичной крыши."""
    noise_fill(img, (0, 0, 16, 3), ROOF, 10)
    for x in range(16):
        put(img, x, 3, ROOF_D)
        if x % 3 == 0:
            put(img, x, 2, ROOF_D)
        put(img, x, 0, ROOF_L)


def plinth(img):
    noise_fill(img, (0, 13, 16, 16), COBBLE, 14)
    for x in range(16):
        put(img, x, 13, STONE_D)


def window(img, x0, y0):
    for y in range(y0, y0 + 4):
        for x in range(x0, x0 + 4):
            put(img, x, y, GLASS if (x + y) % 3 else GLASS_D)
    for i in range(4):
        put(img, x0 + 1, y0 + i, WOOD_D)
        put(img, x0 + i, y0 + 1, WOOD_D)
    for i in range(-1, 5):
        put(img, x0 - 1, y0 + i, WOOD_D)
        put(img, x0 + 4, y0 + i, WOOD_D)
        put(img, x0 + i, y0 - 1, WOOD_D)
        put(img, x0 + i, y0 + 4, WOOD_D)


def banner(img, x0, y0, h):
    """Узкое красное знамя с золотой каймой и «зубцом» снизу."""
    for x in range(x0, x0 + 3):
        put(img, x, y0, WOOD_D)
    for y in range(y0 + 1, y0 + h):
        for x in range(x0, x0 + 3):
            c = RED if x != x0 + 2 else RED_D
            if y == y0 + 1:
                c = GOLD
            put(img, x, y, jitter(c, 6))
    put(img, x0 + 1, y0 + h, RED_D)
    put(img, x0 + 1, y0 + 3, GOLD)


def barracks_front():
    img = Image.new("RGBA", (16, 16))
    stone_wall(img, 0, 16)
    roof_band(img)
    plinth(img)
    # Дверь
    for y in range(7, 16):
        for x in range(6, 10):
            put(img, x, y, jitter(WOOD, 8))
    for y in range(7, 16):
        put(img, 6, y, WOOD_D)
        put(img, 9, y, WOOD_D)
    for x in range(6, 10):
        put(img, x, 7, WOOD_D)
        put(img, x, 11, WOOD_D)
    put(img, 8, 12, IRON)
    # Знамёна по бокам двери
    banner(img, 1, 5, 7)
    banner(img, 12, 5, 7)
    return img


def barracks_side():
    img = Image.new("RGBA", (16, 16))
    stone_wall(img, 0, 16)
    roof_band(img)
    plinth(img)
    window(img, 3, 6)
    window(img, 9, 6)
    return img


def barracks_top():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 2
            c = ROOF if row % 2 else mix(ROOF, ROOF_L, 0.3)
            if y % 2 == 1:
                c = ROOF_D
            if (x + row * 3) % 5 == 0 and y % 2 == 0:
                c = mix(c, ROOF_D, 0.6)
            put(img, x, y, jitter(c, 7))
    # Скрещённые мечи в центре — значок казармы
    for i in range(8):
        put(img, 4 + i, 4 + i, IRON)
        put(img, 11 - i, 4 + i, IRON)
    for (x, y) in ((4, 11), (11, 11), (5, 10), (10, 10)):
        put(img, x, y, GOLD)
    return img


def barracks_bottom():
    img = Image.new("RGBA", (16, 16))
    noise_fill(img, (0, 0, 16, 16), COBBLE, 16)
    for _ in range(18):
        put(img, rnd.randrange(16), rnd.randrange(16), STONE_D)
    return img


# ---------- Военкомат ----------

def planks(img, box, base=WOOD):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = base
            if (y - y0) % 4 == 3:
                c = WOOD_D
            elif (x * 7 + (y // 4) * 5) % 11 == 0:
                c = mix(base, WOOD_D, 0.5)
            put(img, x, y, jitter(c, 6))


def office_side():
    img = Image.new("RGBA", (16, 16))
    planks(img, (0, 0, 16, 16))
    for y in range(16):
        put(img, 0, y, WOOD_D)
        put(img, 15, y, WOOD_D)
    for x in range(16):
        put(img, x, 0, WOOD_L)
    return img


def office_front():
    img = office_side()
    # Ящик сверху с латунной ручкой
    for x in range(2, 14):
        put(img, x, 2, WOOD_D)
        put(img, x, 5, WOOD_D)
    put(img, 7, 3, GOLD)
    put(img, 8, 3, GOLD)
    # Красный щит со скрещёнными мечами
    shield = [
        "..XXXXXXXX..",
        "..XrrrrrrX..",
        "..XrrrrrrX..",
        "..XrrrrrrX..",
        "...XrrrrX...",
        "....XrrX....",
        ".....XX.....",
    ]
    for j, row in enumerate(shield):
        for i, ch in enumerate(row):
            x, y = 2 + i, 7 + j
            if ch == "X":
                put(img, x, y, GOLD)
            elif ch == "r":
                put(img, x, y, jitter(RED, 6))
    for i in range(5):
        put(img, 5 + i, 7 + i, IRON)
        put(img, 10 - i, 7 + i, IRON)
    put(img, 5, 12, IRON_D)
    put(img, 10, 12, IRON_D)
    return img


def office_top():
    img = Image.new("RGBA", (16, 16))
    planks(img, (0, 0, 16, 16), WOOD_L)
    # Зелёное сукно с рамкой
    for y in range(2, 14):
        for x in range(2, 14):
            put(img, x, y, jitter(CLOTH if (x + y) % 4 else CLOTH_D, 5))
    for i in range(2, 14):
        put(img, i, 2, CLOTH_D)
        put(img, i, 13, CLOTH_D)
        put(img, 2, i, CLOTH_D)
        put(img, 13, i, CLOTH_D)
    return img


def office_paper():
    img = Image.new("RGBA", (16, 16))
    noise_fill(img, (0, 0, 16, 16), PAPER, 6)
    for y in range(3, 14, 2):
        for x in range(2, 14 if y < 12 else 8):
            if rnd.random() < 0.8:
                put(img, x, y, PAPER_D)
    # Красная печать
    for (x, y) in ((11, 11), (12, 11), (11, 12), (12, 12), (10, 12), (13, 12), (11, 13), (12, 13)):
        put(img, x, y, RED)
    return img


def office_ink():
    img = Image.new("RGBA", (16, 16))
    noise_fill(img, (0, 0, 16, 16), INK, 6)
    for x in range(16):
        put(img, x, 0, (70, 70, 90))
    put(img, 4, 3, (110, 110, 140))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = {
        "barracks_front": barracks_front(),
        "barracks_side": barracks_side(),
        "barracks_top": barracks_top(),
        "barracks_bottom": barracks_bottom(),
        "recruitment_office_side": office_side(),
        "recruitment_office_front": office_front(),
        "recruitment_office_top": office_top(),
        "recruitment_office_paper": office_paper(),
        "recruitment_office_ink": office_ink(),
    }
    for name, img in files.items():
        img.save(OUT / (name + ".png"))
    print("готово:", len(files), "текстур →", OUT)


if __name__ == "__main__":
    main()
