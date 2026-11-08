package cl.itstuki.uhc.game;

public enum GameState {
    /** Antes de /uhcprepare. */
    LOBBY,
    /** Repartiendo jugadores por el mapa. */
    SCATTERING,
    /** Jugadores repartidos y congelados, esperando /uhcstart. */
    PREPARED,
    /** Cuenta atrás de inicio. */
    STARTING,
    PLAYING,
    ENDED
}
