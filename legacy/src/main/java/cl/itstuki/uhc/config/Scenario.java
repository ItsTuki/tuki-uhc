package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/** Escenarios de UHC: reglas extra que se combinan entre sí, agrupadas por categoría en el menú. */
public enum Scenario {
    // Minería
    CUT_CLEAN(Category.MINING, XMaterial.IRON_INGOT),
    TIMBER(Category.MINING, XMaterial.OAK_LOG),
    VEIN_MINER(Category.MINING, XMaterial.IRON_PICKAXE),
    DOUBLE_ORES(Category.MINING, XMaterial.GOLD_INGOT),
    TRIPLE_ORES(Category.MINING, XMaterial.GOLD_BLOCK),
    DOUBLE_XP(Category.MINING, XMaterial.EXPERIENCE_BOTTLE),
    TRIPLE_XP(Category.MINING, XMaterial.ENCHANTING_TABLE),
    HASTEY_BOYS(Category.MINING, XMaterial.DIAMOND_PICKAXE),
    // Restricciones
    BAREBONES(Category.RESTRICTIONS, XMaterial.BONE),
    DIAMONDLESS(Category.RESTRICTIONS, XMaterial.DIAMOND_ORE),
    GOLDLESS(Category.RESTRICTIONS, XMaterial.GOLD_ORE),
    BOWLESS(Category.RESTRICTIONS, XMaterial.BOW),
    SWORDLESS(Category.RESTRICTIONS, XMaterial.IRON_SWORD),
    NO_ENCHANTS(Category.RESTRICTIONS, XMaterial.BOOK),
    RODLESS(Category.RESTRICTIONS, XMaterial.FISHING_ROD),
    HORSELESS(Category.RESTRICTIONS, XMaterial.SADDLE),
    // Suerte
    LUCKY_LEAVES(Category.LUCK, XMaterial.OAK_LEAVES),
    LUCKY_BOOK(Category.LUCK, XMaterial.ENCHANTED_BOOK),
    BLOOD_DIAMONDS(Category.LUCK, XMaterial.REDSTONE),
    BLOOD_ENCHANT(Category.LUCK, XMaterial.REDSTONE_BLOCK),
    // Acción
    TIME_BOMB(Category.ACTION, XMaterial.TNT),
    AIR_DROPS(Category.ACTION, XMaterial.CHEST),
    RADAR(Category.ACTION, XMaterial.COMPASS),
    RUSH(Category.ACTION, XMaterial.SUGAR),
    RED_VS_BLUE(Category.ACTION, XMaterial.RED_BANNER),
    FINAL_UHC(Category.ACTION, XMaterial.GLISTERING_MELON_SLICE),
    PYRO(Category.ACTION, XMaterial.BLAZE_POWDER),
    SUPERHEROES(Category.ACTION, XMaterial.NETHER_STAR),
    // Más
    ANONYMOUS(Category.MORE, XMaterial.NAME_TAG),
    WEB_CAGE(Category.MORE, XMaterial.COBWEB),
    SAFE_LOOT(Category.MORE, XMaterial.TRIPWIRE_HOOK),
    SIPHON(Category.MORE, XMaterial.GHAST_TEAR),
    NO_FALL(Category.MORE, XMaterial.FEATHER),
    FIRELESS(Category.MORE, XMaterial.FIRE_CHARGE),
    NO_CLEAN(Category.MORE, XMaterial.TOTEM_OF_UNDYING);

    public enum Category {
        MINING(XMaterial.DIAMOND_PICKAXE),
        RESTRICTIONS(XMaterial.BARRIER),
        LUCK(XMaterial.RABBIT_FOOT),
        ACTION(XMaterial.TNT),
        MORE(XMaterial.ENDER_EYE);

        private final XMaterial icon;

        Category(XMaterial icon) {
            this.icon = icon;
        }

        public ItemStack icon() {
            return Compat.firstItem(icon, XMaterial.PAPER);
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Category category;
    private final XMaterial icon;

    Scenario(Category category, XMaterial icon) {
        this.category = category;
        this.icon = icon;
    }

    public Category category() {
        return category;
    }

    public ItemStack icon() {
        return Compat.firstItem(icon, XMaterial.GOLDEN_APPLE, XMaterial.PAPER);
    }

    /** Clave en config.yml y en los idiomas (ej. cut-clean). */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
