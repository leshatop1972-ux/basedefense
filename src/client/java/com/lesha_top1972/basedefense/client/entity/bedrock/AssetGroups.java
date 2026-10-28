package com.lesha_top1972.basedefense.client.entity.bedrock;

/**
 * В какой папке лежат модель, анимации и текстуры моба/оружия: geo/&lt;папка&gt;/, animations/&lt;папка&gt;/,
 * textures/entity/&lt;папка&gt;/. То же правило — tools/asset_groups.py (новые мобы — добавить в оба места).
 */
public final class AssetGroups {

    /** {начало имени файла, папка} — первое совпадение. */
    private static final String[][] GROUPS = {
            {"iron_zombie", "iron_zombie"},
            {"iron_knight", "iron_knight"},
            {"frost_ar", "frost_archer"},
            {"tattered_lancer", "lancer"},
            {"falling_lance", "lancer"},
            {"bloodthirster", "bloodthirster"},
            {"bloodling", "bloodthirster"},
            {"blood_egg", "bloodthirster"},
            {"bloodletter", "bloodletter"},
            {"blood_entity", "horror"},
            {"mist_entity", "horror"},
            {"blood_greatsword", "weapon"},
            {"katana", "weapon"},
            {"curb", "curb"},
            {"nabat", "nabat"},
            {"sapper", "sapper"},
            {"siege_beetle", "beetle"},
            {"beetle", "beetle"},
    };

    private AssetGroups() {
    }

    public static String of(String name) {
        for (String[] g : GROUPS) {
            if (name.startsWith(g[0])) {
                return g[1];
            }
        }
        throw new IllegalArgumentException("Неизвестная папка для " + name + " — добавь в AssetGroups");
    }

    /** "geo/&lt;папка&gt;/name.geo.json" и т.п. */
    public static String path(String kind, String name, String ext) {
        return kind + "/" + of(name) + "/" + name + ext;
    }

    /** Путь текстуры моба: "textures/entity/&lt;папка&gt;/file". */
    public static String texture(String file) {
        return "textures/entity/" + of(file) + "/" + file;
    }
}
