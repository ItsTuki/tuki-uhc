package cl.itstuki.uhc.team;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;

/** Un equipo o un grupo de jugadores. */
public final class Party {
    public enum Kind { TEAM, GROUP }

    private final int id;
    private final Kind kind;
    private String name;
    private final NamedTextColor color;
    private final Set<UUID> members = new LinkedHashSet<>();

    Party(int id, Kind kind, String name, NamedTextColor color) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.color = color;
    }

    public int id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public NamedTextColor color() {
        return color;
    }

    public Set<UUID> members() {
        return members;
    }

    /** Nombre interno para equipos del scoreboard. */
    public String scoreboardId() {
        return (kind == Kind.TEAM ? "uhct_" : "uhcg_") + id;
    }
}
