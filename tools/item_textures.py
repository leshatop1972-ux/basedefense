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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    build(TABLET).save(OUT / "building_tablet.png")
    print("готово:", OUT / "building_tablet.png")


if __name__ == "__main__":
    main()
