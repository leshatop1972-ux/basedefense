"""
Предпросмотр мобов из Blockbench (Bedrock .geo.json + .animation.json) без запуска игры.
Переводит модель и анимации ТЕМИ ЖЕ формулами, что и Java-загрузчик
(client/entity/bedrock/BedrockModelLoader и BedrockAnimationLoader), и рисует кадры.

Запуск:
    python tools/mob_preview.py iron_zombie            — поза покоя спереди/сбоку/сзади
    python tools/mob_preview.py iron_knight attack1    — 8 кадров анимации
    python tools/mob_preview.py frost_archer attack 0.5,1.6,2.0  — кадры в заданные секунды
    (угол обзора: переменная окружения PREVIEW_YAW, радианы)
Картинка пишется в tools/mob_preview.png (в мод не попадает).
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import settler_preview as sp  # noqa: E402  (тот же 3D-рендер кубов, что у жителей)

YAW = float(os.environ.get("PREVIEW_YAW", "0.6"))
ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")
DEG = math.pi / 180


def load_geo(name):
    with open(os.path.join(ASSETS, "geo", name + ".geo.json"), encoding="utf-8") as f:
        geo = json.load(f)["minecraft:geometry"][0]
    return geo


def java_name(name):
    # «root» у ванильной модели — это корень модели, поэтому кость переименовывается
    return "bone_root" if name == "root" else name


def build_parts(geo):
    """Bedrock → Java: x тот же, y = 24 - y, z тот же; повороты те же (в градусах → радианы)."""
    bones = geo["bones"]
    by_parent = {}
    for b in bones:
        by_parent.setdefault(b.get("parent"), []).append(b)

    def make(b, parent_abs):
        px, py, pz = b.get("pivot", [0, 0, 0])
        jp = (px, 24 - py, pz)
        rot = tuple(r * DEG for r in b.get("rotation", [0, 0, 0]))
        cubes, children = [], []
        for i, c in enumerate(b.get("cubes", [])):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            u, v = c["uv"]
            e = c.get("inflate", 0)
            crot = c.get("rotation")
            if crot and any(abs(r) > 1e-6 for r in crot):
                cpx, cpy, cpz = c.get("pivot", [0, 0, 0])
                cj = (cpx, 24 - cpy, cpz)
                cube = (u, v, ox - cpx, cpy - oy - sy, oz - cpz, sx, sy, sz, e)
                children.append(dict(name=java_name(b["name"]) + "_r" + str(i),
                                     pivot=(cj[0] - jp[0], cj[1] - jp[1], cj[2] - jp[2]),
                                     rot=tuple(r * DEG for r in crot), cubes=[cube], children=[], tag=None))
            else:
                cubes.append((u, v, ox - px, py - oy - sy, oz - pz, sx, sy, sz, e))
        for ch in by_parent.get(b["name"], []):
            children.append(make(ch, jp))
        return dict(name=java_name(b["name"]), pivot=(jp[0] - parent_abs[0], jp[1] - parent_abs[1], jp[2] - parent_abs[2]),
                    rot=rot, cubes=cubes, children=children, tag=None)

    return [make(b, (0, 0, 0)) for b in by_parent.get(None, [])]


def _collect(parts, look, transform, out):
    """Как settler_preview.collect, но с масштабом части (как ModelPart: опора → поворот → масштаб)."""
    for p in parts:
        pitch, yaw, roll = p["rot"]
        px, py, pz = p["pivot"]
        sx, sy, sz = p.get("scale", (1, 1, 1))

        def tf(v, pitch=pitch, yaw=yaw, roll=roll, px=px, py=py, pz=pz, sx=sx, sy=sy, sz=sz, parent=transform):
            v = (v[0] * sx, v[1] * sy, v[2] * sz)
            v = sp.rot_x(v, pitch)
            v = sp.rot_y(v, yaw)
            v = sp.rot_z(v, roll)
            return parent((v[0] + px, v[1] + py, v[2] + pz))

        for cube in p["cubes"]:
            for verts, u1, v1, u2, v2 in sp.cuboid_quads(cube):
                out.append(([tf(vv) for vv in verts], u1, v1, u2, v2))
        _collect(p["children"], look, tf, out)


sp.collect = _collect


# --------------------------- анимации ---------------------------

def ease(name, x):
    if name in (None, "linear"):
        return x
    if name == "easeInQuad":
        return x * x
    if name == "easeOutQuad":
        return 1 - (1 - x) * (1 - x)
    if name == "easeInOutQuad":
        return 2 * x * x if x < 0.5 else 1 - (-2 * x + 2) ** 2 / 2
    if name == "easeInCubic":
        return x ** 3
    if name == "easeOutCubic":
        return 1 - (1 - x) ** 3
    if name == "easeInOutCubic":
        return 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2
    if name == "easeInSine":
        return 1 - math.cos(x * math.pi / 2)
    if name == "easeOutSine":
        return math.sin(x * math.pi / 2)
    if name == "easeInOutSine":
        return -(math.cos(math.pi * x) - 1) / 2
    return x


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def channel_keys(ch):
    """[(t, [x,y,z])] — с запечёнными easing и pre/post, только линейная интерполяция."""
    if not isinstance(ch, dict):
        return [(0.0, [num(x) for x in (ch if isinstance(ch, list) else [ch] * 3)])]
    raw = []
    for t, kv in ch.items():
        t = float(t)
        if isinstance(kv, list):
            raw.append((t, [num(x) for x in kv], [num(x) for x in kv], None))
        elif isinstance(kv, dict):
            if "vector" in kv:
                vv = [num(x) for x in kv["vector"]]
                raw.append((t, vv, vv, kv.get("easing")))
            else:
                pre = kv.get("pre", kv.get("post"))
                post = kv.get("post", pre)
                pre = [num(x) for x in (pre if isinstance(pre, list) else [pre] * 3)]
                post = [num(x) for x in (post if isinstance(post, list) else [post] * 3)]
                raw.append((t, pre, post, None))
        else:
            raw.append((t, [num(kv)] * 3, [num(kv)] * 3, None))
    raw.sort(key=lambda r: r[0])
    out = []
    for i, (t, pre, post, easing) in enumerate(raw):
        if i > 0 and easing not in (None, "linear"):
            t0, _, prev, _ = raw[i - 1]
            for k in range(1, 8):
                f = k / 8
                e = ease(easing, f)
                out.append((t0 + (t - t0) * f, [prev[j] + (pre[j] - prev[j]) * e for j in range(3)]))
        out.append((t, pre))
        if post != pre:
            out.append((t + 0.0001, post))
    return out


def sample(keys, t):
    if t <= keys[0][0]:
        return keys[0][1]
    for i in range(1, len(keys)):
        if t <= keys[i][0]:
            t0, a = keys[i - 1]
            t1, b = keys[i]
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a[j] + (b[j] - a[j]) * f for j in range(3)]
    return keys[-1][1]


def load_anims(name):
    with open(os.path.join(ASSETS, "animations", name + ".animation.json"), encoding="utf-8") as f:
        data = json.load(f)["animations"]
    anims = {}
    for full, an in data.items():
        short = full.split(".")[-1]
        bones = {}
        for bone, chans in an.get("bones", {}).items():
            bones[java_name(bone)] = {c: channel_keys(v) for c, v in chans.items() if c in ("rotation", "position", "scale")}
        anims[short] = dict(length=float(an.get("animation_length", 0)), bones=bones)
    return anims


def apply_anim(parts, anim, t, weight=1.0):
    def walk(ps):
        for p in ps:
            ch = anim["bones"].get(p["name"])
            if ch:
                pitch, yaw, roll = p["rot"]
                px, py, pz = p["pivot"]
                if "rotation" in ch:
                    r = sample(ch["rotation"], t)
                    pitch, yaw, roll = pitch + r[0] * DEG * weight, yaw + r[1] * DEG * weight, roll + r[2] * DEG * weight
                if "position" in ch:
                    v = sample(ch["position"], t)
                    px, py, pz = px + v[0] * weight, py - v[1] * weight, pz + v[2] * weight
                p["rot"] = (pitch, yaw, roll)
                p["pivot"] = (px, py, pz)
                if "scale" in ch:
                    v = sample(ch["scale"], t)
                    p["scale"] = tuple(1 + (v[j] - 1) * weight for j in range(3))
            walk(p["children"])
    walk(parts)


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "iron_zombie"
    anim_name = sys.argv[2] if len(sys.argv) > 2 else None
    out = os.path.join(os.path.dirname(__file__), "mob_preview.png")
    geo = load_geo(name)
    tex = Image.open(os.path.join(ASSETS, "textures", "entity", name + ".png")).convert("RGBA")
    size, scale = (260, 300), 6
    frames = []
    if anim_name:
        anims = load_anims(name)
        an = anims[anim_name]
        times = [float(x) for x in sys.argv[3].split(",")] if len(sys.argv) > 3 else None
        n = len(times) if times else 8
        for i in range(n):
            t = times[i] if times else an["length"] * i / n
            parts = build_parts(geo)
            apply_anim(parts, an, t)
            sp.PARTS = parts
            frames.append((sp.render(tex, None, YAW, scale=scale, size=size), "t=%.2f" % t))
    else:
        for yaw in (0.0, math.pi / 2, math.pi):
            sp.PARTS = build_parts(geo)
            frames.append((sp.render(tex, None, yaw, scale=scale, size=size), ""))
    sheet = Image.new("RGBA", (size[0] * len(frames), size[1]), (60, 64, 72, 255))
    for i, (img, _) in enumerate(frames):
        sheet.alpha_composite(img, (i * size[0], 0))
    sheet.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
