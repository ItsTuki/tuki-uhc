package cl.itstuki.uhc.config;

import java.util.Locale;
import org.bukkit.Material;

/**
 * Cómo se mide la partida.
 * EPISODES: partes con título, aviso de fin y descanso opcional; el borde se cierra en una parte.
 * TIMED: una duración total (ej. 2 horas) sin cortes; el borde se cierra de forma continua hasta el final.
 */
public enum MatchFormat {
    EPISODES(Material.BOOKSHELF),
    TIMED(Material.CLOCK);

    private final Material icon;

    MatchFormat(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
