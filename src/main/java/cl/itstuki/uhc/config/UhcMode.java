package cl.itstuki.uhc.config;

import org.bukkit.Material;

/** Modo de juego de la partida. */
public enum UhcMode {
    /** Equipos contra equipos; gana el último equipo con vida. */
    PVP(Material.IRON_SWORD),
    /** Todos contra todos, sin equipos. */
    SOLITARIOS(Material.WOODEN_SWORD),
    /** Gana quien mate a la dragona (un jugador o por equipos). */
    DRAGON(Material.DRAGON_HEAD),
    /** Equipos de 3 con roles secretos: Troll, Rata y Normal. */
    TROLLS_RATAS(Material.SPIDER_EYE),
    /** Conseguir una lista de objetos antes que los demás. */
    RESOURCE_RUSH(Material.CHEST);

    private final Material icon;

    UhcMode(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
