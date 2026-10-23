"""
Общие инструменты для генерации мобов кодом (модель Bedrock + текстура + анимации).
Используется tools/frost_archer_gen.py; пригодится для следующих мобов.

- Model: кости и кубы в координатах Bedrock (y вверх, лицо в −z), упаковка box‑UV, запись .geo.json.
- Painter: рисование граней куба по развёртке (top/bottom/front/back/left/right), светящаяся маска.
- Кинематика в Java‑пространстве (x, 24 − y, z): повороты костей в Bedrock и Java совпадают
  (см. client/entity/bedrock/BedrockModelLoader), world()/chain_R() — прямая кинематика,
  two_bone() — обратная кинематика руки/ноги из двух звеньев.
- bake(): поза‑функция → .animation.json (кадры с заданной частотой, переходы через ±360° — мгновенные).
"""
import json
import math
import os
import random

from PIL import Image


# =====================================================================
# Модель
# =====================================================================
class Model:
    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tex_w, self.tex_h = tex_w, tex_h
        self.bones = []
        self.by_name = {}

    def bone(self, name, parent, pivot):
        b = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def cube(self, bname, origin, size, mat, inflate=0.0, rot=None, pivot=None, face=None, **extra):
        c = {"origin": [round(v, 4) for v in origin], "size": [round(v, 4) for v in size], "mat": mat}
        if inflate:
            c["inflate"] = inflate
        if rot:
            c["rotation"] = [round(v, 3) for v in rot]
            c["pivot"] = [round(v, 4) for v in pivot]
        if face:
            c["face"] = face
        c.update(extra)
        self.by_name[bname]["cubes"].append(c)
        return c

    def mirror_x(self, src_bone, dst_bone, parent=None):
        """Скопировать кость с кубами зеркально по x (правая → левая сторона)."""
        s = self.by_name[src_bone]
        px, py, pz = s["pivot"]
        self.bone(dst_bone, parent or s.get("parent"), (-px, py, pz))
        for c in s["cubes"]:
            (ox, oy, oz), (sx, sy, sz) = c["origin"], c["size"]
            rot = c.get("rotation")
            piv = c.get("pivot")
            extra = {k: v for k, v in c.items() if k not in ("origin", "size", "mat", "inflate", "rotation", "pivot", "face")}
            self.cube(dst_bone, (-ox - sx, oy, oz), (sx, sy, sz), c["mat"], c.get("inflate", 0.0),
                      rot=(rot[0], -rot[1], -rot[2]) if rot else None,
                      pivot=(-piv[0], piv[1], piv[2]) if piv else None, face=c.get("face"), **extra)

    def cubes(self):
        return [c for b in self.bones for c in b["cubes"]]

    def pack(self):
        order = sorted(self.cubes(), key=lambda c: (-footprint(c)[4], -footprint(c)[3]))
        x = y = row_h = 0
        for c in order:
            fw, fh = footprint(c)[3:5]
            if x + fw > self.tex_w:
                x, y, row_h = 0, y + row_h, 0
            c["uv"] = [x, y]
            x += fw
            row_h = max(row_h, fh)
        assert y + row_h <= self.tex_h, "текстура мала: нужно %d" % (y + row_h)

    # --- кинематика ---
    def finalize(self):
        self.piv = {b["name"]: jpos(b["pivot"]) for b in self.bones}
        self.parent = {b["name"]: b.get("parent") for b in self.bones}

    def rel_pivot(self, name):
        p = self.parent[name]
        return sub(self.piv[name], self.piv[p]) if p else self.piv[name]

    def chain_R(self, name, pose):
        path = []
        n = name
        while n:
            path.append(n)
            n = self.parent[n]
        R = ident()
        for n in reversed(path):
            R = mm(R, euler(pose.get(n, {}).get("rot", (0, 0, 0))))
        return R

    def world(self, name, local, pose):
        """Точка local (Java, относительно опоры кости) → пространство модели."""
        p = list(local)
        n = name
        while n:
            st = pose.get(n, {})
            p = mv(euler(st.get("rot", (0, 0, 0))), p)
            p = add(add(p, self.rel_pivot(n)), st.get("pos", (0, 0, 0)))
            n = self.parent[n]
        return p

    def local_of(self, name, bedrock_point):
        """Точка модели (Bedrock) в локальных координатах кости (Java)."""
        return sub(jpos(bedrock_point), self.piv[name])

    def point_bone(self, pose, name, target_dir_world):
        """Повернуть кость так, чтобы её ось (+y Java, «вниз») смотрела в target_dir_world."""
        Rp = self.chain_R(self.parent[name], pose)
        pose.setdefault(name, {})["rot"] = dir_euler(mv(tr(Rp), target_dir_world))

    def two_bone(self, pose, upper, lower, target, pole, a, b):
        """IK: верхнее звено длины a, нижнее b, кисть в target, сгиб в сторону pole (всё в модели)."""
        S = self.world(upper, [0, 0, 0], pose)
        d = sub(target, S)
        dist = length(d)
        dist = max(abs(a - b) + 0.05, min(a + b - 0.05, dist))
        dn = norm(d)
        cos_al = (a * a + dist * dist - b * b) / (2 * a * dist)
        al = math.acos(max(-1.0, min(1.0, cos_al)))
        p = sub(pole, scl(dn, dot(pole, dn)))
        pn = norm(p) if length(p) > 1e-6 else [0, 0, 1]
        E = add(S, scl(add(scl(dn, math.cos(al)), scl(pn, math.sin(al))), a))
        self.point_bone(pose, upper, sub(E, S))
        Tc = add(S, scl(dn, dist))
        self.point_bone(pose, lower, sub(Tc, E))

    def set_world_rot(self, pose, name, R_world):
        Rp = self.chain_R(self.parent[name], pose)
        pose.setdefault(name, {})["rot"] = to_euler(mm(tr(Rp), R_world))

    def write_geo(self, root):
        out_bones = []
        for b in self.bones:
            ob = {"name": b["name"], "pivot": b["pivot"]}
            if "parent" in b:
                ob["parent"] = b["parent"]
            cubes = []
            for c in b["cubes"]:
                oc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
                for k in ("inflate", "pivot", "rotation"):
                    if k in c:
                        oc[k] = c[k]
                cubes.append(oc)
            if cubes:
                ob["cubes"] = cubes
            out_bones.append(ob)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.name, "texture_width": self.tex_w,
                            "texture_height": self.tex_h, "visible_bounds_width": 4, "visible_bounds_height": 4,
                            "visible_bounds_offset": [0, 1.5, 0]},
            "bones": out_bones}]}
        with open(os.path.join(root, "geo", self.name + ".geo.json"), "w", encoding="utf-8") as f:
            json.dump(geo, f, indent=1)


