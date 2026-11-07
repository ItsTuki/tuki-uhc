package cl.itstuki.uhc.config;

import org.bukkit.Material;

/** Cómo se forman los equipos. */
public enum TeamMode {
    /** Sin equipos: cada jugador juega solo. */
    SOLO(Material.PLAYER_HEAD),
    /** El organizador crea los equipos desde el menú o con /uhcteam. */
    MANUAL(Material.CRAFTER),
    /** Equipos al azar que se revelan en un episodio. */
    RANDOM(Material.DISPENSER),
    /** "Amor a primera vista": los equipos se forman al juntarse durante la partida. */
    PROXIMITY(Material.POPPY);

    private final Material icon;

    TeamMode(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
