"""
Проверка стыков: начало и конец каждой атаки должны совпадать с покоем не только по позе,
но и по ЗАПИСИ углов (иначе при плавном переходе кость прокрутится на оборот — так «багались» мечи).
  python tools/anim_seams.py bloodthirster idle2:slash,thrust,... idle1:swing,...
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")


def frame(anims, name, clip, last):
    out = {}
    for b, ch in anims["animation.%s.%s" % (name, clip)]["bones"].items():
        r = ch.get("rotation")
        if not r:
            continue
        ks = sorted(r.items(), key=lambda kv: float(kv[0]))
        v = ks[-1][1] if last else ks[0][1]
        if isinstance(v, dict):
            v = v["pre"] if last else v["post"]
        out[b] = v
    return out


def main():
    name = sys.argv[1]
    with open(os.path.join(ROOT, "animations", name + ".animation.json"), encoding="utf-8") as f:
        anims = json.load(f)["animations"]
    bad = 0
    for spec in sys.argv[2:]:
        base, clips = spec.split(":")
        ref = frame(anims, name, base, False)
        for c in clips.split(","):
            for last, label in ((False, "начало"), (True, "конец")):
                fr = frame(anims, name, c, last)
                for b, v in fr.items():
                    r = ref.get(b, [0, 0, 0])
                    d = max(abs(v[i] - r[i]) for i in range(3))
                    if d > 60:
                        print("%-12s %-6s %-10s %s vs покой %s" % (c, label, b, [round(x) for x in v],
                                                                  [round(x) for x in r]))
                        bad += 1
    print("стыков с расхождением:", bad)


if __name__ == "__main__":
    main()
