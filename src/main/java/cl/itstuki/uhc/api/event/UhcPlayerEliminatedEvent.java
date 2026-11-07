package cl.itstuki.uhc.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Un jugador quedó eliminado de la partida. */
public final class UhcPlayerEliminatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID victim;
    private final @Nullable Player killer;
    private final int remainingPlayers;

    public UhcPlayerEliminatedEvent(UUID victim, @Nullable Player killer, int remainingPlayers) {
        this.victim = victim;
        this.killer = killer;
        this.remainingPlayers = remainingPlayers;
    }

    public UUID victim() {
        return victim;
    }

    /** Jugador que lo mató, o null si murió por otra causa. */
    public @Nullable Player killer() {
        return killer;
    }

    public int remainingPlayers() {
        return remainingPlayers;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
