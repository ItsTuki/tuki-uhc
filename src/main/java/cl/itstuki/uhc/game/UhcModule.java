package cl.itstuki.uhc.game;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jspecify.annotations.Nullable;

/** Parte del juego que reacciona a los momentos de la partida. Todos los métodos son opcionales. */
public interface UhcModule extends Listener {
    /** Tras /uhcprepare, antes de repartir a los jugadores. */
    default void onPrepare() {
    }

    /** Cuando termina la cuenta atrás y comienza la partida. */
    default void onStart() {
    }

    /** Al empezar cada episodio (1, 2, 3…). */
    default void onEpisodeStart(int episode) {
    }

    /** Cada segundo de partida, con los segundos que faltan para el próximo episodio. */
    default void onSecond(int secondsToNextEpisode) {
    }

    /** Partida por tiempo: se acabó el tiempo y empieza la muerte súbita. */
    default void onSuddenDeath() {
    }

    /** Un jugador quedó eliminado. */
    default void onEliminate(UUID player, @Nullable Player killer) {
    }

    /** La partida terminó (con o sin ganador). */
    default void onEnd() {
    }

    /** /sos o /uhcstop: dejar todo como antes de la partida. */
    default void onReset() {
    }
}
