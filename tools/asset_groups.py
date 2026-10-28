"""
По какой папке лежат файлы моба/оружия: geo/<папка>/, animations/<папка>/, textures/entity/<папка>/.
То же правило — в Java (client/entity/bedrock/AssetGroups). Новые мобы — добавить строку в GROUPS
(и в AssetGroups.java).
"""
import os

# (начало имени файла, папка) — первое совпадение
GROUPS = [
    ("iron_zombie", "iron_zombie"),
    ("iron_knight", "iron_knight"),
    ("frost_ar", "frost_archer"),          # frost_archer, frost_archer_bow, frost_arrow
    ("tattered_lancer", "lancer"),
    ("falling_lance", "lancer"),
    ("bloodthirster", "bloodthirster"),     # и его меч bloodthirster_sword
    ("bloodling", "bloodthirster"),
    ("blood_egg", "bloodthirster"),
    ("bloodletter", "bloodletter"),
    ("blood_entity", "horror"),
    ("mist_entity", "horror"),
    ("blood_greatsword", "weapon"),         # меч кровожада (предмет игрока)
    ("katana", "weapon"),
    ("curb", "curb"),
    ("nabat", "nabat"),
    ("sapper", "sapper"),
    ("siege_beetle", "beetle"),
    ("beetle", "beetle"),
]

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "basedefense")


def group(name):
    base = os.path.basename(name)
    for prefix, folder in GROUPS:
        if base.startswith(prefix):
            return folder
    raise KeyError("неизвестно, в какую папку класть " + name + " — добавь в tools/asset_groups.py и AssetGroups.java")


def path(kind, filename, root=None):
    """kind: "geo", "animations" или "textures/entity"; filename — имя файла с расширением.
    Папка создаётся, если её нет."""
    d = os.path.join(root or ASSETS, *kind.split("/"), group(filename))
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, filename)


def tex(filename, root=None):
    return path("textures/entity", filename, root)


def geo(filename, root=None):
    return path("geo", filename, root)


def anim(filename, root=None):
    return path("animations", filename, root)
