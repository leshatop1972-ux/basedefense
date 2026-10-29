"""
Текстуры стола амулетов (16×16, пиксель-арт).
Запуск из корня проекта:  python tools/amulet_table_textures.py   (нужен Pillow)

Пишет в assets/basedefense/textures/block/:
  amulet_table_side.png    — тумба из тёмного дерева с золотыми уголками и фиолетовой руной;
  amulet_table_top.png     — столешница: тёмный камень, по краю золотая кайма, в центре лиловый круг;
  amulet_table_edge.png    — торец столешницы (камень с золотой полосой);
  amulet_table_velvet.png  — бархатная подушечка;
  amulet_table_crystal.png — кристалл на подушечке.
Модель — models/block/amulet_table.json (пишется этим же скриптом).
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense"
OUT = ROOT / "textures/block"
rnd = random.Random(1029)

WOOD = (62, 40, 30)
WOOD_DARK = (40, 25, 20)
STONE = (52, 48, 60)
GOLD = (226, 184, 78)
GOLD_DARK = (150, 108, 40)
VIOLET = (150, 90, 220)
VIOLET_LIGHT = (214, 170, 255)
VELVET = (110, 30, 70)


def jitter(c, amount=8):
    d = rnd.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def new(base, amount=8):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), jitter(base, amount) + (255,))
    return img


def put(img, x, y, c):
    img.putpixel((x, y), tuple(c) + (255,))


def side():
    img = new(WOOD, 6)
    # доски по вертикали
    for x in (4, 8, 12):
        for y in range(16):
            put(img, x, y, jitter(WOOD_DARK, 4))
    # золотая окантовка сверху и снизу
    for x in range(16):
        put(img, x, 0, GOLD_DARK)
        put(img, x, 1, jitter(GOLD, 10))
        put(img, x, 15, GOLD_DARK)
    # уголки
    for x, y in ((0, 2), (1, 2), (0, 3), (15, 2), (14, 2), (15, 3), (0, 14), (1, 14), (15, 14), (14, 14)):
        put(img, x, y, GOLD)
    # руна в центре: ромб с точкой
    for y in range(5, 12):
        for x in range(4, 13):
            d = abs(x - 8) + abs(y - 8)
            if d == 3:
                put(img, x, y, VIOLET)
            elif d == 0:
                put(img, x, y, VIOLET_LIGHT)
    put(img, 8, 4, VIOLET)
    put(img, 8, 12, VIOLET)
    return img


def top():
    img = new(STONE, 6)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, GOLD_DARK)
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= i <= 14:
                put(img, x, y, jitter(GOLD, 10))
    # лиловый круг (под подушечкой виден краем)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d < 6.3:
                put(img, x, y, VIOLET if (x + y) % 3 else VIOLET_LIGHT)
    return img


def edge():
    img = new(STONE, 6)
    for x in range(16):
        put(img, x, 0, GOLD)
        put(img, x, 1, GOLD_DARK)
        put(img, x, 15, (30, 28, 36))
    return img


def velvet():
    img = new(VELVET, 6)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                put(img, x, y, jitter((140, 46, 92), 4))
    for i in range(16):
        put(img, i, 0, GOLD)
        put(img, i, 15, GOLD_DARK)
    return img


def crystal():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = (x + (15 - y)) / 30
            c = tuple(int(VIOLET[i] + (VIOLET_LIGHT[i] - VIOLET[i]) * t) for i in range(3))
            put(img, x, y, c)
    for y in range(2, 14, 3):
        put(img, 3, y, (255, 240, 255))
    return img


def face(tex, uv):
    return {"texture": tex, "uv": uv}


def cube(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def model():
    s = "#side"
    elements = [
        cube([1, 0, 1], [15, 11, 15], {
            "north": face(s, [1, 5, 15, 16]), "south": face(s, [1, 5, 15, 16]),
            "east": face(s, [1, 5, 15, 16]), "west": face(s, [1, 5, 15, 16]),
            "down": face("#edge", [1, 1, 15, 15])}),
        cube([0, 11, 0], [16, 14, 16], {
            "north": face("#edge", [0, 0, 16, 3]), "south": face("#edge", [0, 0, 16, 3]),
            "east": face("#edge", [0, 0, 16, 3]), "west": face("#edge", [0, 0, 16, 3]),
            "up": face("#top", [0, 0, 16, 16]), "down": face("#edge", [0, 0, 16, 16])}),
        cube([4, 14, 4], [12, 15, 12], {
            "north": face("#velvet", [4, 0, 12, 1]), "south": face("#velvet", [4, 0, 12, 1]),
            "east": face("#velvet", [4, 0, 12, 1]), "west": face("#velvet", [4, 0, 12, 1]),
            "up": face("#velvet", [4, 4, 12, 12])}),
        cube([7, 15, 7], [9, 19, 9], {
            "north": face("#crystal", [7, 2, 9, 6]), "south": face("#crystal", [7, 2, 9, 6]),
            "east": face("#crystal", [7, 2, 9, 6]), "west": face("#crystal", [7, 2, 9, 6]),
            "up": face("#crystal", [7, 7, 9, 9])}),
    ]
    data = {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "basedefense:block/amulet_table_side",
            "side": "basedefense:block/amulet_table_side",
            "top": "basedefense:block/amulet_table_top",
            "edge": "basedefense:block/amulet_table_edge",
            "velvet": "basedefense:block/amulet_table_velvet",
            "crystal": "basedefense:block/amulet_table_crystal",
        },
        "elements": elements,
    }
    (ROOT / "models/block/amulet_table.json").write_text(json.dumps(data, indent=2), encoding="utf-8")
    (ROOT / "models/item/amulet_table.json").write_text(
        json.dumps({"parent": "basedefense:block/amulet_table"}, indent=2), encoding="utf-8")
    (ROOT / "blockstates/amulet_table.json").write_text(
        json.dumps({"variants": {"": {"model": "basedefense:block/amulet_table"}}}, indent=2), encoding="utf-8")


def main():
    for name, fn in (("side", side), ("top", top), ("edge", edge), ("velvet", velvet), ("crystal", crystal)):
        fn().save(OUT / f"amulet_table_{name}.png")
    model()
    print("ok")


if __name__ == "__main__":
    main()
