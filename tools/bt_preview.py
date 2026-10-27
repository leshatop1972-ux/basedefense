"""
Превью кровожада С МЕЧАМИ (мечи — отдельная модель по дорожкам bloodthirster_r/l.weapon.json).
    python tools/bt_preview.py <клип> <с,с,...>        (YAW, SCALE, W, H; PHASE=1 — текстура 1‑й фазы)
Картинка — tools/mob_preview.png.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import mob_preview as mp  # noqa: E402
import nabat_gen as ng  # noqa: E402
import settler_preview as sp  # noqa: E402
from PIL import Image, ImageChops, ImageDraw, ImageFilter  # noqa: E402

ROOT = ng.ROOT
MOB, SW = "bloodthirster", "bloodthirster_sword"


def tex(name, glow):
    suf = "_glowmask" if glow else ""
    a = Image.open(os.path.join(ROOT, "textures", "entity", name + suf + ".png")).convert("RGBA")
    w = Image.open(os.path.join(ROOT, "textures", "entity", SW + suf + ".png")).convert("RGBA")
    out = Image.new("RGBA", (max(a.width, w.width), a.height + w.height), (0, 0, 0, 0))
    out.paste(a, (0, 0))
    out.paste(w, (0, a.height))
    return out


def parts_at(clip, t):
    geo = mp.load_geo(MOB)
    parts = mp.build_parts(geo)
    mp.apply_anim(parts, mp.load_anims(MOB)[clip], t)
    th = geo["description"]["texture_height"]
    for side in ("r", "l"):
        with open(os.path.join(ROOT, "animations", "%s_%s.weapon.json" % (MOB, side)), encoding="utf-8") as f:
            wj = json.load(f)
        tr = wj["clips"][clip]
        R = ng.q_to_R(ng.track_q(tr, t))
        s = 1.0
        if "s" in tr:
            x = max(0.0, min(len(tr["s"]) - 1.0, (t % tr["length"] if tr["loop"] else t) * tr["fps"]))
            s = tr["s"][int(x)]
        tf = ng._find_tf(parts, wj["hand"])
        p = tf(tuple(wj["hand_offset"]))
        wparts = mp.build_parts(mp.load_geo(SW))
        ng._shift_uv(wparts, th)
        parts.append(dict(name="w" + side, pivot=tuple(p), rot=tuple(math.radians(v) for v in ng.to_euler(R)),
                          scale=(s, s, s), cubes=[], tag=None,
                          children=[dict(name="wo" + side, pivot=(0, -24.0, 0), rot=(0, 0, 0), cubes=[], tag=None,
                                         children=wparts)]))
    return parts


def main():
    clip = sys.argv[1]
    times = [float(x) for x in sys.argv[2].split(",")]
    name = MOB if os.environ.get("PHASE") == "1" else MOB + "_p2"
    T, G = tex(name, False), tex(name, True)
    yaw = float(os.environ.get("YAW", "0.6"))
    scale = float(os.environ.get("SCALE", "4.5"))
    W, H = int(os.environ.get("W", "380")), int(os.environ.get("H", "460"))
    frames = []
    for t in times:
        sp.PARTS = parts_at(clip, t)
        img = sp.render(T, None, yaw, scale=scale, size=(W, H), baseline=H - 30)
        g = sp.render(G, None, yaw, scale=scale, size=(W, H), baseline=H - 30)
        bg = Image.new("RGB", (W, H), (44, 46, 58))
        d = ImageDraw.Draw(bg)
        d.rectangle([0, H - 30, W, H], fill=(38, 42, 36))
        d.text((6, 6), "%s %.2f" % (clip, t), fill=(230, 220, 200))
        o = bg.convert("RGBA")
        o.alpha_composite(img)
        glow = g.convert("RGB")
        o = ImageChops.add(o.convert("RGB"), ImageChops.multiply(glow.filter(ImageFilter.GaussianBlur(3)),
                                                                Image.new("RGB", (W, H), (120, 60, 60))))
        frames.append(o)
    sheet = Image.new("RGB", (W * len(frames), H))
    for i, f in enumerate(frames):
        sheet.paste(f, (i * W, 0))
    sheet.save(os.path.join(os.path.dirname(__file__), "mob_preview.png"))
    print("saved")


if __name__ == "__main__":
    main()
