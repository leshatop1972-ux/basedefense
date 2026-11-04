"""
Значки температуры для HUD (справа от еды) и меню поселенца. Запуск из корня проекта:
    python tools/temperature_icons.py
Пишет assets/basedefense/textures/gui/temperature.png — полоса 45×9: 5 значков 9×9 по порядку TempStatus:
мороз (тёмная снежинка), холодно (голубая снежинка), комфорт (зелёный термометр), жарко (солнце), пекло (пламя).
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/basedefense/textures/gui/temperature.png"

SNOW = [
    "....A....",
    ".A.AWA.A.",
    "..AAWAA..",
    ".AWAWAWA.",
    "AWWWWWWWA",
    ".AWAWAWA.",
    "..AAWAA..",
    ".A.AWA.A.",
    "....A....",
]

THERMO = [
    "...OOO...",
    "...OwO...",
    "...OwO...",
    "...OGO...",
    "...OGO...",
    "..OGGGO..",
    ".OGGgGGO.",
    ".OGGGGGO.",
    "..OOOOO..",
]

SUN = [
    "....Y....",
    ".Y.....Y.",
    "...OOO...",
    "..OYYYO..",
    "Y.OYyYO.Y",
    "..OYYYO..",
    "...OOO...",
    ".Y.....Y.",
    "....Y....",
]

FLAME = [
    "....R....",
    "...RR....",
    "...RRR.R.",
    "..RRORRR.",
    ".RROOORR.",
    ".RROYORR.",
    ".RRYYYRR.",
    "..RYYYR..",
    "...RRR...",
]


def draw(rows, colors):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 9, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
    return img


def main():
    icons = [
        draw(SNOW, {"A": (30, 60, 150, 255), "W": (120, 170, 255, 255)}),           # мороз
        draw(SNOW, {"A": (90, 170, 230, 255), "W": (230, 248, 255, 255)}),          # холодно
        draw(THERMO, {"O": (40, 50, 40, 255), "w": (235, 240, 235, 255),
                      "G": (70, 200, 80, 255), "g": (170, 255, 160, 255)}),        # комфорт
        draw(SUN, {"Y": (255, 200, 40, 255), "O": (210, 120, 20, 255), "y": (255, 245, 170, 255)}),  # жарко
        draw(FLAME, {"R": (215, 40, 25, 255), "O": (255, 140, 30, 255), "Y": (255, 230, 90, 255)}),  # пекло
    ]
    strip = Image.new("RGBA", (45, 9), (0, 0, 0, 0))
    for i, im in enumerate(icons):
        strip.paste(im, (i * 9, 0))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    strip.save(OUT)
    print("готово:", OUT)


if __name__ == "__main__":
    main()
