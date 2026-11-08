package cl.itstuki.uhc.api.event;

import java.util.Set;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Se lanza al usar /uhcprepare, antes de repartir a los jugadores. Se puede cancelar. */
public final class UhcPrepareEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<UUID> participants;
    private boolean cancelled;

    public UhcPrepareEvent(Set<UUID> participants) {
        this.participants = participants;
    }

    /** Jugadores que van a participar. */
    public Set<UUID> participants() {
        return participants;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
