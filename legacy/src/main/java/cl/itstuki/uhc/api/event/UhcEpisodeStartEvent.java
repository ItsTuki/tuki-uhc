package cl.itstuki.uhc.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Empieza una nueva parte (episodio). */
public final class UhcEpisodeStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int episode;
    private final boolean pvp;

    public UhcEpisodeStartEvent(int episode, boolean pvp) {
        this.episode = episode;
        this.pvp = pvp;
    }

    public int episode() {
        return episode;
    }

    /** Si el PvP está activo en esta parte. */
    public boolean pvp() {
        return pvp;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
