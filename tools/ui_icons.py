"""
Значки построек для меню строительного планшета (16×16, пиксель-арт).
Запуск из корня проекта:  python tools/ui_icons.py   (нужен Pillow)
Пишет assets/basedefense/textures/gui/tablet/<id>.png — по одному на каждое здание.
Значок — символ работы жителя (молоток, сноп, ведро с молоком…), а не блок.
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense/textures/gui/tablet"

PAL = {
    "K": (34, 30, 28),        # контур
    "w": (150, 104, 62),      # дерево
    "W": (196, 146, 92),      # дерево, блик
    "d": (98, 66, 40),        # дерево, тень
    "s": (150, 156, 164),     # железо
    "S": (214, 222, 230),     # железо, блик
    "t": (92, 98, 108),       # железо, тень
    "y": (226, 186, 70),      # солома / золото
    "Y": (250, 224, 128),
    "o": (176, 128, 40),
    "g": (96, 160, 70),       # зелень
    "G": (150, 206, 108),
    "r": (196, 64, 52),       # красный
    "R": (238, 110, 90),
    "b": (60, 120, 196),      # синий
    "B": (130, 186, 240),
    "f": (255, 148, 40),      # огонь
    "F": (255, 220, 110),
    "m": (236, 236, 228),     # молоко / белое
    "c": (120, 84, 60),       # корова (коричневая)
    "n": (70, 66, 64),        # уголь
    "p": (210, 140, 120),     # розовый
    "e": (90, 92, 98),        # тёмный камень
}

ICONS = {
    # Строитель: молоток накрест с мастерком
    "builder_house": [
        "................",
        "....KKK.........",
        "...KSSsK........",
        "..KSSsssK.......",
        ".KSsssssK.......",
        ".KsssssstK......",
        "..KssssttKK.....",
        "...KsttK.KwK....",
        "....KKK..KwdK...",
        "..........KwdK..",
        "...........KwdK.",
        "............KwdK",
        ".............KdK",
        "..............K.",
        "................",
        "................",
    ],
    # Фермер: сноп пшеницы
    "farmer_house": [
        "......K..K......",
        "....K.YK.YK.K...",
        "...KYK.YKy.KYK..",
        "...KyYK.Yy.KyK..",
        "....KyYKyYKyK...",
        ".....KyyYyyK....",
        "......KyYyK.....",
        ".....KoooooK....",
        "......KyyyK.....",
        "......KyYyK.....",
        ".....KyyYyyK....",
        "....KyyKyKyyK...",
        "....KyK.y.KyK...",
        "...KyK..y..KyK..",
        "...KK...K...KK..",
        "................",
    ],
    # Скотовод: ведро с молоком
    "rancher_house": [
        "................",
        "....KKKKKKKK....",
        "...K........K...",
        "..K..........K..",
        "..KKKKKKKKKKKK..",
        "..KmmmmmmmmmmK..",
        "..KSsmmmmmmssK..",
        "...KSsssssssK...",
        "...KSsssssstK...",
        "...KssKKKKstK...",
        "...KSsssssstK...",
        "....KSsssstK....",
        "....KSsssstK....",
        "....KKKKKKKK....",
        "................",
        "................",
    ],
    # Повар: котелок над огнём
    "cook_house": [
        "....K..K..K.....",
        "...K..K..K......",
        "....K..K..K.....",
        "..KKKKKKKKKKK...",
        ".KtsSSSSSSsstK..",
        ".KKKKKKKKKKKKK..",
        "..KnssssssssK...",
        "..KnsssssssnK...",
        "..KnnsssssnnK...",
        "...KnnnnnnnK....",
        "....KKKKKKK.....",
        "...KfK.KfK.KfK..",
        "..KfFfKfFfKfFfK.",
        "..KdwwwwwwwwwdK.",
        "...KKKKKKKKKKK..",
        "................",
    ],
    # Шахтёр: кирка
    "miner_house": [
        "................",
        "...KKKKKKK......",
        "..KSSsssstKK....",
        ".KSKKKKKstsK....",
        ".KK....KwKtsK...",
        "......KwdK.KtK..",
        ".....KwdK...KtK.",
        "....KwdK....KK..",
        "...KwdK.........",
        "..KwdK..........",
        ".KwdK...........",
        "KwdK............",
        "KdK.............",
        ".K..............",
        "................",
        "................",
    ],
    # Кузнец: наковальня с искрами
    "blacksmith_house": [
        "..........F.....",
        "........F...F...",
        "......F..Y.F....",
        "..........F.....",
        ".KKKKKKKKKKKK...",
        "KSSSSSSSSSSssK..",
        ".KsssssssssttK..",
        "..KKKsssssKKK...",
        "....KsssstK.....",
        "....KsssstK.....",
        "...KssssssKK....",
        "..KsssssssstK...",
        "..KKKKKKKKKKK...",
        "................",
        "................",
        "................",
    ],
    # Лесоруб: топор в пне
    "lumberjack_house": [
        "................",
        "........KKKK....",
        ".......KSSssK...",
        "......KSSsssK...",
        "......KSsssK....",
        ".....KKKstK.....",
        "....KwdK.KK.....",
        "...KwdK.........",
        "..KwdK..........",
        "..KdK...........",
        "...K............",
        ".KKKKKKKKKKK....",
        "KWWWWWWWWWWWK...",
        "KwdwWWdwwdwwK...",
        "KwwdwwwdwwdwK...",
        "KKKKKKKKKKKKK...",
    ],
    # База: сундук-склад с бирюзовым флагом
    "base_house": [
        "..K.............",
        "..KbbbbK........",
        "..KBbbbbK.......",
        "..KbBbbK........",
        "..KbbK..........",
        "..K.............",
        "..K.............",
        ".KKKKKKKKKKKKK..",
        ".KWWWWWWWWWWWK..",
        ".KwwwwwKKwwwwK..",
        ".KKKKKKyyKKKKK..",
        ".KwwwwwKKwwwwK..",
        ".KwdwwwwwwwwdK..",
        ".KwwdwwwwwwdwK..",
        ".KKKKKKKKKKKKK..",
        "................",
    ],
    # Казарма: красный щит и скрещённые мечи
    "barracks": [
        "SK............KS",
        "KSK..........KSK",
        ".KSK........KSK.",
        "..KSKKKKKKKKKSK.",
        "...KSyyyyyySK...",
        "...KrSrrrrSrK...",
        "...KrrSrrSrrK...",
        "...KrrrSSrrrK...",
        "...KrrrSSrrrK...",
        "...KrrSrrSrrK...",
        "..KoKrrrrrrKoK..",
        ".KoK.KrrrrK.KoK.",
        "KdK...KrrK...KdK",
        "dK.....KK.....Kd",
        "................",
        "................",
    ],
    # Площадь: колокол
    "town_square": [
        "................",
        ".......KK.......",
        "......KyyK......",
        ".....KYyyoK.....",
        "....KYyyyyoK....",
        "....KYyyyyoK....",
        "....KYyyyyoK....",
        "...KYyyyyyyoK...",
        "...KYyyyyyyoK...",
        "..KYyyyyyyyyoK..",
        "..KKKKKKKKKKKK..",
        "......KooK......",
        ".......KK.......",
        "................",
        "................",
        "................",
    ],
}


def build(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, f"строка {y}: {len(row)} символов"
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), PAL[ch] + (255,))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        assert len(rows) == 16, name
        build(rows).save(OUT / (name + ".png"))
    print("готово:", len(ICONS), "значков →", OUT)


if __name__ == "__main__":
    main()
