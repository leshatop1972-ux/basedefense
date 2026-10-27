"""
Дописывает в анимации железного зомби (модель и файл анимаций — от пользователя, Blockbench) два клипа
«Рывка‑хвата», не трогая остальные:
  lunge — замах (присел, руки разведены назад), рывок вперёд с вытянутыми руками; если промах — спотыкается
          и выпрямляется (длина 1.5 с);
  grab  — играет с момента хвата: руки смыкаются, 1 с удержания (рывки‑дрожь), отпускает, замах ножом
          и удар (на 1.35 с — серверный тик 27), возврат (длина 1.9 с).
Запуск: python tools/iron_zombie_anims.py   (повторный запуск просто перезаписывает эти два клипа)
Серверные тайминги — в IronZombieEntity (LUNGE_*, GRAB_*).
"""
import json
import os

PATH = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense",
                    "animations", "iron_zombie.animation.json")


def ch(*keys):
    """keys: (время, [x,y,z], easing|None) → канал Bedrock."""
    out = {}
    for t, v, e in keys:
        k = "%g" % round(t, 3)
        out[k] = {"vector": list(v), "easing": e} if e else list(v)
    return out


Z = [0, 0, 0]
EO, EI, EIO = "easeOutQuad", "easeInQuad", "easeInOutQuad"

# --- рывок: 0–0.45 замах, 0.45–0.8 рывок (руки вперёд), 0.8–1.5 промах: спотыкается, выпрямляется
REACH_TORSO = [32, 0, 0]
REACH_POS = [0, -1.2, 2.0]
REACH_R = [-22, 16, 0]          # правая рука вытянута вперёд‑вверх и чуть внутрь
REACH_L = [-22, -16, 0]
REACH_HEAD = [-16, 0, 0]        # голова поднята на цель (корпус наклонён)
REACH_KNIFE = [0, 0, 0]

lunge = {
    "loop": "play_once", "animation_length": 1.5,
    "bones": {
        "torso": {
            "rotation": ch((0, Z, None), (0.38, [-8, 0, 0], EO), (0.45, [-6, 0, 0], None),
                           (0.6, REACH_TORSO, EI), (0.8, REACH_TORSO, None), (1.0, [40, 0, 4], EO),
                           (1.5, Z, EIO)),
            "position": ch((0, Z, None), (0.38, [0, -2.2, -1.4], EO), (0.45, [0, -2.0, -1.2], None),
                           (0.6, REACH_POS, EI), (0.8, REACH_POS, None), (1.0, [0, -1.6, 2.4], EO),
                           (1.5, Z, EIO)),
        },
        "right_arm": {"rotation": ch((0, Z, None), (0.38, [38, -55, -10], EO), (0.45, [36, -58, -10], None),
                                     (0.6, REACH_R, EI), (0.8, REACH_R, None), (1.0, [10, 8, 0], EO), (1.5, Z, EIO))},
        "left_arm": {"rotation": ch((0, Z, None), (0.38, [38, 55, 10], EO), (0.45, [36, 58, 10], None),
                                    (0.6, REACH_L, EI), (0.8, REACH_L, None), (1.0, [10, -8, 0], EO), (1.5, Z, EIO))},
        "knife": {"rotation": ch((0, Z, None), (0.38, [0, 0, -30], EO), (0.6, REACH_KNIFE, EI),
                                 (0.8, REACH_KNIFE, None), (1.5, Z, EIO))},
        "head": {"rotation": ch((0, Z, None), (0.38, [14, 0, 0], EO), (0.6, REACH_HEAD, EI),
                                (0.8, REACH_HEAD, None), (1.0, [-6, 10, 0], EO), (1.5, Z, EIO))},
        "right_leg": {"rotation": ch((0, Z, None), (0.38, [-28, 0, 0], EO), (0.6, [30, 0, 0], EI),
                                     (0.8, [24, 0, 0], None), (1.0, [-10, 0, 0], EO), (1.5, Z, EIO))},
        "left_leg": {"rotation": ch((0, Z, None), (0.38, [18, 0, 0], EO), (0.6, [-38, 0, 0], EI),
                                    (0.8, [-30, 0, 0], None), (1.0, [8, 0, 0], EO), (1.5, Z, EIO))},
        "pauldron_right": {"rotation": ch((0, Z, None), (0.38, [0, 0, 10], EO), (0.6, [0, 0, -6], EI), (1.5, Z, EIO))},
        "pauldron_left": {"rotation": ch((0, Z, None), (0.38, [0, 0, -10], EO), (0.6, [0, 0, 6], EI), (1.5, Z, EIO))},
        "cape": {"rotation": ch((0, [6, 0, 0], None), (0.38, [-2, 0, 0], EO), (0.6, [30, 0, 0], EI),
                                (0.8, [34, 0, 0], None), (1.1, [20, 0, 0], EO), (1.5, [6, 0, 0], EIO))},
        "tabard": {"rotation": ch((0, Z, None), (0.38, [-10, 0, 0], EO), (0.6, [24, 0, 0], EI),
                                  (0.8, [26, 0, 0], None), (1.5, Z, EIO))},
    },
}


