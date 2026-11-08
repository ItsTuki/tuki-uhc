package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/**
 * Opciones activables de la partida. Cada una tiene un valor numérico opcional
 * (episodio, porcentaje, segundos, bloques). Para episodios, 0 significa "el último".
 * {@code since} es la versión menor de Minecraft desde la que existe (las demás se ocultan).
 */
public enum Feature {
    TEAMMATE_COMPASS(XMaterial.COMPASS, null, ValueType.EPISODE, true, 2, 8),
    TEAMMATE_TELEPORT(XMaterial.ENDER_PEARL, null, ValueType.NONE, true, 0, 8),
    RESURRECTION(XMaterial.END_CRYSTAL, XMaterial.NETHER_STAR, ValueType.NONE, false, 0, 8),
    ESCORT(XMaterial.LEAD, null, ValueType.BLOCKS, false, 100, 8),
    EXTRA_EPISODE(XMaterial.CLOCK, null, ValueType.NONE, false, 0, 8),
    REST(XMaterial.RED_BED, null, ValueType.SECONDS, false, 5, 8),
    PLAYER_LOCATOR(XMaterial.RECOVERY_COMPASS, null, ValueType.EPISODE, true, 0, 99),
    SKIN_ROTATION(XMaterial.ARMOR_STAND, null, ValueType.NONE, false, 0, 8),
    HEAD_STEAL_ROD(XMaterial.FISHING_ROD, null, ValueType.NONE, false, 0, 8),
    TRIDENTS(XMaterial.TRIDENT, null, ValueType.PERCENT, false, 50, 13),
    OUTPOST_DIAMONDS(XMaterial.CROSSBOW, null, ValueType.PERCENT, false, 50, 99),
    CAMP_LOOT(XMaterial.CAMPFIRE, null, ValueType.PERCENT, false, 50, 99),
    ANCIENT_CITY_CAP(XMaterial.SCULK_SHRIEKER, null, ValueType.NONE, true, 0, 99),
    KNIGHTS_PACT(XMaterial.SHIELD, XMaterial.IRON_CHESTPLATE, ValueType.EPISODE, false, 4, 8),
    SHULKER_BOX(XMaterial.SHULKER_BOX, null, ValueType.EPISODE, true, 4, 11),
    LIFE_BOOST(XMaterial.TOTEM_OF_UNDYING, XMaterial.GOLDEN_APPLE, ValueType.EPISODE, true, 0, 8),
    COPPER_AGE(XMaterial.COPPER_BLOCK, null, ValueType.NONE, false, 0, 99),
    GRAVES(XMaterial.SKELETON_SKULL, null, ValueType.EPISODE, false, 4, 8),
    SUPER_APPLES(XMaterial.GOLDEN_APPLE, null, ValueType.NONE, false, 0, 8),
    DRAGON_BREATH(XMaterial.DRAGON_BREATH, null, ValueType.NONE, false, 0, 9),
    ORE_BOOST(XMaterial.DIAMOND_ORE, null, ValueType.BOOST, false, 100, 8),
    BIG_CAVES(XMaterial.POINTED_DRIPSTONE, XMaterial.COBBLESTONE, ValueType.NONE, false, 0, 8);

    public enum ValueType { NONE, EPISODE, PERCENT, SECONDS, BLOCKS, BOOST }

    private final XMaterial icon;
    private final XMaterial fallback;
    private final ValueType valueType;
    private final boolean defaultEnabled;
    private final int defaultValue;
    private final int since;

    Feature(XMaterial icon, XMaterial fallback, ValueType valueType, boolean defaultEnabled, int defaultValue, int since) {
        this.icon = icon;
        this.fallback = fallback;
        this.valueType = valueType;
        this.defaultEnabled = defaultEnabled;
        this.defaultValue = defaultValue;
        this.since = since;
    }

    public ItemStack icon() {
        return fallback == null ? Compat.firstItem(icon, XMaterial.PAPER) : Compat.firstItem(icon, fallback);
    }

    /** Si existe en la versión del servidor. */
    public boolean supported() {
        return Compat.atLeast(since);
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
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public int clampValue(int value) {
        switch (valueType) {
            case NONE:
                return 0;
            case EPISODE:
                return Math.max(0, Math.min(99, value));
            case PERCENT:
                return Math.max(0, Math.min(100, value));
            case SECONDS:
                return Math.max(1, Math.min(600, value));
            case BOOST:
                return Math.max(0, Math.min(400, value));
            default:
                return Math.max(5, Math.min(5000, value));
        }
    }
}
