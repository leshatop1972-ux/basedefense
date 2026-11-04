"""
Текстуры предметов мода (16×16). Запуск из корня проекта:
    python tools/item_textures.py
Нужен Pillow (pip install pillow).

Предметы: повестка, рубашка (одежда).
Значки пустых слотов одежды (шапка, рубашка, штаны, обувь) — бледные силуэты,
как у ванильных пустых слотов брони: textures/item/empty_slot_clothing_*.png.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense/textures/item"


def build_with(rows, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    assert len(rows) == 16, "нужно 16 строк"
    for y, row in enumerate(rows):
        assert len(row) == 16, f"строка {y}: нужно 16 символов, а их {len(row)}"
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
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

# Рубашка: льняная, светлая, воротник, планка с пуговицами, закатанные рукава
SHIRT = [
    "................",
    "....OOO..OOO....",
    "..OOcCCOOCCcOO..",
    ".OLLLcCKKCcLLLO.",
    "OLLLLLLKbKLLLLLO",
    "OLLLLLLKKLLLLLLO",
    "OlLLOLLKbKLLOLlO",
    "OsslOLLKKLLLOlsO",
    ".OO.OLLKbKLLO.O.",
    "....OLLKKLLLO...",
    "....OLLKbKLLO...",
    "....OlLKKLLlO...",
    "....OlLLLLLlO...",
    "....OslllllsO...",
    "....OOOOOOOOO...",
    "................",
]

SHIRT_COLORS = {
    "O": (74, 60, 46, 255),      # обводка
    "L": (222, 210, 184, 255),   # лён
    "l": (190, 176, 148, 255),   # лён, тень
    "s": (158, 142, 114, 255),   # складки, глубокая тень
    "c": (240, 232, 212, 255),   # воротник, блик
    "C": (206, 194, 168, 255),   # воротник, изнанка
    "K": (196, 182, 154, 255),   # планка
    "b": (110, 82, 56, 255),     # пуговицы
}

# Пустые слоты одежды: силуэты одним бледным цветом (как ванильные значки пустой брони)
SLOT_HEAD = [
    "................",
    "................",
    "................",
    "................",
    "......XXXX......",
    ".....XXXXXX.....",
    "....XXXXXXXX....",
    "....XXXXXXXX....",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

SLOT_CHEST = [
    "................",
    "................",
    ".....XX..XX.....",
    "...XXXXXXXXXX...",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XX.XXXXXX.XX..",
    "..XX.XXXXXX.XX..",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    "................",
    "................",
    "................",
]

SLOT_LEGS = [
    "................",
    "................",
    "................",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    ".....XXXXXX.....",
    ".....XXX.XXX....",
    ".....XXX.XXX....",
    ".....XXX.XXX....",
    ".....XXX.XXX....",
    ".....XXX.XXX....",
    ".....XXX.XXX....",
    "................",
    "................",
    "................",
    "................",
]

SLOT_FEET = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....XX....XX....",
    "....XX....XX....",
    "....XX....XX....",
    "...XXX...XXX....",
    "..XXXX..XXXX....",
    "................",
    "................",
    "................",
    "................",
    "................",
]

SLOT_COLORS = {"X": (139, 139, 139, 140)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    build_with(SUMMONS, SUMMONS_COLORS).save(OUT / "summons.png")
    build_with(SHIRT, SHIRT_COLORS).save(OUT / "shirt.png")
    for name, rows in (("head", SLOT_HEAD), ("chest", SLOT_CHEST), ("legs", SLOT_LEGS), ("feet", SLOT_FEET)):
        build_with(rows, SLOT_COLORS).save(OUT / f"empty_slot_clothing_{name}.png")
    print("готово:", OUT)


if __name__ == "__main__":
    main()
