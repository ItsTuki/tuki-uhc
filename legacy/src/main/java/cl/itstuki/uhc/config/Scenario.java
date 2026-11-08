package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/** Escenarios clásicos de UHC: reglas extra que se combinan entre sí. */
public enum Scenario {
    CUT_CLEAN(XMaterial.IRON_INGOT, null),
    TIMBER(XMaterial.OAK_LOG, null),
    HASTEY_BOYS(XMaterial.DIAMOND_PICKAXE, null),
    DOUBLE_ORES(XMaterial.GOLD_INGOT, null),
    TRIPLE_ORES(XMaterial.GOLD_BLOCK, null),
    LUCKY_LEAVES(XMaterial.OAK_LEAVES, null),
    FIRELESS(XMaterial.FIRE_CHARGE, null),
    NO_FALL(XMaterial.FEATHER, null),
    NO_CLEAN(XMaterial.TOTEM_OF_UNDYING, XMaterial.GOLDEN_APPLE),
    BLOOD_DIAMONDS(XMaterial.REDSTONE, null),
    DIAMONDLESS(XMaterial.DIAMOND_ORE, null),
    GOLDLESS(XMaterial.GOLD_ORE, null),
    BOWLESS(XMaterial.BOW, null),
    RODLESS(XMaterial.FISHING_ROD, null),
    HORSELESS(XMaterial.SADDLE, null);

    private final XMaterial icon;
    private final XMaterial fallback;

    Scenario(XMaterial icon, XMaterial fallback) {
        this.icon = icon;
        this.fallback = fallback;
    }

    public ItemStack icon() {
        return fallback == null ? Compat.firstItem(icon, XMaterial.PAPER) : Compat.firstItem(icon, fallback);
    }

    /** Clave en config.yml y en los idiomas (ej. cut-clean). */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
