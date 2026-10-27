"""
Оружие — отдельной моделью (как молот Набата и мечи кровожада) для мобов, сделанных генераторами mobgen.

Берёт поддерево костей оружия из модели моба (корень — кость оружия, например «lance» или «bow»), и:
  * пишет отдельную модель оружия geo/<оружие>.geo.json (кости сдвинуты так, что кость оружия — в начале
    координат; UV те же, что у моба, — оружие рисуется текстурой моба);
  * в модели моба кость оружия остаётся пустой (по ней считается поворот), её дочерние кости убираются;
  * дорожка поворота оружия animations/<оружие>.weapon.json — кватернион кости оружия в системе модели
    по каждому кадру каждого клипа (смешивание кватернионами — оружие не «прокручивается» на переходах);
  * анимации дочерних костей оружия (тетива, стрела, вымпел…) — в animations/<оружие>.animation.json
    (те же имена клипов), в анимации моба их больше нет.

Клиент: WeaponTrack / WeaponFeature / WeaponHolder (client/entity/nabat), подсветка и текстура — моба.
"""
import copy
import json
import os

from mobgen import Model, write_anims


def quat(R_):
    tr_ = R_[0][0] + R_[1][1] + R_[2][2]
    if tr_ > 0:
        k = (tr_ + 1.0) ** 0.5 * 2
        w, x, y, z = 0.25 * k, (R_[2][1] - R_[1][2]) / k, (R_[0][2] - R_[2][0]) / k, (R_[1][0] - R_[0][1]) / k
    elif R_[0][0] > R_[1][1] and R_[0][0] > R_[2][2]:
        k = (1.0 + R_[0][0] - R_[1][1] - R_[2][2]) ** 0.5 * 2
        w, x, y, z = (R_[2][1] - R_[1][2]) / k, 0.25 * k, (R_[0][1] + R_[1][0]) / k, (R_[0][2] + R_[2][0]) / k
    elif R_[1][1] > R_[2][2]:
        k = (1.0 + R_[1][1] - R_[0][0] - R_[2][2]) ** 0.5 * 2
        w, x, y, z = (R_[0][2] - R_[2][0]) / k, (R_[0][1] + R_[1][0]) / k, 0.25 * k, (R_[1][2] + R_[2][1]) / k
    else:
        k = (1.0 + R_[2][2] - R_[0][0] - R_[1][1]) ** 0.5 * 2
        w, x, y, z = (R_[1][0] - R_[0][1]) / k, (R_[0][2] + R_[2][0]) / k, (R_[1][2] + R_[2][1]) / k, 0.25 * k
    return [x, y, z, w]


def subtree(M, root):
    names = [root]
    grown = True
    while grown:
        grown = False
        for b in M.bones:
            if b.get("parent") in names and b["name"] not in names:
                names.append(b["name"])
                grown = True
    return names


def split_model(M, root_bone, weapon_name):
    """→ (модель моба без оружия, модель оружия, кость руки, сдвиг от руки (Java px), имена дочерних костей)."""
    names = subtree(M, root_bone)
    rp = M.by_name[root_bone]["pivot"]
    W = Model(weapon_name, M.tex_w, M.tex_h)
    for b in M.bones:
        if b["name"] not in names:
            continue
        parent = None if b["name"] == root_bone else b.get("parent")
        nb = W.bone(b["name"], parent, [b["pivot"][i] - rp[i] for i in range(3)])
        for c in b["cubes"]:
            nc = copy.deepcopy(c)
            nc["origin"] = [round(c["origin"][i] - rp[i], 4) for i in range(3)]
            if "pivot" in c:
                nc["pivot"] = [round(c["pivot"][i] - rp[i], 4) for i in range(3)]
            nb["cubes"].append(nc)
    main = copy.copy(M)
    main.bones = []
    for b in M.bones:
        if b["name"] in names[1:]:
            continue
        nb = dict(b)
        if b["name"] == root_bone:
            nb["cubes"] = []
        main.bones.append(nb)
    hand = M.by_name[root_bone]["parent"]
    hp = M.by_name[hand]["pivot"]
    off = [round(rp[0] - hp[0], 4), round(-(rp[1] - hp[1]), 4), round(rp[2] - hp[2], 4)]
    return main, W, hand, off, names[1:]


def track(M, fn, length_s, fps, loop, bone):
    """Кватернион кости оружия в системе модели по кадрам (как bake: шаг 1/fps), знак — непрерывный."""
    n = int(round(length_s * fps))
    qs, prev = [], None
    for k in range(n + 1):
        t = min(k / fps, length_s)
        q = quat(M.chain_R(bone, fn(t)))
        if prev is not None and sum(q[i] * prev[i] for i in range(4)) < 0:
            q = [-v for v in q]
        prev = q
        qs.append([round(v, 5) for v in q])
    return {"fps": fps, "length": length_s, "loop": loop, "q": qs}


def export(root, M, anims, clips, root_bone, weapon_name):
    """clips — [(короткое имя, fn, длина, fps, цикл)]; anims — запечённые анимации моба (их правим на месте).
    Пишет geo моба и оружия, дорожку и анимацию оружия; возвращает отфильтрованные анимации моба."""
    main, W, hand, off, subs = split_model(M, root_bone, weapon_name)
    main.write_geo(root)
    W.write_geo(root)
    tracks = {name: track(M, fn, ln, fps, loop, root_bone) for name, fn, ln, fps, loop in clips}
    with open(os.path.join(root, "animations", weapon_name + ".weapon.json"), "w", encoding="utf-8") as f:
        json.dump({"weapon": weapon_name, "hand": hand, "hand_offset": off, "grip_offset": [0.0, 0.0, 0.0],
                   "clips": tracks}, f, separators=(",", ":"))
    main_anims, weap = {}, {}
    for key, a in anims.items():
        clip = key.split(".")[-1]
        bones = a.get("bones", {})
        wb = {n: bones[n] for n in subs if n in bones}
        mb = {n: v for n, v in bones.items() if n not in subs and n != root_bone}
        na = dict(a)
        na["bones"] = mb
        main_anims[key] = na
        if wb:
            wa = dict(a)
            wa["bones"] = wb
            weap["animation.%s.%s" % (weapon_name, clip)] = wa
    if weap:
        write_anims(root, weapon_name, weap)
    else:
        p = os.path.join(root, "animations", weapon_name + ".animation.json")
        if os.path.exists(p):
            os.remove(p)
    return main_anims
