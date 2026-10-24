"""
Текстуры эффектов Бордюра: иконки «Оглушения» и «Шипов» (18×18) и картинки для экрана.
Запуск: python tools/curb_fx_textures.py
  textures/mob_effect/stun.png, textures/mob_effect/spikes.png
  textures/misc/stun_vignette.png  — тёмная дымка по краям с бледными «звёздочками» (центр прозрачный)
  textures/misc/spikes_flash.png   — бело‑синие осколки, влетающие с краёв экрана
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense", "textures")


def stun_icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    p = img.load()
    # три звёздочки по кругу над «головой» и спираль
    for k in range(3):
        a = k * 2 * math.pi / 3 - math.pi / 2
        cx, cy = 9 + math.cos(a) * 5.5, 9 + math.sin(a) * 5.5
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            x, y = int(round(cx + dx)), int(round(cy + dy))
            p[x, y] = (255, 236, 140, 255) if (dx, dy) == (0, 0) else (214, 180, 70, 255)
    for i in range(26):
        t = i / 26 * 2.2 * math.pi
        r = 1.0 + t * 0.55
        x, y = int(round(9 + math.cos(t) * r * 0.9)), int(round(9 + math.sin(t) * r * 0.6))
        if 0 <= x < 18 and 0 <= y < 18 and p[x, y][3] == 0:
            p[x, y] = (150, 140, 120, 255)
    img.save(os.path.join(ROOT, "mob_effect", "stun.png"))


def spikes_icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # щит‑кристалл с шипами
    for k in range(8):
        a = k * math.pi / 4
        tip = (9 + math.cos(a) * 8.4, 9 + math.sin(a) * 8.4)
        l = (9 + math.cos(a + 0.5) * 3.2, 9 + math.sin(a + 0.5) * 3.2)
        r = (9 + math.cos(a - 0.5) * 3.2, 9 + math.sin(a - 0.5) * 3.2)
        d.polygon([tip, l, r], fill=(150, 205, 255, 255) if k % 2 else (230, 245, 255, 255))
    d.ellipse((5, 5, 12, 12), fill=(90, 150, 230, 255))
    d.ellipse((7, 6, 10, 9), fill=(235, 248, 255, 255))
    img.save(os.path.join(ROOT, "mob_effect", "spikes.png"))


def cartridge_icon():
    """Латунная гильза с розовым светящимся капсюлем (как ядро на груди Бордюра)."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(6, 5), (9, 1), (12, 5)], fill=(150, 150, 160, 255))           # пуля
    d.line([(9, 1), (11, 4)], fill=(210, 210, 220, 255))
    d.rectangle((6, 5, 12, 15), fill=(196, 160, 72, 255))                  # гильза
    d.rectangle((6, 5, 7, 15), fill=(232, 198, 110, 255))                  # блик
    d.rectangle((11, 5, 12, 15), fill=(140, 110, 44, 255))
    d.line([(6, 8), (12, 8)], fill=(120, 94, 36, 255))
    d.rectangle((5, 15, 13, 16), fill=(150, 120, 50, 255))                 # донце
    d.rectangle((8, 11, 10, 13), fill=(255, 150, 240, 255))                # светящаяся метка
    d.point((9, 12), fill=(255, 235, 252, 255))
    img.save(os.path.join(ROOT, "mob_effect", "cartridge.png"))


def stun_vignette():
    W = H = 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    p = img.load()
    for y in range(H):
        for x in range(W):
            dx, dy = (x + 0.5) / W - 0.5, (y + 0.5) / H - 0.5
            r = math.sqrt(dx * dx * 1.0 + dy * dy * 1.0) * 2
            a = max(0.0, min(1.0, (r - 0.55) / 0.5)) ** 1.6
            p[x, y] = (18, 16, 12, int(235 * a))
    d = ImageDraw.Draw(img)
    rnd = random.Random(4)
    # бледные «звёздочки» по краю — круг оглушения
    for k in range(14):
        a = k / 14 * 2 * math.pi + rnd.random() * 0.2
        cx, cy = W / 2 + math.cos(a) * W * 0.40, H / 2 + math.sin(a) * H * 0.40
        s = 3 + rnd.random() * 2.5
        d.polygon([(cx, cy - s * 1.6), (cx + s * 0.45, cy - s * 0.45), (cx + s * 1.6, cy), (cx + s * 0.45, cy + s * 0.45),
                   (cx, cy + s * 1.6), (cx - s * 0.45, cy + s * 0.45), (cx - s * 1.6, cy), (cx - s * 0.45, cy - s * 0.45)],
                  fill=(255, 232, 150, 150))
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    img.save(os.path.join(ROOT, "misc", "stun_vignette.png"))


def spikes_flash():
    W = H = 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(11)
    for k in range(34):
        side = k % 4
        t = rnd.random()
        if side == 0:
            base = (t * W, 0)
        elif side == 1:
            base = (t * W, H)
        elif side == 2:
            base = (0, t * H)
        else:
            base = (W, t * H)
        cx, cy = W / 2, H / 2
        vx, vy = cx - base[0], cy - base[1]
        ln = math.hypot(vx, vy)
        vx, vy = vx / ln, vy / ln
        length = 26 + rnd.random() * 46
        width = 5 + rnd.random() * 8
        tip = (base[0] + vx * length, base[1] + vy * length)
        nx, ny = -vy, vx
        a = (base[0] + nx * width, base[1] + ny * width)
        b = (base[0] - nx * width, base[1] - ny * width)
        col = (235, 248, 255, 225) if rnd.random() < 0.4 else (150, 205, 255, 200)
        d.polygon([a, tip, b], fill=col)
        d.line([base, tip], fill=(255, 255, 255, 240), width=1)
    img = img.filter(ImageFilter.GaussianBlur(0.4))
    img.save(os.path.join(ROOT, "misc", "spikes_flash.png"))


if __name__ == "__main__":
    os.makedirs(os.path.join(ROOT, "misc"), exist_ok=True)
    stun_icon()
    spikes_icon()
    cartridge_icon()
    stun_vignette()
    spikes_flash()
    print("готово")