def shake(t0, t1, base, amp, step=0.1):
    """Рывки удержания: base ± amp по очереди (вырывающуюся жертву удерживают)."""
    keys, t, s = [], t0, 1
    while t < t1 - 1e-6:
        keys.append((t, [base[i] + amp[i] * s for i in range(3)], EIO))
        s = -s
        t += step
    return keys


HOLD_TORSO = [26, 0, 0]
HOLD_POS = [0, -1.0, 1.6]
HOLD_R = [-8, 34, 0]            # руки сомкнулись на жертве
HOLD_L = [-8, -34, 0]

grab = {
    "loop": "play_once", "animation_length": 1.9,
    "bones": {
        "torso": {
            "rotation": ch((0, REACH_TORSO, None), (0.15, HOLD_TORSO, EO),
                           *shake(0.25, 0.95, HOLD_TORSO, [3, 0, 4]),
                           (1.0, HOLD_TORSO, EIO), (1.25, [-6, -24, -4], EO), (1.38, [16, 28, 5], EI),
                           (1.48, [17, 30, 5], None), (1.9, Z, EIO)),
            "position": ch((0, REACH_POS, None), (0.15, HOLD_POS, EO), (1.0, HOLD_POS, None),
                           (1.25, [0, -0.4, -0.6], EO), (1.38, [0, -0.6, 1.6], EI), (1.9, Z, EIO)),
        },
        "right_arm": {"rotation": ch((0, REACH_R, None), (0.15, HOLD_R, EO),
                                     *shake(0.25, 0.95, HOLD_R, [4, 3, 0]),
                                     (1.0, HOLD_R, EIO), (1.25, [-12, -46, -4], EO), (1.38, [6, 44, 4], EI),
                                     (1.48, [4, 52, 4], None), (1.9, Z, EIO))},
        "left_arm": {"rotation": ch((0, REACH_L, None), (0.15, HOLD_L, EO),
                                    *shake(0.25, 0.95, HOLD_L, [-4, -3, 0]),
                                    (1.0, HOLD_L, EIO), (1.25, [4, 10, 0], EO), (1.38, [10, -22, 0], EI),
                                    (1.9, Z, EIO))},
        "knife": {"rotation": ch((0, REACH_KNIFE, None), (0.15, [0, 0, -6], EO), (1.0, [0, 0, -6], None),
                                 (1.25, [0, 0, -26], EO), (1.38, [0, 0, 60], EI), (1.48, [0, 0, 70], None),
                                 (1.9, Z, EIO))},
        "head": {"rotation": ch((0, REACH_HEAD, None), (0.15, [-10, 0, 0], EO),
                                *shake(0.25, 0.95, [-10, 0, 0], [0, 6, 3], 0.14),
                                (1.0, [-8, 0, 0], EIO), (1.25, [-2, 14, 0], EO), (1.38, [8, -14, 0], EI),
                                (1.9, Z, EIO))},
        "right_leg": {"rotation": ch((0, [24, 0, 0], None), (0.2, [12, 0, 0], EO), (1.0, [12, 0, 0], None),
                                     (1.25, [4, 0, 0], EO), (1.9, Z, EIO))},
        "left_leg": {"rotation": ch((0, [-30, 0, 0], None), (0.2, [-18, 0, 0], EO), (1.0, [-18, 0, 0], None),
                                    (1.25, [-10, 0, 0], EO), (1.9, Z, EIO))},
        "pauldron_right": {"rotation": ch((0, [0, 0, -6], None), (0.2, [0, 0, 4], EO), (1.25, [0, 0, 6], EO),
                                          (1.38, [0, 0, -8], EI), (1.9, Z, EIO))},
        "pauldron_left": {"rotation": ch((0, [0, 0, 6], None), (0.2, [0, 0, -4], EO), (1.25, [0, 0, -4], EO),
                                         (1.38, [0, 0, 6], EI), (1.9, Z, EIO))},
        "cape": {"rotation": ch((0, [34, 0, 0], None), (0.4, [16, 0, 0], EO), (1.25, [4, 0, -4], EO),
                                (1.38, [36, 0, 8], EI), (1.9, [6, 0, 0], EIO))},
        "tabard": {"rotation": ch((0, [26, 0, 0], None), (0.4, [8, 0, 0], EO), (1.38, [18, 0, 0], EI),
                                  (1.9, Z, EIO))},
    },
}

if __name__ == "__main__":
    with open(PATH, encoding="utf-8") as f:
        data = json.load(f)
    data["animations"]["animation.iron_zombie.lunge"] = lunge
    data["animations"]["animation.iron_zombie.grab"] = grab
    with open(PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("клипы lunge и grab записаны")
