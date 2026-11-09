package cl.itstuki.uhc.config;

import java.util.Locale;
import org.bukkit.Material;

/** Escenarios de UHC: reglas extra que se combinan entre sí, agrupadas por categoría en el menú. */
public enum Scenario {
    // ⛏ Minería
    CUT_CLEAN(Category.MINING, Material.IRON_INGOT),
    TIMBER(Category.MINING, Material.OAK_LOG),
    VEIN_MINER(Category.MINING, Material.IRON_PICKAXE),
    DOUBLE_ORES(Category.MINING, Material.RAW_GOLD),
    TRIPLE_ORES(Category.MINING, Material.RAW_GOLD_BLOCK),
    DOUBLE_XP(Category.MINING, Material.EXPERIENCE_BOTTLE),
    TRIPLE_XP(Category.MINING, Material.ENCHANTING_TABLE),
    HASTEY_BOYS(Category.MINING, Material.DIAMOND_PICKAXE),
    // 🚫 Restricciones
    BAREBONES(Category.RESTRICTIONS, Material.BONE),
    DIAMONDLESS(Category.RESTRICTIONS, Material.DIAMOND_ORE),
    GOLDLESS(Category.RESTRICTIONS, Material.GOLD_ORE),
    BOWLESS(Category.RESTRICTIONS, Material.BOW),
    SWORDLESS(Category.RESTRICTIONS, Material.IRON_SWORD),
    NO_ENCHANTS(Category.RESTRICTIONS, Material.BOOK),
    RODLESS(Category.RESTRICTIONS, Material.FISHING_ROD),
    HORSELESS(Category.RESTRICTIONS, Material.SADDLE),
    // 🍀 Suerte
    LUCKY_LEAVES(Category.LUCK, Material.OAK_LEAVES),
    LUCKY_BOOK(Category.LUCK, Material.ENCHANTED_BOOK),
    BLOOD_DIAMONDS(Category.LUCK, Material.REDSTONE),
    BLOOD_ENCHANT(Category.LUCK, Material.REDSTONE_BLOCK),
    // 💥 Acción
    TIME_BOMB(Category.ACTION, Material.TNT),
    AIR_DROPS(Category.ACTION, Material.CHEST),
    RADAR(Category.ACTION, Material.COMPASS),
    RUSH(Category.ACTION, Material.SUGAR),
    RED_VS_BLUE(Category.ACTION, Material.RED_BANNER),
    FINAL_UHC(Category.ACTION, Material.GLISTERING_MELON_SLICE),
    PYRO(Category.ACTION, Material.BLAZE_POWDER),
    SUPERHEROES(Category.ACTION, Material.NETHER_STAR),
    // 🕶 Más
    ANONYMOUS(Category.MORE, Material.NAME_TAG),
    WEB_CAGE(Category.MORE, Material.COBWEB),
    SAFE_LOOT(Category.MORE, Material.TRIPWIRE_HOOK),
    SIPHON(Category.MORE, Material.GHAST_TEAR),
    NO_FALL(Category.MORE, Material.FEATHER),
    FIRELESS(Category.MORE, Material.FIRE_CHARGE),
    NO_CLEAN(Category.MORE, Material.TOTEM_OF_UNDYING);

    public enum Category {
        MINING(Material.DIAMOND_PICKAXE),
        RESTRICTIONS(Material.BARRIER),
        LUCK(Material.RABBIT_FOOT),
        ACTION(Material.TNT),
        MORE(Material.ENDER_EYE);

        private final Material icon;

        Category(Material icon) {
            this.icon = icon;
        }

        public Material icon() {
            return icon;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Category category;
    private final Material icon;

    Scenario(Category category, Material icon) {
        this.category = category;
        this.icon = icon;
    }

    public Category category() {
        return category;
    }

    public Material icon() {
        return icon;
    }

    /** Clave en config.yml y en los idiomas (ej. cut-clean). */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
