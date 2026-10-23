"""
Проверка анимаций моба на «баги» без игры:
  python tools/anim_check.py bloodthirster [порог_градусов_за_кадр]

Ищет:
  * рывки — поворот кости между соседними кадрами больше порога (по умолчанию 40°): обычно это
    переворот Эйлера в IK (кость «прокручивается» на 180° за кадр);
  * разрыв цикла — у зацикленной анимации первый и последний кадр сильно различаются;
  * масштаб, который «прыгает» (например, мечи/лицо).
Выводит список проблем; пусто — всё гладко.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import euler, mm, tr  # noqa: E402


def angle_between(r0, r1):
    """Настоящий угол между двумя поворотами (градусы), независимо от записи Эйлера."""
    m = mm(tr(euler(r0)), euler(r1))
    c = max(-1.0, min(1.0, (m[0][0] + m[1][1] + m[2][2] - 1) / 2))
    return math.degrees(math.acos(c))

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")


def val(v):
    if isinstance(v, dict):
        return v.get("pre", v.get("post")), v.get("post", v.get("pre"))
    return v, v


def main():
    name = sys.argv[1]
    limit = float(sys.argv[2]) if len(sys.argv) > 2 else 40.0
    with open(os.path.join(ROOT, "animations", name + ".animation.json"), encoding="utf-8") as f:
        anims = json.load(f)["animations"]
    problems = 0
    for an, a in anims.items():
        short = an.split(".")[-1]
        for bone, chans in a.get("bones", {}).items():
            for ch in ("rotation", "scale"):
                keys = chans.get(ch)
                if not keys:
                    continue
                items = sorted(((float(t), v) for t, v in keys.items()), key=lambda x: x[0])
                lim = limit if ch == "rotation" else 0.35
                for (t0, v0), (t1, v1) in zip(items, items[1:]):
                    _, post0 = val(v0)
                    pre1, _ = val(v1)
                    d = max(abs(pre1[i] - post0[i]) for i in range(3))
                    if ch == "rotation":
                        real = angle_between(post0, pre1)
                        if real > lim:
                            print("%-14s %-12s %.3f→%.3f с: рывок %.0f° за кадр" % (short, bone, t0, t1, real))
                            problems += 1
                        elif d > lim * 1.5:
                            print("%-14s %-12s %.3f→%.3f с: «кувырок» Эйлера %.0f° (реально %.0f°)"
                                  % (short, bone, t0, t1, d, real))
                            problems += 1
                    elif d > lim:
                        print("%-14s %-12s %-8s %.3f→%.3f с: скачок %.1f" % (short, bone, ch, t0, t1, d))
                        problems += 1
                if a.get("loop") and ch == "rotation" and len(items) > 2:
                    first = val(items[0][1])[1]
                    last = val(items[-1][1])[0]
                    d = max(abs(((first[i] - last[i]) + 180) % 360 - 180) for i in range(3))
                    if d > 6:
                        print("%-14s %-12s цикл не замкнут: %.1f°" % (short, bone, d))
                        problems += 1
    print("проблем:", problems)


if __name__ == "__main__":
    main()