def footprint(c):
    w, h, d = (max(1, math.ceil(s - 1e-6)) for s in c["size"])
    return w, h, d, 2 * (w + d), h + d


def faces(c):
    w, h, d = footprint(c)[:3]
    u, v = c["uv"]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


# =====================================================================
# Рисование
# =====================================================================
FACE_LIGHT = {"top": 1.08, "bottom": 0.70, "front": 1.0, "back": 0.86, "right": 0.91, "left": 0.91}


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(ch * k) for ch in c[:3])


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Painter:
    def __init__(self, w, h, seed=1972):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px, self.gl = self.img.load(), self.glow.load()
        self.w, self.h = w, h
        self.rnd = random.Random(seed)

    def put(self, x, y, col, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (col[0], col[1], col[2], a)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (0, 0, 0, 0)
            self.gl[x, y] = (0, 0, 0, 0)

    def glow_put(self, x, y, col):
        self.put(x, y, col)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.gl[x, y] = (col[0], col[1], col[2], 255)

    def noise(self, k=0.05):
        return 1 + (self.rnd.random() - 0.5) * 2 * k

    def fill(self, rect, fn):
        """fn(i, j, w, h) → цвет | None (прозрачно) | ('glow', цвет)."""
        x0, y0, w, h = rect
        for j in range(h):
            for i in range(w):
                c = fn(i, j, w, h)
                if c is None:
                    self.clear(x0 + i, y0 + j)
                elif isinstance(c, tuple) and len(c) == 2 and c[0] == "glow":
                    self.glow_put(x0 + i, y0 + j, c[1])
                else:
                    self.put(x0 + i, y0 + j, c)


# =====================================================================
# Математика (Java‑пространство: y вниз, лицо в −z)
# =====================================================================
def ident():
    return [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def rx_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz_m(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def tr(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def euler(deg):
    x, y, z = (math.radians(d) for d in deg)
    return mm(rz_m(z), mm(ry_m(y), rx_m(x)))


def eulerR(x=0.0, y=0.0, z=0.0):
    return euler((x, y, z))


def to_euler(R):
    sy = max(-1.0, min(1.0, -R[2][0]))
    b = math.asin(sy)
    a = math.atan2(R[2][1], R[2][2])
    c = math.atan2(R[1][0], R[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def dir_euler(d):
    """Повороты (rz = 0), переводящие локальную +y в направление d."""
    d = norm(d)
    a = -math.acos(max(-1, min(1, d[1])))
    b = math.atan2(-d[0], -d[2]) if abs(math.sin(a)) > 1e-4 else 0.0
    return [math.degrees(a), math.degrees(b), 0.0]


def length(v):
    return math.sqrt(sum(x * x for x in v))


def norm(v):
    l = length(v) or 1
    return [x / l for x in v]


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def scl(a, k):
    return [x * k for x in a]


def jpos(p):
    return [p[0], 24 - p[1], p[2]]


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = max(0.0, min(1.0, t))
    return t * t * t


def lerp(a, b, t):
    return a + (b - a) * t


def lerp3(a, b, t):
    return [lerp(a[i], b[i], t) for i in range(3)]


def blend(p1, p2, t):
    out = {}
    for n in set(p1) | set(p2):
        a, b = p1.get(n, {}), p2.get(n, {})
        st = {}
        for k, dflt in (("rot", (0, 0, 0)), ("pos", (0, 0, 0)), ("scale", (1, 1, 1))):
            if k in a or k in b:
                st[k] = lerp3(a.get(k, dflt), b.get(k, dflt), t)
        out[n] = st
    return out


# =====================================================================
# Анимации
# =====================================================================
def bake(fn, length_s, fps, loop, bones, extra=None):
    out = {}
    n = int(round(length_s * fps))
    for k in range(n + 1):
        t = min(k / fps, length_s)
        pose = fn(t)
        if extra:
            extra(pose, t)
        key = ("%.4f" % t).rstrip("0").rstrip(".") or "0"
        for name in bones:
            st = pose.get(name, {})
            ch = out.setdefault(name, {})
            ch.setdefault("rotation", {})[key] = [round(v, 2) for v in st.get("rot", (0, 0, 0))]
            if "pos" in st:
                px, py, pz = st["pos"]
                ch.setdefault("position", {})[key] = [round(px, 3), round(-py, 3), round(pz, 3)]
            if "scale" in st:
                ch.setdefault("scale", {})[key] = [round(v, 4) for v in st["scale"]]
    for name, chans in out.items():
        rot = chans.get("rotation")
        if not rot:
            continue
        keys = list(rot.keys())
        for i in range(1, len(keys)):
            prev, cur = rot[keys[i - 1]], rot[keys[i]]
            prev = prev["post"] if isinstance(prev, dict) else prev
            if any(abs(cur[j] - prev[j]) > 180 for j in range(3)):
                pre = [cur[j] + round((prev[j] - cur[j]) / 360.0) * 360 for j in range(3)]
                rot[keys[i]] = {"pre": pre, "post": cur}
    for name in list(out):
        for cname in list(out[name]):
            vals = [v if isinstance(v, list) else v["post"] for v in out[name][cname].values()]
            neutral = [1, 1, 1] if cname == "scale" else [0, 0, 0]
            if all(v == neutral for v in vals):
                del out[name][cname]
        if not out[name]:
            del out[name]
    return {"loop": loop, "animation_length": length_s, "bones": out}


def write_anims(root, name, animations):
    with open(os.path.join(root, "animations", name + ".animation.json"), "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": animations}, f, separators=(",", ":"))
