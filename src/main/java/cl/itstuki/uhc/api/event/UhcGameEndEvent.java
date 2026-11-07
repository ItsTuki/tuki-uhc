package cl.itstuki.uhc.api.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** La partida terminó. La lista de ganadores puede estar vacía (sin ganador o detenida). */
public final class UhcGameEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final List<UUID> winners;
    private final long durationSeconds;

    public UhcGameEndEvent(List<UUID> winners, long durationSeconds) {
        this.winners = winners;
        this.durationSeconds = durationSeconds;
    }

    public List<UUID> winners() {
        return winners;
    }

    public long durationSeconds() {
        return durationSeconds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
