"""
Текстуры предметов мода (16×16). Запуск из корня проекта:
    python tools/item_textures.py
Нужен Pillow (pip install pillow).

Сейчас тут один предмет — строительный планшет:
деревянная дощечка с латунным зажимом, на ней лист-чертёж (синяя «синька»
с сеткой и белым контуром домика), справа — перо с чернилами.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense/textures/item"

# Пиксель-арт по строкам, 16×16. Точка — прозрачный пиксель.
TABLET = [
    "......KMMK......",
    ".....KmmMMK.....",
    ".OOOOKMMMMKOOO..",
    "OwwwwKKKKKKwwdO.",
    "OwbBBBBBBBBBBdO.",
    "OwBbBBBLBBBbBdOP",
    "OwBBBBLBLBBBBdPp",
    "OwBBBLBBBLBBBPpO",
    "OwbBLLLLLLLBbPdO",
    "OwBBBLBBBLBBPpdO",
    "OwBBBLBYBLBBPBdO",
    "OwBbBLBYBLBNbBdO",
    "OwBBBLLLLLLNBBdO",
    "OwbBBBBBBBBBBbdO",
    "OwddddddddddddO.",
    ".OOOOOOOOOOOOOO.",
]

COLORS = {
    "O": (59, 40, 24, 255),      # тёмная обводка
    "w": (176, 128, 74, 255),    # светлое дерево (край)
    "d": (112, 76, 42, 255),     # тёмное дерево (тень)
    "B": (46, 94, 168, 255),     # синька
    "b": (70, 122, 196, 255),    # линии сетки на синьке
    "L": (232, 242, 255, 255),   # белый контур домика
    "Y": (247, 214, 92, 255),    # окошко/дверь — тёплый свет
    "K": (122, 96, 40, 255),     # латунь, тень
    "M": (201, 160, 74, 255),    # латунь
    "m": (246, 222, 150, 255),   # латунь, блик
    "P": (24, 22, 30, 255),      # перо с чернилами
    "p": (92, 88, 110, 255),     # блик на пере
    "N": (36, 52, 96, 255),      # капля чернил у кончика пера
}


def build(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    assert len(rows) == 16, "нужно 16 строк"
    for y, row in enumerate(rows):
        assert len(row) == 16, f"строка {y}: нужно 16 символов, а их {len(row)}"
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), COLORS[ch])
    return img


# Повестка: свёрнутый лист с текстом, красная сургучная печать с мечом и лента
SUMMONS = [
    "................",
    "...OOOOOOOOOO...",
    "..OwWWWWWWWWwO..",
    "..OWWWWWWWWWWO..",
    "..OWllllllllWO..",
    "..OWWWWWWWWWWO..",
    "..OWllllllWWWO..",
    "..OWWWWWWWWWWO..",
    "..OWlllllRRRWO..",
    "..OWWWWWRrrrRO..",
    "..OWllllRrSrRO..",
    "..OWWWWWRrSrRO..",
    "..OwwwwwRrrrRO..",
    "..OOOOOOOROROO..",
    ".........R.R....",
    "................",
]

SUMMONS_COLORS = {
    "O": (70, 52, 34, 255),      # обводка
    "W": (236, 228, 204, 255),   # бумага
    "w": (204, 192, 160, 255),   # бумага, тень по краю
    "l": (120, 112, 104, 255),   # строки текста
    "R": (150, 24, 28, 255),     # сургуч, край
    "r": (196, 44, 40, 255),     # сургуч
    "S": (232, 200, 120, 255),   # оттиск меча на печати
}


def build_with(rows, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, f"строка {y}: нужно 16 символов, а их {len(row)}"
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    build(TABLET).save(OUT / "building_tablet.png")
    build_with(SUMMONS, SUMMONS_COLORS).save(OUT / "summons.png")
    print("готово:", OUT / "building_tablet.png", OUT / "summons.png")


if __name__ == "__main__":
    main()
