package cl.itstuki.uhc.hook;

import org.bukkit.entity.Player;

/** Prefijos y rangos de los jugadores (TukiCore o LuckPerms). Sin ninguno, todo queda vacío. */
public interface RankProvider {
    RankProvider NONE = new RankProvider() {
    };

    /** Prefijo del rango en formato legacy (&a, &#RRGGBB) o vacío. */
    default String prefix(Player player) {
        return "";
    }

    default String suffix(Player player) {
        return "";
    }

    /** Grupo principal del jugador, o vacío. */
    default String group(Player player) {
        return "";
    }

    default boolean available() {
        return false;
    }
}
