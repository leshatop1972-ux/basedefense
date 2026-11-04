"""
Текстуры предметов мода (16×16). Запуск из корня проекта:
    python tools/item_textures.py
Нужен Pillow (pip install pillow).

Предметы: повестка и одежда (рубашка + 16 вещей из CLOTHES).
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


# ---------------------------------------------------------------------------
# Одежда. Символы: O — обводка, M — основной цвет, S — тень, H — блик, A/B — детали (пуговицы, пояс, мех…)
# ---------------------------------------------------------------------------

STRAW_HAT = [
    "................",
    "................",
    "................",
    "................",
    ".....OOOOOO.....",
    "....OHMMMMSO....",
    "....OMHMMMSO....",
    "....OAAAAAAO....",
    "..OOOMMMMMSSOO..",
    ".OHHMMMMMMMMSSO.",
    "OMMMMMMMMMMMMMSO",
    ".OSSSSSSSSSSSSO.",
    "..OOOOOOOOOOOO..",
    "................",
    "................",
    "................",
]

CAP = [
    "................",
    "................",
    "................",
    "................",
    "......OOOO......",
    "....OOHMMMOO....",
    "...OHMMMMMMSO...",
    "...OMMMAMMMSO...",
    "..OMMMMMMMMMSO..",
    "..OSSSSSSSSSSO..",
    "..OOOOOOOOAAAAO.",
    "..........OBBBBO",
    "...........OOOO.",
    "................",
    "................",
    "................",
]

FUR_HAT = [
    "................",
    "................",
    "................",
    ".....OOOOOO.....",
    "...OOMHMMMSOO...",
    "..OMHMMMMMMMSO..",
    "..OMMMMMMMMMSO..",
    ".OAABAABAABAABO.",
    ".OABBAABBAABBAO.",
    ".OAAOOOOOOOOAAO.",
    ".OABO......OBAO.",
    ".OAAO......OAAO.",
    ".OBAO......OABO.",
    "..OO........OO..",
    "................",
    "................",
]

VEST = [
    "................",
    "....OOO..OOO....",
    "...OMMO..OMMO...",
    "..OMHMMO.OMMSO..",
    "..OHMMMOOMMMSO..",
    "..OMMMMAAMMMSO..",
    "..OMMMMA.AMMSO..",
    "..OMMMMAAMMMSO..",
    "..OMHMMA.AMMSO..",
    "..OMMMMAAMMMSO..",
    "..OMMMMA.AMMSO..",
    "..OMMMMAAMMMSO..",
    "..OSSSSO.OSSSO..",
    "..OOOOO..OOOOO..",
    "................",
    "................",
]

TUNIC = [
    "................",
    "....OOO..OOO....",
    "..OOMMMOOMMMOO..",
    ".OMMMMMHHMMMMMO.",
    "OMMMMMMMMMMMMMSO",
    "OMMMMMMMMMMMMMSO",
    "OSMMOMMMMMMOMMSO",
    "OSSMOMMMMMMOMSSO",
    ".OO.OMMMMMMO.OO.",
    "....OAABAAAO....",
    "....OMMMMMMO....",
    "....OMMMMMMO....",
    "....OMMMMMSO....",
    "....OSSSSSSO....",
    "....OOOOOOOO....",
    "................",
]

SWEATER = [
    "................",
    "....OOOOOOOO....",
    "..OOAAAAAAAAOO..",
    ".OMMMAAAAAAMMMO.",
    "OMMHMMMMMMMMMMSO",
    "OMHMMMMMMMMMMMSO",
    "OMMMOBBBBBBOMMSO",
    "OMMMOMBMMBMOMMSO",
    "OMMMOBBBBBBOMMSO",
    "OMMSOMMMMMMOSMSO",
    "OAAAOMMMMMMOAAAO",
    "OOOOOMMMMMSO.OOO",
    "....OAAAAAAO....",
    "....OOOOOOOO....",
    "................",
    "................",
]

PADDED_JACKET = [
    "................",
    "....OOOOOOOO....",
    "..OOMAAAAAAMOO..",
    ".OMMMMMOOMMMMMO.",
    "OMHMMMMOAMMMMMSO",
    "OSSSSSSOASSSSSSO",
    "OMMMOMMOAMMOMMSO",
    "OSSSOSSOASSOSSSO",
    "OMMMOMMOAMMOMMSO",
    "OSSSOSSOASSOSSSO",
    "OMMMOMMOAMMOMMSO",
    "OOOOOSSOASSOOOOO",
    "....OMMOAMMO....",
    "....OOOOOOOO....",
    "................",
    "................",
]

SHEEPSKIN_COAT = [
    "................",
    "...OOOOOOOOOO...",
    "..OAAAAAAAAAAO..",
    ".OMAAAAOOAAAAMO.",
    "OMMHMMMOAMMMMMSO",
    "OMHMMMMOAMMMMMSO",
    "OMMMOMMOAMMOMMSO",
    "OMMMOMMOBMMOMMSO",
    "OMMMOMMOAMMOMMSO",
    "OMMSOMMOAMMOSMSO",
    "OAAAOMMOBMMOAAAO",
    "OOOOOMMOAMMO.OOO",
    "....OMMOAMMSO...",
    "....OAAAAAAAO...",
    "....OOOOOOOOO...",
    "................",
]

TROUSERS = [
    "................",
    "................",
    "....OOOOOOOO....",
    "....OAAABAAO....",
    "....OMHMMMSO....",
    "....OMHMMMSO....",
    "....OMMMOMMSO...",
    "....OMMSOMMSO...",
    "....OMMSOMMSO...",
    "....OMMSOMMSO...",
    "....OMMSOMMSO...",
    "....OMMSOMMSO...",
    "....OMMSOMMSO...",
    "....OSSSOSSSO...",
    "....OOOO.OOOO...",
    "................",
]

SHORTS = [
    "................",
    "................",
    "................",
    "................",
    "....OOOOOOOO....",
    "....OAAABAAO....",
    "....OMHMMMSO....",
    "....OMHMMMSO....",
    "....OMMMOMMSO...",
    "....OMMSOMMSO...",
    "....OSSSOSSSO...",
    "....OOOO.OOOO...",
    "................",
    "................",
    "................",
    "................",
]

SKIRT = [
    "................",
    "................",
    "................",
    ".....OOOOOO.....",
    ".....OAABAO.....",
    "....OMHMMMSO....",
    "....OMHMMMSO....",
    "...OMHMMMMMSO...",
    "...OMMMMSMMSO...",
    "..OMHMMSMMMMSO..",
    "..OMMMMSMMSMSO..",
    ".OMMMSMMMSMMMSO.",
    ".OAAAAAAAAAAAAO.",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]

SHOES = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...OOOO..OOOO...",
    "...OMMO..OMMO...",
    "...OMAO..OMAO...",
    "...OMMO..OMMO...",
    "..OMHMO.OMHMO...",
    ".OMMMMO.OMMMMO..",
    ".OSSSSOOOSSSSO..",
    ".OOOOOO.OOOOOO..",
    "................",
    "................",
]

SANDALS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....O.....O.....",
    "...OAO...OAO....",
    "...OAO...OAO....",
    "..OA.AO.OA.AO...",
    ".OAAAAO.OAAAAO..",
    ".OMMMMO.OMMMMO..",
    ".OSSSSO.OSSSSO..",
    ".OOOOOO.OOOOOO..",
    "................",
]

FELT_BOOTS = [
    "................",
    "................",
    "...OOOOO.OOOOO..",
    "...OHMMO.OHMMO..",
    "...OMMSO.OMMSO..",
    "...OMMSO.OMMSO..",
    "...OMMSO.OMMSO..",
    "...OMMSO.OMMSO..",
    "...OMMSO.OMMSO..",
    "...OMMSO.OMMSO..",
    "..OMHMSO.OHMMSO.",
    ".OMMMMSOOMMMMSO.",
    ".OSSSSSOOSSSSSO.",
    ".OOOOOOO.OOOOOO.",
    "................",
    "................",
]


def pal(o, m, s, h, a=None, b=None):
    d = {"O": o, "M": m, "S": s, "H": h}
    if a:
        d["A"] = a
    if b:
        d["B"] = b
    return {k: tuple(v) + (255,) for k, v in d.items()}


CLOTHES = {
    # обычная
    "straw_hat": (STRAW_HAT, pal((92, 70, 30), (226, 196, 112), (184, 150, 74), (246, 226, 160), (150, 40, 34))),
    "cap": (CAP, pal((40, 38, 42), (92, 88, 96), (66, 62, 70), (124, 120, 128), (52, 50, 56), (78, 74, 82))),
    "vest": (VEST, pal((52, 36, 24), (128, 86, 52), (98, 64, 38), (158, 112, 72), (210, 180, 90))),
    "tunic": (TUNIC, pal((60, 56, 40), (150, 146, 104), (120, 116, 80), (178, 174, 132), (96, 62, 34), (200, 170, 80))),
    "trousers": (TROUSERS, pal((30, 34, 48), (72, 84, 116), (54, 64, 92), (98, 112, 146), (84, 56, 32), (190, 160, 80))),
    "shorts": (SHORTS, pal((60, 50, 34), (176, 150, 104), (146, 122, 82), (204, 182, 138), (96, 62, 34), (190, 160, 80))),
    "skirt": (SKIRT, pal((60, 24, 30), (160, 60, 64), (126, 44, 50), (194, 92, 94), (90, 54, 32), (210, 180, 90))),
    "shoes": (SHOES, pal((36, 24, 16), (110, 72, 42), (80, 52, 30), (146, 100, 62), (190, 160, 80))),
    "sandals": (SANDALS, pal((60, 42, 24), (176, 132, 82), (140, 102, 60), (200, 160, 110), (120, 80, 44))),
    # тёплая
    "fur_hat": (FUR_HAT, pal((44, 30, 20), (90, 62, 40), (68, 46, 30), (118, 86, 58), (176, 150, 116), (140, 116, 86))),
    "wool_sweater": (SWEATER, pal((48, 20, 20), (168, 48, 44), (132, 34, 32), (200, 80, 72), (226, 220, 206), (236, 230, 216))),
    "padded_jacket": (PADDED_JACKET, pal((28, 36, 26), (82, 98, 70), (62, 76, 52), (108, 126, 94), (40, 44, 38))),
    "sheepskin_coat": (SHEEPSKIN_COAT, pal((50, 32, 18), (150, 98, 52), (120, 76, 38), (180, 126, 74), (232, 224, 204), (200, 170, 80))),
    "wool_pants": (TROUSERS, pal((36, 32, 30), (110, 100, 92), (86, 78, 72), (138, 128, 118), (70, 52, 36), (190, 160, 80))),
    "padded_trousers": (TROUSERS, pal((24, 30, 22), (70, 86, 60), (52, 66, 44), (96, 114, 84), (40, 44, 38), (110, 110, 100))),
    "felt_boots": (FELT_BOOTS, pal((40, 38, 36), (132, 126, 118), (104, 98, 92), (160, 154, 146))),
}

KNIT_HAT = [
    "................",
    "................",
    ".......OO.......",
    "......OBBO......",
    ".......OO.......",
    ".....OOOOOO.....",
    "....OHMMMMSO....",
    "...OMHMMMMMSO...",
    "...OMMMMMMMSO...",
    "...OMMMMMMMSO...",
    "...OAAAAAAAAO...",
    "...OABABABABO...",
    "...OOOOOOOOOO...",
    "................",
    "................",
    "................",
]

RAINCOAT = [
    "................",
    "....OOOOOOOO....",
    "...OMMMMMMMMO...",
    "..OMMHOAAOMMSO..",
    ".OMMHMMOAOMMMSO.",
    "OMMHMMMOAOMMMMSO",
    "OMHMOMMOBOMMOMSO",
    "OMMMOMMOAOMMOMSO",
    "OMMMOMMOAOMMOMSO",
    "OSSMOMMOBOMMOSSO",
    "OOOOOMMOAOMMOOOO",
    "....OMMOAOMMSO..",
    "....OMMOAOMMSO..",
    "....OSSSSSSSSO..",
    "....OOOOOOOOOO..",
    "................",
]

BACKPACK = [
    "................",
    "......OOOO......",
    ".....OS..SO.....",
    "...OOOOOOOOOO...",
    "..OHMMMMMMMMSO..",
    "..OMMMMMMMMMSO..",
    "..OAAAAAAAAAAO..",
    "..OMMMOBBOMMSO..",
    "..OMHMOBBOMMSO..",
    "..OMMMOOOOMMSO..",
    "..OMMMMMMMMMSO..",
    "..OMHMMMMMMMSO..",
    "..OSSSSSSSSSSO..",
    "...OOOOOOOOOO...",
    "................",
    "................",
]

BIG_BACKPACK = [
    "......OOOO......",
    ".....OS..SO.....",
    "..OOOOOOOOOOOO..",
    ".OHMMMMMMMMMMSO.",
    ".OAAAAAAAAAAAAO.",
    ".OMMOOOOOOOOMSO.",
    ".OMMOMMBBMMOMSO.",
    "OOMMOMMBBMMOMSOO",
    "OAOMOOOOOOOOMOAO",
    "OAOMMMMMMMMMMOAO",
    "OAOMHMMMMMMMMOAO",
    "OOOMMMMMMMMMSOOO",
    ".OMMMMMMMMMMMSO.",
    ".OSSSSSSSSSSSSO.",
    "..OOOOOOOOOOOO..",
    "................",
]

SLOT_BACKPACK = [
    "................",
    "................",
    "......XXXX......",
    ".....X....X.....",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "...XXX....XXX...",
    "...XXX....XXX...",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "................",
    "................",
    "................",
    "................",
]

CLOTHES.update({
    "knit_hat": (KNIT_HAT, pal((40, 50, 70), (70, 110, 170), (52, 84, 136), (110, 150, 206), (236, 232, 220), (200, 60, 50))),
    "raincoat": (RAINCOAT, pal((60, 50, 10), (226, 190, 40), (186, 150, 24), (250, 224, 110), (90, 72, 20), (40, 36, 30))),
    "backpack": (BACKPACK, pal((50, 32, 18), (140, 92, 52), (108, 70, 38), (172, 122, 76), (90, 60, 34), (200, 170, 80))),
    "big_backpack": (BIG_BACKPACK, pal((40, 34, 22), (104, 96, 62), (80, 72, 46), (134, 124, 86), (120, 78, 40), (200, 170, 80))),
})

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
    for name, (rows, colors) in CLOTHES.items():
        build_with(rows, colors).save(OUT / f"{name}.png")
    for name, rows in (("head", SLOT_HEAD), ("chest", SLOT_CHEST), ("legs", SLOT_LEGS), ("feet", SLOT_FEET)):
        build_with(rows, SLOT_COLORS).save(OUT / f"empty_slot_clothing_{name}.png")
    build_with(SLOT_BACKPACK, SLOT_COLORS).save(OUT / "empty_slot_backpack.png")
    print("готово:", OUT)


if __name__ == "__main__":
    main()
