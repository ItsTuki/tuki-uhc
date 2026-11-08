package cl.itstuki.uhc.config;

import org.bukkit.Material;

/**
 * Opciones activables de la partida. Cada una tiene un valor numérico opcional
 * (episodio, porcentaje, segundos, bloques). Para episodios, 0 significa "el último".
 */
public enum Feature {
    TEAMMATE_COMPASS(Material.COMPASS, ValueType.EPISODE, true, 2),
    TEAMMATE_TELEPORT(Material.ENDER_PEARL, ValueType.NONE, true, 0),
    RESURRECTION(Material.END_CRYSTAL, ValueType.NONE, false, 0),
    ESCORT(Material.LEAD, ValueType.BLOCKS, false, 100),
    EXTRA_EPISODE(Material.CLOCK, ValueType.NONE, false, 0),
    REST(Material.RED_BED, ValueType.SECONDS, false, 5),
    PLAYER_LOCATOR(Material.RECOVERY_COMPASS, ValueType.EPISODE, true, 0),
    SKIN_ROTATION(Material.ARMOR_STAND, ValueType.NONE, false, 0),
    HEAD_STEAL_ROD(Material.FISHING_ROD, ValueType.NONE, false, 0),
    TRIDENTS(Material.TRIDENT, ValueType.PERCENT, false, 50),
    OUTPOST_DIAMONDS(Material.CROSSBOW, ValueType.PERCENT, false, 50),
    CAMP_LOOT(Material.CAMPFIRE, ValueType.PERCENT, false, 50),
    ANCIENT_CITY_CAP(Material.SCULK_SHRIEKER, ValueType.NONE, true, 0),
    KNIGHTS_PACT(Material.SHIELD, ValueType.EPISODE, false, 4),
    SHULKER_BOX(Material.SHULKER_BOX, ValueType.EPISODE, true, 4),
    LIFE_BOOST(Material.TOTEM_OF_UNDYING, ValueType.EPISODE, true, 0),
    COPPER_AGE(Material.COPPER_GOLEM_STATUE, ValueType.NONE, false, 0),
    GRAVES(Material.SKELETON_SKULL, ValueType.EPISODE, false, 4),
    SUPER_APPLES(Material.GOLDEN_APPLE, ValueType.NONE, false, 0),
    DRAGON_BREATH(Material.DRAGON_BREATH, ValueType.NONE, false, 0),
    ORE_BOOST(Material.DIAMOND_ORE, ValueType.BOOST, false, 100),
    BIG_CAVES(Material.POINTED_DRIPSTONE, ValueType.NONE, false, 0);

    public enum ValueType { NONE, EPISODE, PERCENT, SECONDS, BLOCKS, BOOST }

    private final Material icon;
    private final ValueType valueType;
    private final boolean defaultEnabled;
    private final int defaultValue;

    Feature(Material icon, ValueType valueType, boolean defaultEnabled, int defaultValue) {
        this.icon = icon;
        this.valueType = valueType;
        this.defaultEnabled = defaultEnabled;
        this.defaultValue = defaultValue;
    }

    public Material icon() {
        return icon;
    }

    public ValueType valueType() {
        return valueType;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public int defaultValue() {
        return defaultValue;
    }

    /** Clave en config.yml y en los archivos de idioma (ej. teammate-compass). */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public int clampValue(int value) {
        return switch (valueType) {
            case NONE -> 0;
            case EPISODE -> Math.max(0, Math.min(99, value));
            case PERCENT -> Math.max(0, Math.min(100, value));
            case SECONDS -> Math.max(1, Math.min(600, value));
            case BLOCKS -> Math.max(5, Math.min(5000, value));
            case BOOST -> Math.max(0, Math.min(400, value));
        };
    }
}
