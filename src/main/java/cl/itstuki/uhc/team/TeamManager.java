package cl.itstuki.uhc.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

/** Equipos y grupos de la partida (no se guardan entre reinicios). */
public final class TeamManager {
    private static final List<NamedTextColor> COLORS = List.of(
            NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.AQUA, NamedTextColor.GOLD, NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_RED, NamedTextColor.DARK_BLUE,
            NamedTextColor.WHITE, NamedTextColor.GRAY);

    private final Map<Integer, Party> teams = new LinkedHashMap<>();
    private final Map<Integer, Party> groups = new LinkedHashMap<>();
    private final Map<UUID, Party> teamOf = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Party> groupOf = new java.util.concurrent.ConcurrentHashMap<>();
    private int nextId = 1;
    private int version;

    /** Cambia cada vez que se modifica algo (para refrescar scoreboards). */
    public int version() {
        return version;
    }

    public Party create(Party.Kind kind, String name) {
        Map<Integer, Party> map = kind == Party.Kind.TEAM ? teams : groups;
        NamedTextColor color = COLORS.get(map.size() % COLORS.size());
        Party party = new Party(nextId++, kind, name, color);
        map.put(party.id(), party);
        version++;
        return party;
    }

    public void delete(Party party) {
        Map<Integer, Party> map = party.kind() == Party.Kind.TEAM ? teams : groups;
        Map<UUID, Party> index = party.kind() == Party.Kind.TEAM ? teamOf : groupOf;
        party.members().forEach(index::remove);
        map.remove(party.id());
        version++;
    }

    public void add(Party party, UUID player) {
        Map<UUID, Party> index = party.kind() == Party.Kind.TEAM ? teamOf : groupOf;
        Party previous = index.get(player);
        if (previous != null) {
            previous.members().remove(player);
        }
        party.members().add(player);
        index.put(player, party);
        version++;
    }

    public void remove(UUID player, Party.Kind kind) {
        Map<UUID, Party> index = kind == Party.Kind.TEAM ? teamOf : groupOf;
        Party previous = index.remove(player);
        if (previous != null) {
            previous.members().remove(player);
            version++;
        }
    }

    public void rename(Party party, String name) {
        party.rename(name);
        version++;
    }

    public void clear() {
        teams.clear();
        groups.clear();
        teamOf.clear();
        groupOf.clear();
        version++;
    }

    public void clearTeams() {
        teams.clear();
        teamOf.clear();
        version++;
    }

    public Collection<Party> teams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Collection<Party> groups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public Collection<Party> all(Party.Kind kind) {
        return kind == Party.Kind.TEAM ? teams() : groups();
    }

    public @Nullable Party team(UUID player) {
        return teamOf.get(player);
    }

    public @Nullable Party group(UUID player) {
        return groupOf.get(player);
    }

    public Optional<Party> byName(Party.Kind kind, String name) {
        return all(kind).stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<Party> byId(int id) {
        Party party = teams.get(id);
        return Optional.ofNullable(party != null ? party : groups.get(id));
    }

    /** Compañeros de equipo (sin incluir al propio jugador). */
    public List<UUID> teammates(UUID player) {
        Party party = teamOf.get(player);
        if (party == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>(party.members());
        result.remove(player);
        return result;
    }

    public boolean sameTeam(UUID a, UUID b) {
        Party party = teamOf.get(a);
        return party != null && party.members().contains(b);
    }

    public boolean hasTeam(UUID player) {
        return teamOf.containsKey(player);
    }

    /** Arma equipos al azar del tamaño indicado con los jugadores dados. */
    public void randomize(List<UUID> players, int size, String namePrefix) {
        List<UUID> pool = new ArrayList<>(players);
        Collections.shuffle(pool);
        int count = Math.max(1, (int) Math.ceil(pool.size() / (double) Math.max(1, size)));
        List<Party> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(create(Party.Kind.TEAM, namePrefix + " " + (teams.size() + 1)));
        }
        for (int i = 0; i < pool.size(); i++) {
            add(created.get(i % count), pool.get(i));
        }
    }

    public Set<UUID> playersWithTeam() {
        return Collections.unmodifiableSet(teamOf.keySet());
    }
}
