package cl.itstuki.uhc.api.event;

import java.util.Set;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** La cuenta atrás terminó y la partida comenzó. */
public final class UhcStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<UUID> participants;

    public UhcStartEvent(Set<UUID> participants) {
        this.participants = participants;
    }

    public Set<UUID> participants() {
        return participants;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
