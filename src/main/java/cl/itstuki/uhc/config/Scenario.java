package cl.itstuki.uhc.config;

import java.util.Locale;
import org.bukkit.Material;

/** Escenarios clásicos de UHC: reglas extra que se combinan entre sí. */
public enum Scenario {
    CUT_CLEAN(Material.IRON_INGOT),
    TIMBER(Material.OAK_LOG),
    HASTEY_BOYS(Material.DIAMOND_PICKAXE),
    DOUBLE_ORES(Material.RAW_GOLD),
    TRIPLE_ORES(Material.RAW_GOLD_BLOCK),
    LUCKY_LEAVES(Material.OAK_LEAVES),
    FIRELESS(Material.FIRE_CHARGE),
    NO_FALL(Material.FEATHER),
    NO_CLEAN(Material.TOTEM_OF_UNDYING),
    BLOOD_DIAMONDS(Material.REDSTONE),
    DIAMONDLESS(Material.DIAMOND_ORE),
    GOLDLESS(Material.GOLD_ORE),
    BOWLESS(Material.BOW),
    RODLESS(Material.FISHING_ROD),
    HORSELESS(Material.SADDLE);

    private final Material icon;

    Scenario(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    /** Clave en config.yml y en los idiomas (ej. cut-clean). */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
