"""
Генератор босса «Громовой тигр» (thunder_tiger): модели Bedrock, текстуры, анимации, дорожка тесака
и таблица таймингов для сервера (TigerTimings.java — сервер и анимации всегда совпадают).

Запуск:  python tools/thunder_tiger_gen.py
Проверка без игры:
    python tools/mob_preview.py thunder_tiger                         — поза покоя с 3 ракурсов
    python tools/thunder_tiger_gen.py --preview triple 0.2,0.4,0.6    — кадры С ТЕСАКОМ (YAW=…, SCALE=…, W=…)
    python tools/thunder_tiger_gen.py --check                         — тесак не протыкает голову и корпус
    python tools/anim_check.py thunder_tiger

Что создаёт (assets/basedefense):
  geo/thunder_tiger/thunder_tiger, thunder_tiger_blade (.geo.json)
  textures/entity/thunder_tiger/<то же>.png + _glowmask.png
  animations/thunder_tiger/thunder_tiger.animation.json и thunder_tiger.weapon.json (дорожка тесака)
  java/.../entity/enemy/thunder_tiger/TigerTimings.java

Внешность (своя): огромный бандит‑главарь ≈2.4 блока, широкий, вальяжный. Короткие чёрные волосы, щетина,
шрам через бровь, сигара в углу рта (уголёк светится). Тяжёлая тёмная кожаная куртка с латунными заклёпками
и поднятым воротом, бордовая рубаха, патронташ через грудь с крупными гильзами, тигровая шкура на левом плече,
широкий пояс с подсумками, полы куртки качаются от шагов, высокие сапоги. Тесак‑ганблейд: широкий изогнутый клинок
с вытравленными тигровыми полосами, вороненая гарда с латунным барабаном на 6 патронов и спуском,
на обухе три выхлопные трубки (тлеют). В фазе 2 за спиной — 5 золотых колец силы (кости ring0..4).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mobgen import (FACE_LIGHT, Model, Painter, add, bake, cross, dot, faces, length, lerp,  # noqa: E402
                    mix, mm, mul, mv, norm, rx_m, scl, sub, tr, write_anims)
import nabat_gen as ng  # noqa: E402  (IK, ключи поз, дорожки оружия, предпросмотр)
import asset_groups  # noqa: E402

ROOT = ng.ROOT
JAVA_OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "java", "com", "lesha_top1972",
                        "basedefense", "entity", "enemy", "thunder_tiger", "TigerTimings.java")
MODEL_SCALE = 1.1        # в игре (ThunderTigerRenderer.SCALE)

# =====================================================================
# 1. Модели — из модели пользователя (tools/ref/thunder_tiger_src.geo.json + .png)
# =====================================================================
# Модель и текстура — от пользователя (Blockbench). Скрипт только перестраивает кости под анимации:
# руки и ноги делятся на два звена (локоть, колено) с ТЕМИ ЖЕ UV (сдвиг v на высоту верхнего звена — боковые
# грани совпадают с исходными), куртка на плечах — на 5 костей (спина, две полы, два пустых рукава),
# тесак уходит в отдельную модель (хват в начале координат), ножны‑копия убрана. Текстура не меняется:
# добавляется только золотой квадратик для колец силы и маска свечения (уголёк сигары, кольца).
SRC_GEO = os.path.join(os.path.dirname(__file__), "ref", "thunder_tiger_src.geo.json")
SRC_TEX = os.path.join(os.path.dirname(__file__), "ref", "thunder_tiger_src.png")
UPPER, FORE = 5.0, 5.0          # плечо (28 → 23) и предплечье (23 → 18, запястье)
THIGH, SHIN = 8.0, 8.0          # бедро (16 → 8) и голень (8 → 0)
SH_X = 6.0
SH_Y = 28.0
TORSO_Y = 16.0
RINGS = 5
RING_UV = (100, 0)
CIGAR_TIP = (29, 5)             # передняя грань сигареты на текстуре (уголёк)


def src_bones():
    with open(SRC_GEO, encoding="utf-8") as f:
        geo = json.load(f)["minecraft:geometry"][0]
    return {b["name"]: b for b in geo["bones"]}


def put(M, bone, c, dy_uv=0, origin=None, size=None, **kw):
    """Куб исходной модели (с её UV) в кость нашей модели; origin/size — для половинок рук и ног."""
    o = list(origin or c["origin"])
    s = list(size or c["size"])
    extra = {"uv": [c["uv"][0], c["uv"][1] + dy_uv]}
    if c.get("mirror"):
        extra["mirror"] = True
    M.cube(bone, o, s, "src", c.get("inflate", 0.0), rot=c.get("rotation"), pivot=c.get("pivot"), **extra)


def split(M, src_cubes, upper_bone, lower_bone, y_cut, y_top):
    """Конечность из одного куба → верхнее звено (y_cut..y_top) и нижнее (низ..y_cut); UV нижнего — ниже на высоту верхнего."""
    for c in src_cubes:
        ox, oy, oz = c["origin"]
        sx, sy, sz = c["size"]
        hu = oy + sy - y_cut
        put(M, upper_bone, c, 0, (ox, y_cut, oz), (sx, hu, sz))
        put(M, lower_bone, c, int(round(hu)), (ox, oy, oz), (sx, y_cut - oy, sz))


def build_tiger():
    S = src_bones()
    M = Model("thunder_tiger", 128, 128)
    b = M.bone
    b("root", None, (0, 0, 0))
    b("body", "root", (0, TORSO_Y, 0))
    for side, sx, src in (("right", -1, "rightLeg"), ("left", 1, "leftLeg")):
        b(side + "_leg", "body", (sx * 2, 16, 0))
        b(side + "_shin", side + "_leg", (sx * 2, 8, 0))
        b(side + "_foot", side + "_shin", (sx * 2, 0, 0))
        split(M, S[src]["cubes"], side + "_leg", side + "_shin", 8, 16)
    b("torso", "body", (0, TORSO_Y, 0))
    for c in S["body"]["cubes"]:
        put(M, "torso", c)
    b("head", "torso", (0, 30, 0))
    for c in S["head"]["cubes"]:
        put(M, "head", c)
    b("right_brow", "head", tuple(S["rightEyebrow"]["pivot"]))
    put(M, "right_brow", S["rightEyebrow"]["cubes"][0])
    b("left_brow", "head", tuple(S["leftEyebrow"]["pivot"]))
    put(M, "left_brow", S["leftEyebrow"]["cubes"][0])
    b("cigar", "head", tuple(S["cigarette"]["pivot"]))
    put(M, "cigar", S["cigarette"]["cubes"][0])
    for side, sx, src in (("right", -1, "rightArm"), ("left", 1, "leftArm")):
        b(side + "_arm", "torso", (sx * SH_X, SH_Y, 0))
        b(side + "_fore", side + "_arm", (sx * SH_X, 23, 0))
        b(side + "_hand", side + "_fore", (sx * SH_X, 18, 0))
        split(M, S[src]["cubes"], side + "_arm", side + "_fore", 23, 30)
    # куртка на плечах: спина, полы, пустые рукава
    jc = S["jacket"]["cubes"]
    b("coat_b", "torso", (0, 30, 2))
    put(M, "coat_b", jc[0])
    b("sleeve_r", "torso", (-6, 28, 4))
    put(M, "sleeve_r", jc[1])
    put(M, "sleeve_r", jc[2])
    b("sleeve_l", "torso", (6, 28, 4))
    put(M, "sleeve_l", jc[3])
    put(M, "sleeve_l", jc[4])
    b("coat_r", "torso", (-4, 30, 1))
    put(M, "coat_r", jc[5])
    b("coat_l", "torso", (4, 30, 1))
    put(M, "coat_l", jc[6])
    # кольца силы (фаза 2): за спиной веером, каждое — восьмиугольник из 8 брусков (золото, светится)
    for k in range(RINGS):
        nm = "ring%d" % k
        ang = math.radians(-64 + k * 32)
        cx, cy, cz = 11.0 * math.sin(ang), 31.0 + 3.0 * math.cos(ang * 1.6), 7.0 + 3.0 * math.cos(ang)
        b(nm, "torso", (cx, cy, cz))
        rr = 3.2
        for j in range(8):
            th = j * 45.0
            sx_, sy_ = rr * math.sin(math.radians(th)), rr * math.cos(math.radians(th))
            M.cube(nm, (cx + sx_ - 1.33, cy + sy_ - 0.3, cz - 0.3), (2.66, 0.6, 0.6), "ring",
                   rot=(0, 0, -th), pivot=(cx + sx_, cy + sy_, cz), uv=list(RING_UV))
        M.cube(nm, (cx - 0.6, cy - 0.6, cz - 0.3), (1.2, 1.2, 0.6), "ring", rot=(0, 0, 45), pivot=(cx, cy, cz),
               uv=list(RING_UV))
    return M


# Тесак: в исходной модели он в кости hilt (опора −6, 18, 0; клинок вдоль +y). Переносим так, чтобы хват
# (опора) был в начале координат. Барабан‑патронник (cube 3×4×2 у гарды) — своя кость «chamber».
BLADE_SHIFT = (6.0, -18.0, 0.0)


def build_blade():
    S = src_bones()
    M = Model("thunder_tiger_blade", 128, 128)
    b = M.bone
    dx, dy, dz = BLADE_SHIFT

    def mv_c(c):
        c2 = dict(c)
        c2["origin"] = [c["origin"][0] + dx, c["origin"][1] + dy, c["origin"][2] + dz]
        if "pivot" in c:
            c2["pivot"] = [c["pivot"][0] + dx, c["pivot"][1] + dy, c["pivot"][2] + dz]
        return c2

    b("root", None, (0, 0, 0))
    b("blade_root", "root", (0, 0, 0))
    hc = S["hilt"]["cubes"]
    for i, c in enumerate(hc):
        if i == 2:
            continue
        put(M, "blade_root", mv_c(c))
    b("chamber", "blade_root", (0, 4, -0.5))
    put(M, "chamber", mv_c(hc[2]))
    ch = S["chain"]
    b("chain", "blade_root", (ch["pivot"][0] + dx, ch["pivot"][1] + dy, ch["pivot"][2] + dz))
    for c in ch["cubes"]:
        put(M, "chain", mv_c(c))
    bl = S["blade"]
    b("blade", "blade_root", (bl["pivot"][0] + dx, bl["pivot"][1] + dy, bl["pivot"][2] + dz))
    for c in bl["cubes"]:
        put(M, "blade", mv_c(c))
    return M


def write_geo(M):
    out_bones = []
    for b_ in M.bones:
        ob = {"name": b_["name"], "pivot": b_["pivot"]}
        if "parent" in b_:
            ob["parent"] = b_["parent"]
        cubes = []
        for c in b_["cubes"]:
            oc = {"origin": c["origin"], "size": c["size"], "uv": c["uv"]}
            for k in ("inflate", "pivot", "rotation", "mirror"):
                if k in c:
                    oc[k] = c[k]
            cubes.append(oc)
        if cubes:
            ob["cubes"] = cubes
        out_bones.append(ob)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + M.name, "texture_width": M.tex_w, "texture_height": M.tex_h,
                        "visible_bounds_width": 5, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out_bones}]}
    with open(asset_groups.geo(M.name + ".geo.json", ROOT), "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=1)


# =====================================================================
# 2. Текстуры: исходная + золото колец; маска свечения
# =====================================================================
GOLD = (255, 204, 92, 255)
GOLD_HI = (255, 244, 196, 255)


def save_textures():
    from PIL import Image
    src = Image.open(SRC_TEX).convert("RGBA")
    tex = src.copy()
    glow = Image.new("RGBA", src.size, (0, 0, 0, 0))
    u, v = RING_UV
    for y in range(v, v + 4):
        for x in range(u, u + 8):
            c = GOLD_HI if y == v + 1 else GOLD
            tex.putpixel((x, y), c)
            glow.putpixel((x, y), c)
    glow.putpixel(CIGAR_TIP, (255, 140, 40, 255))
    tex.save(asset_groups.tex("thunder_tiger.png", ROOT))
    glow.save(asset_groups.tex("thunder_tiger_glowmask.png", ROOT))
    src.save(asset_groups.tex("thunder_tiger_blade.png", ROOT))
    Image.new("RGBA", src.size, (0, 0, 0, 0)).save(asset_groups.tex("thunder_tiger_blade_glowmask.png", ROOT))


def build_all():
    out = {}
    for fn in (build_tiger, build_blade):
        M_ = fn()
        write_geo(M_)
        out[M_.name] = M_
    save_textures()
    return out


# =====================================================================
# 3. Позы: параметры, IK, тесак, куртка на плечах
# =====================================================================
# Позы задаются в «расчётном» пространстве (как у прежнего наброска: плечи (∓7.2, −8.5, 0) от корпуса,
# лодыжки y = 22.5, тело ×1), а resolve() переводит их в размеры модели пользователя (плечи (∓6, −12, 0),
# руки 5+5, ноги 8+8 до земли y = 24). Так все удары и шаги описаны в одной системе.
# Кисти (rh/lh) — запястье в системе корпуса; ступни (rf/lf) — в системе модели; wdir — куда смотрит клинок
# от хвата к острию (система корпуса), wface — куда смотрит лезвие; two — хват двумя руками (0..1).
# Java: +y вниз, −z вперёд, +x — левая сторона.
SHOULDER = {"rh": (-7.2, -8.5, 0.0), "lh": (7.2, -8.5, 0.0)}
LH_REST = (7.9, 3.2, 0.2)
DEF = dict(root=(0, 0, 0), root_pos=(0, 0, 0), body=(0, 0, 0), body_pos=(0, 0.6, 0), torso=(4, 0, 0),
           head=(-4, 0, 0), rf=(-2.6, 22.5, 0.4), lf=(2.6, 22.5, -0.4), toe_r=0.0, toe_l=0.0,
           rh=(-8.0, 2.0, -2.5), lh=LH_REST, r_pole=(-0.6, 0.3, 1.0), l_pole=(0.6, 0.3, 1.0),
           wdir=(-0.3, 0.45, -0.84), wface=(0.0, 1.0, 0.3), two=0.0, lhand=(0, 0, 0), coat=0.0, swing=1.0,
           brow=0.0)
ng.SHOULDER = SHOULDER
ng.DIRS = ("r_pole", "l_pole", "wdir", "wface")
LAG = {"head": -0.02, "lh": 0.03, "l_pole": 0.03, "r_pole": 0.02, "coat": 0.1}
GRIP_G = ng.GRIP_G
PALM = (0.0, 1.0, 0.0)
GRIP_OFFSET = mv(tr(GRIP_G), list(PALM))     # центр кулака в системе тесака
HILT2 = 3.6                                  # левая кисть ниже правой по рукояти (px)
EDGE_SIGN = 1.0                              # куда у модели тесака смотрит лезвие: +1 — в −z


def hand_xyz(p):
    return (p[0] * 0.833, p[1] * 0.833 - 4.92, p[2] * 0.833)


def foot_xyz(p):
    return (p[0] * 0.8, 24.0 - (22.5 - p[1]) * 1.185, p[2] * 1.05)


def P(base=None, **kw):
    d = dict(DEF)
    if base:
        d.update(base)
    d.update(kw)
    return d


def weapon_R(model, pose, pr):
    Rt = model.chain_R("torso", pose)
    up = norm(list(pr["wdir"]))
    fc = list(pr["wface"])
    fc = sub(fc, scl(up, dot(fc, up)))
    if length(fc) < 0.15:
        fc = cross([1.0, 0.0, 0.0], up)
        fc = sub(fc, scl(up, dot(fc, up)))
    fc = norm(fc)
    y = scl(up, -1)
    z = scl(fc, -EDGE_SIGN)
    x = cross(y, z)
    Rl = [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]
    return mm(Rt, Rl)


HANGERS = ("sleeve_r", "sleeve_l")
# Скручивание корпуса и головы смягчено: у модели широкие плечи и голова с волосами по бокам —
# при сильном повороте голова «двоится» (видна чёрная боковина рядом с лицом).
TWIST = 0.65
HEAD_TWIST = 0.35


def resolve(pr, model, hang=None):
    bp = pr["body_pos"]
    rp = pr["root_pos"]
    pose = {"root": {"rot": pr["root"], "pos": (rp[0] * 1.1, rp[1] * 1.15, rp[2] * 1.1)},
            "body": {"rot": pr["body"], "pos": (bp[0] * 1.1, bp[1] * 1.18, bp[2] * 1.1)},
            "torso": {"rot": (pr["torso"][0], pr["torso"][1] * TWIST, pr["torso"][2])},
            "head": {"rot": (pr["head"][0], pr["head"][1] * HEAD_TWIST, pr["head"][2] * 0.7)}}
    Rroot = model.chain_R("root", pose)
    for s_, key in (("right", "rf"), ("left", "lf")):
        fx = foot_xyz(pr[key])
        tgt = model.world("root", [fx[0], fx[1] - 24, fx[2]], pose)
        ng.limb_ik(model, pose, s_ + "_leg", s_ + "_shin", tgt, mv(Rroot, [0, -0.3, -1]), THIGH, SHIN, -1)
        model.set_world_rot(pose, s_ + "_foot", Rroot)
    Rt = model.chain_R("torso", pose)
    tgt_r = model.world("torso", list(hand_xyz(pr["rh"])), pose)
    ng.limb_ik(model, pose, "right_arm", "right_fore", tgt_r, mv(Rt, list(pr["r_pole"])), UPPER, FORE, 1,
               mv(Rt, [-1.0, 0.3, 0.4]))
    Rw = weapon_R(model, pose, pr)
    model.set_world_rot(pose, "right_hand", mm(Rw, tr(GRIP_G)))
    tgt_l = model.world("torso", list(hand_xyz(pr["lh"])), pose)
    two = max(0.0, min(1.0, pr["two"]))
    if two > 0.001:
        wrist_r = model.world("right_hand", [0, 0, 0], pose)
        hilt = add(wrist_r, mv(Rw, [0.0, HILT2, 0.0]))
        tgt_l = add(scl(tgt_l, 1 - two), scl(hilt, two))
    ng.limb_ik(model, pose, "left_arm", "left_fore", tgt_l, mv(Rt, list(pr["l_pole"])), UPPER, FORE, 1,
               mv(Rt, [1.0, 0.3, 0.4]))
    if two > 0.5:
        model.set_world_rot(pose, "left_hand", mm(Rw, tr(GRIP_G)))
    else:
        pose["left_hand"] = {"rot": pr["lhand"]}
    # куртка на плечах: висит почти отвесно (гасит наклон корпуса), полы раздвигают ноги, на бегу — назад
    rl = pose["right_leg"]["rot"][0]
    ll = pose["left_leg"]["rot"][0]
    tilt = pr["root"][0] + pr["body"][0] + pr["torso"][0]
    fl = pr["coat"]
    pose["coat_b"] = {"rot": (-tilt * 0.75 + max(rl, ll, 0) * 0.3 + fl, 0, 0)}
    pose["coat_r"] = {"rot": (-tilt * 0.7 + min(rl, 0) * 0.45 + fl * 0.6, 0, -abs(rl) * 0.06 - fl * 0.1)}
    pose["coat_l"] = {"rot": (-tilt * 0.7 + min(ll, 0) * 0.45 + fl * 0.6, 0, abs(ll) * 0.06 + fl * 0.1)}
    # брови: хмурится в атаке (brow > 0)
    br = pr.get("brow", 0.0)
    pose["right_brow"] = {"rot": (0, 0, -14 * br)}
    pose["left_brow"] = {"rot": (0, 0, 14 * br)}
    hang = hang or {}
    for name in HANGERS:
        d = hang.get(name)
        if d is not None:
            model.set_world_rot(pose, name, ng.frame_X(d, mv(model.chain_R("torso", pose), [1.0, 0, 0])))
    return pose


SLEEVE_K = (0.010, 0.006)
SLEEVE_LIM = 40


def pose_at(fn, t, model, length_s, loop):
    def tt(x):
        return x % length_s if loop else max(0.0, x)

    pr = fn(tt(t))
    ng.AUX[0] = True
    now = resolve(pr, model)
    p_a = resolve(fn(tt(t - 0.10)), model)
    p_b = resolve(fn(tt(t - 0.40)), model)
    p_c = resolve(fn(tt(t - 0.18)), model)
    ng.AUX[0] = False
    hang = {}
    for name in HANGERS:
        loc = model.rel_pivot(name)
        x0 = model.world("torso", loc, now)
        v1 = ng.sat(scl(sub(x0, model.world("torso", loc, p_c)), 1 / 0.18), 60.0)
        v2 = ng.sat(scl(sub(model.world("torso", loc, p_a), model.world("torso", loc, p_b)), 1 / 0.30), 60.0)
        rest = norm(add(mv(model.chain_R("torso", now), [0, 1.0, 0]), [0, 1.2, 0]))
        k1, k2 = SLEEVE_K[0] * pr["swing"], SLEEVE_K[1] * pr["swing"]
        d = norm(add(rest, add(scl(v1, -k1), scl(sub(v2, v1), k2))))
        lim = math.radians(SLEEVE_LIM)
        c_ = dot(d, rest)
        if c_ < math.cos(lim):
            perp = norm(sub(d, scl(rest, c_)))
            d = add(scl(rest, math.cos(lim)), scl(perp, math.sin(lim)))
        hang[name] = d
    return pr, resolve(pr, model, hang)


# --- ключи: тело и тесак отдельными дорожками ---
WEAPON_KEYS = ("rh", "wdir", "wface", "r_pole", "two")


def track(keys, t):
    return ng.keyed(keys, t, DEF)


def lagged(keys, t):
    pr = track(keys, t)
    for name, lag in LAG.items():
        pr[name] = track(keys, max(0.0, t - lag))[name]
    return pr


def clip(body, weapon=None):
    """Поза = дорожка тела (+ запаздывание частей); дорожка тесака (rh, wdir, wface, r_pole, two) поверх."""
    def fn(t):
        pr = lagged(body, t)
        if weapon:
            w = track(weapon, t)
            for k in WEAPON_KEYS:
                if any(k in d for _, d, _ in weapon):
                    pr[k] = w[k]
        return pr
    return fn


def hdir(phi, elev=0.0):
    """Направление клинка в горизонтальной плоскости корпуса: phi 0 — вперёд, +90 — влево, −90 — вправо."""
    p, e = math.radians(phi), math.radians(elev)
    return (math.sin(p) * math.cos(e), -math.sin(e), -math.cos(p) * math.cos(e))


def vdir(theta, side=-0.12):
    """Вертикальный взмах: theta 0 — вверх, 90 — вперёд, 160 — вниз‑вперёд."""
    th = math.radians(theta)
    return norm((side, -math.cos(th), -math.sin(th)))


def sweep(t0, t1, dirs, center=(-2.0, -4.0, -1.0), radius=6.5, two=None, steps=10, e="in", hand=None):
    """Ключи взмаха: клинок по дуге через направления dirs (сферически), кисть — на сфере вокруг center,
    лезвие смотрит по ходу движения. e — сглаживание по времени взмаха."""
    pts = [norm(list(d)) for d in dirs]
    seg = len(pts) - 1
    keys = []
    ease_fn = ng.EASES[e]

    def at(u):
        x = min(seg - 1e-9, u * seg)
        k = int(x)
        return norm(ng.slerp_dir(pts[k], pts[k + 1], x - k))

    for s in range(steps + 1):
        u = s / steps
        tt = t0 + (t1 - t0) * u
        uu = ease_fn(u)
        d = at(uu)
        d2 = at(min(1.0, uu + 0.02)) if uu < 0.98 else at(1.0)
        d1 = at(max(0.0, uu - 0.02))
        tan = sub(d2, d1)
        if length(tan) < 1e-6:
            tan = sub(at(1.0), at(0.0))
        kd = {"wdir": tuple(d), "wface": tuple(norm(tan))}
        c = hand(u) if hand else center
        kd["rh"] = tuple(add(list(c), scl(d, radius)))
        if two is not None:
            kd["two"] = two
        keys.append((tt, kd, "lin"))
    return keys


def hold(t, base, **kw):
    d = dict(base)
    d.update(kw)
    return (t, d, "soft")


def gait(w, stride, lift, base_y=22.5, width=2.6, z0=0.0):
    r = (-width, base_y - lift * max(0.0, math.sin(w)), z0 - stride * math.cos(w))
    l_ = (width, base_y - lift * max(0.0, -math.sin(w)), z0 + stride * math.cos(w))
    return r, l_


def side_gait(w, stride, lift, sign, base_y=22.5, width=2.8):
    """Приставной шаг вбок: sign +1 — влево (+x), −1 — вправо."""
    r = (-width + sign * stride * math.cos(w), base_y - lift * max(0.0, math.sin(w)), 0.6)
    l_ = (width + sign * stride * math.cos(w + math.pi), base_y - lift * max(0.0, -math.sin(w)), -0.6)
    return r, l_


# =====================================================================
# 4. Анимации
# =====================================================================
# Корпус: rot y > 0 — поворот вправо (правое плечо назад). Голова — ребёнок корпуса, компенсирует поворот.
LAZY = dict(body=(0, 0, -3), body_pos=(0.4, 0.8, 0), torso=(2, -6, 3), head=(-2, 10, -2),
            rf=(-2.5, 22.5, 0.6), lf=(3.3, 22.5, -1.7), toe_l=-4.0)
STANCE = dict(body=(0, 8, 0), body_pos=(0, 1.8, 0), torso=(8, 14, 0), head=(-8, -18, 0),
              rf=(-3.7, 22.5, 2.2), lf=(3.0, 22.5, -3.0), toe_l=0.0, toe_r=0.0, root=(0, 0, 0), root_pos=(0, 0, 0))
CARRY = dict(rh=(-5.4, -5.4, -4.4), wdir=(0.12, -0.72, 0.68), wface=(0.0, -0.7, -0.72), r_pole=(-0.9, 0.6, 0.1),
             two=0.0)
LOW = dict(rh=(-8.2, 1.4, -2.2), wdir=(-0.35, 0.5, -0.8), wface=(0.0, 1.0, 0.45), r_pole=(-0.6, 0.3, 1.0),
           two=0.0)
GUARD2 = dict(rh=(-2.4, -1.4, -7.2), two=1.0, wdir=(0.22, -0.62, -0.75), wface=(0.0, 0.78, -0.62),
              r_pole=(-0.7, 0.5, 0.6))
LH_GUARD = (5.8, -2.6, -6.2)
LH_REST_L = (7.6, 2.8, -0.6)
MOUTH_L = (-0.4, -10.6, -7.0)          # левая кисть у рта (сигара)
FREE_L = dict(lh=LH_GUARD, l_pole=(0.7, 0.4, 0.8))
C1 = (-2.0, -4.0, -1.0)                # центр взмаха одной рукой
C2 = (-0.6, -4.2, -2.2)                # центр взмаха двумя руками


def first(keys, t, e="soft"):
    """Ключ в момент t — копия начала взмаха (чтобы замах пришёл ровно в стартовую позу удара)."""
    return (t, dict(keys[0][1]), e)


def last(keys, t, e="soft"):
    return (t, dict(keys[-1][1]), e)


def W(*parts):
    out = []
    for p in parts:
        if isinstance(p, list):
            out.extend(p)
        else:
            out.append(p)
    out.sort(key=lambda k: k[0])
    return out


# ---------------------------------------------------------------- циклы
def idle_smoke(t):
    """Вальяжно стоит, тесак на плече, курит: затяжка (0.9–1.6), выдох с запрокинутой головой, стряхивает пепел."""
    T_ = 3.6
    w = 2 * math.pi * t / T_
    pr = P(LAZY, **CARRY)
    pr["body_pos"] = (0.4, 0.8 + 0.25 * math.sin(w * 2), 0)
    pr["torso"] = (2 + 1.2 * math.sin(w * 2 + 0.5), -6 + 2 * math.sin(w), 3)
    sm = ng.smooth01(0.6, 1.0, t) * (1 - ng.smooth01(1.7, 2.1, t))
    ash = ng.smooth01(2.1, 2.35, t) * (1 - ng.smooth01(2.9, 3.4, t))
    exh = ng.smooth01(1.6, 1.9, t) * (1 - ng.smooth01(2.3, 2.8, t))
    rest = (LH_REST_L[0], LH_REST_L[1] + 0.3 * math.sin(w * 2), LH_REST_L[2])
    pos = ng.lerp_val(rest, MOUTH_L, sm)
    pos = ng.lerp_val(pos, (6.0, -1.2, -5.4), ash)
    pr["lh"] = pos
    pr["l_pole"] = (0.6, 0.6 + 0.3 * sm, 0.5 - 0.3 * sm)
    flick = math.sin(max(0.0, min(1.0, (t - 2.45) / 0.25)) * math.pi)
    pr["lhand"] = (0, 0, -30 * flick * ash)
    pr["head"] = (-2 - 12 * exh + 4 * sm, 10 - 6 * sm, -2 + 4 * exh)
    pr["rh"] = (-5.4, -5.4 + 0.2 * math.sin(w * 2 + 0.6), -4.4)
    pr["swing"] = 0.6
    return pr


def guard(t):
    """Фаза 1, бой: низкая стойка, тесак в правой руке остриём вниз, левая ладонь вперёд."""
    T_ = 2.4
    w = 2 * math.pi * t / T_
    pr = P(STANCE, **LOW, **FREE_L)
    pr["body_pos"] = (0, 1.8 + 0.3 * math.sin(w * 2), 0)
    pr["torso"] = (8 + 1.5 * math.sin(w * 2 + 0.4), 14 + 2 * math.sin(w), 0)
    pr["head"] = (-8, -18 - 2 * math.sin(w), 0)
    pr["rh"] = (-8.2, 1.4 + 0.3 * math.sin(w * 2 + 0.8), -2.2)
    pr["wdir"] = (-0.35 + 0.06 * math.sin(w), 0.5, -0.8)
    pr["lh"] = (5.8, -2.6 + 0.4 * math.sin(w * 2 + 1.0), -6.2)
    return pr


def idle(t):
    """Фаза 2: тесак двумя руками, остриём вперёд‑вверх, тяжёлое дыхание."""
    T_ = 2.0
    w = 2 * math.pi * t / T_
    pr = P(STANCE, **GUARD2)
    pr["body_pos"] = (0, 2.2 + 0.4 * math.sin(w * 2), 0)
    pr["torso"] = (10 + 2 * math.sin(w * 2 + 0.4), 14, 0)
    pr["head"] = (-10 - 1.5 * math.sin(w * 2), -18, 0)
    pr["rh"] = (-2.4, -1.4 + 0.4 * math.sin(w * 2 + 0.7), -7.2)
    return pr


def walk_lazy(t):
    """Вразвалку: тесак на плече, плечи раскачиваются, левая рука болтается."""
    T_ = 1.25
    w = 2 * math.pi * t / T_
    pr = P(LAZY, **CARRY)
    pr["rf"], pr["lf"] = gait(w, 3.4, 1.6, width=2.9)
    pr["body_pos"] = (0, 0.7 + 0.6 * abs(math.sin(w)), 0)
    pr["body"] = (2, 6 * math.sin(w), -4 * math.sin(w))
    pr["torso"] = (3, -8 * math.sin(w), 5 * math.sin(w))
    pr["head"] = (-3, 2 * math.sin(w), -3 * math.sin(w))
    pr["lh"] = (7.8, 2.6, 0.6 - 3.6 * math.sin(w))
    pr["l_pole"] = (0.6, 0.3, 1.0)
    pr["toe_r"] = -12 * max(0.0, math.sin(w))
    pr["toe_l"] = -12 * max(0.0, -math.sin(w))
    pr["rh"] = (-5.4, -5.4 + 0.4 * abs(math.sin(w)), -4.4)
    return pr


def walk(t):
    T_ = 1.0
    w = 2 * math.pi * t / T_
    pr = P(STANCE, **LOW, **FREE_L)
    pr["rf"], pr["lf"] = gait(w, 3.0, 1.6, width=3.0)
    pr["body_pos"] = (0, 1.4 + 0.5 * abs(math.sin(w)), 0)
    pr["body"] = (4, 6 * math.sin(w), 0)
    pr["torso"] = (10, 10 - 6 * math.sin(w), 0)
    pr["head"] = (-10, -12 + 4 * math.sin(w), 0)
    pr["lh"] = (5.8, -2.4, -5.4 + 1.4 * math.sin(w))
    pr["rh"] = (-8.2, 1.2 + 0.5 * abs(math.sin(w)), -2.2 - 1.0 * math.sin(w))
    pr["toe_r"] = -14 * max(0.0, math.sin(w))
    pr["toe_l"] = -14 * max(0.0, -math.sin(w))
    return pr


def run(t):
    T_ = 0.62
    w = 2 * math.pi * t / T_
    pr = P(STANCE, rh=(-7.4, 1.0, 3.0), wdir=(-0.25, 0.42, 0.87), wface=(0.0, 1.0, -0.4), r_pole=(-0.6, -0.2, 1.0))
    pr["rf"], pr["lf"] = gait(w, 5.2, 3.2, width=2.8)
    pr["body_pos"] = (0, 1.2 + 1.2 * abs(math.sin(w)), 0)
    pr["body"] = (16, 8 * math.sin(w), 0)
    pr["torso"] = (16, -10 * math.sin(w), 0)
    pr["head"] = (-28, 4 * math.sin(w), 0)
    pr["rh"] = (-7.4, 1.0 + 0.8 * math.sin(w), 2.4 - 2.0 * math.sin(w))
    pr["lh"] = (6.6, -1.0 - 1.0 * math.sin(w), -2.0 + 5.0 * math.sin(w))
    pr["l_pole"] = (0.5, 0.6, 0.8)
    pr["toe_r"] = -24 * max(0.0, math.sin(w))
    pr["toe_l"] = -24 * max(0.0, -math.sin(w))
    pr["coat"] = 26 + 6 * math.sin(2 * w)
    return pr


def strafe(sign):
    def fn(t):
        T_ = 0.9
        w = 2 * math.pi * t / T_
        pr = P(STANCE, **LOW, **FREE_L)
        pr["rf"], pr["lf"] = side_gait(w, 2.6, 1.6, sign)
        pr["body_pos"] = (0, 1.8 + 0.6 * abs(math.sin(w)), 0)
        pr["body"] = (4, 4, -sign * 4 + 3 * math.sin(w))
        pr["torso"] = (8, 12, sign * 3)
        pr["head"] = (-8, -16, -sign * 3)
        pr["rh"] = (-8.2, 1.4 + 0.4 * abs(math.sin(w)), -2.2)
        pr["lh"] = (5.8, -2.6 + 0.6 * math.sin(w), -6.2)
        pr["coat"] = 6
        return pr
    return fn


def back(t):
    T_ = 1.0
    w = 2 * math.pi * t / T_
    pr = P(STANCE, **LOW, **FREE_L)
    r_, l_ = gait(w, 2.6, 1.4, width=3.0)
    pr["rf"], pr["lf"] = (r_[0], r_[1], 1.8 - (r_[2])), (l_[0], l_[1], -1.8 - (l_[2]))
    pr["body_pos"] = (0, 1.6 + 0.5 * abs(math.sin(w)), 0)
    pr["body"] = (0, 6 * math.sin(w), 0)
    pr["torso"] = (6, 12, 0)
    pr["head"] = (-6, -16, 0)
    return pr


# ---------------------------------------------------------------- атаки
# «Двойной разрез»: справа налево, обратным — слева направо
DBL_S1 = sweep(0.27, 0.41, [hdir(-140, 14), hdir(-70, 6), hdir(10, 0), hdir(96, -8)], C1)
DBL_S2 = sweep(0.55, 0.69, [hdir(108, -8), hdir(30, 0), hdir(-50, 10), hdir(-128, 24)], C1)
DOUBLE_HITS = (0.36, 0.64)
DOUBLE_W = W((0.0, LOW, "lin"), hold(0.1, LOW), first(DBL_S1, 0.24, "soft"), DBL_S1, last(DBL_S1, 0.48, "out3"),
             DBL_S2, last(DBL_S2, 0.8, "out3"), hold(1.1, LOW))
DOUBLE_B = [
    (0.00, STANCE, "lin"),
    (0.24, dict(torso=(10, 40, 4), head=(-10, -36, 0), body=(0, 12, 0), body_pos=(0, 2.4, 0),
                rf=(-3.9, 22.5, 2.8), lf=(3.0, 22.5, -2.4), lh=(5.0, -4.0, -5.0)), "soft"),
    (0.36, dict(torso=(14, -30, -4), head=(-12, 26, 0), body=(4, -12, 0), body_pos=(0, 2.9, 0),
                root_pos=(0, 0, -2.0), lf=(3.2, 22.5, -4.8), lh=(8.6, -2.4, 2.4)), "in"),
    (0.47, dict(torso=(12, -38, -4), head=(-12, 32, 0)), "out3"),
    (0.55, dict(torso=(12, -24, 0), head=(-12, 20, 0), lh=(7.0, -4.0, -2.0)), "soft"),
    (0.64, dict(torso=(10, 32, 4), head=(-10, -28, 0), body=(2, 14, 0), lh=(6.0, -4.6, -4.4)), "in"),
    (0.80, dict(torso=(8, 38, 4), head=(-8, -32, 0)), "out3"),
    (1.10, dict(STANCE, **FREE_L), "soft"),
]

# «Тройной разрез»: справа налево, слева направо, сверху двумя руками (разрыв дрожи)
TRI_S1 = sweep(0.24, 0.37, [hdir(-140, 12), hdir(-60, 4), hdir(20, 0), hdir(100, -8)], C1)
TRI_S2 = sweep(0.49, 0.62, [hdir(108, -6), hdir(20, 2), hdir(-60, 12), hdir(-130, 26)], C1)
TRI_S3 = sweep(0.88, 1.01, [vdir(-28), vdir(30), vdir(95), vdir(172)], C2, radius=6.0, two=1.0, e="in3")
TRIPLE_HITS = (0.33, 0.58, 0.98)
TRIPLE_W = W((0.0, LOW, "lin"), first(TRI_S1, 0.2, "soft"), TRI_S1, last(TRI_S1, 0.43, "out3"), TRI_S2,
             (0.70, dict(TRI_S3[0][1], two=0.0), "soft"), first(TRI_S3, 0.8, "soft"), TRI_S3,
             last(TRI_S3, 1.18, "lin"), hold(1.5, LOW))
TRIPLE_B = [
    (0.00, STANCE, "lin"),
    (0.20, dict(torso=(10, 38, 4), head=(-10, -34, 0), body=(0, 12, 0), body_pos=(0, 2.4, 0), lh=(5.0, -4.0, -5.0)), "soft"),
    (0.33, dict(torso=(14, -30, -4), head=(-12, 26, 0), body=(4, -12, 0), body_pos=(0, 2.8, 0),
                root_pos=(0, 0, -2.0), lf=(3.2, 22.5, -4.8), lh=(8.6, -2.4, 2.4)), "in"),
    (0.45, dict(torso=(12, -26, 0), head=(-12, 22, 0), lh=(7.0, -4.0, -2.0)), "soft"),
    (0.58, dict(torso=(10, 32, 4), head=(-10, -28, 0), body=(2, 14, 0), lh=(6.0, -4.6, -4.4)), "in"),
    (0.70, dict(torso=(4, 30, 0), head=(-4, -26, 0)), "out3"),
    (0.86, dict(torso=(-14, 10, 0), head=(6, -10, 0), body=(-4, 6, 0), body_pos=(0, 0.6, 0),
                rf=(-3.4, 22.5, 3.2), lf=(3.0, 22.5, -2.6), root_pos=(0, -1.0, -1.0)), "soft"),
    (0.98, dict(torso=(40, 4, 0), head=(-30, -4, 0), body=(10, 4, 0), body_pos=(0, 5.0, 0),
                root_pos=(0, 0, -3.5), lf=(3.0, 22.5, -6.4), rf=(-3.6, 22.5, 3.4)), "in3"),
    (1.18, dict(torso=(36, 4, 0), head=(-26, -4, 0), body_pos=(0, 4.6, 0)), "soft"),
    (1.50, dict(STANCE, **FREE_L), "soft"),
]

# «Двойной разрез — взрыв»: удар в землю с выстрелом → рывок → восходящий разрез с выстрелом
DB_SMASH = sweep(0.32, 0.43, [vdir(-22), vdir(60), vdir(130), vdir(172)], C2, radius=6.0, two=1.0, e="in3")
DB_RISE = sweep(0.80, 0.92, [hdir(120, -40), hdir(40, -10), hdir(-40, 22), hdir(-100, 46)], C1, e="in")
DBLAST_EV = dict(hits=(0.43, 0.88), smash=0.43, dash=(0.58, 0.8), shots=(0.43, 0.88))
DBLAST_W = W((0.0, LOW, "lin"), (0.16, dict(DB_SMASH[0][1], two=0.6), "soft"), first(DB_SMASH, 0.28, "soft"),
             DB_SMASH, last(DB_SMASH, 0.54, "lin"),
             (0.62, dict(rh=(-6.6, 2.0, 3.0), wdir=(-0.2, 0.45, 0.87), wface=(0.0, 1.0, -0.4), two=0.0), "soft"),
             first(DB_RISE, 0.76, "soft"), DB_RISE, last(DB_RISE, 1.0, "out3"), hold(1.4, LOW))
DBLAST_B = [
    (0.00, STANCE, "lin"),
    (0.28, dict(torso=(-14, 6, 0), head=(8, -6, 0), body=(-4, 4, 0), body_pos=(0, 0.6, 0),
                rf=(-3.4, 22.5, 3.0), lf=(3.0, 22.5, -2.6), lh=LH_GUARD), "soft"),
    (0.43, dict(torso=(42, 2, 0), head=(-32, -2, 0), body=(10, 2, 0), body_pos=(0, 5.0, 0),
                lf=(3.0, 22.5, -5.0)), "in3"),
    (0.56, dict(torso=(36, 2, 0), head=(-28, 0, 0)), "soft"),
    (0.64, dict(torso=(30, 10, 0), head=(-26, -8, 0), body=(18, 6, 0), body_pos=(0, 3.4, 0),
                rf=(-3.0, 21.5, 4.6), lf=(3.0, 22.5, -4.4), lh=(7.0, -2.0, -4.0), coat=24), "soft"),
    (0.78, dict(torso=(26, 30, 0), head=(-22, -26, 0), body=(14, 18, 0), coat=30), "lin"),
    (0.88, dict(torso=(-2, -34, -6), head=(0, 28, 0), body=(0, -14, 0), body_pos=(0, 1.4, 0),
                rf=(-3.6, 22.5, 2.4), lf=(3.4, 22.5, -3.6), lh=(8.6, -5.0, 1.0), coat=10), "in"),
    (1.00, dict(torso=(-4, -40, -6), head=(2, 32, 0)), "out3"),
    (1.40, dict(STANCE, **FREE_L, coat=0), "soft"),
]

# «Тройной разрез — взрыв»: рывок, три горящих разреза (выстрел на каждом), последний — в прыжке
TB_S1 = sweep(0.44, 0.55, [hdir(-125, 4), hdir(-40, 0), hdir(40, -4), hdir(100, -10)], C1)
TB_S2 = sweep(0.66, 0.78, [hdir(106, -8), hdir(30, 0), hdir(-50, 12), hdir(-126, 30)], C1)
TB_S3 = sweep(0.98, 1.10, [norm((-0.6, -0.7, 0.35)), norm((-0.25, -0.35, -0.9)), norm((0.35, 0.3, -0.88)),
                           norm((0.75, 0.6, -0.25))], C2, radius=6.2, two=1.0, e="in3")
TBLAST_EV = dict(hits=(0.50, 0.74, 1.07), dash=(0.18, 0.42), leap=0.88, shots=(0.50, 0.74, 1.07))
TBLAST_W = W((0.0, LOW, "lin"),
             (0.16, dict(rh=(-6.8, 2.0, 3.2), wdir=(-0.25, 0.42, 0.87), wface=(0.0, 1.0, -0.4)), "soft"),
             (0.40, dict(rh=(-6.8, 2.0, 3.2), wdir=(-0.25, 0.42, 0.87), wface=(0.0, 1.0, -0.4)), "lin"),
             TB_S1, TB_S2, (0.86, dict(TB_S3[0][1], two=0.5), "soft"), first(TB_S3, 0.94, "soft"), TB_S3,
             last(TB_S3, 1.25, "lin"), hold(1.6, LOW))
TBLAST_B = [
    (0.00, STANCE, "lin"),
    (0.14, dict(torso=(22, 10, 0), head=(-20, -8, 0), body=(10, 6, 0), body_pos=(0, 3.6, 0),
                rf=(-3.4, 22.5, 3.6), lf=(3.0, 22.5, -3.0), lh=(6.6, -1.0, -3.0)), "soft"),
    (0.20, dict(torso=(34, 6, 0), head=(-30, -4, 0), body=(22, 4, 0), body_pos=(0, 3.0, 0), coat=28,
                rf=(-3.0, 21.0, 5.4), lf=(3.0, 22.5, -3.2)), "out"),
    (0.40, dict(torso=(30, 24, 0), head=(-26, -20, 0), body=(18, 12, 0), coat=34,
                rf=(-3.0, 22.5, 3.4), lf=(3.0, 21.5, -5.0)), "lin"),
    (0.50, dict(torso=(16, -30, -4), head=(-14, 26, 0), body=(6, -12, 0), body_pos=(0, 2.8, 0), coat=12,
                rf=(-3.8, 22.5, 2.6), lf=(3.2, 22.5, -4.6), lh=(8.6, -2.4, 2.4)), "in"),
    (0.62, dict(torso=(12, -24, 0), head=(-12, 20, 0), lh=(7.0, -4.0, -2.0)), "soft"),
    (0.74, dict(torso=(10, 34, 4), head=(-10, -30, 0), body=(2, 14, 0), lh=(6.0, -4.6, -4.4)), "in"),
    (0.88, dict(torso=(-10, 20, 6), head=(4, -16, 0), body=(-2, 8, 0), body_pos=(0, 0.2, 0),
                root_pos=(0, -3.0, -1.0), rf=(-3.0, 20.5, 2.0), lf=(3.0, 20.0, -2.0), toe_r=-20, toe_l=-20), "out"),
    (0.98, dict(torso=(8, 24, 4), head=(-6, -20, 0), root_pos=(0, -3.6, -2.0), rf=(-3.0, 19.0, 3.0),
                lf=(3.0, 18.5, -3.0)), "soft"),
    (1.07, dict(torso=(42, -20, -6), head=(-32, 16, 0), body=(10, -8, 0), body_pos=(0, 5.0, 0),
                root_pos=(0, 0, -3.0), rf=(-3.6, 22.5, 3.6), lf=(3.2, 22.5, -5.2), toe_r=0, toe_l=0), "in3"),
    (1.25, dict(torso=(36, -16, -4), head=(-26, 12, 0), body_pos=(0, 4.4, 0)), "soft"),
    (1.60, dict(STANCE, **FREE_L, coat=0), "soft"),
]

# «Сокрушающий разрыв»: неостановимый, клинок светится золотом; сверху, наотмашь, в прыжке сверху
SH_S1 = sweep(0.70, 0.83, [vdir(-30), vdir(40), vdir(110), vdir(172)], C2, radius=6.0, two=1.0, e="in3")
SH_S2 = sweep(1.10, 1.23, [hdir(-150, 0), hdir(-70, 0), hdir(20, -4), hdir(110, -10)], C2, radius=6.2, two=1.0)
SH_S3 = sweep(1.50, 1.63, [vdir(-35), vdir(40), vdir(110), vdir(174)], C2, radius=6.0, two=1.0, e="in3")
SHATTER_EV = dict(hits=(0.82, 1.20, 1.62), glow=(0.0, 1.75), leap=1.36)
SHATTER_W = W((0.0, LOW, "lin"), (0.25, dict(GUARD2), "soft"), first(SH_S1, 0.62, "soft"), SH_S1,
              last(SH_S1, 0.92, "lin"),
              (1.02, dict(SH_S2[0][1]), "soft"), SH_S2, last(SH_S2, 1.28, "out3"),
              (1.40, dict(SH_S3[0][1]), "soft"), SH_S3, last(SH_S3, 1.85, "lin"), hold(2.2, LOW))
SHATTER_B = [
    (0.00, STANCE, "lin"),
    (0.30, dict(torso=(2, 10, 0), head=(-2, -8, 0), body_pos=(0, 1.2, 0)), "soft"),
    (0.66, dict(torso=(-16, 8, 0), head=(10, -6, 0), body=(-4, 4, 0), body_pos=(0, 0.2, 0),
                rf=(-3.6, 22.5, 3.2), lf=(3.0, 22.5, -2.6)), "soft"),
    (0.82, dict(torso=(42, 2, 0), head=(-32, -2, 0), body=(10, 2, 0), body_pos=(0, 5.0, 0),
                root_pos=(0, 0, -2.5), lf=(3.0, 22.5, -5.4)), "in3"),
    (0.96, dict(torso=(30, 30, 0), head=(-24, -26, 0), body=(8, 16, 0), body_pos=(0, 3.6, 0)), "soft"),
    (1.08, dict(torso=(16, 44, 4), head=(-14, -40, 0), body=(2, 18, 0), body_pos=(0, 3.0, 0)), "soft"),
    (1.20, dict(torso=(16, -36, -4), head=(-14, 32, 0), body=(6, -16, 0), body_pos=(0, 3.4, 0),
                root_pos=(0, 0, -4.0), lf=(3.2, 22.5, -5.6)), "in"),
    (1.30, dict(torso=(10, -40, -4), head=(-10, 36, 0)), "out3"),
    (1.40, dict(torso=(-20, 0, 0), head=(12, 0, 0), body=(-4, 0, 0), body_pos=(0, 1.0, 0),
                root_pos=(0, -4.0, -4.0), rf=(-3.0, 20.0, 2.0), lf=(3.0, 19.5, -2.4), toe_r=-20, toe_l=-20), "out"),
    (1.52, dict(torso=(-10, 0, 0), root_pos=(0, -5.0, -5.0)), "soft"),
    (1.62, dict(torso=(46, 0, 0), head=(-34, 0, 0), body=(12, 0, 0), body_pos=(0, 5.6, 0),
                root_pos=(0, 0, -5.0), rf=(-3.8, 22.5, 3.8), lf=(3.2, 22.5, -5.6), toe_r=0, toe_l=0), "in3"),
    (1.85, dict(torso=(40, 0, 0), head=(-30, 0, 0), body_pos=(0, 5.0, 0)), "soft"),
    (2.20, dict(STANCE, **FREE_L), "soft"),
]

# «Тесак в узел»: заряд пламенем (клинок низко сзади), рывок, прыжок, удар в землю → огненная волна
TG_SMASH = sweep(1.34, 1.45, [vdir(-34), vdir(50), vdir(120), vdir(174)], C2, radius=6.0, two=1.0, e="in3")
TANGLE_EV = dict(charge=(0.0, 1.0), dash=(1.0, 1.22), leap=1.22, smash=1.45, hits=(1.45,))
LOWBACK2 = dict(rh=(-6.4, 1.0, 3.4), wdir=hdir(-160, -34), wface=(0.0, 1.0, 0.3), two=1.0)
TANGLE_W = W((0.0, LOW, "lin"), (0.36, dict(LOWBACK2), "soft"),
             (1.0, dict(LOWBACK2, rh=(-6.2, 1.2, 3.6)), "lin"),
             (1.24, dict(TG_SMASH[0][1]), "soft"), TG_SMASH, last(TG_SMASH, 1.85, "lin"),
             (2.05, dict(GUARD2), "soft"), hold(2.4, LOW))
TANGLE_B = [
    (0.00, STANCE, "lin"),
    (0.36, dict(torso=(26, 44, 0), head=(-24, -40, 0), body=(6, 18, 0), body_pos=(0, 4.0, 0),
                rf=(-4.2, 22.5, 3.6), lf=(3.4, 22.5, -4.0)), "soft"),
    (0.70, dict(torso=(28, 46, 2), head=(-26, -42, 0), body_pos=(0, 4.4, 0)), "soft"),
    (1.00, dict(torso=(26, 44, 0), head=(-24, -40, 0), body_pos=(0, 4.2, 0)), "soft"),
    (1.08, dict(torso=(38, 30, 0), head=(-32, -26, 0), body=(22, 14, 0), body_pos=(0, 3.4, 0), coat=34,
                rf=(-3.0, 21.0, 5.6), lf=(3.0, 22.5, -3.6)), "out"),
    (1.22, dict(torso=(34, 26, 0), head=(-30, -22, 0), body=(20, 10, 0), coat=36,
                rf=(-3.0, 22.5, 3.0), lf=(3.0, 21.0, -5.4)), "lin"),
    (1.32, dict(torso=(-22, 4, 0), head=(14, -4, 0), body=(-4, 2, 0), body_pos=(0, 0.8, 0), coat=20,
                root_pos=(0, -5.0, -2.0), rf=(-3.0, 20.0, 2.0), lf=(3.0, 19.5, -2.4), toe_r=-20, toe_l=-20), "out"),
    (1.45, dict(torso=(48, 0, 0), head=(-36, 0, 0), body=(12, 0, 0), body_pos=(0, 6.0, 0), coat=0,
                root_pos=(0, 0, -4.0), rf=(-4.0, 22.5, 4.0), lf=(3.4, 22.5, -5.6), toe_r=0, toe_l=0), "in3"),
    (1.85, dict(torso=(44, 0, 0), head=(-32, 0, 0), body_pos=(0, 5.6, 0)), "soft"),
    (2.40, dict(STANCE, **FREE_L), "soft"),
]

# Контрстойка: тесак поперёк корпуса двумя руками, лезвием к врагу; ударили — контрудар
STANCE_C_POSE = dict(rh=(-1.6, -3.6, -6.8), wdir=hdir(84, 6), wface=(0.0, 0.0, -1.0), two=1.0, r_pole=(-0.8, 0.6, 0.4))
STANCEC_W = [(0.0, LOW, "lin"), (0.3, STANCE_C_POSE, "soft"), (1.5, STANCE_C_POSE, "soft")]


def stance_c(t):
    pr = clip(STANCEC_B, STANCEC_W)(t)
    k = ng.smooth01(0.2, 0.4, t)
    pr["body_pos"] = (0, 2.6 + 0.3 * math.sin(t * 7) * k, 0)
    return pr


STANCEC_B = [(0.0, STANCE, "lin"),
             (0.3, dict(torso=(10, 30, 0), head=(-12, -30, 8), body=(0, 10, 0), body_pos=(0, 2.6, 0),
                        rf=(-4.0, 22.5, 3.0), lf=(3.0, 22.5, -3.6)), "soft"),
             (1.5, dict(torso=(10, 30, 0)), "soft")]
CT_S = sweep(0.12, 0.26, [hdir(84, 6), hdir(10, 2), hdir(-70, 8), hdir(-138, 20)], C2, radius=6.2, two=1.0)
COUNTER_EV = dict(hits=(0.24,), shots=(0.24,))
COUNTER_W = W((0.0, STANCE_C_POSE, "lin"), first(CT_S, 0.1, "soft"), CT_S, last(CT_S, 0.4, "out3"), hold(0.8, LOW))
COUNTER_B = [(0.0, STANCEC_B[1][1], "lin"),
             (0.24, dict(torso=(12, -30, -4), head=(-12, 26, 0), body=(4, -12, 0), body_pos=(0, 2.8, 0),
                         root_pos=(0, 0, -2.4), lf=(3.2, 22.5, -5.0)), "in"),
             (0.40, dict(torso=(10, -36, -4), head=(-10, 32, 0)), "out3"),
             (0.80, dict(STANCE, **FREE_L), "soft")]

# Перезарядка: откинуть барабан, гильзы вон, три патрона из патронташа, защёлкнуть, крутануть тесак
UPRIGHT = dict(rh=(-8.8, -1.2, -7.2), wdir=(-0.28, -0.93, -0.22), wface=(0.0, -0.25, -1.0), two=0.0,
               r_pole=(-0.9, 0.4, 0.4))
DRUM_L = (-4.0, -3.4, -8.2)
BAND_L = (2.4, -5.8, -6.6)
RELOAD_EV = dict(open=0.22, eject=0.32, insert=(0.52, 0.74, 0.96), close=1.14, spin=1.3)
SPIN = [(1.20 + k * 0.035, dict(UPRIGHT, wdir=norm((math.sin(math.radians(a)) * 0.9, -math.cos(math.radians(a)), -0.2)),
                                 wface=(0.0, 0.0, -1.0)), "lin") for k, a in enumerate(range(0, 361, 45))]
RELOAD_W = W((0.0, LOW, "lin"), (0.16, UPRIGHT, "soft"), (1.18, UPRIGHT, "soft"), SPIN,
             (1.6, dict(CARRY), "soft"))
RELOAD_B = [
    (0.00, STANCE, "lin"),
    (0.16, dict(torso=(10, 6, 0), head=(14, -6, 0), body_pos=(0, 1.4, 0), lh=(3.0, -3.2, -6.0),
                l_pole=(0.7, 0.6, 0.4)), "soft"),
    (0.22, dict(lh=DRUM_L, lhand=(0, 0, -30)), "in"),
    (0.30, dict(lh=(2.0, -3.4, -5.0), lhand=(0, 0, 0)), "out3"),
    (0.44, dict(lh=BAND_L, head=(18, 10, 0)), "soft"),
    (0.52, dict(lh=DRUM_L, head=(14, -4, 0)), "soft"),
    (0.64, dict(lh=BAND_L, head=(18, 10, 0)), "soft"),
    (0.74, dict(lh=DRUM_L, head=(14, -4, 0)), "soft"),
    (0.86, dict(lh=BAND_L, head=(18, 10, 0)), "soft"),
    (0.96, dict(lh=DRUM_L, head=(14, -4, 0)), "soft"),
    (1.08, dict(lh=(1.4, -3.0, -4.6)), "soft"),
    (1.14, dict(lh=(-1.2, -4.4, -6.6), lhand=(0, 0, 20)), "in3"),
    (1.26, dict(lh=LH_REST_L, lhand=(0, 0, 0), head=(-4, 8, 0), torso=(4, -2, 2), l_pole=(0.6, 0.3, 1.0)), "soft"),
    (1.60, dict(LAZY), "soft"),
]
RTAC_EV = dict(open=0.12, eject=0.18, insert=(0.38,), close=0.62)
RTAC_W = W((0.0, LOW, "lin"), (0.1, UPRIGHT, "soft"), (0.7, UPRIGHT, "soft"), (1.0, LOW, "soft"))
RTAC_B = [
    (0.00, STANCE, "lin"),
    (0.10, dict(torso=(10, 6, 0), head=(12, -6, 0), lh=(3.0, -3.2, -6.0), l_pole=(0.7, 0.6, 0.4)), "soft"),
    (0.14, dict(lh=DRUM_L, lhand=(0, 0, -30)), "in"),
    (0.26, dict(lh=BAND_L, lhand=(0, 0, 0)), "soft"),
    (0.38, dict(lh=DRUM_L), "soft"),
    (0.56, dict(lh=(1.4, -3.0, -4.6)), "soft"),
    (0.62, dict(lh=(-1.2, -4.4, -6.6), lhand=(0, 0, 20)), "in3"),
    (1.00, dict(STANCE, **FREE_L, lhand=(0, 0, 0)), "soft"),
]

# «Один удар»: втыкает тесак в землю и ждёт; потом выдёргивает и бьёт
PLANT_POSE = dict(rh=(-1.0, -1.8, -8.6), wdir=(0.0, 1.0, -0.06), wface=(0.0, 0.0, -1.0), two=0.0,
                  r_pole=(-0.8, 0.3, 0.6))
OW_EV = dict(plant=0.46)
OW_W = W((0.0, LOW, "lin"), (0.26, dict(PLANT_POSE, rh=(-1.0, -7.0, -8.0), two=1.0), "soft"),
         (0.46, dict(PLANT_POSE, two=1.0), "in3"), (0.64, PLANT_POSE, "soft"), (3.6, PLANT_POSE, "lin"))


def one_wait(t):
    pr = clip(OW_B)(t)
    w_ = track(OW_W, t)
    for k in WEAPON_KEYS:
        pr[k] = w_[k]
    k = ng.smooth01(0.6, 0.9, t)
    w = 2 * math.pi * t / 1.8
    pr["body_pos"] = (0.4 * k, pr["body_pos"][1] + 0.25 * math.sin(w) * k, 0)
    # «давай»: ладонь вверх, пальцы зовут (1.2–1.9 и 2.6–3.2)
    beck = ng.smooth01(1.1, 1.3, t) * (1 - ng.smooth01(1.8, 2.0, t)) + \
        ng.smooth01(2.5, 2.7, t) * (1 - ng.smooth01(3.1, 3.3, t))
    if beck > 0:
        pr["lh"] = ng.lerp_val(pr["lh"], (4.6, -3.8, -8.6), beck)
        pr["lhand"] = (-50 * beck + 26 * beck * max(0.0, math.sin(t * 16)), 0, 60 * beck)
        pr["head"] = (pr["head"][0] - 6 * beck, pr["head"][1], pr["head"][2] + 10 * beck)
    return pr


OW_B = [
    (0.00, STANCE, "lin"),
    (0.26, dict(torso=(-6, 4, 0), head=(4, -4, 0), body_pos=(0, 1.0, 0), rf=(-3.0, 22.5, 1.4),
                lf=(3.0, 22.5, -2.0)), "soft"),
    (0.46, dict(torso=(26, 2, 0), head=(-20, -2, 0), body_pos=(0, 3.4, 0)), "in3"),
    (0.70, dict(torso=(10, -4, 2), head=(-10, 8, -4), body=(0, -6, -2), body_pos=(0, 1.0, 0),
                rf=(-2.8, 22.5, 1.0), lf=(3.4, 22.5, -1.4), lh=LH_REST_L), "soft"),
    (3.60, dict(torso=(10, -4, 2)), "lin"),
]
OH_S = sweep(0.26, 0.40, [hdir(-150, 22), hdir(-70, 8), hdir(10, 0), hdir(100, -8)], C2, radius=6.2, two=1.0)
OH_EV = dict(hits=(0.38,), pull=0.16)
OH_W = W((0.0, PLANT_POSE, "lin"), (0.12, dict(PLANT_POSE, two=1.0), "soft"), first(OH_S, 0.22, "out"), OH_S,
         last(OH_S, 0.55, "out3"), hold(1.0, LOW))
OH_B = [(0.0, OW_B[3][1], "lin"),
        (0.12, dict(torso=(20, 6, 0), head=(-14, -6, 0), body_pos=(0, 2.4, 0)), "soft"),
        (0.24, dict(torso=(4, 40, 4), head=(-6, -36, 0), body=(0, 14, 0), body_pos=(0, 1.6, 0),
                    rf=(-3.9, 22.5, 2.8), lf=(3.0, 22.5, -2.6)), "out"),
        (0.38, dict(torso=(16, -36, -4), head=(-14, 32, 0), body=(6, -16, 0), body_pos=(0, 3.4, 0),
                    root_pos=(0, 0, -3.0), lf=(3.2, 22.5, -5.4)), "in"),
        (0.55, dict(torso=(12, -40, -4), head=(-10, 36, 0)), "out3"),
        (1.00, dict(STANCE, **FREE_L), "soft")]

# Слом стойки: отшатнулся, рухнул на правое колено, опирается на тесак; тяжело дышит; встаёт
KNEEL = dict(body=(6, 0, 0), body_pos=(0, 7.4, 1.0), torso=(24, -6, 0), head=(18, 6, 0),
             rf=(-3.0, 22.0, 6.4), lf=(3.2, 22.5, -4.6), toe_r=-50.0, lh=(3.6, 5.6, -7.0), l_pole=(0.8, -0.4, 0.4))
KNEEL_W = dict(rh=(-8.6, 1.0, -3.6), wdir=(-0.12, 1.0, -0.15), wface=(0.0, 0.0, -1.0), two=0.0, r_pole=(-1, 0, 0.4))
STAGGER_EV = dict(fall=0.36, rise=2.8)
STAGGER_W = [(0.0, LOW, "lin"), (0.36, KNEEL_W, "in3"), (2.8, KNEEL_W, "lin"), (3.4, LOW, "soft")]


def stagger(t):
    pr = clip(STAGGER_B, STAGGER_W)(t)
    k = ng.smooth01(0.4, 0.6, t) * (1 - ng.smooth01(2.7, 2.9, t))
    w = 2 * math.pi * t / 0.9
    pr["torso"] = (pr["torso"][0] + 4 * math.sin(w) * k, pr["torso"][1], pr["torso"][2])
    pr["head"] = (pr["head"][0] - 3 * math.sin(w) * k, pr["head"][1], pr["head"][2])
    return pr


STAGGER_B = [
    (0.00, STANCE, "lin"),
    (0.14, dict(torso=(-18, -10, -6), head=(12, 14, 8), body=(-6, 0, 0), body_pos=(0, 1.0, 1.4),
                rf=(-3.4, 22.5, 3.8), lh=(8.4, -3.0, 2.0)), "out3"),
    (0.36, KNEEL, "in3"),
    (2.80, dict(KNEEL, torso=(22, -6, 0)), "lin"),
    (3.10, dict(body_pos=(0, 3.4, 0.4), torso=(18, 6, 0), head=(-10, -6, 0), rf=(-3.6, 22.5, 2.6), toe_r=0.0,
                lf=(3.0, 22.5, -3.0), lh=(5.4, -1.0, -4.4)), "soft"),
    (3.40, dict(STANCE, **FREE_L), "soft"),
]

# Переход в фазу 2: вынимает сигару, щелчком отбрасывает, хрустит шеей, хватает тесак двумя руками, взмах — кольца
PH2_EV = dict(cigar_off=0.55, flick=0.92, crack=1.25, burst=1.78)
PH2_W = W((0.0, LOW, "lin"), (1.5, LOW, "soft"), (1.62, dict(GUARD2, rh=(-3.2, -4.6, -5.4), wdir=(0.1, -0.95, -0.2),
                                                          wface=(0.0, -0.2, -1.0)), "soft"),
          (1.78, dict(rh=(-7.6, 1.6, -2.4), wdir=hdir(-118, -36), wface=(0.0, 1.0, 0.3), two=0.0), "in3"),
          (1.95, dict(rh=(-7.8, 1.4, -2.0), wdir=hdir(-120, -40), wface=(0.0, 1.0, 0.3), two=0.0), "soft"),
          (2.4, dict(GUARD2), "soft"))
PH2_B = [
    (0.00, STANCE, "lin"),
    (0.22, dict(body_pos=(0, 0.8, 0), torso=(0, 0, 0), head=(-2, 0, 0), rf=(-2.8, 22.5, 0.6),
                lf=(3.0, 22.5, -1.4)), "soft"),
    (0.50, dict(lh=MOUTH_L, l_pole=(0.6, 0.9, 0.2)), "soft"),
    (0.62, dict(lh=(-0.6, -9.8, -8.0)), "soft"),
    (0.80, dict(lh=(9.2, -4.6, -3.0), l_pole=(0.4, 0.6, 0.8), head=(-2, -14, 0)), "soft"),
    (0.92, dict(lh=(10.2, -2.0, -6.2), lhand=(0, 0, -40)), "in3"),
    (1.08, dict(lh=LH_REST_L, lhand=(0, 0, 0), head=(-4, 0, 0)), "soft"),
    (1.20, dict(head=(-4, 0, 22)), "soft"),
    (1.30, dict(head=(-4, 0, -22)), "soft"),
    (1.44, dict(head=(-6, 0, 0), torso=(4, 0, 0)), "soft"),
    (1.62, dict(torso=(-8, -10, 0), head=(6, 10, 0), body_pos=(0, 0.6, 0)), "soft"),
    (1.78, dict(torso=(18, 34, 0), head=(-16, -30, 0), body=(4, 12, 0), body_pos=(0, 3.4, 0),
                rf=(-4.0, 22.5, 3.4), lf=(3.2, 22.5, -3.6), lh=(9.0, -4.0, 0.6)), "in3"),
    (2.00, dict(torso=(14, 30, 0), head=(-14, -26, 0)), "soft"),
    (2.40, dict(STANCE), "soft"),
]

# Шквал: рывки‑разрезы насквозь (A — справа налево, B — слева направо), в конце — прыжок и удар всеми патронами
FA_S = sweep(0.14, 0.26, [hdir(-130, 4), hdir(-40, 0), hdir(40, -4), hdir(104, -10)], C1)
FB_S = sweep(0.14, 0.26, [hdir(108, -8), hdir(30, 0), hdir(-50, 10), hdir(-128, 24)], C1)
FLURRY_EV = dict(hits=(0.22,), dash=(0.08, 0.3))
DASHPOSE = dict(torso=(30, 10, 0), head=(-26, -8, 0), body=(20, 6, 0), body_pos=(0, 3.0, 0), coat=30,
                rf=(-3.0, 21.0, 5.0), lf=(3.0, 22.5, -3.6), lh=(7.0, -1.0, 2.0))
FA_W = W((0.0, dict(FA_S[0][1]), "lin"), FA_S, last(FA_S, 0.45, "soft"))
FB_W = W((0.0, dict(FB_S[0][1]), "lin"), FB_S, last(FB_S, 0.45, "soft"))
FA_B = [(0.0, DASHPOSE, "lin"),
        (0.22, dict(torso=(20, -30, -4), head=(-18, 26, 0), body=(10, -12, 0), lh=(8.6, -2.4, 2.4)), "in"),
        (0.45, dict(DASHPOSE, torso=(26, -6, 0)), "soft")]
FB_B = [(0.0, dict(DASHPOSE, torso=(28, -20, 0), head=(-24, 16, 0)), "lin"),
        (0.22, dict(torso=(18, 34, 4), head=(-16, -30, 0), body=(8, 14, 0), lh=(6.0, -4.6, -4.4)), "in"),
        (0.45, dict(DASHPOSE), "soft")]
FE_S = sweep(0.60, 0.73, [vdir(-34), vdir(50), vdir(120), vdir(174)], C2, radius=6.0, two=1.0, e="in3")
FEND_EV = dict(jump=0.16, hits=(0.73,), shots=(0.73,))
FEND_W = W((0.0, LOW, "lin"), (0.4, dict(FE_S[0][1]), "soft"), FE_S, last(FE_S, 1.3, "lin"),
           (1.55, dict(UPRIGHT), "soft"), (2.0, dict(CARRY), "soft"))
FEND_B = [
    (0.00, DASHPOSE, "lin"),
    (0.14, dict(STANCE, torso=(24, 0, 0), head=(-20, 0, 0), body_pos=(0, 4.4, 0), coat=10), "soft"),
    (0.30, dict(torso=(-10, 0, 0), head=(4, 0, 0), body_pos=(0, 0.4, 0), root_pos=(0, -6.0, 0),
                rf=(-3.0, 19.0, 2.0), lf=(3.0, 18.5, -2.4), toe_r=-24, toe_l=-24, coat=-10), "out"),
    (0.58, dict(torso=(-22, 0, 0), head=(14, 0, 0), root_pos=(0, -7.0, -1.0)), "soft"),
    (0.73, dict(torso=(48, 0, 0), head=(-36, 0, 0), body=(12, 0, 0), body_pos=(0, 6.2, 0), root_pos=(0, 0, -3.0),
                rf=(-4.0, 22.5, 4.0), lf=(3.4, 22.5, -5.6), toe_r=0, toe_l=0, coat=20), "in3"),
    (1.30, dict(torso=(44, 0, 0), head=(-30, 0, 0), body_pos=(0, 5.8, 0), coat=0), "lin"),
    (1.60, dict(body_pos=(0, 1.6, 0), torso=(6, 0, 0), head=(-6, 0, 0), rf=(-2.8, 22.5, 0.8),
                lf=(3.2, 22.5, -1.6)), "soft"),
    (2.00, dict(LAZY), "soft"),
]

# Уклонения, рывок, пинок, отскок с выстрелом
DODGE_EV = dict(move=(0.04, 0.26))


def dodge_b(sign):
    return [(0.0, STANCE, "lin"),
            (0.08, dict(body=(0, 8, -sign * 14), body_pos=(sign * 1.0, 3.2, 0), torso=(14, 14, -sign * 16),
                        head=(-12, -18, sign * 14), rf=(-3.6 + sign * 1.6, 21.2, 1.6), lf=(3.0 + sign * 2.4, 22.5, -2.0),
                        lh=(6.0 + sign * 1.2, -4.2, -4.0)), "out"),
            (0.26, dict(body=(0, 8, -sign * 8), body_pos=(sign * 0.6, 3.6, 0), torso=(12, 14, -sign * 8),
                        rf=(-3.4 + sign * 2.4, 22.5, 1.8), lf=(3.2 + sign * 1.6, 22.5, -2.6)), "soft"),
            (0.45, dict(STANCE, **FREE_L), "soft")]


DODGE_W = [(0.0, LOW, "lin"), (0.1, dict(LOW, rh=(-8.0, -0.6, -3.4)), "out"), (0.45, LOW, "soft")]
DBACK_B = [(0.0, STANCE, "lin"),
           (0.06, dict(body_pos=(0, 3.6, 0), torso=(16, 14, 0)), "out"),
           (0.18, dict(torso=(-8, 10, 0), head=(4, -12, 0), body_pos=(0, 0.6, 0), root_pos=(0, -2.4, 0),
                       rf=(-3.4, 20.4, 3.4), lf=(3.0, 20.0, -1.4), toe_r=-22, toe_l=-22, coat=-16), "out"),
           (0.34, dict(torso=(20, 14, 0), head=(-18, -16, 0), body_pos=(0, 3.8, 0), root_pos=(0, 0, 0),
                       rf=(-3.8, 22.5, 3.0), lf=(3.0, 22.5, -3.4), toe_r=0, toe_l=0, coat=8), "in3"),
           (0.50, dict(STANCE, **FREE_L, coat=0), "soft")]
DASH_EV = dict(dash=(0.08, 0.36))
DASH_B = [(0.0, STANCE, "lin"),
          (0.08, dict(torso=(22, 12, 0), body_pos=(0, 3.8, 0)), "soft"),
          (0.14, dict(DASHPOSE), "out"),
          (0.36, dict(DASHPOSE, torso=(28, 6, 0)), "lin"),
          (0.50, dict(STANCE, **FREE_L, coat=0), "soft")]
DASH_W = [(0.0, LOW, "lin"),
          (0.14, dict(rh=(-6.8, 2.0, 3.2), wdir=(-0.25, 0.42, 0.87), wface=(0.0, 1.0, -0.4), two=0.0), "soft"),
          (0.36, dict(rh=(-6.8, 2.0, 3.2), wdir=(-0.25, 0.42, 0.87), wface=(0.0, 1.0, -0.4)), "lin"),
          (0.5, LOW, "soft")]
KICK_EV = dict(hits=(0.30,))
KICK_B = [(0.0, STANCE, "lin"),
          (0.18, dict(torso=(-4, 18, 0), head=(-2, -20, 0), body=(-6, 10, 0), body_pos=(0, 1.0, 0),
                      lf=(3.0, 18.0, -1.0), toe_l=10, lh=(7.6, -3.0, -1.0)), "soft"),
          (0.30, dict(torso=(-14, 20, 0), head=(8, -22, 0), body=(-14, 12, 0), lf=(3.0, 13.0, -10.6), toe_l=-10,
                      rf=(-3.4, 22.5, 2.6)), "in3"),
          (0.44, dict(torso=(0, 16, 0), head=(-4, -18, 0), body=(-2, 8, 0), lf=(3.0, 19.0, -4.0), toe_l=0), "soft"),
          (0.70, dict(STANCE, **FREE_L), "soft")]
BS_EV = dict(hop=(0.04, 0.3), shots=(0.48,))
BS_POINT = dict(rh=(-3.6, -1.4, -10.2), wdir=(0.0, 0.62, -0.79), wface=(0.0, 0.79, 0.62), two=0.0,
                r_pole=(-0.9, 0.3, 0.3))
BS_W = [(0.0, LOW, "lin"), (0.30, LOW, "soft"), (0.42, BS_POINT, "out"), (0.48, BS_POINT, "lin"),
        (0.56, dict(BS_POINT, rh=(-4.0, -4.4, -8.4), wdir=(0.0, -0.1, -1.0), wface=(0.0, 1.0, 0.0)), "out3"),
        (0.9, LOW, "soft")]
BS_B = [(0.0, STANCE, "lin"),
        (0.06, dict(body_pos=(0, 3.6, 0), torso=(16, 14, 0)), "out"),
        (0.18, dict(torso=(-8, 10, 0), head=(4, -12, 0), body_pos=(0, 0.6, 0), root_pos=(0, -2.6, 0),
                    rf=(-3.4, 20.4, 3.4), lf=(3.0, 20.0, -1.4), toe_r=-22, toe_l=-22, coat=-16), "out"),
        (0.32, dict(torso=(20, 14, 0), head=(-18, -16, 0), body_pos=(0, 3.8, 0), root_pos=(0, 0, 0),
                    rf=(-3.8, 22.5, 3.0), lf=(3.0, 22.5, -3.4), toe_r=0, toe_l=0, coat=8), "in3"),
        (0.42, dict(torso=(26, -6, 0), head=(-20, 4, 0), body_pos=(0, 3.4, 0), lh=(7.6, -3.0, 1.0)), "soft"),
        (0.56, dict(torso=(10, -2, 0), head=(-8, 2, 0), body_pos=(0, 2.4, 0.6)), "out3"),
        (0.90, dict(STANCE, **FREE_L, coat=0), "soft")]

# Смерть: отшатнулся, рухнул на колени, оперся на тесак, голова упала
DEATH_B = [(0.0, STANCE, "lin"),
           (0.22, dict(torso=(-16, -8, -6), head=(14, 10, 8), body_pos=(0, 1.0, 1.2), lh=(4.0, -3.0, -6.0)), "out3"),
           (0.70, dict(KNEEL, rf=(-3.0, 22.0, 5.8), lf=(3.0, 22.0, 4.6), toe_l=-50.0, body_pos=(0, 8.2, 2.4),
                       torso=(20, -4, 0)), "in3"),
           (1.30, dict(torso=(38, -2, 6), head=(40, 4, 10), lh=(5.6, 7.0, -3.0)), "soft"),
           (2.60, dict(torso=(42, -2, 8), head=(44, 4, 12)), "soft")]
DEATH_W = [(0.0, LOW, "lin"), (0.7, KNEEL_W, "in3"), (2.6, KNEEL_W, "lin")]

# Подначка: «давай, подходи», тесак на плече; вращение тесаком в руке
TAUNT_B = [(0.0, LAZY, "lin"), (1.6, LAZY, "lin")]


def taunt(t):
    pr = clip(TAUNT_B, [(0.0, CARRY, "lin"), (1.6, CARRY, "lin")])(t)
    beck = ng.smooth01(0.2, 0.45, t) * (1 - ng.smooth01(1.2, 1.5, t))
    pr["lh"] = ng.lerp_val(LH_REST_L, (5.0, -3.8, -8.4), beck)
    pr["l_pole"] = (0.6, 0.6, 0.6)
    pr["lhand"] = (-50 * beck + 28 * beck * max(0.0, math.sin(t * 15)), 0, 60 * beck)
    pr["head"] = (-8 * beck - 2, 10, 8 * beck)
    pr["torso"] = (-4 * beck + 2, -6, 3)
    return pr


TWIRL_SPIN = [(0.18 + k * 0.045, dict(rh=(-7.6, -1.6, -5.2), wdir=vdir(a, side=-0.2),
                                       wface=(0.0, 0.0, -1.0) if False else norm((0, -math.sin(math.radians(a + 90)) * 0, 1)),
                                       two=0.0, r_pole=(-0.8, 0.4, 0.4)), "lin") for k, a in
              enumerate(range(0, 721, 45))]
for _k, (_t, _d, _e) in enumerate(TWIRL_SPIN):
    _a = math.radians(_k * 45 + 90)
    _d["wface"] = norm((0.0, -math.cos(_a), -math.sin(_a)))
TWIRL_EV = dict(spin=(0.18, 0.95))
TWIRL_W = W((0.0, LOW, "lin"), TWIRL_SPIN, (1.3, CARRY, "soft"))
TWIRL_B = [(0.0, STANCE, "lin"), (0.2, dict(LAZY, torso=(2, 10, 0), head=(-2, -6, 0)), "soft"),
           (1.0, dict(LAZY, torso=(2, 8, 0)), "soft"), (1.3, LAZY, "soft")]

# Финальный подъём после третьего слома: встаёт, раскидывает руки (кольца вспыхивают), хватает тесак
FR_EV = dict(flare=0.8)
FR_B = [(0.0, KNEEL, "lin"),
        (0.5, dict(body_pos=(0, 2.4, 0), torso=(10, 0, 0), head=(4, 0, 0), rf=(-3.0, 22.5, 1.6), toe_r=0.0,
                   lf=(3.0, 22.5, -2.0), lh=LH_REST_L), "soft"),
        (0.8, dict(torso=(-14, 0, 0), head=(-24, 0, 0), body_pos=(0, 1.0, 0), lh=(12.0, -6.0, 0.6),
                   l_pole=(0.0, 1.0, 0.4)), "out3"),
        (1.3, dict(torso=(-12, 0, 0), head=(-20, 0, 0)), "soft"),
        (2.0, dict(STANCE), "soft")]
FR_W = [(0.0, KNEEL_W, "lin"), (0.5, LOW, "soft"),
        (0.8, dict(rh=(-12.0, -6.0, 0.6), wdir=hdir(-100, -30), wface=(0.0, 0.0, -1.0), two=0.0,
                   r_pole=(0.0, 1.0, 0.4)), "out3"),
        (1.3, dict(rh=(-12.0, -5.6, 0.6), wdir=hdir(-100, -32), wface=(0.0, 0.0, -1.0)), "soft"),
        (2.0, GUARD2, "soft")]

# ----------------------------------------------------------------
# (имя, функция, длина, кадров/с, цикл, события) — события в секундах (для TigerTimings.java)
LOOPS = [
    ("idle_smoke", idle_smoke, 3.6, 20, True),
    ("guard", guard, 2.4, 20, True),
    ("idle", idle, 2.0, 20, True),
    ("walk_lazy", walk_lazy, 1.25, 30, True),
    ("walk", walk, 1.0, 30, True),
    ("run", run, 0.62, 60, True),
    ("strafe_l", strafe(1), 0.9, 30, True),
    ("strafe_r", strafe(-1), 0.9, 30, True),
    ("back", back, 1.0, 30, True),
]
ACTIONS = [
    ("double", clip(DOUBLE_B, DOUBLE_W), 1.1, dict(hits=DOUBLE_HITS)),
    ("triple", clip(TRIPLE_B, TRIPLE_W), 1.5, dict(hits=TRIPLE_HITS)),
    ("double_blast", clip(DBLAST_B, DBLAST_W), 1.4, DBLAST_EV),
    ("triple_blast", clip(TBLAST_B, TBLAST_W), 1.6, TBLAST_EV),
    ("shatter", clip(SHATTER_B, SHATTER_W), 2.2, SHATTER_EV),
    ("tangle", clip(TANGLE_B, TANGLE_W), 2.4, TANGLE_EV),
    ("stance", stance_c, 1.5, dict()),
    ("counter", clip(COUNTER_B, COUNTER_W), 0.8, COUNTER_EV),
    ("reload", clip(RELOAD_B, RELOAD_W), 1.6, RELOAD_EV),
    ("reload_tac", clip(RTAC_B, RTAC_W), 1.0, RTAC_EV),
    ("one_wait", one_wait, 3.6, OW_EV),
    ("one_hit", clip(OH_B, OH_W), 1.0, OH_EV),
    ("stagger", stagger, 3.4, STAGGER_EV),
    ("phase2", clip(PH2_B, PH2_W), 2.4, PH2_EV),
    ("flurry_a", clip(FA_B, FA_W), 0.45, FLURRY_EV),
    ("flurry_b", clip(FB_B, FB_W), 0.45, FLURRY_EV),
    ("flurry_end", clip(FEND_B, FEND_W), 2.0, FEND_EV),
    ("dodge_l", clip(dodge_b(1), DODGE_W), 0.45, DODGE_EV),
    ("dodge_r", clip(dodge_b(-1), DODGE_W), 0.45, DODGE_EV),
    ("dodge_back", clip(DBACK_B, DODGE_W), 0.5, DODGE_EV),
    ("dash", clip(DASH_B, DASH_W), 0.5, DASH_EV),
    ("kick", clip(KICK_B, [(0.0, LOW, "lin"), (0.7, LOW, "lin")]), 0.7, KICK_EV),
    ("backshot", clip(BS_B, BS_W), 0.9, BS_EV),
    ("death", clip(DEATH_B, DEATH_W), 2.6, dict()),
    ("taunt", taunt, 1.6, dict()),
    ("twirl", clip(TWIRL_B, TWIRL_W), 1.3, TWIRL_EV),
    ("final_rise", clip(FR_B, FR_W), 2.0, FR_EV),
]


# ----------------------------------------------------------------
# Замедление времени по участкам (старое время → новое): замах перед ударом чуть дольше (~+0.1 с),
# сам удар — с той же скоростью. Моменты ударов для сервера пересчитываются.
WARP = {
    "double": [(0, 0), (0.10, 0.12), (0.27, 0.38), (0.41, 0.52), (0.55, 0.70), (0.69, 0.84), (1.1, 1.3)],
    "triple": [(0, 0), (0.24, 0.34), (0.37, 0.47), (0.49, 0.63), (0.62, 0.76), (0.88, 1.08), (1.01, 1.21), (1.5, 1.75)],
    "double_blast": [(0, 0), (0.32, 0.44), (0.43, 0.55), (0.56, 0.70), (0.62, 0.78), (0.80, 0.96), (0.92, 1.08),
                     (1.4, 1.6)],
    "triple_blast": [(0, 0), (0.16, 0.26), (0.42, 0.52), (0.55, 0.65), (0.66, 0.80), (0.78, 0.92), (0.98, 1.16),
                     (1.10, 1.28), (1.6, 1.85)],
    "shatter": [(0, 0), (0.70, 0.78), (0.83, 0.91), (1.10, 1.22), (1.23, 1.35), (1.50, 1.66), (1.63, 1.79), (2.2, 2.4)],
    "counter": [(0, 0), (0.12, 0.16), (0.26, 0.30), (0.8, 0.9)],
    "one_hit": [(0, 0), (0.26, 0.34), (0.40, 0.48), (1.0, 1.1)],
    "flurry_a": [(0, 0), (0.14, 0.18), (0.26, 0.30), (0.45, 0.5)],
    "flurry_b": [(0, 0), (0.14, 0.18), (0.26, 0.30), (0.45, 0.5)],
    "flurry_end": [(0, 0), (0.60, 0.70), (0.73, 0.83), (2.0, 2.1)],
    "kick": [(0, 0), (0.18, 0.24), (0.30, 0.36), (0.7, 0.8)],
    "backshot": [(0, 0), (0.42, 0.50), (0.56, 0.64), (0.9, 1.0)],
    "dash": [(0, 0), (0.08, 0.12), (0.5, 0.55)],
}


def _interp(knots, x, a, b):
    for i in range(1, len(knots)):
        x0, x1 = knots[i - 1][a], knots[i][a]
        if x <= x1 or i == len(knots) - 1:
            y0, y1 = knots[i - 1][b], knots[i][b]
            f = 0.0 if x1 == x0 else (x - x0) / (x1 - x0)
            return y0 + (y1 - y0) * max(0.0, min(1.0, f))
    return knots[-1][b]


def _retime(entry):
    name, fn, ln, ev = entry
    k = WARP.get(name)
    if not k:
        return entry
    old = lambda tn: _interp(k, tn, 1, 0)       # noqa: E731
    new = lambda to: _interp(k, to, 0, 1)       # noqa: E731

    def mp(v):
        return tuple(new(x) for x in v) if isinstance(v, tuple) else new(v)
    return name, (lambda t, fn=fn: fn(old(t))), k[-1][1], {kk: mp(vv) for kk, vv in ev.items()}


ACTIONS = [_retime(a_) for a_ in ACTIONS]


# =====================================================================
# 5. Запекание, дорожка тесака, тайминги для сервера, проверка, предпросмотр
# =====================================================================
def bake_clip(model, fn, length_s, fps, loop):
    ng.PREV_PN.clear()
    poses = {}
    bones = [b_["name"] for b_ in model.bones if not b_["name"].startswith("ring")]

    def full(t):
        pr, pose = pose_at(fn, t, model, length_s, loop)
        poses[t] = (pr, pose)
        return pose

    anim = bake(full, length_s, fps, loop, bones, unwrap=True)
    if not loop:
        ng.anchor_end(anim)
    trk, prev_q = [], None
    for k in range(int(round(length_s * fps)) + 1):
        t = min(k / fps, length_s)
        pr, pose = poses[t]
        q = ng.quat(weapon_R(model, pose, pr))
        if prev_q is not None and sum(q[i] * prev_q[i] for i in range(4)) < 0:
            q = [-v for v in q]
        prev_q = q
        trk.append([round(v, 5) for v in q])
    return anim, {"fps": fps, "length": length_s, "loop": loop, "q": trk}


def all_clips():
    out = [(n, f, ln, fps, lp) for n, f, ln, fps, lp in LOOPS]
    out += [(n, f, ln, 60, False) for n, f, ln, _ in ACTIONS]
    return out


def export(model):
    anims, tracks = {}, {}
    for name, fn, ln, fps, loop in all_clips():
        a, w = bake_clip(model, fn, ln, fps, loop)
        anims["animation.thunder_tiger." + name] = a
        tracks[name] = w
        print("  %-12s %.2f с" % (name, ln))
    write_anims(ROOT, "thunder_tiger", anims)
    with open(asset_groups.anim("thunder_tiger.weapon.json", ROOT), "w", encoding="utf-8") as f:
        json.dump({"weapon": "thunder_tiger_blade", "hand": "right_hand",
                   "grip_offset": [round(v, 4) for v in GRIP_OFFSET], "clips": tracks}, f, separators=(",", ":"))


def ticks(s):
    return int(round(s * 20))


def write_timings():
    """Тайминги действий в тиках — сервер берёт их отсюда, поэтому удары всегда совпадают с анимацией."""
    L_ = ["package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;", "",
          "/**", " * Тайминги «Громового тигра» в тиках. СОЗДАНО tools/thunder_tiger_gen.py — не править руками:",
          " * поменял анимацию → запусти скрипт, и сервер возьмёт новые моменты ударов.", " */",
          "public final class TigerTimings {", "", "    private TigerTimings() {", "    }", ""]
    names = []
    for idx, (name, fn, ln, ev) in enumerate(ACTIONS):
        cn = name.upper()
        names.append(name)
        L_.append("    // %s" % name)
        L_.append("    public static final int %s = %d;" % (cn, idx))
        L_.append("    public static final int %s_LEN = %d;" % (cn, ticks(ln)))
        for k, v in ev.items():
            kn = "%s_%s" % (cn, k.upper())
            if isinstance(v, tuple) and k in ("hits", "shots", "insert"):
                L_.append("    public static final int[] %s = {%s};" % (kn, ", ".join(str(ticks(x)) for x in v)))
            elif isinstance(v, tuple):
                L_.append("    public static final int %s_FROM = %d;" % (kn, ticks(v[0])))
                L_.append("    public static final int %s_TO = %d;" % (kn, ticks(v[1])))
            else:
                L_.append("    public static final int %s = %d;" % (kn, ticks(v)))
        L_.append("")
    L_.append("    /** Имена клипов по номеру действия (клиент). */")
    L_.append("    public static final String[] NAMES = {%s};" % ", ".join('"%s"' % n for n in names))
    L_.append("}")
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L_) + "\n")


BLADE_LEN = 32.0


def check(model):
    """Клинок не должен проходить сквозь голову и корпус (кроме удара в землю перед собой)."""
    bad = 0
    for name, fn, ln, fps, loop in all_clips():
        ng.PREV_PN.clear()
        for k in range(int(ln * 30) + 1):
            t = min(k / 30.0, ln)
            pr, pose = pose_at(fn, t, model, ln, loop)
            Rw = weapon_R(model, pose, pr)
            grip = add(model.world("right_hand", [0, 0, 0], pose), mv(Rw, GRIP_OFFSET))
            tip = add(grip, mv(Rw, [0, -BLADE_LEN, 0]))
            base = add(grip, mv(Rw, [0, -3.0, 0]))
            hc = model.world("head", model.local_of("head", (0, 34.0, 0.0)), pose)
            tc = model.world("torso", model.local_of("torso", (0, 23.0, 0.0)), pose)
            pc = model.world("body", model.local_of("body", (0, 16.0, 0.0)), pose)
            probs = []
            for nm, c_, r_ in (("голова", hc, 4.4), ("корпус", tc, 4.0), ("таз", pc, 3.6)):
                d = ng.seg_dist(c_, base, tip)
                if d < r_:
                    probs.append("%s %.1f" % (nm, d))
            if probs:
                bad += 1
                print("%-12s %.2f с: %s" % (name, t, ", ".join(probs)))
    print("проблемных кадров:", bad)


HIDE_RINGS = [True]


def main():
    args = sys.argv[1:]
    if args and args[0] == "--preview":
        orig = ng.preview_parts

        def parts(mob, weapon, clip_, t, hide=()):
            ps = orig(mob, weapon, clip_, t, hide)

            def strip(lst):
                out = []
                hide_ = set(filter(None, os.environ.get("HIDE", "").split(",")))
                for p in lst:
                    if p["name"].startswith("ring") or p["name"] in hide_:
                        continue
                    p["children"] = strip(p["children"])
                    out.append(p)
                return out
            return strip(ps)
        ng.preview_parts = parts
        clip_ = args[1] if len(args) > 1 and args[1] != "-" else None
        times = [float(x) for x in args[2].split(",")] if len(args) > 2 else [0.0]
        ng.preview("thunder_tiger", "thunder_tiger_blade", clip_, times, yaw=float(os.environ.get("YAW", "0.6")),
                   scale=float(os.environ.get("SCALE", "8")), lift=float(os.environ.get("LIFT", "0")),
                   size=(int(os.environ.get("W", "360")), int(os.environ.get("H", "520"))),
                   out_name=os.environ.get("OUT", "tools/mob_preview.png"))
        return
    ms = build_all()
    M = ms["thunder_tiger"]
    M.finalize()
    if args and args[0] == "--check":
        check(M)
        return
    print("кубов:", {k: len(v.cubes()) for k, v in ms.items()})
    export(M)
    write_timings()


if __name__ == "__main__":
    main()
